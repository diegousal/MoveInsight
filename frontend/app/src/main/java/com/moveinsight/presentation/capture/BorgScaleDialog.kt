package com.moveinsight.presentation.capture

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moveinsight.presentation.theme.*

/** Mapa de la escala Borg CR-10 */
private val borgLabels = mapOf(
    0  to "Nada en absoluto",
    1  to "Muy, muy leve",
    2  to "Leve",
    3  to "Moderado",
    4  to "Algo duro",
    5  to "Duro",
    6  to "Más duro",
    7  to "Muy duro",
    8  to "Muy, muy duro",
    9  to "Extremadamente duro",
    10 to "Máximo absoluto"
)

private fun borgColor(score: Int): Color = when (score) {
    in 0..3  -> GreenReady
    in 4..6  -> YellowCaution
    in 7..9  -> OrangePower
    else     -> RedAlert
}

@Composable
fun BorgScaleDialog(
    initialScore : Int = 5,
    onConfirm    : (borgScore: Int, notes: String) -> Unit,
    onDismiss    : () -> Unit
) {
    var currentScore by remember { mutableIntStateOf(initialScore) }
    var notes        by remember { mutableStateOf("") }

    val scoreColor by animateColorAsState(
        targetValue   = borgColor(currentScore),
        animationSpec = tween(300),
        label         = "borg_color"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(NavyMid)
                .padding(28.dp)
        ) {
            Column(
                modifier            = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Encabezado ────────────────────────────────────────────
                Text(
                    text      = "¿Cómo de duro ha sido?",
                    style     = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Escala de Borg CR-10",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(28.dp))

                // ── Círculo con puntuación ────────────────────────────────
                Box(
                    modifier         = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(scoreColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = currentScore.toString(),
                        color = scoreColor,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize   = 48.sp
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Etiqueta descriptiva ──────────────────────────────────
                Text(
                    text  = borgLabels[currentScore] ?: "",
                    style = MaterialTheme.typography.titleLarge.copy(color = scoreColor),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // ── Slider ────────────────────────────────────────────────
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text("0", style = MaterialTheme.typography.labelLarge, color = GreenReady)
                    Slider(
                        value         = currentScore.toFloat(),
                        onValueChange = { currentScore = it.toInt() },
                        valueRange    = 0f..10f,
                        steps         = 9,         // 11 posiciones (0..10) → 9 pasos internos
                        modifier      = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors        = SliderDefaults.colors(
                            thumbColor            = scoreColor,
                            activeTrackColor      = scoreColor,
                            inactiveTrackColor    = DividerColor
                        )
                    )
                    Text("10", style = MaterialTheme.typography.labelLarge, color = RedAlert)
                }

                // ── Etiquetas extremos ────────────────────────────────────
                Row(
                    modifier            = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Reposo",  style = MaterialTheme.typography.bodyMedium)
                    Text("Máximo", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(24.dp))

                // ── Etiqueta de sesión (opcional) ─────────────────────────
                OutlinedTextField(
                    value         = notes,
                    onValueChange = { if (it.length <= 120) notes = it },
                    label         = { Text("Etiqueta / notas (opcional)") },
                    placeholder   = { Text("Ej: Día de pierna, PR intento…") },
                    leadingIcon   = { Icon(Icons.Filled.Notes, null, tint = CyanPrimary.copy(alpha = 0.7f)) },
                    singleLine    = false,
                    maxLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = CyanPrimary,
                        unfocusedBorderColor = DividerColor,
                        focusedLabelColor    = CyanPrimary,
                        unfocusedLabelColor  = TextSecondary,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary,
                        cursorColor          = CyanPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                if (notes.length >= 100) {
                    Text(
                        text  = "${notes.length}/120",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Botones ───────────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
                    ) {
                        Text("Descartar", color = TextSecondary)
                    }
                    Button(
                        onClick  = { onConfirm(currentScore, notes.trim()) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor   = NavyDeep
                        )
                    ) {
                        Text(
                            text  = "Confirmar",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}