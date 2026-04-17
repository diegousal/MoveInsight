package com.moveinsight.presentation.checkin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.presentation.checkin.BodyMapComponent
import com.moveinsight.presentation.checkin.EvaScaleSlider
import com.moveinsight.presentation.components.MoveInsightLogo
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PainCheckInScreen(
    onNavigateHome : () -> Unit,
    viewModel      : PainCheckInViewModel = hiltViewModel()
) {
    val form         by viewModel.form.collectAsStateWithLifecycle()
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost =  remember { SnackbarHostState() }
    val isLoading    =  uiState is PainCheckInUiState.Loading

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                PainCheckInUiEvent.NavigateHome       -> onNavigateHome()
                is PainCheckInUiEvent.ShowSnackbar    -> snackbarHost.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = NavyDeep,
        topBar         = {
            TopAppBar(
                title   = { MoveInsightLogo(fontSize = 22.sp) },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDeep)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyDeep)
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Cabecera ──────────────────────────────────────────────────
            Text(
                text  = "Check-in de Dolor",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = "Seguimiento a las ${form.hoursAfter} horas del entrenamiento",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            // ── Sección 1: Escala EVA ─────────────────────────────────────
            Spacer(Modifier.height(28.dp))
            SectionTitle("1 · Intensidad del dolor (EVA)")
            Spacer(Modifier.height(16.dp))

            EvaScaleSlider(
                score    = form.evaScore,
                onChange = viewModel::onEvaScoreChange
            )

            // ── Aviso de inconsistencia zonas/EVA ─────────────────────────
            val hasConflict = form.selectedZones.isNotEmpty() && form.evaScore == 0
            AnimatedVisibility(
                visible = hasConflict,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color    = OrangePower.copy(alpha = 0.10f),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment     = Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint               = OrangePower,
                                modifier           = Modifier.size(16.dp).padding(top = 1.dp)
                            )
                            Text(
                                text  = "Si marcas zonas con molestias el nivel de dolor debe ser mayor que 0. " +
                                        "Usa «Sin molestias» si finalmente no tienes dolor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OrangePower
                            )
                        }
                    }
                }
            }

            // ── Sección 2: Mapa corporal ──────────────────────────────────
            Spacer(Modifier.height(32.dp))
            SectionTitle("2 · ¿Dónde notas molestias?")
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Pulsa las zonas afectadas (opcional si no hay dolor)",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            BodyMapComponent(
                selectedZones = form.selectedZones,
                showFront     = form.showFrontBody,
                onZoneToggle  = viewModel::onBodyZoneToggle,
                onViewToggle  = viewModel::onBodyViewToggle
            )

            // ── Resumen de zonas seleccionadas ────────────────────────────
            if (form.selectedZones.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color  = OrangePower.copy(alpha = 0.08f),
                    shape  = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text     = form.selectedZones.joinToString(" · ") { it.label },
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = OrangePower,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── CTA: Enviar ───────────────────────────────────────────────
            Button(
                onClick  = viewModel::onSubmit,
                enabled  = !isLoading && !hasConflict,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor   = NavyDeep
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(22.dp),
                        color       = NavyDeep,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text("Guardar check-in", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Opción sin dolor — guarda EVA=0
            OutlinedButton(
                onClick  = viewModel::onNoPain,
                enabled  = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                border  = BorderStroke(1.dp, GreenReady.copy(alpha = 0.55f)),
                colors  = ButtonDefaults.outlinedButtonColors(
                    contentColor = GreenReady
                )
            ) {
                Text(
                    "Sin molestias",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth()
    )
}