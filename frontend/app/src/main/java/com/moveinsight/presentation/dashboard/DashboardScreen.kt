package com.moveinsight.presentation.dashboard

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.domain.model.ReadinessLevel
import com.moveinsight.domain.model.readinessLevel
import com.moveinsight.presentation.components.MoveInsightLogo
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateBack      : () -> Unit,
    onNavigateToResults : (Int) -> Unit = {},
    viewModel           : DashboardViewModel = hiltViewModel()
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost =  remember { SnackbarHostState() }
    val context      =  LocalContext.current

    // ── Manejo de eventos ─────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is DashboardUiEvent.ExportReady -> {
                    // Abrir el PDF con el visor del sistema
                    val file = File(event.filePath)
                    val uri  = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Abrir informe"))
                }
                is DashboardUiEvent.ShowSnackbar ->
                    snackbarHost.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        containerColor = NavyDeep,
        snackbarHost   = { SnackbarHost(snackbarHost) },
        topBar         = {
            TopAppBar(
                title          = { MoveInsightLogo(fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary)
                    }
                },
                actions        = {
                    // Botón de prueba de notificación
                    IconButton(onClick = viewModel::testNotification) {
                        Icon(
                            imageVector        = Icons.Filled.Notifications,
                            contentDescription = "Probar notificación",
                            tint               = OrangePower
                        )
                    }
                    // Botón de exportación PDF
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            color    = CyanPrimary,
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = viewModel::onExportClick) {
                            Icon(Icons.Filled.FileDownload, "Exportar PDF", tint = CyanPrimary)
                        }
                    }
                },
                colors         = TopAppBarDefaults.topAppBarColors(containerColor = NavyDeep)
            )
        }
    ) { padding ->

        if (!uiState.isFullyLoaded) {
            // ── Carga ─────────────────────────────────────────────────
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CyanPrimary)
                    Spacer(Modifier.height(16.dp))
                    Text("Cargando análisis…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            // ── Contenido principal con tabs ──────────────────────────
            var selectedTab by remember { mutableIntStateOf(0) }
            val tabs = listOf("Sesiones", "Gráficos", "Análisis")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NavyDeep)
                    .padding(padding)
            ) {
                // ── Resumen de readiness ───────────────────────────────
                uiState.analytics?.let { analytics ->
                    ReadinessBanner(
                        score  = analytics.readinessScore,
                        level  = analytics.readinessLevel(),
                    )
                }

                // ── Tabs ──────────────────────────────────────────────
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = NavyDeep,
                    contentColor     = CyanPrimary,
                    indicator        = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier  = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color     = CyanPrimary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, label ->
                        Tab(
                            selected         = selectedTab == index,
                            onClick          = { selectedTab = index },
                            text             = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selectedTab == index) CyanPrimary else TextSecondary
                                )
                            }
                        )
                    }
                }

                // ── Contenido por tab ─────────────────────────────────
                when (selectedTab) {
                    0 -> SessionsTab(uiState, viewModel::loadAll, onNavigateToResults)
                    1 -> ChartsTab(uiState)
                    2 -> InsightsTab(uiState)
                }
            }
        }
    }
}

// ── Banner de Readiness ───────────────────────────────────────────────────

@Composable
private fun ReadinessBanner(
    score : Int,
    level : ReadinessLevel
) {
    val color = when (level) {
        ReadinessLevel.HIGH   -> GreenReady
        ReadinessLevel.MEDIUM -> YellowCaution
        ReadinessLevel.LOW    -> RedAlert
    }
    val labelText = when (level) {
        ReadinessLevel.HIGH   -> "Óptimo para entrenar"
        ReadinessLevel.MEDIUM -> "Entrena con moderación"
        ReadinessLevel.LOW    -> "Descansa hoy"
    }

    Surface(
        color    = color.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Círculo de readiness
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "$score",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color      = color,
                        fontWeight = FontWeight.Black,
                        fontSize   = 20.sp
                    )
                )
            }
            Column {
                Text(
                    text  = "Estado físico",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text  = labelText,
                    style = MaterialTheme.typography.titleLarge.copy(color = color)
                )
            }
        }
    }
}

// ── Tab: Sesiones ─────────────────────────────────────────────────────────

@Composable
private fun SessionsTab(
    uiState        : DashboardUiState,
    onRetry        : () -> Unit,
    onSessionClick : (Int) -> Unit = {}
) {
    if (uiState.errorSessions != null) {
        ErrorContent(uiState.errorSessions, onRetry)
        return
    }
    if (uiState.sessions.isEmpty()) {
        EmptyContent("Aún no tienes sesiones registradas.\nPulsa 'Iniciar Entrenamiento' para empezar.")
        return
    }

    LazyColumn(
        contentPadding       = PaddingValues(16.dp),
        verticalArrangement  = Arrangement.spacedBy(12.dp),
        modifier             = Modifier.fillMaxSize()
    ) {
        items(uiState.sessions, key = { it.id }) { session ->
            SessionCard(session = session, onClick = { onSessionClick(session.id) })
        }
    }
}

// ── Tab: Gráficos ─────────────────────────────────────────────────────────

@Composable
private fun ChartsTab(uiState: DashboardUiState) {
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier            = Modifier.fillMaxSize()
    ) {
        // ── Banner de periodos de lesión ─────────────────────────────────
        if (uiState.incidents.isNotEmpty()) {
            item { InjuryPeriodsBanner(uiState.incidents) }
        }
        item { TechniqueLineChart(uiState.sessions, uiState.incidents) }
        item { LoadProgressionChart(uiState.sessions, uiState.incidents) }
        item { BorgScaleChart(uiState.sessions, uiState.incidents) }
    }
}

/**
 * Banner que lista los periodos de lesión registrados (activos + recientes).
 * Aparece encima de los gráficos para que el usuario interprete posibles caídas
 * en técnica/carga teniendo presente si hubo lesión durante esas sesiones.
 */
@Composable
private fun InjuryPeriodsBanner(incidents: List<com.moveinsight.domain.model.Incident>) {
    val active = incidents.filter { it.isActive }
    val recent = incidents
        .filterNot { it.isActive }
        .sortedByDescending { it.endDate ?: it.startDate }
        .take(3)
    val toShow = (active + recent)
    if (toShow.isEmpty()) return

    val fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yy")

    Surface(
        color    = RedAlert.copy(alpha = 0.10f),
        shape    = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.HealthAndSafety,
                    null,
                    tint     = RedAlert,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = "Periodos de lesión registrados",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text  = "Las sesiones realizadas durante estos periodos pueden mostrar técnica/carga reducida.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary, lineHeight = 17.sp
                )
            )
            Spacer(Modifier.height(10.dp))
            toShow.forEach { inc ->
                val start = runCatching { java.time.LocalDate.parse(inc.startDate) }.getOrNull()
                val end   = inc.endDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                val period = when {
                    start != null && end != null -> "${start.format(fmt)} → ${end.format(fmt)}"
                    start != null && inc.isActive -> "${start.format(fmt)} → hoy"
                    start != null                -> start.format(fmt)
                    else                          -> "—"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RedAlert.copy(alpha = if (inc.isActive) 0.85f else 0.45f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = displayBodyZoneShort(inc.bodyZone),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextPrimary, fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text  = period,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
            }
        }
    }
}

private fun displayBodyZoneShort(code: String): String = when (code) {
    "rodilla_izq" -> "Rodilla izq."
    "rodilla_der" -> "Rodilla der."
    "lumbar"      -> "Lumbar"
    "cervical"    -> "Cervical"
    "cadera_izq"  -> "Cadera izq."
    "cadera_der"  -> "Cadera der."
    "tobillo_izq" -> "Tobillo izq."
    "tobillo_der" -> "Tobillo der."
    "hombro_izq"  -> "Hombro izq."
    "hombro_der"  -> "Hombro der."
    "general"     -> "General"
    else          -> code.replaceFirstChar { it.uppercase() }
}

// ── Tab: Insights ─────────────────────────────────────────────────────────

@Composable
private fun InsightsTab(uiState: DashboardUiState) {
    val insights = uiState.analytics?.insights ?: emptyList()

    if (uiState.errorAnalytics != null) {
        ErrorContent(uiState.errorAnalytics) {}
        return
    }
    if (insights.isEmpty()) {
        EmptyContent("Completa más sesiones para recibir\nrecomendaciones personalizadas.")
        return
    }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier            = Modifier.fillMaxSize()
    ) {
        uiState.analytics?.let { analytics ->
            // ── Estadísticas globales ────────────────────────────────
            item {
                GlobalStatsRow(analytics)
            }

            // ── Récords personales ───────────────────────────────────
            if (analytics.bestTechniqueScore != null || analytics.maxReps != null || analytics.maxWeightKg != null) {
                item {
                    Spacer(Modifier.height(4.dp))
                    PersonalRecordsCard(analytics)
                }
            }

            // ── Alerta sobreentrenamiento ────────────────────────────
            if (analytics.overtrainingRisk) {
                item { OvertrainingBanner() }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Recomendaciones",
                    style    = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        items(insights, key = { it.title }) { insight ->
            InsightCard(insight = insight)
        }
    }
}

@Composable
private fun GlobalStatsRow(analytics: com.moveinsight.domain.model.Analytics) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        StatChip(label = "Sesiones",    value = "${analytics.totalSessions}")
        StatChip(label = "Técnica avg", value = analytics.avgTechniqueScore
            ?.let { "%.0f".format(it) } ?: "—")
        StatChip(label = "Máx. carga",  value = analytics.maxWeightKg
            ?.let { "${it.toInt()} kg" } ?: "—")
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        color  = NavyLight,
        shape  = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color      = CyanPrimary,
                    fontWeight = FontWeight.Black
                )
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── Récords personales ────────────────────────────────────────────────────

@Composable
private fun PersonalRecordsCard(analytics: com.moveinsight.domain.model.Analytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = NavyLight.copy(alpha = 0.7f)),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint               = YellowCaution,
                    modifier           = Modifier.size(22.dp)
                )
                Text(
                    text  = "Récords personales",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary
                    )
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                analytics.bestTechniqueScore?.let { best ->
                    PrChip(
                        icon  = Icons.Filled.Star,
                        label = "Mejor técnica",
                        value = "%.1f/10".format(best),
                        color = GreenReady
                    )
                }
                analytics.maxWeightKg?.let { maxW ->
                    PrChip(
                        icon  = Icons.Filled.FitnessCenter,
                        label = "Máx. carga",
                        value = "${maxW.toInt()} kg",
                        color = CyanPrimary
                    )
                }
                analytics.maxReps?.let { maxR ->
                    PrChip(
                        icon  = Icons.Filled.Repeat,
                        label = "Máx. reps",
                        value = "$maxR",
                        color = OrangePower
                    )
                }
            }
        }
    }
}

@Composable
private fun PrChip(
    icon  : androidx.compose.ui.graphics.vector.ImageVector,
    label : String,
    value : String,
    color : androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier         = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text  = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                color      = color
            )
        )
        Text(
            text      = label,
            style     = MaterialTheme.typography.bodySmall,
            color     = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ── Alerta sobreentrenamiento ─────────────────────────────────────────────

@Composable
private fun OvertrainingBanner() {
    Surface(
        color  = RedAlert.copy(alpha = 0.12f),
        shape  = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = Icons.Filled.Warning,
                contentDescription = null,
                tint               = RedAlert,
                modifier           = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text  = "Riesgo de sobreentrenamiento",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color      = RedAlert
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "Tu fatiga acumulada es elevada. Toma al menos 48h de descanso antes de tu próximo entreno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

// ── Estados vacío / error ─────────────────────────────────────────────────

@Composable
private fun EmptyContent(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp)
        ) {
            Text(error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors  = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = NavyDeep)
            ) {
                Text("Reintentar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}