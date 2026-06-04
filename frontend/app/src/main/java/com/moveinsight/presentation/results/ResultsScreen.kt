package com.moveinsight.presentation.results

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.domain.model.PainCheckInSummary
import com.moveinsight.domain.model.Recommendation
import com.moveinsight.domain.model.RecommendationCategory
import com.moveinsight.domain.model.RepResult
import com.moveinsight.presentation.components.LoadingWithTips
import com.moveinsight.presentation.components.RecommendationCard
import com.moveinsight.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onNavigateHome  : () -> Unit,
    onViewSkeleton  : (Int) -> Unit = {},
    sessionIdForNav : Int = -1,
    viewModel       : ResultsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Mantener la pantalla encendida MIENTRAS se analiza: evita que el móvil
    // entre en reposo (Doze) y corte la conexión durante la larga espera del
    // análisis. Se libera en cuanto hay resultados/error o se sale de la pantalla.
    val view = LocalView.current
    val keepScreenOn = uiState is ResultsUiState.Loading
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    when (val state = uiState) {
        is ResultsUiState.Loading -> {
            LoadingWithTips(statusMessage = "Analizando tu técnica…")
        }
        is ResultsUiState.Error -> {
            ErrorContent(
                message = state.message,
                onRetry = viewModel::retry,
                onBack  = onNavigateHome
            )
        }
        is ResultsUiState.Success -> {
            ResultsContent(
                data          = state.data,
                onBack        = onNavigateHome,
                onViewSkeleton = { onViewSkeleton(state.data.sessionId) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Contenido principal de resultados
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsContent(
    data           : ResultsData,
    onBack         : () -> Unit,
    onViewSkeleton : () -> Unit = {}
) {
    Scaffold(
        containerColor = NavyDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Resultados",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDeep)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Cabecera: puntuación global ──────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                OverallScoreCard(data)
            }

            // ── Resumen de KPIs globales ─────────────────────────────────
            item {
                Text(
                    text  = "Media por métrica",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                GlobalKpisGrid(data)
            }

            // ── Etiqueta de sesión ───────────────────────────────────────
            if (data.userNotes.isNotBlank()) {
                item { SessionNotesCard(data.userNotes) }
            }

            // ── Comparativa con sesión anterior ──────────────────────────
            data.comparison?.let { cmp ->
                item { ComparisonCard(cmp) }
            }

            // ── Fatiga intra-sesión ──────────────────────────────────────
            data.fatigue?.let { fat ->
                item { FatigueCard(fat) }
            }

            // ── Seguimiento del dolor EVA ────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                EvaSection(data.checkins)
            }

            // ── Detalle por repetición ───────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = "Detalle por repetición",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
            }

            itemsIndexed(data.reps) { index, rep ->
                RepCard(rep = rep, index = index)
            }

            // ── Consejo basado en KPI más bajo (F7) ─────────────────────
            item {
                val tip = lowestKpiTip(data)
                if (tip != null) {
                    Spacer(Modifier.height(4.dp))
                    RecommendationCard(rec = tip, compact = false)
                }
            }

            // ── Acciones finales ─────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                // Botón ver esqueleto
                OutlinedButton(
                    onClick  = onViewSkeleton,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.6f)),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Accessibility,
                        contentDescription = null,
                        tint               = CyanPrimary,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Ver vídeo con esqueleto",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanPrimary
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick  = onBack,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor   = NavyDeep
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Volver al inicio", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Consejo rápido basado en el KPI más bajo (F7)
// ─────────────────────────────────────────────────────────────────────────────

private fun lowestKpiTip(data: ResultsData): Recommendation? {
    data class KpiEntry(val score: Float, val recId: String)

    // Solo KPIs medibles (no N/D). En lateral el valgo no está disponible.
    val kpis = listOfNotNull(
        data.avgDepth?.let { KpiEntry(it, "tech_depth") },
        data.avgKnees?.let { KpiEntry(it, "tech_knees") },
        data.avgTorso?.let { KpiEntry(it, "tech_torso") },
    )

    // Solo mostramos consejo si el KPI más bajo < 5 (escala 0-10)
    val worst = kpis.minByOrNull { it.score } ?: return null
    if (worst.score >= 5f) return null

    return RESULTS_TIPS[worst.recId]
}

/** Catálogo local reducido para la pantalla de resultados (sin llamada extra al servidor). */
private val RESULTS_TIPS: Map<String, Recommendation> = mapOf(
    "tech_depth" to Recommendation(
        id       = "tech_depth",
        category = RecommendationCategory.TECHNIQUE,
        title    = "Trabaja la profundidad de la sentadilla",
        body     = "Tu profundidad esta sesión fue inferior a 5/10. Practica sentadillas con pausa en el punto más bajo y trabaja la movilidad de cadera y tobillo.",
        priority = 1,
        videoUrl = "https://www.youtube.com/watch?v=ultWZbUMPL8",
        trigger  = "Profundidad < 5/10 en esta sesión",
    ),
    "tech_knees" to Recommendation(
        id       = "tech_knees",
        category = RecommendationCategory.TECHNIQUE,
        title    = "Alineación de rodillas en la sentadilla",
        body     = "Tus rodillas tendieron a caer hacia dentro esta sesión. Activa glúteos con ejercicios de abducción antes de cada entrenamiento.",
        priority = 2,
        videoUrl = "https://www.youtube.com/watch?v=MeIiIdhvXT4",
        trigger  = "Rodillas < 5/10 en esta sesión",
    ),
    "tech_torso" to Recommendation(
        id       = "tech_torso",
        category = RecommendationCategory.TECHNIQUE,
        title    = "Mantén el torso erguido",
        body     = "La inclinación de torso fue elevada esta sesión. Trabaja los erectores de columna y practica la sentadilla con palo en overhead.",
        priority = 3,
        videoUrl = "https://www.youtube.com/watch?v=HKbOECHcgFs",
        trigger  = "Torso < 5/10 en esta sesión",
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// Tarjeta de puntuación global
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OverallScoreCard(data: ResultsData) {
    val scoreColor = scoreToColor(data.overallAverage)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Círculo con la nota
            Box(
                modifier         = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                scoreColor.copy(alpha = 0.25f),
                                scoreColor.copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "%.1f".format(data.overallAverage),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize   = 36.sp
                    ),
                    color = scoreColor
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text  = "Puntuación Global",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
            )

            Text(
                text  = scoreFeedback(data.overallAverage),
                style = MaterialTheme.typography.bodyMedium,
                color = scoreColor
            )

            Spacer(Modifier.height(16.dp))

            // Info de la sesión
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoChip(
                    icon  = Icons.Filled.Repeat,
                    label = "${data.reps.size}",
                    desc  = "reps"
                )
                InfoChip(
                    icon  = Icons.Filled.FitnessCenter,
                    label = if (data.weightKg > 0f) "%.0f kg".format(data.weightKg) else "Libre",
                    desc  = "carga"
                )
                InfoChip(
                    icon  = Icons.Filled.Speed,
                    label = "${data.borgScore}",
                    desc  = "Borg"
                )
            }

            // Fecha y hora de la sesión
            val sessionDateTime = data.createdAt.formatSessionDateTime()
            if (sessionDateTime.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(
                    color     = NavyLight,
                    thickness = 0.5.dp,
                    modifier  = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector        = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint               = CyanPrimary.copy(alpha = 0.6f),
                        modifier           = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text  = sessionDateTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/** "2024-12-15T18:32:00" → "15/12/2024 · 18:32" */
private fun String.formatSessionDateTime(): String {
    return try {
        val datePart = this.take(10)
        val timePart = if (this.length >= 16) this.substring(11, 16) else ""
        val parts    = datePart.split("-")
        val date     = if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else return ""
        if (timePart.isNotEmpty()) "$date · $timePart" else date
    } catch (_: Exception) { "" }
}

@Composable
private fun InfoChip(icon: ImageVector, label: String, desc: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid de KPIs globales
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlobalKpisGrid(data: ResultsData) {
    val kpis = listOf(
        KpiInfo("Profundidad", Icons.Filled.SwapVert,      data.avgDepth),
        KpiInfo("Torso",       Icons.Filled.Accessibility, data.avgTorso),
        KpiInfo("Valgo",       Icons.Filled.AirlineSeatLegroomNormal, data.avgKnees),
        KpiInfo("Simetría",    Icons.Filled.Balance,       data.avgSymmetry),
        KpiInfo("Ritmo",       Icons.Filled.Timer,         data.avgRhythm)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Fila de 3
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            kpis.take(3).forEach { kpi ->
                KpiCard(kpi = kpi, modifier = Modifier.weight(1f))
            }
        }
        // Fila de 2
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            kpis.drop(3).forEach { kpi ->
                KpiCard(kpi = kpi, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

private data class KpiInfo(
    val name  : String,
    val icon  : ImageVector,
    val score : Float?
)

@Composable
private fun KpiCard(kpi: KpiInfo, modifier: Modifier = Modifier) {
    val color = kpi.score?.let { scoreToColor(it) } ?: TextSecondary
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = NavyLight.copy(alpha = 0.7f)),
        shape    = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(kpi.icon, null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text  = kpi.score?.let { "%.1f".format(it) } ?: "N/D",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text      = kpi.name,
                style     = MaterialTheme.typography.bodySmall,
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tarjeta por repetición
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Etiqueta cualitativa de un KPI (Opción B): el modelo predice clases 1/2/3 que
 * se guardan como 0-10 (3.0 / 7.0 / 10.0). Aquí revertimos a clase para mostrar
 * el texto descriptivo. null → "N/D" (no medible en este perfil de cámara).
 */
private fun kpiLabel(key: String, score: Float?): String {
    if (score == null) return "N/D"
    val cls = when { score <= 4f -> 1; score <= 8f -> 2; else -> 3 }
    val table = when (key) {
        "depth"    -> mapOf(1 to "Insuficiente", 2 to "Paralela",     3 to "Completa")
        "torso"    -> mapOf(1 to "Excesiva",     2 to "Leve inclin.", 3 to "Normal")
        "knees"    -> mapOf(1 to "Valgo",        2 to "Ligero valgo", 3 to "Sin valgo")
        "symmetry" -> mapOf(1 to "Asimetría",    2 to "Leve asim.",   3 to "Simétrico")
        "rhythm"   -> mapOf(1 to "Descontrol.",  2 to "Aceptable",    3 to "Buen ritmo")
        else       -> emptyMap()
    }
    return table[cls] ?: ""
}

@Composable
private fun RepCard(rep: RepResult, index: Int) {
    val overallColor = rep.overallScore?.let { scoreToColor(it) } ?: TextSecondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier         = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "${rep.repNumber}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = CyanPrimary
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Repetición ${rep.repNumber}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                }
                // Score badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(overallColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text  = rep.overallScore?.let { "%.1f".format(it) } ?: "N/D",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = overallColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // KPI bars (con etiqueta cualitativa; "N/D" si no medible en este perfil)
            KpiBar("Profundidad", "depth",    rep.depthScore)
            KpiBar("Torso",       "torso",    rep.torsoScore)
            KpiBar("Valgo",       "knees",    rep.kneesScore)
            KpiBar("Simetría",    "symmetry", rep.symmetryScore)
            KpiBar("Ritmo",       "rhythm",   rep.rhythmScore)
        }
    }
}

@Composable
private fun KpiBar(name: String, kpiKey: String, score: Float?) {
    val color    = score?.let { scoreToColor(it) } ?: TextSecondary
    val fraction = score?.let { (it / 10f).coerceIn(0f, 1f) } ?: 0f
    val label    = kpiLabel(kpiKey, score)

    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = name,
            style    = MaterialTheme.typography.bodySmall,
            color    = TextSecondary,
            modifier = Modifier.width(80.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(NavyLight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text      = label,
            style     = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color     = color,
            modifier  = Modifier.width(82.dp),
            textAlign = TextAlign.End,
            maxLines  = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pantalla de error
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    message : String,
    onRetry : () -> Unit,
    onBack  : () -> Unit
) {
    Box(
        modifier         = Modifier.fillMaxSize().background(NavyDeep).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.ErrorOutline, null,
                tint     = RedAlert,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text      = "Error al cargar resultados",
                style     = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color     = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = message,
                style     = MaterialTheme.typography.bodyMedium,
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors  = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = NavyDeep)
            ) {
                Text("Reintentar")
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onBack) {
                Text("Volver al inicio", color = TextSecondary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sección EVA
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EvaSection(checkins: List<PainCheckInSummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text  = "Seguimiento del Dolor (EVA)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )

        if (checkins.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = NavyMid),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier          = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint               = CyanPrimary.copy(alpha = 0.7f),
                        modifier           = Modifier.size(22.dp)
                    )
                    Text(
                        text  = "Recibirás notificaciones a las 24h y 48h para registrar el dolor post-entrenamiento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            checkins.forEach { checkin ->
                EvaCheckinCard(checkin)
            }
        }
    }
}

@Composable
private fun EvaCheckinCard(checkin: PainCheckInSummary) {
    val evaColor = when {
        checkin.evaScore <= 3 -> GreenReady
        checkin.evaScore <= 6 -> YellowCaution
        else                  -> RedAlert
    }
    val zonesStr = checkin.bodyZones.filter { it.isNotBlank() }.joinToString(", ")
        .ifEmpty { "Sin molestias" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Score circle
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(evaColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "${checkin.evaScore}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = evaColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "EVA ${checkin.hoursAfter}h post-entrenamiento",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = if (zonesStr == "Sin molestias") "Sin molestias"
                            else "Zonas: $zonesStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (checkin.notes.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = checkin.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            // Pain level label
            Surface(
                color = evaColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text     = evaLabel(checkin.evaScore),
                    style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color    = evaColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun evaLabel(score: Int): String = when {
    score == 0  -> "Sin dolor"
    score <= 3  -> "Leve"
    score <= 6  -> "Moderado"
    score <= 8  -> "Intenso"
    else        -> "Severo"
}

// ─────────────────────────────────────────────────────────────────────────────
// Etiqueta de sesión
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionNotesCard(notes: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = Icons.Filled.Notes,
                contentDescription = null,
                tint               = CyanPrimary.copy(alpha = 0.8f),
                modifier           = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text  = "Etiqueta de sesión",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = notes,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Comparativa con sesión anterior
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ComparisonCard(cmp: SessionComparison) {
    val overallColor = when {
        cmp.overallDelta >  0.3f -> GreenReady
        cmp.overallDelta < -0.3f -> RedAlert
        else                     -> YellowCaution
    }
    val overallIcon = when {
        cmp.overallDelta >  0.3f -> Icons.AutoMirrored.Filled.TrendingUp
        cmp.overallDelta < -0.3f -> Icons.AutoMirrored.Filled.TrendingDown
        else                     -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    val weightColor = when {
        cmp.weightDelta >  0f -> CyanPrimary
        cmp.weightDelta <  0f -> OrangePower
        else                  -> TextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Filled.CompareArrows,
                    contentDescription = null,
                    tint               = CyanPrimary,
                    modifier           = Modifier.size(18.dp)
                )
                Text(
                    text  = "vs sesión anterior (${cmp.prevCreatedAt})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Técnica delta
                DeltaChip(
                    label  = "Técnica",
                    value  = "%+.1f".format(cmp.overallDelta),
                    color  = overallColor,
                    icon   = overallIcon
                )
                // Carga delta
                DeltaChip(
                    label = "Carga",
                    value = "%+.0f kg".format(cmp.weightDelta),
                    color = weightColor,
                    icon  = if (cmp.weightDelta >= 0f)
                                Icons.AutoMirrored.Filled.TrendingUp
                            else
                                Icons.AutoMirrored.Filled.TrendingDown
                )
            }
        }
    }
}

@Composable
private fun DeltaChip(
    label : String,
    value : String,
    color : Color,
    icon  : ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Text(
                text  = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fatiga intra-sesión
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FatigueCard(fat: RepFatigueData) {
    val labelColor = when (fat.label) {
        "Fatiga detectada"     -> OrangePower
        "Mejora intra-sesión"  -> GreenReady
        else                   -> CyanPrimary
    }
    val labelIcon = when (fat.label) {
        "Fatiga detectada"     -> Icons.AutoMirrored.Filled.TrendingDown
        "Mejora intra-sesión"  -> Icons.AutoMirrored.Filled.TrendingUp
        else                   -> Icons.AutoMirrored.Filled.TrendingFlat
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Filled.ShowChart,
                    contentDescription = null,
                    tint               = CyanPrimary,
                    modifier           = Modifier.size(18.dp)
                )
                Text(
                    text  = "Fatiga intra-sesión",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text  = "%.1f".format(fat.firstHalfAvg),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = scoreToColor(fat.firstHalfAvg)
                    )
                    Text("1ª mitad", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Icon(
                    imageVector        = labelIcon,
                    contentDescription = null,
                    tint               = labelColor,
                    modifier           = Modifier.size(28.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text  = "%.1f".format(fat.secondHalfAvg),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = scoreToColor(fat.secondHalfAvg)
                    )
                    Text("2ª mitad", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                color  = labelColor.copy(alpha = 0.12f),
                shape  = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text      = fat.label,
                    style     = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color     = labelColor,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilidades
// ─────────────────────────────────────────────────────────────────────────────

private fun scoreToColor(score: Float): Color = when {
    score >= 7.5f -> GreenReady
    score >= 5.0f -> YellowCaution
    score >= 3.0f -> OrangePower
    else          -> RedAlert
}

private fun scoreFeedback(score: Float): String = when {
    score >= 8.0f -> "Excelente técnica"
    score >= 7.0f -> "Buena técnica"
    score >= 5.0f -> "Técnica aceptable"
    score >= 3.0f -> "Necesita mejoras"
    else          -> "Técnica deficiente"
}
