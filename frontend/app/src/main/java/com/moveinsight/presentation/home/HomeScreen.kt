package com.moveinsight.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.domain.model.ReadinessLevel
import com.moveinsight.presentation.components.MoveInsightLogo
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout              : () -> Unit,
    onNavigateToRecord    : () -> Unit,
    onNavigateToUpload    : () -> Unit,
    onNavigateToDashboard : () -> Unit,
    viewModel             : HomeViewModel = hiltViewModel()
) {
    val data                     by viewModel.data.collectAsStateWithLifecycle()
    val snackbarHost             =  remember { SnackbarHostState() }
    var showTrainingSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                HomeUiEvent.NavigateToLogin -> onLogout()
            }
        }
    }

    // ── Bottom Sheet ──────────────────────────────────────────────────────
    if (showTrainingSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTrainingSheet = false },
            containerColor   = NavyMid,
            tonalElevation   = 0.dp
        ) {
            TrainingModeSheet(
                onRecordSelected = { showTrainingSheet = false; onNavigateToRecord() },
                onUploadSelected = { showTrainingSheet = false; onNavigateToUpload() }
            )
        }
    }

    val readinessColor = when (data.readinessLevel) {
        ReadinessLevel.HIGH   -> GreenReady
        ReadinessLevel.MEDIUM -> YellowCaution
        ReadinessLevel.LOW    -> RedAlert
    }
    val readinessText = when (data.readinessLevel) {
        ReadinessLevel.HIGH   -> "Óptimo para entrenar"
        ReadinessLevel.MEDIUM -> "Entrena con moderación"
        ReadinessLevel.LOW    -> "Necesitas descanso"
    }

    Scaffold(
        containerColor               = NavyDeep,
        snackbarHost                 = { SnackbarHost(snackbarHost) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton         = {
            ExtendedFloatingActionButton(
                onClick        = { showTrainingSheet = true },
                icon           = { Icon(Icons.Filled.FitnessCenter, null) },
                text           = { Text("Iniciar Entrenamiento") },
                containerColor = CyanPrimary,
                contentColor   = NavyDeep
            )
        },
        topBar = {
            TopAppBar(
                title   = { MoveInsightLogo(fontSize = 22.sp) },
                colors  = TopAppBarDefaults.topAppBarColors(containerColor = NavyDeep),
                actions = {
                    // Botón Dashboard
                    IconButton(onClick = onNavigateToDashboard) {
                        Icon(Icons.Filled.BarChart, "Ver análisis", tint = CyanPrimary)
                    }
                    IconButton(onClick = viewModel::onLogoutClick) {
                        Icon(Icons.Filled.Logout, "Cerrar sesión", tint = TextSecondary)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .background(NavyDeep)
                .padding(padding)
                .padding(bottom = 88.dp),
            contentAlignment = Alignment.Center
        ) {
            if (data.isLoading) {
                CircularProgressIndicator(color = CyanPrimary)
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(horizontal = 24.dp)
                ) {
                    // ── Semáforo ───────────────────────────────────────
                    Box(
                        modifier         = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(readinessColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Score numérico del servidor o punto sólido
                        val scoreText = data.analytics?.readinessScore?.toString()
                        if (scoreText != null) {
                            Text(
                                text  = scoreText,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    color      = readinessColor,
                                    fontWeight = FontWeight.Black,
                                    fontSize   = 40.sp
                                )
                            )
                        } else {
                            // Punto sólido cuando no hay datos del servidor aún
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(readinessColor)
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text  = readinessText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = readinessColor
                    )

                    Spacer(Modifier.height(8.dp))

                    // Estadísticas rápidas si hay datos
                    data.analytics?.let { analytics ->
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            QuickStat("${analytics.totalSessions}", "sesiones")
                            Spacer(Modifier.width(24.dp))
                            analytics.maxWeightKg?.let {
                                QuickStat("${it.toInt()} kg", "máx. carga")
                            }
                            Spacer(Modifier.width(24.dp))
                            analytics.avgTechniqueScore?.let {
                                QuickStat("%.0f".format(it), "técnica avg")
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        text      = "↓  Pulsa para registrar una nueva serie",
                        style     = MaterialTheme.typography.bodyMedium.copy(color = CyanPrimary),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Bottom Sheet de modo de entrenamiento (reutilizado de fase anterior) ──
@Composable
private fun TrainingModeSheet(
    onRecordSelected : () -> Unit,
    onUploadSelected : () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("¿Cómo quieres registrar la serie?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TrainingModeCard(
                modifier    = Modifier.weight(1f),
                icon        = Icons.Filled.Videocam,
                iconTint    = CyanPrimary,
                title       = "Grabar\nvídeo",
                description = "Usa la cámara en tiempo real",
                onClick     = onRecordSelected
            )
            TrainingModeCard(
                modifier    = Modifier.weight(1f),
                icon        = Icons.Filled.VideoLibrary,
                iconTint    = OrangePower,
                title       = "Subir\nvídeo",
                description = "Selecciona uno de tu galería",
                onClick     = onUploadSelected
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingModeCard(
    modifier    : Modifier,
    icon        : androidx.compose.ui.graphics.vector.ImageVector,
    iconTint    : androidx.compose.ui.graphics.Color,
    title       : String,
    description : String,
    onClick     : () -> Unit
) {
    Card(
        onClick  = onClick,
        modifier = modifier.height(160.dp),
        colors   = CardDefaults.cardColors(containerColor = NavyLight),
        shape    = MaterialTheme.shapes.large
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}