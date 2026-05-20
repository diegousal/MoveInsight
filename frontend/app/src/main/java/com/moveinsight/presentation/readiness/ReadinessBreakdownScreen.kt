package com.moveinsight.presentation.readiness

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.domain.model.ReadinessBreakdown
import com.moveinsight.domain.model.ReadinessComponent
import com.moveinsight.domain.model.ReadinessLevel
import com.moveinsight.domain.model.ReadinessStage
import com.moveinsight.domain.model.level
import com.moveinsight.presentation.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Pantalla
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessBreakdownScreen(
    onNavigateBack : () -> Unit,
    viewModel      : ReadinessBreakdownViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = NavyDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Detalle de Readiness",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color      = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                            tint = TextPrimary
                        )
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
            when (val s = state) {
                ReadinessBreakdownUiState.Loading      -> CircularProgressIndicator(color = CyanPrimary)
                is ReadinessBreakdownUiState.Error     -> ErrorBlock(s.message, viewModel::load)
                is ReadinessBreakdownUiState.Success   -> BreakdownContent(s.breakdown)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Contenido principal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BreakdownContent(b: ReadinessBreakdown) {
    val readinessColor = when (b.level()) {
        ReadinessLevel.HIGH   -> GreenReady
        ReadinessLevel.MEDIUM -> YellowCaution
        ReadinessLevel.LOW    -> RedAlert
    }
    val labelText = when (b.level()) {
        ReadinessLevel.HIGH   -> "Óptimo para entrenar"
        ReadinessLevel.MEDIUM -> "Entrena con moderación"
        ReadinessLevel.LOW    -> "Necesitas descanso"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Score circular grande ──────────────────────────────────────
        ScoreCircle(score = b.score, color = readinessColor)
        Spacer(Modifier.height(12.dp))
        Text(
            text  = labelText,
            style = MaterialTheme.typography.titleLarge.copy(
                color      = readinessColor,
                fontWeight = FontWeight.SemiBold
            )
        )

        Spacer(Modifier.height(20.dp))

        // ── Resumen ────────────────────────────────────────────────────
        SummaryCard(summary = b.summary, stage = b.stage)

        // ── Warning principal (si existe) ─────────────────────────────
        b.mainWarning?.let { warning ->
            Spacer(Modifier.height(12.dp))
            WarningCard(warning)
        }

        Spacer(Modifier.height(28.dp))

        // ── Componentes ────────────────────────────────────────────────
        if (b.components.isNotEmpty()) {
            Text(
                text     = "Componentes del cálculo",
                style    = MaterialTheme.typography.labelLarge.copy(
                    color         = TextSecondary,
                    letterSpacing = 0.5.sp,
                    fontWeight    = FontWeight.SemiBold
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            b.components.forEach { component ->
                ComponentRow(component)
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Disclaimer ─────────────────────────────────────────────────
        DisclaimerNote()

        Spacer(Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Score circle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScoreCircle(score: Int, color: Color) {
    val animatedFraction by animateFloatAsState(
        targetValue   = score / 100f,
        animationSpec = tween(900),
        label         = "score_anim"
    )

    Box(
        modifier         = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 14f
            // Fondo del anillo
            drawArc(
                color     = color.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter  = false,
                topLeft    = Offset(stroke, stroke),
                size       = Size(size.width - stroke * 2, size.height - stroke * 2),
                style      = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Anillo activo
            drawArc(
                color      = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedFraction,
                useCenter  = false,
                topLeft    = Offset(stroke, stroke),
                size       = Size(size.width - stroke * 2, size.height - stroke * 2),
                style      = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = "$score",
                style = MaterialTheme.typography.displayMedium.copy(
                    color      = color,
                    fontWeight = FontWeight.Black,
                    fontSize   = 48.sp
                )
            )
            Text(
                text  = "/ 100",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Resumen / Stage badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(summary: String, stage: ReadinessStage) {
    val (badgeText, badgeColor) = when (stage) {
        ReadinessStage.COLD_START  -> "Datos iniciales" to OrangePower
        ReadinessStage.EARLY       -> "Análisis intermedio" to YellowCaution
        ReadinessStage.ESTABLISHED -> "Análisis completo" to GreenReady
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavyMid)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text  = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text  = summary,
            style = MaterialTheme.typography.bodyMedium.copy(
                color      = TextPrimary,
                lineHeight = 20.sp
            )
        )
    }
}

@Composable
private fun WarningCard(warning: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OrangePower.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = OrangePower,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text  = warning,
            style = MaterialTheme.typography.bodySmall.copy(
                color      = TextPrimary,
                lineHeight = 18.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Componente individual
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ComponentRow(component: ReadinessComponent) {
    val color = when {
        component.value >= 70 -> GreenReady
        component.value >= 40 -> YellowCaution
        else                  -> RedAlert
    }
    val animated by animateFloatAsState(
        targetValue   = component.value / 100f,
        animationSpec = tween(700),
        label         = "comp_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavyMid)
            .padding(14.dp)
    ) {
        // Cabecera: nombre + valor + peso
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = component.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    color      = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = "${component.value}%",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color      = color,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.width(8.dp))
                WeightBadge(weight = component.weight)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Barra de progreso
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(NavyDeep)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text  = component.explanation,
            style = MaterialTheme.typography.bodySmall.copy(
                color      = TextSecondary,
                lineHeight = 17.sp
            )
        )
    }
}

@Composable
private fun WeightBadge(weight: Float) {
    val pct = (weight * 100).toInt()
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(NavyDeep)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text  = "peso $pct%",
            style = MaterialTheme.typography.labelSmall.copy(
                color      = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Disclaimer
// ─────────────────────────────────────────────────────────────────────────────

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
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text  = "Este semáforo se calcula a partir de tus sentadillas registradas. " +
                    "No sustituye a un profesional sanitario.",
            style = MaterialTheme.typography.labelSmall.copy(
                color      = TextSecondary,
                lineHeight = 16.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier            = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = RedAlert,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text     = message,
            style    = MaterialTheme.typography.bodyMedium,
            color    = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}
