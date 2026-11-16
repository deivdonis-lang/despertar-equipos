package com.deivid.despertar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivid.despertar.ui.theme.Boton
import com.deivid.despertar.ui.theme.Marco
import com.deivid.despertar.ui.theme.Paleta
import com.deivid.despertar.ui.theme.SoporteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoporteTheme {
                Surface(Modifier.fillMaxSize(), color = Paleta.Fondo) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var ajustes by remember { mutableStateOf(Ajustes.leer(ctx)) }
    var enAjustes by remember { mutableStateOf(!ajustes.completos) }
    LaunchedEffect(Unit) { Servicios.aplicarTodos(ctx) }

    if (enAjustes) {
        BackHandler(enabled = ajustes.completos) { enAjustes = false }
        PantallaAjustes(ajustes) { nuevos ->
            nuevos.guardar(ctx); ajustes = Ajustes.leer(ctx); enAjustes = false
        }
    } else {
        Portada(ajustes) { enAjustes = true }
    }
}

// ---------------------------------------------------------------------
//  Iconos dibujados
// ---------------------------------------------------------------------
@Composable
fun SimboloEncendido(tam: Dp, color: Color = Color.White) {
    Canvas(Modifier.size(tam)) {
        val g = size.minDimension * 0.11f
        val r = size.minDimension * 0.34f
        val c = center
        drawArc(color, startAngle = -60f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r + g * 0.4f), size = Size(r * 2, r * 2),
            style = Stroke(g, cap = StrokeCap.Round))
        drawLine(color, Offset(c.x, c.y - r - g * 0.2f), Offset(c.x, c.y), g, StrokeCap.Round)
    }
}

@Composable
fun Pastilla(tam: Dp, encendido: Boolean = true, contenido: @Composable () -> Unit = { SimboloEncendido(tam * 0.7f) }) {
    Box(
        Modifier.size(tam).clip(RoundedCornerShape(tam * 0.3f))
            .background(if (encendido) Paleta.Degradado else androidx.compose.ui.graphics.SolidColor(Paleta.Superficie2)),
        contentAlignment = Alignment.Center,
    ) { contenido() }
}

@Composable
fun Cabecera(red: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Paleta.DegradadoIcono),
            contentAlignment = Alignment.Center) { SimboloEncendido(30.dp) }
        Text("v" + BuildConfig.VERSION_NAME, color = Paleta.Apagado, fontSize = 11.sp,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp).align(Alignment.Bottom))
        Column(Modifier.weight(1f)) {
            Text("Despertar Equipos", color = Paleta.Titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (red.isNotBlank()) Text("Red: $red", color = Paleta.Apagado, fontSize = 13.sp)
        }
    }
}

// ---------------------------------------------------------------------
//  Portada: equipos en casillas cuadradas de 3 columnas
// ---------------------------------------------------------------------
@Composable
fun Portada(ajustes: Ajustes, abrirAjustes: () -> Unit) {
    val ctx = LocalContext.current
    val api = remember(ajustes) { Api(ajustes) }
    val alcance = rememberCoroutineScope()
    var red by remember { mutableStateOf("") }
    var equipos by remember { mutableStateOf<List<Equipo>?>(null) }
    var error by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var elegido by remember { mutableStateOf<Equipo?>(null) }

    LaunchedEffect(ajustes) {
        red = runCatching { api.entrar() }.getOrDefault("")
        while (true) {
            try { equipos = api.equipos(); error = "" }
            catch (e: Exception) { error = e.message ?: "Sin conexión" }
            delay(5000)
        }
    }

    fun despertar(e: Equipo) {
        alcance.launch {
            mensaje = "Enviando a ${e.nombre}…"
            val local = runCatching { PaqueteMagico.enviar(ctx, e) }.getOrDefault(false)
            val nube = runCatching { api.pedirDespertar(e) }
            mensaje = buildString {
                append(if (local) "Paquete mágico enviado por la Wi-Fi. " else "")
                append(if (nube.isSuccess) "Orden enviada a los agentes de la red de ${e.nombre}."
                       else "No se pudo enviar la orden: ${nube.exceptionOrNull()?.message}")
            }
            runCatching { equipos = api.equipos() }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Cabecera(red)
        PanelPermisos()
        if (error.isNotBlank()) Text(error, color = Paleta.Rojo, modifier = Modifier.padding(bottom = 8.dp))
        if (mensaje.isNotBlank()) Text(mensaje, color = Paleta.CianClaro, modifier = Modifier.padding(bottom = 8.dp))

        val lista = equipos
        Marco("Equipos", Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (lista == null) {
                    item(span = { GridItemSpan(3) }) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Paleta.Cian)
                        }
                    }
                } else if (lista.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Text("Todavía no hay equipos. Instala el agente en cada PC desde la web.",
                            color = Paleta.Apagado)
                    }
                }
                items(lista ?: emptyList(), key = { it.id }) { e ->
                    CasillaEquipo(e) { elegido = e }
                }
                item(key = "ajustes") {
                    Casilla("Ajustes", "Servidor y clave", true, abrirAjustes) {
                        Pastilla(42.dp) { Text("⚙", color = Color.White, fontSize = 24.sp) }
                    }
                }
            }
        }
    }

    elegido?.let { e ->
        val actual = equipos?.firstOrNull { it.id == e.id } ?: e
        DialogoEquipo(actual, onCerrar = { elegido = null }, onDespertar = { despertar(actual); elegido = null })
    }
}

@Composable
fun Casilla(titulo: String, subtitulo: String, activo: Boolean, onClick: () -> Unit, icono: @Composable () -> Unit) {
    val forma = RoundedCornerShape(14.dp)
    Column(
        Modifier.aspectRatio(1f).clip(forma).background(Paleta.Superficie)
            .border(1.dp, if (activo) Paleta.Cian.copy(alpha = 0.5f) else Color(0xFF252D52), forma)
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icono()
        Spacer(Modifier.height(6.dp))
        Text(titulo, color = Paleta.Titulo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(subtitulo, color = if (activo) Paleta.CianClaro else Paleta.Apagado, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

fun estadoTexto(e: Equipo) = when {
    e.encendido -> "Encendido"
    e.ordenEstado == "enviada" -> "Despertando…"
    e.ordenEstado == "pendiente" -> "Esperando…"
    else -> "Apagado"
}

@Composable
fun CasillaEquipo(e: Equipo, onClick: () -> Unit) {
    Casilla(e.nombre, estadoTexto(e), e.encendido, onClick) {
        Box {
            Pastilla(42.dp, e.encendido)
            Box(Modifier.size(10.dp).align(Alignment.TopEnd).clip(CircleShape)
                .background(if (e.encendido) Paleta.Cian else Color(0xFF4A5070)))
        }
    }
}

fun haceCuanto(iso: String): String = runCatching {
    val s = Duration.between(OffsetDateTime.parse(iso), OffsetDateTime.now()).seconds.coerceAtLeast(0)
    when {
        s < 60 -> "hace $s s"
        s < 3600 -> "hace ${s / 60} min"
        s < 86400 -> "hace ${s / 3600} h"
        else -> "hace ${s / 86400} días"
    }
}.getOrDefault(iso)

@Composable
fun DialogoEquipo(e: Equipo, onCerrar: () -> Unit, onDespertar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Paleta.Marco,
        title = { Text(e.nombre, color = Paleta.Titulo, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(estadoTexto(e), color = if (e.encendido) Paleta.CianClaro else Paleta.Apagado)
                Dato("IP local", e.ipLocal)
                Dato("MAC", e.mac)
                Dato("Broadcast", e.broadcast)
                Dato("IP pública", e.ipPublica)
                if (e.usuario.isNotBlank()) Dato("Usuario", e.usuario)
                if (e.so.isNotBlank()) Dato("Sistema", e.so)
                Dato("Visto", haceCuanto(e.ultimoVisto))
            }
        },
        confirmButton = {
            Boton("Despertar", Modifier.width(140.dp), activo = !e.encendido, onClick = onDespertar)
        },
        dismissButton = {
            Boton("Cerrar", Modifier.width(110.dp), color = Paleta.Apagado, onClick = onCerrar)
        },
    )
}

@Composable
fun Dato(etiqueta: String, valor: String) {
    Row {
        Text(etiqueta, color = Paleta.Apagado, fontSize = 13.sp, modifier = Modifier.width(90.dp))
        Text(valor.ifBlank { "—" }, color = Paleta.Menta, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------
//  Ajustes
// ---------------------------------------------------------------------
@Composable
fun PantallaAjustes(inicial: Ajustes, onGuardar: (Ajustes) -> Unit) {
    var url by remember { mutableStateOf(inicial.url) }
    var key by remember { mutableStateOf(inicial.anonKey) }
    var clave by remember { mutableStateOf(inicial.clave) }
    var error by remember { mutableStateOf("") }
    var probando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding()) {
        Cabecera("")
        Marco("Conexión") {
            Campo("URL de Supabase", url, { url = it }, "https://xxxx.supabase.co")
            Campo("Anon key", key, { key = it }, "eyJ…")
            Campo("Clave de la red", clave, { clave = it }, "la que creaste en la web", secreto = true)
            if (error.isNotBlank()) Text(error, color = Paleta.Rojo, modifier = Modifier.padding(vertical = 6.dp))
            Spacer(Modifier.height(8.dp))
            Boton(if (probando) "Comprobando…" else "Guardar y entrar", Modifier.fillMaxWidth(),
                activo = !probando && url.isNotBlank() && key.isNotBlank() && clave.isNotBlank()) {
                val a = Ajustes(url.trim().trimEnd('/'), key.trim(), clave)
                probando = true; error = ""
                alcance.launch {
                    runCatching { Api(a).entrar() }
                        .onSuccess { onGuardar(a) }
                        .onFailure { error = it.message ?: "No se pudo conectar" }
                    probando = false
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        FichaServicios()
        Spacer(Modifier.height(14.dp))
        Marco("Cómo funciona") {
            Text(
                "Los PC con el agente instalado suben su IP y MAC a la web cada pocos segundos. " +
                "Al pulsar Despertar, el móvil envía el paquete mágico por la Wi-Fi (si estás en la misma red) " +
                "y además deja una orden para que lo despierte cualquier PC encendido de esa red o un móvil con el modo vigilante en esa Wi-Fi.",
                color = Paleta.Menta, fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun Campo(etiqueta: String, valor: String, cambiar: (String) -> Unit, pista: String, secreto: Boolean = false) {
    OutlinedTextField(
        value = valor, onValueChange = cambiar, singleLine = true,
        label = { Text(etiqueta) }, placeholder = { Text(pista, color = Paleta.Apagado) },
        visualTransformation = if (secreto) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secreto) KeyboardType.Password else KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Paleta.Cian, unfocusedBorderColor = Color(0xFF2C3560),
            focusedLabelColor = Paleta.CianClaro, unfocusedLabelColor = Paleta.Apagado,
            focusedTextColor = Paleta.Menta, unfocusedTextColor = Paleta.Menta,
            cursorColor = Paleta.Cian,
            focusedContainerColor = Paleta.Superficie2, unfocusedContainerColor = Paleta.Superficie2,
        ),
    )
}
