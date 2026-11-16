package com.deivid.despertar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.deivid.despertar.ui.theme.Boton
import com.deivid.despertar.ui.theme.Paleta
import kotlinx.coroutines.delay

private val Verde = Color(0xFF3DDC84)
private val Ambar = Color(0xFFFFB300)

/** Explicación corta debajo del nombre, en letra pequeña (norma de David). */
@Composable
fun Explicacion(texto: String) {
    Text(texto, color = Paleta.Apagado, fontSize = 12.sp, lineHeight = 15.sp)
}

/** Flecha grande de los desplegables: círculo de ~26 dp con flecha de ~23 dp, del color de la ficha. */
@Composable
fun FlechaGrande(abierta: Boolean, color: Color) {
    val giro by animateFloatAsState(if (abierta) 180f else 0f, label = "giro")
    Box(Modifier.size(26.dp).background(color.copy(alpha = 0.22f), CircleShape).border(1.dp, color.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = color, modifier = Modifier.size(23.dp).graphicsLayer { rotationZ = giro })
    }
}

/** Interruptor al 62,5 % del normal. */
@Composable
fun InterruptorPequeno(activo: Boolean, cambiar: (Boolean) -> Unit) {
    Switch(activo, cambiar, Modifier.scale(0.625f), colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White, checkedTrackColor = Verde,
        uncheckedThumbColor = Paleta.Apagado, uncheckedTrackColor = Paleta.Superficie2, uncheckedBorderColor = Paleta.Apagado))
}

/** Tarjeta con interruptor: título en negrita, explicación debajo y toda la fila se puede pulsar. */
@Composable
fun FilaInterruptor(titulo: String, explicacion: String, activo: Boolean, cambiar: (Boolean) -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(forma).background(Paleta.Superficie2)
        .border(1.dp, (if (activo) Verde else Paleta.Rojo).copy(alpha = 0.45f), forma)
        .clickable { cambiar(!activo) }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Paleta.Menta, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Explicacion(explicacion)
        }
        InterruptorPequeno(activo, cambiar)
    }
}

/** Ficha "Servicios en segundo plano": interruptor general junto a la flecha y uno por servicio dentro. */
@Composable
fun FichaServicios() {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    val estado = remember(ver) { Servicios.TODOS.associateWith { Servicios.activo(ctx, it) } }
    val todos = estado.values.all { it }
    val alguno = estado.values.any { it }
    var abierta by remember { mutableStateOf(true) }
    var estadoVigilante by remember { mutableStateOf(VigilanteService.estado) }
    LaunchedEffect(Unit) { while (true) { estadoVigilante = VigilanteService.estado; delay(2000) } }

    val color = if (alguno) Verde else Paleta.Rojo
    val forma = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(forma).background(color.copy(alpha = 0.08f)).border(1.dp, color.copy(alpha = 0.7f), forma)) {
        Row(Modifier.fillMaxWidth().clickable { abierta = !abierta }.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Servicios en segundo plano", color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Explicacion(when {
                    todos -> "Todos encendidos"
                    !alguno -> "Todos apagados: la app no hace nada con ella cerrada"
                    else -> "Algunos apagados"
                })
            }
            InterruptorPequeno(todos) { Servicios.todos(ctx, it); ver++ }
            FlechaGrande(abierta, color)
        }
        if (abierta) Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
            FilaInterruptor("Modo vigilante",
                "Enciéndelo SOLO en el móvil que se queda en casa en la Wi-Fi (mejor enchufado). Cada 10 s mira si alguien " +
                    "ha pulsado Despertar y manda el paquete mágico a los PC de su misma red. Así no hace falta otro PC encendido.",
                estado[Servicios.VIGILANTE] == true) { Servicios.poner(ctx, Servicios.VIGILANTE, it); ver++ }
            if (estado[Servicios.VIGILANTE] == true)
                Text("Estado: $estadoVigilante", color = Paleta.CianClaro, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
            FilaInterruptor("Arrancar al encender el móvil",
                "Si el móvil se reinicia (o se actualiza la app), vuelve a poner en marcha el modo vigilante solo.",
                estado[Servicios.ARRANQUE] == true) { Servicios.poner(ctx, Servicios.ARRANQUE, it); ver++ }
        }
    }
}

// ---------------------------------------------------------------------
//  Permisos: solo en la pantalla principal (norma de David)
// ---------------------------------------------------------------------
private fun faltaNotificaciones(ctx: Context) = Build.VERSION.SDK_INT >= 33 &&
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

private fun faltaBateria(ctx: Context) =
    !ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

@SuppressLint("BatteryLife")
@Composable
fun PanelPermisos() {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    // Se vuelven a mirar al volver a la app (por si se han dado en Ajustes de Android).
    val ciclo = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ciclo) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) ver++ }
        ciclo.addObserver(o); onDispose { ciclo.removeObserver(o) }
    }
    val pedirNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ver++ }

    val vigilante = remember(ver) { Servicios.activo(ctx, Servicios.VIGILANTE) }
    val faltan = remember(ver) {
        buildList {
            if (vigilante && faltaNotificaciones(ctx)) add(Triple("Notificaciones", "Para el aviso fijo del modo vigilante") {
                if (Build.VERSION.SDK_INT >= 33) pedirNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
            if (vigilante && faltaBateria(ctx)) add(Triple("Sin límite de batería", "Para que Android no pare el modo vigilante") {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }
            })
        }
    }

    if (faltan.isEmpty()) {
        Text("✅ Permisos OK", color = Verde, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
    } else {
        val forma = RoundedCornerShape(14.dp)
        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(forma).background(Paleta.Rojo.copy(alpha = 0.14f))
            .border(2.dp, Paleta.Rojo, forma).padding(12.dp)) {
            Text("Faltan permisos", color = Paleta.Rojo, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            faltan.forEach { (nombre, para, accion) ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(nombre, color = Paleta.Menta, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Explicacion(para)
                    }
                    Boton("DAR PERMISO", Modifier.width(130.dp), color = Paleta.Rojo) { accion(); ver++ }
                }
            }
        }
    }
}
