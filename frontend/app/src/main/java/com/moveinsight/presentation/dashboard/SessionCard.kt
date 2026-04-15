package com.moveinsight.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moveinsight.domain.model.SessionDetail
import com.moveinsight.presentation.theme.*

@Composable
fun SessionCard(
    session  : SessionDetail,
    modifier : Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Cabecera: fecha + estado ───────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = session.createdAt.take(10),  // "YYYY-MM-DD"
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                StatusChip(session.status)
            }

            Spacer(Modifier.height(12.dp))

            // ── Métricas principales ───────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricItem(
                    label = "Carga",
                    value = "${session.weightKg.toInt()} kg",
                    icon  = Icons.Filled.FitnessCenter,
                    color = CyanPrimary
                )
                MetricItem(
                    label = "Borg",
                    value = "${session.borgScore}/10",
                    icon  = null,
                    color = borgColor(session.borgScore)
                )
                if (session.isCompleted) {
                    MetricItem(
                        label = "Técnica",
                        value = session.techniqueScore?.let { "%.0f".format(it) } ?: "—",
                        icon  = null,
                        color = techniqueColor(session.techniqueScore)
                    )
                    MetricItem(
                        label = "Vel.",
                        value = session.avgVelocity?.let { "%.2f m/s".format(it) } ?: "—",
                        icon  = null,
                        color = CyanPrimary
                    )
                }
            }

            // ── Reps ──────────────────────────────────────────────────
            if (session.isCompleted && session.repCount != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text  = "${session.repCount} repeticiones  ·  " +
                            "Simetría ${session.symmetryPct?.let { "%.1f%%".format(it) } ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "completed"  -> "Completado" to GreenReady
        "processing" -> "Procesando" to YellowCaution
        "failed"     -> "Error"      to RedAlert
        else         -> status       to TextSecondary
    }
    Surface(
        color  = color.copy(alpha = 0.15f),
        shape  = MaterialTheme.shapes.small
    ) {
        Text(
            text     = label,
            color    = color,
            style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MetricItem(
    label : String,
    value : String,
    icon  : androidx.compose.ui.graphics.vector.ImageVector?,
    color : androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
        }
        Text(value, style = MaterialTheme.typography.titleLarge.copy(
            color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp
        ))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Helpers de color ──────────────────────────────────────────────────────
private fun borgColor(score: Int) = when (score) {
    in 0..3  -> GreenReady
    in 4..6  -> YellowCaution
    in 7..9  -> OrangePower
    else     -> RedAlert
}

private fun techniqueColor(score: Float?) = when {
    score == null  -> TextSecondary
    score >= 80f   -> GreenReady
    score >= 60f   -> YellowCaution
    else           -> RedAlert
}