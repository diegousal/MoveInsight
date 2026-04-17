package com.moveinsight.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moveinsight.domain.model.SessionDetail
import com.moveinsight.presentation.theme.*

@Composable
fun SessionCard(
    session  : SessionDetail,
    onClick  : () -> Unit = {},
    modifier : Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: date + status
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = session.createdAt.formatDisplayDate(),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                StatusChip(session.status)
            }

            Spacer(Modifier.height(12.dp))

            // Main metrics
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricItem(
                    label = "Carga",
                    value = if (session.weightKg > 0f) "${session.weightKg.toInt()} kg" else "Libre",
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
                        label = "Tecnica",
                        value = session.overallScore?.let { "%.1f".format(it) } ?: "-",
                        icon  = null,
                        color = techniqueColor(session.overallScore)
                    )
                    MetricItem(
                        label = "Reps",
                        value = session.repCount?.toString() ?: "-",
                        icon  = null,
                        color = CyanPrimary
                    )
                }
            }

            // ── EVA check-ins (si existen) ────────────────────────────────
            if (session.checkins.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(
                    color     = NavyLight,
                    thickness = 0.5.dp,
                    modifier  = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                session.checkins.forEach { checkin ->
                    EvaChip(checkin)
                    if (checkin != session.checkins.last()) Spacer(Modifier.height(6.dp))
                }
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

// -- Color helpers ----------------------------------------------------------------
private fun borgColor(score: Int) = when (score) {
    in 0..3  -> GreenReady
    in 4..6  -> YellowCaution
    in 7..9  -> OrangePower
    else     -> RedAlert
}

private fun techniqueColor(score: Float?) = when {
    score == null  -> TextSecondary
    score >= 7.5f  -> GreenReady
    score >= 5.0f  -> YellowCaution
    score >= 3.0f  -> OrangePower
    else           -> RedAlert
}

@Composable
private fun EvaChip(checkin: com.moveinsight.domain.model.PainCheckInSummary) {
    val evaColor = when {
        checkin.evaScore <= 3 -> GreenReady
        checkin.evaScore <= 6 -> YellowCaution
        else                  -> RedAlert
    }
    val label    = "EVA ${checkin.hoursAfter}h"
    val zonesStr = checkin.bodyZones
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .ifEmpty { "Sin molestias" }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NavyDeep)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Colored score badge
        Box(
            modifier         = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(evaColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = "${checkin.evaScore}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = evaColor
            )
        }
        // Label
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = evaColor
        )
        // Separator dot
        Text("·", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        // Zones
        Text(
            text     = zonesStr,
            style    = MaterialTheme.typography.bodySmall,
            color    = TextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun String.formatDisplayDate(): String {
    return try {
        val datePart  = this.take(10)               // "2024-12-15"
        val timePart  = if (this.length >= 16) this.substring(11, 16) else ""  // "18:32"
        val parts     = datePart.split("-")          // ["2024", "12", "15"]
        val date      = if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
        if (timePart.isNotEmpty()) "$date · $timePart" else date
    } catch (_: Exception) { this.take(10) }
}
