# =====================================================================
#  Agente WOL (Despertar Equipos) — Windows 10 / 11, PowerShell 5.1+
#  - Cada N segundos sube a la web el nombre, MAC, IP y broadcast del PC.
#  - Recoge las órdenes de "despertar" de su red y envía el paquete mágico.
#  Lo arranca una tarea programada (como SYSTEM) al encender el PC.
# =====================================================================
param([switch]$UnaVez)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Carpeta = Split-Path -Parent $MyInvocation.MyCommand.Path
$Log     = Join-Path $Carpeta 'agente.log'
$Config  = Get-Content (Join-Path $Carpeta 'config.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$Url       = $Config.url.TrimEnd('/')
$Key       = $Config.anonKey
$Clave     = $Config.clave
$Intervalo = if ($Config.intervalo) { [int]$Config.intervalo } else { 15 }

function Escribir-Log([string]$texto) {
    try {
        if ((Test-Path $Log) -and (Get-Item $Log).Length -gt 1MB) {
            Move-Item $Log "$Log.old" -Force
        }
        Add-Content -Path $Log -Value ("{0:yyyy-MM-dd HH:mm:ss}  {1}" -f (Get-Date), $texto) -Encoding UTF8
    } catch { }
}

function Llamar-Rpc([string]$funcion, [hashtable]$datos) {
    $cuerpo = [Text.Encoding]::UTF8.GetBytes(($datos | ConvertTo-Json -Compress))
    $cab = @{ apikey = $Key; Authorization = "Bearer $Key" }
    Invoke-RestMethod -Method Post -Uri "$Url/rest/v1/rpc/$funcion" -Headers $cab `
        -ContentType 'application/json; charset=utf-8' -Body $cuerpo -TimeoutSec 20
}

function Ip-A-Numero([string]$ip) {
    $b = [Net.IPAddress]::Parse($ip).GetAddressBytes(); [Array]::Reverse($b)
    [BitConverter]::ToUInt32($b, 0)
}
function Numero-A-Ip([uint32]$n) {
    $b = [BitConverter]::GetBytes($n); [Array]::Reverse($b)
    ([Net.IPAddress]$b).ToString()
}

# Adaptador con la ruta por defecto (el que sale a Internet).
function Obtener-Red {
    $ruta = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
        Sort-Object { $_.RouteMetric + (Get-NetIPInterface -InterfaceIndex $_.InterfaceIndex -AddressFamily IPv4).InterfaceMetric } |
        Select-Object -First 1
    if (-not $ruta) { return $null }
    $ip  = Get-NetIPAddress -InterfaceIndex $ruta.InterfaceIndex -AddressFamily IPv4 |
           Where-Object { $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1
    $nic = Get-NetAdapter -InterfaceIndex $ruta.InterfaceIndex
    if (-not $ip -or -not $nic) { return $null }

    $pref    = [int]$ip.PrefixLength
    $tamano  = [uint64][math]::Pow(2, 32 - $pref)          # direcciones de la subred
    $mascNum = [uint32]([uint64]4294967296 - $tamano)
    $ipNum   = [uint64](Ip-A-Numero $ip.IPAddress)
    $bcNum   = [uint32]([uint64]([math]::Floor($ipNum / $tamano)) * $tamano + $tamano - 1)

    [pscustomobject]@{
        Ip        = $ip.IPAddress
        Mascara   = Numero-A-Ip $mascNum
        Broadcast = Numero-A-Ip $bcNum
        Mac       = ($nic.MacAddress -replace '-', ':').ToUpper()
        Adaptador = $nic.Name
    }
}

function Enviar-PaqueteMagico([string]$mac, [string[]]$destinos) {
    $hex = $mac -replace '[^0-9A-Fa-f]', ''
    if ($hex.Length -ne 12) { throw "MAC no válida: $mac" }
    $macBytes = for ($i = 0; $i -lt 12; $i += 2) { [Convert]::ToByte($hex.Substring($i, 2), 16) }
    $paquete = New-Object byte[] 102
    for ($i = 0; $i -lt 6; $i++) { $paquete[$i] = 0xFF }
    for ($r = 0; $r -lt 16; $r++) { [Array]::Copy([byte[]]$macBytes, 0, $paquete, 6 + $r * 6, 6) }

    $udp = New-Object Net.Sockets.UdpClient
    try {
        $udp.EnableBroadcast = $true
        foreach ($d in ($destinos | Where-Object { $_ } | Select-Object -Unique)) {
            foreach ($puerto in 9, 7) {
                [void]$udp.Send($paquete, $paquete.Length, $d, $puerto)
            }
        }
    } finally { $udp.Close() }
}

function Ciclo {
    $red = Obtener-Red
    if (-not $red) { Escribir-Log 'Sin red, reintento luego.'; return }

    $so = (Get-CimInstance Win32_OperatingSystem).Caption
    $usuario = try { (Get-CimInstance Win32_ComputerSystem).UserName } catch { $null }

    [void](Llamar-Rpc 'reportar_equipo' @{
        p_clave = $Clave; p_nombre = $env:COMPUTERNAME; p_mac = $red.Mac
        p_ip_local = $red.Ip; p_mascara = $red.Mascara; p_broadcast = $red.Broadcast
        p_so = $so; p_usuario = $usuario
    })

    $ordenes = @(Llamar-Rpc 'ordenes_pendientes' @{ p_clave = $Clave })
    foreach ($o in $ordenes) {
        if (-not $o.orden_id) { continue }
        if ($o.mac -eq $red.Mac) { continue }                       # soy yo
        if ($o.broadcast -and $o.broadcast -ne $red.Broadcast) { continue }  # otra red local
        try {
            Enviar-PaqueteMagico $o.mac @($red.Broadcast, '255.255.255.255', $o.ip_local)
            [void](Llamar-Rpc 'marcar_enviada' @{ p_clave = $Clave; p_orden = $o.orden_id; p_quien = $env:COMPUTERNAME })
            Escribir-Log "Paquete mágico enviado a $($o.nombre) ($($o.mac))"
        } catch { Escribir-Log "Error despertando $($o.nombre): $_" }
    }
}

Escribir-Log "Agente iniciado (cada $Intervalo s)."
do {
    try { Ciclo } catch { Escribir-Log "Error: $($_.Exception.Message)" }
    if ($UnaVez) { break }
    Start-Sleep -Seconds $Intervalo
} while ($true)
