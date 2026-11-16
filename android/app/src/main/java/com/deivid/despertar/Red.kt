package com.deivid.despertar

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

data class Ajustes(val url: String, val anonKey: String, val clave: String) {
    val completos get() = url.isNotBlank() && anonKey.isNotBlank() && clave.isNotBlank()

    companion object {
        fun leer(ctx: Context): Ajustes {
            val p = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
            return Ajustes(p.getString("url", "")!!, p.getString("key", "")!!, p.getString("clave", "")!!)
        }
    }

    fun guardar(ctx: Context) {
        ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE).edit()
            .putString("url", url.trim().trimEnd('/'))
            .putString("key", anonKey.trim())
            .putString("clave", clave)
            .apply()
    }
}

data class Equipo(
    val id: String,
    val nombre: String,
    val mac: String,
    val ipLocal: String,
    val broadcast: String,
    val ipPublica: String,
    val so: String,
    val usuario: String,
    val ultimoVisto: String,
    val encendido: Boolean,
    val ordenEstado: String,
)

class Api(private val a: Ajustes) {

    private suspend fun rpc(funcion: String, datos: JSONObject): String = withContext(Dispatchers.IO) {
        val c = URL("${a.url.trimEnd('/')}/rest/v1/rpc/$funcion").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.doOutput = true
            c.setRequestProperty("apikey", a.anonKey)
            c.setRequestProperty("Authorization", "Bearer ${a.anonKey}")
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(datos.toString().toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode in 200..299
            val texto = (if (ok) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (!ok) {
                val msg = runCatching { JSONObject(texto).optString("message") }.getOrNull()
                throw Exception(if (msg.isNullOrBlank()) "Error ${c.responseCode}" else msg)
            }
            texto
        } finally { c.disconnect() }
    }

    suspend fun entrar(): String =
        rpc("entrar_red", JSONObject().put("p_clave", a.clave)).trim().trim('"')

    suspend fun equipos(): List<Equipo> {
        val arr = JSONArray(rpc("listar_equipos", JSONObject().put("p_clave", a.clave)))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun s(k: String) = if (o.isNull(k)) "" else o.optString(k)
            Equipo(
                id = s("id"), nombre = s("nombre"), mac = s("mac"), ipLocal = s("ip_local"),
                broadcast = s("broadcast"), ipPublica = s("ip_publica"), so = s("so"),
                usuario = s("usuario"), ultimoVisto = s("ultimo_visto"),
                encendido = o.optBoolean("encendido"), ordenEstado = s("orden_estado"),
            )
        }
    }

    suspend fun pedirDespertar(e: Equipo) {
        rpc("pedir_despertar", JSONObject()
            .put("p_clave", a.clave).put("p_equipo", e.id).put("p_quien", "móvil"))
    }
}

object PaqueteMagico {
    /** Envía el paquete mágico por la red local (sirve si el móvil está en la misma Wi-Fi). */
    suspend fun enviar(ctx: Context, e: Equipo): Boolean = withContext(Dispatchers.IO) {
        val hex = e.mac.filter { it.isLetterOrDigit() }
        if (hex.length != 12) return@withContext false
        val mac = ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val datos = ByteArray(102)
        for (i in 0 until 6) datos[i] = 0xFF.toByte()
        for (r in 0 until 16) System.arraycopy(mac, 0, datos, 6 + r * 6, 6)

        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val candado = wifi.createMulticastLock("wol").apply { setReferenceCounted(false); acquire() }
        var alguno = false
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                for (destino in listOf(e.broadcast, "255.255.255.255", e.ipLocal).filter { it.isNotBlank() }.distinct()) {
                    for (puerto in listOf(9, 7)) {
                        runCatching {
                            s.send(DatagramPacket(datos, datos.size, InetAddress.getByName(destino), puerto))
                            alguno = true
                        }
                    }
                }
            }
        } finally { candado.release() }
        alguno
    }
}
