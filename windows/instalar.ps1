# Instalador del Agente WOL. Ejecutar como administrador (usa instalar.bat).
$ErrorActionPreference = 'Stop'
$Origen  = Split-Path -Parent $MyInvocation.MyCommand.Path
$Destino = Join-Path $env:ProgramData 'AgenteWOL'
$Tarea   = 'AgenteWOL'

Write-Host '== Agente WOL - instalación ==' -ForegroundColor Cyan

if (-not (Test-Path (Join-Path $Origen 'config.json'))) {
    Write-Host 'Falta config.json. Descárgalo de la web (botón config.json) y ponlo junto a este archivo.' -ForegroundColor Red
    exit 1
}

# 1. Copiar archivos
New-Item -ItemType Directory -Force -Path $Destino | Out-Null
Copy-Item (Join-Path $Origen 'AgenteWOL.ps1'), (Join-Path $Origen 'config.json') -Destination $Destino -Force
Write-Host "Archivos copiados a $Destino"

# 2. Activar Wake on LAN en los adaptadores de red físicos
Get-NetAdapter -Physical | ForEach-Object {
    $n = $_.Name
    try { Set-NetAdapterPowerManagement -Name $n -WakeOnMagicPacket Enabled -ErrorAction Stop; Write-Host "WOL activado en '$n'" }
    catch { Write-Host "No se pudo activar WOL por energía en '$n' (puede que no lo soporte)" -ForegroundColor Yellow }
    foreach ($prop in 'Wake on Magic Packet', 'Activación con Magic Packet', 'Reactivar con Magic Packet') {
        try { Set-NetAdapterAdvancedProperty -Name $n -DisplayName $prop -DisplayValue 'Enabled' -ErrorAction Stop } catch { }
        try { Set-NetAdapterAdvancedProperty -Name $n -DisplayName $prop -DisplayValue 'Habilitado' -ErrorAction Stop } catch { }
    }
}
# Dejar que la tarjeta despierte el equipo
try { powercfg /deviceenablewake (Get-NetAdapter -Physical | Where-Object Status -eq 'Up' | Select-Object -First 1).InterfaceDescription 2>$null | Out-Null } catch { }

# 3. Desactivar el inicio rápido (impide WOL desde "apagado")
try {
    Set-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Power' -Name HiberbootEnabled -Value 0 -Type DWord
    Write-Host 'Inicio rápido desactivado'
} catch { Write-Host 'No se pudo desactivar el inicio rápido' -ForegroundColor Yellow }

# 4. Tarea programada al arrancar, como SYSTEM, reiniciándose si falla
$accion = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$Destino\AgenteWOL.ps1`""
$disparadores = @(
    (New-ScheduledTaskTrigger -AtStartup),
    (New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 5))
)
$ajustes = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
$usuario = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest

Unregister-ScheduledTask -TaskName $Tarea -Confirm:$false -ErrorAction SilentlyContinue
Register-ScheduledTask -TaskName $Tarea -Action $accion -Trigger $disparadores -Settings $ajustes -Principal $usuario | Out-Null
Start-ScheduledTask -TaskName $Tarea
Write-Host "Tarea '$Tarea' creada y en marcha." -ForegroundColor Green

# 5. Prueba
Start-Sleep -Seconds 5
$log = Join-Path $Destino 'agente.log'
if (Test-Path $log) { Write-Host "`nÚltimas líneas del registro:"; Get-Content $log -Tail 5 }
Write-Host "`nListo. El equipo debería aparecer en la web como Encendido."
Write-Host 'Recuerda activar Wake on LAN / Power On by PCI-E en la BIOS.' -ForegroundColor Yellow
