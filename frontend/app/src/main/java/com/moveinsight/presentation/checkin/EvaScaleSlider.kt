package com.moveinsight.presentation.checkin


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moveinsight.presentation.theme.*

/** Escala EVA (Visual Analogue Scale) 0-10 para dolor */
@Composable
fun EvaScaleSlider(
    score    : Int,
    onChange : (Int) -> Unit,
    modifier : Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue   = evaColor(score),
        animationSpec = tween(300),
        label         = "eva_color"
    )

    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Círculo con puntuación ────────────────────────────────────────
        Box(
            modifier         = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = score.toString(),
                color = color,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize   = 44.sp
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text  = evaLabel(score),
            style = MaterialTheme.typography.titleLarge.copy(color = color)
        )

        Spacer(Modifier.height(20.dp))

        // ── Slider ────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "0",
                style = MaterialTheme.typography.labelLarge,
                color = GreenReady
            )
            Slider(
                value         = score.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange    = 0f..10f,
                steps         = 9,
                modifier      = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors        = SliderDefaults.colors(
                    thumbColor         = color,
                    activeTrackColor   = color,
                    inactiveTrackColor = DividerColor
                )
            )
            Text(
                "10",
                style = MaterialTheme.typography.labelLarge,
                color = RedAlert
            )
        }

        // ── Etiquetas extremos ────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Sin dolor",    style = MaterialTheme.typography.bodyMedium)
            Text("Dolor máximo", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun evaColor(score: Int): Color = when (score) {
    0       -> GreenReady
    in 1..3 -> GreenReady
    in 4..6 -> YellowCaution
    in 7..9 -> OrangePower
    else    -> RedAlert
}

private fun evaLabel(score: Int): String = when (score) {
    0       -> "Sin dolor"
    in 1..2 -> "Dolor muy leve"
    in 3..4 -> "Dolor leve"
    in 5..6 -> "Dolor moderado"
    in 7..8 -> "Dolor intenso"
    in 9..9 -> "Dolor muy intenso"
    else    -> "Dolor insoportable"
}