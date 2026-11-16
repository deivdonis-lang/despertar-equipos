package com.deivid.despertar.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Texto en una sola línea, centrado, que reduce la letra hasta que cabe. */
@Composable
fun TextoAjustado(
    texto: String,
    modifier: Modifier = Modifier,
    color: Color = Paleta.CianClaro,
    tamano: TextUnit = 16.sp,
    peso: FontWeight = FontWeight.SemiBold,
) {
    var escala by remember(texto) { mutableStateOf(1f) }
    var listo by remember(texto) { mutableStateOf(false) }
    Text(
        texto,
        modifier = modifier.drawWithContent { if (listo) drawContent() },
        color = color,
        fontSize = tamano * escala,
        fontWeight = peso,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        onTextLayout = { r ->
            if (r.didOverflowWidth && escala > 0.4f) escala *= 0.9f else listo = true
        },
    )
}

/** Botón relleno cian translúcido con borde cian claro. */
@Composable
fun Boton(
    texto: String,
    modifier: Modifier = Modifier,
    activo: Boolean = true,
    color: Color = Paleta.CianClaro,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = activo,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (activo) color else color.copy(alpha = 0.3f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.18f),
            contentColor = color,
            disabledContainerColor = color.copy(alpha = 0.06f),
            disabledContentColor = color.copy(alpha = 0.35f),
        ),
    ) {
        TextoAjustado(texto, color = if (activo) color else color.copy(alpha = 0.35f))
    }
}
