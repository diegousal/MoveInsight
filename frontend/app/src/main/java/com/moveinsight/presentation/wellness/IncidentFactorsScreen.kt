package com.moveinsight.presentation.wellness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.domain.model.FactorSeverity
import com.moveinsight.domain.model.Incident
import com.moveinsight.domain.model.IncidentFactors
import com.moveinsight.domain.model.ObservedFactor
import com.moveinsight.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentFactorsScreen(
    onNavigateBack : () -> Unit,
    viewModel      : IncidentFactorsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = NavyDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text("Factores observados",
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
        }
    ) { padding ->
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .background(NavyDeep)
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading            -> CircularProgressIndicator(color = CyanPrimary)
                state.errorMsg != null     -> ErrorBlock(state.errorMsg!!, viewModel::load)
                state.incident != null && state.factors != null -> {
                    Content(state.incident!!, state.factors!!)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Content(incident: Incident, factors: IncidentFactors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // ── Cabecera de la incidencia ─────────────────────────────────
        IncidentHeader(incident, windowDays = factors.windowDays, sampleSize = factors.sampleSize)

        Spacer(Modifier.height(20.dp))

        // ── Aviso explicativo ─────────────────────────────────────────
        InfoBox(message = factors.note)

        Spacer(Modifier.height(20.dp))

        // ── Lista de factores ─────────────────────────────────────────
        if (factors.factors.isEmpty()) {
            EmptyFactorsCard()
        } else {
            Text(
                text  = "Métricas pre-incidencia (${factors.windowDays} días previos)",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = TextPrimary, fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.height(10.dp))
            factors.factors.forEach { factor ->
                FactorRow(factor)
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(28.dp))
        DisclaimerNote()
        Spacer(Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cabecera incidencia
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IncidentHeader(incident: Incident, windowDays: Int, sampleSize: Int) {
    val statusColor = if (incident.isActive) RedAlert else GreenReady
    val statusLabel = if (incident.isActive) "Activa" else "Recuperada"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavyMid)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.HealthAndSafety,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = displayBodyZone(incident.bodyZone),
                    style = MaterialTheme.typography.titleMedium.copy(
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
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text  = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = statusColor, fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text  = "Inicio: ${incident.startDate}" +
                    (incident.endDate?.let { "  ·  Fin: $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text  = "Análisis sobre $windowDays días previos · $sampleSize sesión(es) en ese periodo",
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
        )
        if (incident.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text  = incident.notes,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextPrimary, lineHeight = 17.sp
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Factor row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FactorRow(factor: ObservedFactor) {
    val sevColor = when (factor.severity) {
        FactorSeverity.HIGH     -> RedAlert
        FactorSeverity.MODERATE -> YellowCaution
        FactorSeverity.LOW      -> GreenReady
    }
    val arrow = if (factor.deltaPct > 0) Icons.AutoMirrored.Filled.TrendingUp
                else                     Icons.AutoMirrored.Filled.TrendingDown

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavyMid)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text  = factor.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = TextPrimary, fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.weight(1f)
            )
            Icon(arrow, null, tint = sevColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text  = (if (factor.deltaPct > 0) "+" else "") + "${factor.deltaPct.toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = sevColor, fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text  = factor.description,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary, lineHeight = 17.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Auxiliares
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InfoBox(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CyanPrimary.copy(alpha = 0.10f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Info, null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
        Text(
            text  = message,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextPrimary, lineHeight = 17.sp
            )
        )
    }
}

@Composable
private fun EmptyFactorsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavyMid)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.SearchOff, null, tint = TextSecondary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "No hay datos suficientes en el periodo previo",
            style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Necesitamos sesiones registradas en los días previos para detectar patrones.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary, lineHeight = 16.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DisclaimerNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NavyMid.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.PrivacyTip, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Text(
            text  = "Este análisis es descriptivo, no diagnóstico. " +
                    "Consulta con un profesional sanitario ante dolor persistente o lesión.",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary, lineHeight = 16.sp
            )
        )
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.ErrorOutline, null, tint = RedAlert, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}
