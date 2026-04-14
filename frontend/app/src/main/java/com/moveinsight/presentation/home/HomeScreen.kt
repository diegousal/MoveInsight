package com.moveinsight.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moveinsight.presentation.components.MoveInsightLogo
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout             : () -> Unit,
    onNavigateToRecord   : () -> Unit,     // → Grabar vídeo
    onNavigateToUpload   : () -> Unit,     // → Subir vídeo existente
    viewModel            : HomeViewModel = hiltViewModel()
) {
    val snackbarHost             = remember { SnackbarHostState() }
    var showTrainingSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                HomeUiEvent.NavigateToLogin -> onLogout()
            }
        }
    }

    // ── Bottom Sheet: selección de modo ──────────────────────────────────
    if (showTrainingSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTrainingSheet = false },
            containerColor   = NavyMid,
            tonalElevation   = 0.dp
        ) {
            TrainingModeSheet(
                onRecordSelected = {
                    showTrainingSheet = false
                    onNavigateToRecord()
                },
                onUploadSelected = {
                    showTrainingSheet = false
                    onNavigateToUpload()
                }
            )
        }
    }

    Scaffold(
        containerColor               = NavyDeep,
        snackbarHost                 = { SnackbarHost(snackbarHost) },
        // ── Fix 2: FAB centrado ──────────────────────────────────────────
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
                title  = { MoveInsightLogo(fontSize = 22.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDeep
                ),
                actions = {
                    IconButton(onClick = viewModel::onLogoutClick) {
                        Icon(
                            imageVector        = Icons.Filled.Logout,
                            contentDescription = "Cerrar sesión",
                            tint               = TextSecondary
                        )
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
                // Espacio para que el FAB centrado no tape el contenido
                .padding(bottom = 88.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.padding(horizontal = 24.dp)
            ) {
                // ── Fix 1: semáforo correctamente centrado ─────────────
                ReadinessIndicator(color = GreenReady)

                Spacer(Modifier.height(20.dp))

                Text(
                    text  = "Listo para entrenar",
                    style = MaterialTheme.typography.headlineMedium,
                    color = GreenReady
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = "El dashboard de readiness y análisis\nse implementará en la Fase 3.",
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(40.dp))
                Text(
                    text      = "↓  Pulsa el botón para iniciar",
                    style     = MaterialTheme.typography.bodyMedium.copy(color = CyanPrimary),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Semáforo: círculos anidados perfectamente centrados ───────────────────
@Composable
private fun ReadinessIndicator(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier         = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center     // centra el hijo exactamente
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// ── Bottom sheet de selección de modo ────────────────────────────────────
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
        Text(
            text  = "¿Cómo quieres registrar la serie?",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Opción: Grabar ─────────────────────────────────────────
            TrainingModeCard(
                modifier    = Modifier.weight(1f),
                icon        = Icons.Filled.Videocam,
                iconTint    = CyanPrimary,
                title       = "Grabar\nvídeo",
                description = "Usa la cámara en tiempo real",
                onClick     = onRecordSelected
            )
            // ── Opción: Subir ──────────────────────────────────────────
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
        onClick    = onClick,
        modifier   = modifier.height(160.dp),
        colors     = CardDefaults.cardColors(containerColor = NavyLight),
        shape      = MaterialTheme.shapes.large
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text      = title,
                style     = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color     = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = description,
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}