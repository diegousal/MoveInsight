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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.domain.model.RepResult
import com.moveinsight.presentation.components.LoadingWithTips
import com.moveinsight.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onNavigateHome : () -> Unit,
    viewModel      : ResultsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                data   = state.data,
                onBack = onNavigateHome
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
    data   : ResultsData,
    onBack : () -> Unit
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
                    text  = "Media por KPI",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                GlobalKpisGrid(data)
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

            // ── Espacio final ────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
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
        }
    }
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
        KpiInfo("Estabilidad", Icons.Filled.Balance,       data.avgStability),
        KpiInfo("Rodillas",    Icons.Filled.AirlineSeatLegroomNormal, data.avgKnees),
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
    val score : Float
)

@Composable
private fun KpiCard(kpi: KpiInfo, modifier: Modifier = Modifier) {
    val color = scoreToColor(kpi.score)
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
                text  = "%.1f".format(kpi.score),
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

@Composable
private fun RepCard(rep: RepResult, index: Int) {
    val overallColor = scoreToColor(rep.overallScore)

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
                        text  = "%.1f".format(rep.overallScore),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = overallColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // KPI bars
            KpiBar("Profundidad", rep.depthScore)
            KpiBar("Torso",       rep.torsoScore)
            KpiBar("Estabilidad", rep.stabilityScore)
            KpiBar("Rodillas",    rep.kneesScore)
            KpiBar("Ritmo",       rep.rhythmScore)
        }
    }
}

@Composable
private fun KpiBar(name: String, score: Float) {
    val color      = scoreToColor(score)
    val fraction   = (score / 10f).coerceIn(0f, 1f)

    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = name,
            style    = MaterialTheme.typography.bodySmall,
            color    = TextSecondary,
            modifier = Modifier.width(85.dp)
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
            text     = "%.1f".format(score),
            style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color    = color,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
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
    score >= 6.5f -> "Buena técnica"
    score >= 5.0f -> "Técnica aceptable"
    score >= 3.0f -> "Necesita mejoras"
    else          -> "Técnica deficiente"
}
