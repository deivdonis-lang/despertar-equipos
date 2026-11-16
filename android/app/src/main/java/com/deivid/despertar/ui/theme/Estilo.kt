package com.deivid.despertar.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Paleta {
    val Fondo = Color(0xFF0B0E1A)
    val Superficie = Color(0xFF141A2E)
    val Superficie2 = Color(0xFF1C2340)
    val Marco = Color(0xFF141932)
    val Violeta = Color(0xFFA66BFF)
    val Titulo = Color(0xFFC9A6FF)
    val Cian = Color(0xFF00D1FF)
    val CianClaro = Color(0xFF7CE7FF)
    val Menta = Color(0xFF8BF0D8)
    val Apagado = Color(0xFF8E97BF)
    val Rojo = Color(0xFFFF4D6D)
    val Azul = Color(0xFF3B6BFF)

    val Degradado = Brush.linearGradient(listOf(Violeta, Cian))
    val DegradadoIcono = Brush.linearGradient(listOf(Violeta, Azul, Cian))
}

/** Sección en marco redondeado con borde violeta y título. */
@Composable
fun Marco(titulo: String, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(Paleta.Marco, forma)
            .border(1.dp, Paleta.Violeta, forma)
            .padding(14.dp)
    ) {
        Text(titulo, color = Paleta.Titulo, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp))
        contenido()
    }
}
