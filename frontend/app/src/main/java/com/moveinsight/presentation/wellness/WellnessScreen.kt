package com.moveinsight.presentation.wellness

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.domain.model.Incident
import com.moveinsight.domain.model.ReadinessTrend
import com.moveinsight.domain.model.ValidationResult
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// Pantalla
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellnessScreen(
    onNavigateBack         : () -> Unit,
    onNavigateToFactors    : (incidentId: Int) -> Unit,
    viewModel              : WellnessViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is WellnessEvent.ShowSnackbar -> snackbarHost.showSnackbar(event.msg)
            }
        }
    }

    if (state.showRegisterDialog) {
        RegisterIncidentDialog(
            isSaving  = state.isSaving,
            onDismiss = viewModel::closeRegisterDialog,
            onSave    = { zone, sev, type, startDate, endDate, notes ->
                viewModel.createIncident(zone, sev, type, startDate, endDate, notes)
            }
        )
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = NavyDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text("Bienestar",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = TextPrimary, fontWeight = FontWeight.SemiBold))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDeep)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = viewModel::openRegisterDialog,
                containerColor = CyanPrimary,
                contentColor   = NavyDeep
            ) {
                Icon(Icons.Filled.Add, "Registrar molestia")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyDeep)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ── Selector de rango ──────────────────────────────────────────
            RangeSelector(currentDays = state.days, onChange = viewModel::changeRange)
            Spacer(Modifier.height(20.dp))

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = CyanPrimary) }

                state.errorMsg != null -> ErrorBlock(state.errorMsg!!, viewModel::reload)

                state.timeline != null -> {
                    val tl = state.timeline!!

                    // ── Gráfico Readiness + bandas de incidencias ────────
                    WellnessChart(
                        points    = tl.points,
                        incidents = tl.incidents,
                        rangeDays = state.days
                    )

                    Spacer(Modifier.height(24.dp))

                    // ── Lista de incidencias ──────────────────────────────
                    SectionTitle(
                        title    = "Incidencias",
                        subtitle = "${tl.incidents.size} registrada(s)"
                    )
                    Spacer(Modifier.height(8.dp))

                    if (tl.incidents.isEmpty()) {
                        EmptyIncidentsCard(onRegister = viewModel::openRegisterDialog)
                    } else {
                        tl.incidents.forEach { incident ->
                            IncidentCard(
                                incident   = incident,
                                onRecover  = { viewModel.recoverIncident(incident) },
                                onDelete   = { viewModel.deleteIncident(incident) },
                                onAnalyze  = { onNavigateToFactors(incident.id) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // ── Validación retrospectiva (F8) ─────────────────────
                    Spacer(Modifier.height(20.dp))
                    ValidationSection(
                        validation      = state.validation,
                        isLoading       = state.isLoadingValidation,
                        isExpanded      = state.showValidation,
                        onToggle        = viewModel::toggleValidation
                    )
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sección de Validación retrospectiva (F8)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ValidationSection(
    validation : ValidationResult?,
    isLoading  : Boolean,
    isExpanded : Boolean,
    onToggle   : () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Header (siempre visible) ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NavyMid)
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Analytics, null,
                tint     = CyanPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Validación retrospectiva",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = TextPrimary, fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    "¿Descendió el readiness antes de las incidencias?",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // ── Contenido expandible ─────────────────────────────────────────
        if (isExpanded) {
            Spacer(Modifier.height(8.dp))
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = CyanPrimary, modifier = Modifier.size(28.dp)) }

                validation == null -> Text(
                    "No se pudieron cargar los datos.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    modifier = Modifier.padding(8.dp)
                )

                validation.incidentsAnalyzed == 0 -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavyMid)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Registra incidencias en Bienestar para activar este análisis.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary, lineHeight = 17.sp
                        )
                    )
                }

                else -> ValidationContent(validation)
            }
        }
    }
}

@Composable
private fun ValidationContent(val_: ValidationResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavyMid)
            .padding(16.dp)
    ) {
        // ── KPI principal ─────────────────────────────────────────────────
        val rate = (val_.earlyWarningRate * 100).toInt()
        val rateColor = when {
            rate >= 60 -> GreenReady
            rate >= 30 -> YellowCaution
            else       -> TextSecondary
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "$rate%",
                    style = MaterialTheme.typography.displaySmall.copy(
                        color      = rateColor,
                        fontWeight = FontWeight.Black,
                        fontSize   = 40.sp
                    )
                )
                Text(
                    "de incidencias con aviso previo",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${val_.earlyWarningCount} / ${val_.incidentsAnalyzed}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = TextPrimary, fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    "con descenso ≥ 10 pts",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Resumen textual ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CyanPrimary.copy(alpha = 0.08f))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Filled.Info, null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
            Text(
                val_.summary,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextPrimary, lineHeight = 17.sp
                )
            )
        }

        // ── Detalle por incidencia ────────────────────────────────────────
        if (val_.details.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Detalle por incidencia",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = TextSecondary, fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.height(6.dp))
            val_.details.forEach { det ->
                ValidationDetailRow(det)
                Spacer(Modifier.height(6.dp))
            }
        }

        // ── Disclaimer ────────────────────────────────────────────────────
        Spacer(Modifier.height(10.dp))
        Text(
            "Análisis descriptivo para el TFG. No implica relación causal entre readiness y lesión.",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary, lineHeight = 15.sp
            )
        )
    }
}

@Composable
private fun ValidationDetailRow(det: com.moveinsight.domain.model.ValidationDetail) {
    val trendColor = when (det.trend) {
        ReadinessTrend.DECLINING -> RedAlert
        ReadinessTrend.IMPROVING -> GreenReady
        ReadinessTrend.STABLE    -> TextSecondary
    }
    val trendIcon = when (det.trend) {
        ReadinessTrend.DECLINING -> Icons.AutoMirrored.Filled.TrendingDown
        ReadinessTrend.IMPROVING -> Icons.AutoMirrored.Filled.TrendingUp
        ReadinessTrend.STABLE    -> Icons.AutoMirrored.Filled.TrendingFlat
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NavyDeep.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(trendIcon, null, tint = trendColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayBodyZoneShort(det.bodyZone),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = TextPrimary, fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                det.trend.display,
                style = MaterialTheme.typography.labelSmall.copy(color = trendColor)
            )
        }
        // Valores antes / después
        if (det.readinessBefore != null && det.readinessAt != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${det.readinessBefore} → ${det.readinessAt}",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary)
                )
                det.delta?.let { d ->
                    Text(
                        (if (d >= 0) "+$d" else "$d") + " pts",
                        style = MaterialTheme.typography.labelSmall.copy(color = trendColor)
                    )
                }
            }
        } else {
            Text(
                "Sin datos suficientes",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
        }
    }
}

private fun displayBodyZoneShort(raw: String): String = when (raw) {
    "rodilla_izq"  -> "Rodilla Izq."
    "rodilla_der"  -> "Rodilla Der."
    "lumbar"       -> "Lumbar"
    "cadera_izq"   -> "Cadera Izq."
    "cadera_der"   -> "Cadera Der."
    "tobillo_izq"  -> "Tobillo Izq."
    "tobillo_der"  -> "Tobillo Der."
    "hombro_izq"   -> "Hombro Izq."
    "hombro_der"   -> "Hombro Der."
    "cuadriceps"   -> "Cuádriceps"
    "isquiotibial" -> "Isquiotibial"
    else           -> raw.replaceFirstChar { it.uppercase() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Selector de rango
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RangeSelector(currentDays: Int, onChange: (Int) -> Unit) {
    val options = listOf(30 to "30d", 90 to "3 meses", 180 to "6 meses", 365 to "1 año")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (days, label) ->
            val selected = days == currentDays
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) CyanPrimary else NavyMid)
                    .clickable { onChange(days) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color      = if (selected) NavyDeep else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sección título + sub
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = TextPrimary, fontWeight = FontWeight.SemiBold
            )
        )
        Text(
            text  = subtitle,
            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card de incidencia
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IncidentCard(
    incident  : Incident,
    onRecover : () -> Unit,
    onDelete  : () -> Unit,
    onAnalyze : () -> Unit
) {
    val statusColor = if (incident.isActive) RedAlert else GreenReady
    val statusLabel = if (incident.isActive) "Activa" else "Recuperada"
    val sevColor = when {
        incident.severity >= 7 -> RedAlert
        incident.severity >= 4 -> YellowCaution
        else                   -> GreenReady
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavyMid)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = displayBodyZone(incident.bodyZone),
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = TextPrimary, fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text  = "${incident.incidentType.display} · EVA ${incident.severity}/10",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text  = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = statusColor, fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Inicio: ${incident.startDate}" +
                    (incident.endDate?.let { "  ·  Fin: $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        if (incident.notes.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text  = incident.notes,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextPrimary, lineHeight = 17.sp
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Severidad chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(sevColor.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text  = "Sev ${incident.severity}/10",
                    style = MaterialTheme.typography.labelSmall.copy(color = sevColor)
                )
            }
            Spacer(Modifier.weight(1f))
            // Botones de acción
            TextButton(onClick = onAnalyze, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Filled.Analytics, null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Factores", color = CyanPrimary, style = MaterialTheme.typography.labelSmall)
            }
            if (incident.isActive) {
                TextButton(onClick = onRecover, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = GreenReady, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Recuperada", color = GreenReady, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.DeleteOutline, null, tint = TextSecondary,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty / Error
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyIncidentsCard(onRegister: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavyMid)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.HealthAndSafety, null, tint = CyanPrimary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Sin incidencias registradas",
            style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Registra molestias o dolores para detectar patrones.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRegister) {
            Text("Registrar primera incidencia", color = CyanPrimary)
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.ErrorOutline, null, tint = RedAlert, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: nombre legible de zona corporal
// ─────────────────────────────────────────────────────────────────────────────

internal fun displayBodyZone(code: String): String = when (code) {
    "rodilla_izq" -> "Rodilla izquierda"
    "rodilla_der" -> "Rodilla derecha"
    "lumbar"      -> "Lumbar"
    "cervical"    -> "Cervical"
    "cadera_izq"  -> "Cadera izquierda"
    "cadera_der"  -> "Cadera derecha"
    "tobillo_izq" -> "Tobillo izquierdo"
    "tobillo_der" -> "Tobillo derecho"
    "hombro_izq"  -> "Hombro izquierdo"
    "hombro_der"  -> "Hombro derecho"
    "general"     -> "General"
    else          -> code.replaceFirstChar { it.uppercase() }
}
