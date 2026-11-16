package com.deivid.despertar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val Colores = darkColorScheme(
    primary = Paleta.Cian,
    onPrimary = Paleta.Fondo,
    secondary = Paleta.Violeta,
    background = Paleta.Fondo,
    onBackground = Paleta.Menta,
    surface = Paleta.Superficie,
    onSurface = Paleta.Menta,
    surfaceVariant = Paleta.Superficie2,
    onSurfaceVariant = Paleta.Apagado,
    error = Paleta.Rojo,
    outline = Paleta.Violeta,
)

/** Tema siempre oscuro, con la letra un 25 % más pequeña. */
@Composable
fun SoporteTheme(contenido: @Composable () -> Unit) {
    val d = LocalDensity.current
    MaterialTheme(colorScheme = Colores) {
        CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * 0.75f)) {
            contenido()
        }
    }
}
