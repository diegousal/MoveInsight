package com.moveinsight.presentation.dashboard

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Notifications
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
            val tabs = listOf("Sesiones", "Gráficos", "Insights")

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
                    text  = "Readiness",
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
        item { TechniqueLineChart(uiState.sessions) }
        item { LoadProgressionChart(uiState.sessions) }
        item { BorgScaleChart(uiState.sessions) }
    }
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

    // ── Estadísticas globales ──────────────────────────────────────────
    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier            = Modifier.fillMaxSize()
    ) {
        uiState.analytics?.let { analytics ->
            item {
                GlobalStatsRow(analytics)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recomendaciones",
                    style    = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
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