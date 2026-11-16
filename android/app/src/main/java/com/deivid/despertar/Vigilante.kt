package com.deivid.despertar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetAddress
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * SERVICIOS EN SEGUNDO PLANO (norma de David): cada cosa que la app hace con ella cerrada
 * tiene su interruptor, y hay uno general que lo enciende o apaga todo.
 */
object Servicios {
    const val VIGILANTE = "vigilante"   // servicio fijo que recoge las órdenes y manda el paquete mágico
    const val ARRANQUE = "arranque"     // volver a encender el vigilante al reiniciar el móvil

    val TODOS = listOf(VIGILANTE, ARRANQUE)

    private fun p(ctx: Context) = ctx.getSharedPreferences("servicios", Context.MODE_PRIVATE)

    /** El vigilante viene APAGADO (solo hace falta en el móvil que se queda en casa); el arranque, encendido. */
    fun activo(ctx: Context, s: String) = p(ctx).getBoolean(s, s == ARRANQUE)

    fun poner(ctx: Context, s: String, on: Boolean) {
        p(ctx).edit().putBoolean(s, on).apply()
        aplicar(ctx, s)
    }

    fun todos(ctx: Context, on: Boolean) = TODOS.forEach { poner(ctx, it, on) }

    fun aplicarTodos(ctx: Context) = TODOS.forEach { aplicar(ctx, it) }

    private fun aplicar(ctx: Context, s: String) {
        val on = activo(ctx, s)
        runCatching {
            when (s) {
                VIGILANTE -> if (on) VigilanteService.arrancar(ctx) else ctx.stopService(Intent(ctx, VigilanteService::class.java))
                ARRANQUE -> ctx.packageManager.setComponentEnabledSetting(
                    ComponentName(ctx, ArranqueReceiver::class.java),
                    if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP)
            }
        }
    }
}

/** Al encender el móvil vuelve a poner en marcha el vigilante (si está encendido). */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (Servicios.activo(ctx, Servicios.VIGILANTE)) VigilanteService.arrancar(ctx)
        }
    }
}

/**
 * MODO VIGILANTE: el móvil se queda en casa en la Wi-Fi (mejor enchufado). Cada 10 s pregunta a Supabase
 * si hay órdenes de "Despertar" y, para los equipos de SU MISMA red, manda el paquete mágico y marca la
 * orden como enviada. Así no hace falta otro PC encendido.
 */
class VigilanteService : Service() {
    companion object {
        private const val CANAL = "vigilante"
        private const val ID = 7
        const val INTERVALO_MS = 10_000L

        @Volatile var estado: String = "Apagado"
            private set

        fun arrancar(ctx: Context) {
            val i = Intent(ctx, VigilanteService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var despierto: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var enviados = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Modo vigilante", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Aviso fijo mientras el móvil vigila la red para despertar los equipos"
        })
        aviso("Arrancando…")
        // El móvil no se duerme ni apaga la Wi-Fi mientras vigila.
        despierto = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "despertar:vigilante").apply { setReferenceCounted(false); acquire() }
        @Suppress("DEPRECATION")
        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "despertar:vigilante").apply { setReferenceCounted(false); acquire() }

        alcance.launch {
            while (isActive) {
                runCatching { ciclo() }.onFailure { aviso("Sin conexión con la red: ${it.message ?: "error"}") }
                delay(INTERVALO_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        alcance.cancel()
        runCatching { despierto?.release() }
        runCatching { wifiLock?.release() }
        estado = "Apagado"
        super.onDestroy()
    }

    private suspend fun ciclo() {
        val ajustes = Ajustes.leer(this)
        if (!ajustes.completos) { aviso("Falta poner la conexión en Ajustes"); return }
        val red = redWifi()
        if (red == null) { aviso("Esperando Wi-Fi…"); return }

        val api = Api(ajustes)
        val quien = "vigilante (${Build.MODEL})"
        for (o in api.ordenesPendientes()) {
            if (!red.contiene(o.ipLocal)) continue   // es de otra red: que la atienda su agente
            var ok = false
            repeat(3) { ok = PaqueteMagico.enviar(this, o.mac, o.broadcast, o.ipLocal) || ok; delay(300) }
            if (ok) {
                api.marcarEnviada(o.ordenId, quien)
                enviados++
            }
        }
        val hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        aviso("Vigilando ${red.texto} · revisado a las $hora" + if (enviados > 0) " · equipos despertados: $enviados" else "")
    }

    /** Red IPv4 de la Wi-Fi actual (dirección y máscara), o null si no hay Wi-Fi. */
    private fun redWifi(): RedLocal? {
        val cm = getSystemService(ConnectivityManager::class.java)
        val n = cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        } ?: return null
        val la = cm.getLinkProperties(n)?.linkAddresses?.firstOrNull { it.address is Inet4Address } ?: return null
        return RedLocal(la.address as Inet4Address, la.prefixLength)
    }

    private fun aviso(texto: String) {
        estado = texto
        val abrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_dibujo)
            .setContentTitle("Modo vigilante")
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setOngoing(true)
            .setContentIntent(abrir)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, n)
    }
}

class RedLocal(private val ip: Inet4Address, private val prefijo: Int) {
    private fun num(a: InetAddress) = a.address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
    private val mascara = if (prefijo == 0) 0L else (0xFFFFFFFFL shl (32 - prefijo)) and 0xFFFFFFFFL
    val texto get() = "${ip.hostAddress}/$prefijo"

    /** ¿Esa IP está en esta misma red? Si el equipo no tiene IP apuntada, se intenta igual. */
    fun contiene(otra: String): Boolean {
        if (otra.isBlank()) return true
        val a = runCatching { InetAddress.getByName(otra) }.getOrNull() as? Inet4Address ?: return true
        return (num(a) and mascara) == (num(ip) and mascara)
    }
}
