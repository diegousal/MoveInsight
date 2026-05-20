package com.moveinsight.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moveinsight.domain.model.Recommendation
import com.moveinsight.domain.model.RecommendationCategory
import com.moveinsight.presentation.theme.*

// ── Colores y metadatos por categoría ─────────────────────────────────────────

private data class CategoryStyle(
    val color : Color,
    val icon  : ImageVector,
    val label : String,
)

private fun categoryStyle(cat: RecommendationCategory) = when (cat) {
    RecommendationCategory.TECHNIQUE   -> CategoryStyle(CyanPrimary,   Icons.Filled.SelfImprovement, "Técnica")
    RecommendationCategory.LOAD        -> CategoryStyle(YellowCaution, Icons.Filled.FitnessCenter,   "Carga")
    RecommendationCategory.RECOVERY    -> CategoryStyle(RedAlert,      Icons.Filled.Healing,          "Recuperación")
    RecommendationCategory.PROGRESSION -> CategoryStyle(GreenReady,    Icons.Filled.TrendingUp,       "Progresión")
    RecommendationCategory.UNKNOWN     -> CategoryStyle(TextSecondary, Icons.Filled.Info,             "General")
}

// ── Composable principal ──────────────────────────────────────────────────────

/**
 * Tarjeta de recomendación reutilizable (Home + Results).
 *
 * @param rec         Recomendación a mostrar.
 * @param compact     Si es true, no muestra el cuerpo completo ni el botón de vídeo.
 * @param modifier    Modifier externo.
 */
@Composable
fun RecommendationCard(
    rec     : Recommendation,
    compact : Boolean  = false,
    modifier: Modifier = Modifier,
) {
    val style   = categoryStyle(rec.category)
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavyMid)
            .padding(14.dp)
    ) {
        // ── Cabecera ─────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(style.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = rec.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = TextPrimary, fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text  = style.label,
                    style = MaterialTheme.typography.labelSmall.copy(color = style.color)
                )
            }
            // Badge de prioridad 1
            if (rec.priority == 1) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RedAlert.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Prioritario",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RedAlert, fontWeight = FontWeight.Bold, fontSize = 10.sp
                        )
                    )
                }
            }
        }

        if (!compact) {
            // ── Cuerpo ───────────────────────────────────────────────────
            Spacer(Modifier.height(10.dp))
            Text(
                text  = rec.body,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary, lineHeight = 17.sp
                )
            )

            // ── Botón de vídeo ───────────────────────────────────────────
            if (!rec.videoUrl.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rec.videoUrl))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = style.color),
                    border = androidx.compose.foundation.BorderStroke(1.dp, style.color.copy(alpha = 0.5f)),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.PlayCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ver ejercicio", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── Sección de recomendaciones (usada en Home) ────────────────────────────────

@Composable
fun RecommendationsSection(
    recommendations : List<Recommendation>,
    maxVisible      : Int      = 2,
    modifier        : Modifier = Modifier,
) {
    if (recommendations.isEmpty()) return

    val visible = recommendations.take(maxVisible)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = YellowCaution,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text  = "Recomendaciones",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = TextPrimary, fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.weight(1f))
            if (recommendations.size > maxVisible) {
                Text(
                    text  = "+${recommendations.size - maxVisible} más",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        visible.forEachIndexed { index, rec ->
            RecommendationCard(rec = rec, compact = false)
            if (index < visible.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}
