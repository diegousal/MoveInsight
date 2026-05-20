package com.moveinsight.presentation.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.presentation.components.NeuroSquatPrimaryButton
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    onNavigateToHome : () -> Unit,
    viewModel        : OnboardingViewModel = hiltViewModel()
) {
    val form         by viewModel.form.collectAsStateWithLifecycle()
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost =  remember { SnackbarHostState() }
    var step         by remember { mutableIntStateOf(0) }   // 0 = disclaimer, 1 = datos, 2 = nivel/obj

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                OnboardingEvent.NavigateToHome     -> onNavigateToHome()
                is OnboardingEvent.ShowSnackbar    -> snackbarHost.showSnackbar(event.msg)
            }
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = NavyDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyDeep)
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Indicador de pasos ─────────────────────────────────────────
            StepIndicator(currentStep = step, totalSteps = 3)

            Spacer(Modifier.height(32.dp))

            // ── Contenido del paso ─────────────────────────────────────────
            AnimatedContent(
                targetState  = step,
                transitionSpec = {
                    if (targetState > initialState)
                        slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                    else
                        slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                },
                label = "onboarding_step"
            ) { currentStep ->
                when (currentStep) {
                    0 -> DisclaimerStep(onNext = { step = 1 })
                    1 -> PersonalDataStep(
                        form              = form,
                        onAgeChange       = viewModel::onAgeChange,
                        onWeightChange    = viewModel::onBodyWeightChange,
                        onAgeFocusLost    = viewModel::onAgeFocusLost,
                        onWeightFocusLost = viewModel::onWeightFocusLost,
                        onNext = { if (viewModel.validatePersonalData()) step = 2 },
                        onBack = { step = 0 }
                    )
                    2 -> LevelObjectiveStep(
                        form              = form,
                        onLevelSelected   = viewModel::onLevelSelected,
                        onObjectiveSelected = viewModel::onObjectiveSelected,
                        isLoading         = uiState is OnboardingUiState.Loading,
                        canFinish         = form.level.isNotBlank() && form.objective.isNotBlank(),
                        onFinish          = viewModel::onFinish,
                        onBack            = { step = 1 }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Paso 0 — Disclaimer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DisclaimerStep(onNext: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector        = Icons.Filled.HealthAndSafety,
            contentDescription = null,
            tint               = CyanPrimary,
            modifier           = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text  = "Bienvenido a MoveInsight",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "Antes de empezar, lee esto",
            style     = MaterialTheme.typography.bodyMedium,
            color     = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        DisclaimerCard(
            icon    = Icons.Filled.Analytics,
            color   = CyanPrimary,
            title   = "Herramienta de seguimiento",
            message = "MoveInsight analiza tu técnica de sentadilla y estima tu estado de readiness a partir de los datos que registras."
        )
        Spacer(Modifier.height(12.dp))
        DisclaimerCard(
            icon    = Icons.Filled.Warning,
            color   = YellowCaution,
            title   = "No es consejo médico",
            message = "Las recomendaciones de la app son orientativas. Ante cualquier dolor persistente o lesión, consulta a un profesional sanitario."
        )
        Spacer(Modifier.height(12.dp))
        DisclaimerCard(
            icon    = Icons.Filled.FitnessCenter,
            color   = OrangePower,
            title   = "Solo mide sentadilla",
            message = "El cálculo de readiness se basa únicamente en las sesiones registradas en esta app. Actividad fuera de la app no se tiene en cuenta."
        )
        Spacer(Modifier.height(12.dp))
        DisclaimerCard(
            icon    = Icons.Filled.Lock,
            color   = GreenReady,
            title   = "Tus datos son tuyos",
            message = "La información que introduces se usa exclusivamente para generar tu análisis personalizado."
        )

        Spacer(Modifier.height(36.dp))
        NeuroSquatPrimaryButton(
            text    = "Entendido, continuar",
            onClick = onNext
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DisclaimerCard(
    icon    : ImageVector,
    color   : Color,
    title   : String,
    message : String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavyMid)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment     = Alignment.Top
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text  = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    color      = TextSecondary,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Paso 1 — Datos personales
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PersonalDataStep(
    form              : OnboardingFormState,
    onAgeChange       : (String) -> Unit,
    onWeightChange    : (String) -> Unit,
    onAgeFocusLost    : () -> Unit,
    onWeightFocusLost : () -> Unit,
    onNext            : () -> Unit,
    onBack            : () -> Unit
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = CyanPrimary,
        unfocusedBorderColor = NavyLight,
        focusedLabelColor    = CyanPrimary,
        unfocusedLabelColor  = TextSecondary,
        cursorColor          = CyanPrimary,
        // Estados de error — Material 3 los gestiona automáticamente con isError=true
        errorBorderColor     = MaterialTheme.colorScheme.error,
        errorLabelColor      = MaterialTheme.colorScheme.error,
        errorLeadingIconColor= MaterialTheme.colorScheme.error,
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector        = Icons.Filled.Person,
            contentDescription = null,
            tint               = CyanPrimary,
            modifier           = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text  = "Cuéntanos sobre ti",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text      = "Estos datos permiten calibrar mejor tu semáforo de readiness.\nAmbos campos son opcionales.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        // ── Edad ──────────────────────────────────────────────────────────
        OutlinedTextField(
            value           = form.age,
            onValueChange   = onAgeChange,
            label           = { Text("Edad (años)") },
            placeholder     = { Text("Ej: 25 · rango 10–99", color = TextSecondary) },
            leadingIcon     = { Icon(Icons.Filled.Cake, null) },
            isError         = form.ageError != null,
            supportingText  = form.ageError?.let { msg ->
                { Text(msg, color = MaterialTheme.colorScheme.error) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine      = true,
            modifier        = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) onAgeFocusLost() },
            colors          = fieldColors
        )

        Spacer(Modifier.height(16.dp))

        // ── Peso ──────────────────────────────────────────────────────────
        OutlinedTextField(
            value           = form.bodyWeightKg,
            onValueChange   = onWeightChange,
            label           = { Text("Peso corporal (kg)") },
            placeholder     = { Text("Ej: 75.5 · rango 30–300", color = TextSecondary) },
            leadingIcon     = { Icon(Icons.Filled.MonitorWeight, null) },
            isError         = form.weightError != null,
            supportingText  = form.weightError?.let { msg ->
                { Text(msg, color = MaterialTheme.colorScheme.error) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine      = true,
            modifier        = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) onWeightFocusLost() },
            colors          = fieldColors
        )

        Spacer(Modifier.height(40.dp))

        NeuroSquatPrimaryButton(text = "Siguiente", onClick = onNext)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Volver", color = TextSecondary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Paso 2 — Nivel y objetivo
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LevelObjectiveStep(
    form                : OnboardingFormState,
    onLevelSelected     : (String) -> Unit,
    onObjectiveSelected : (String) -> Unit,
    isLoading           : Boolean,
    canFinish           : Boolean,
    onFinish            : () -> Unit,
    onBack              : () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector        = Icons.Filled.FitnessCenter,
            contentDescription = null,
            tint               = CyanPrimary,
            modifier           = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text  = "Tu perfil de entrenamiento",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        // ── Nivel ────────────────────────────────────────────────────────
        SectionLabel("¿Cuál es tu nivel en sentadilla?")
        Spacer(Modifier.height(10.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LevelCard(
                modifier  = Modifier.weight(1f),
                icon      = Icons.Filled.EmojiPeople,
                label     = "Principiante",
                sublabel  = "< 1 año",
                value     = "beginner",
                selected  = form.level == "beginner",
                onSelect  = onLevelSelected
            )
            LevelCard(
                modifier  = Modifier.weight(1f),
                icon      = Icons.Filled.TrendingUp,
                label     = "Intermedio",
                sublabel  = "1–3 años",
                value     = "intermediate",
                selected  = form.level == "intermediate",
                onSelect  = onLevelSelected
            )
            LevelCard(
                modifier  = Modifier.weight(1f),
                icon      = Icons.Filled.Star,
                label     = "Avanzado",
                sublabel  = "> 3 años",
                value     = "advanced",
                selected  = form.level == "advanced",
                onSelect  = onLevelSelected
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Objetivo ─────────────────────────────────────────────────────
        SectionLabel("¿Cuál es tu objetivo principal?")
        Spacer(Modifier.height(10.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ObjectiveCard(
                modifier = Modifier.weight(1f),
                icon     = Icons.Filled.School,
                label    = "Mejorar\ntécnica",
                value    = "technique",
                selected = form.objective == "technique",
                onSelect = onObjectiveSelected
            )
            ObjectiveCard(
                modifier = Modifier.weight(1f),
                icon     = Icons.Filled.FitnessCenter,
                label    = "Progresar\nen carga",
                value    = "progression",
                selected = form.objective == "progression",
                onSelect = onObjectiveSelected
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ObjectiveCard(
                modifier = Modifier.weight(1f),
                icon     = Icons.Filled.Healing,
                label    = "Control\ndel dolor",
                value    = "pain_monitoring",
                selected = form.objective == "pain_monitoring",
                onSelect = onObjectiveSelected
            )
            ObjectiveCard(
                modifier = Modifier.weight(1f),
                icon     = Icons.Filled.FavoriteBorder,
                label    = "Salud\ngeneral",
                value    = "health",
                selected = form.objective == "health",
                onSelect = onObjectiveSelected
            )
        }

        Spacer(Modifier.height(36.dp))

        NeuroSquatPrimaryButton(
            text      = "¡Empezar!",
            isLoading = isLoading,
            enabled   = canFinish,
            onClick   = onFinish
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Volver", color = TextSecondary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Componentes de selección
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelLarge.copy(
            color      = TextSecondary,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LevelCard(
    modifier  : Modifier,
    icon      : ImageVector,
    label     : String,
    sublabel  : String,
    value     : String,
    selected  : Boolean,
    onSelect  : (String) -> Unit
) {
    val borderColor = if (selected) CyanPrimary else NavyLight
    val bgColor     = if (selected) CyanPrimary.copy(alpha = 0.10f) else NavyMid

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onSelect(value) }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon, null,
            tint     = if (selected) CyanPrimary else TextSecondary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color      = if (selected) TextPrimary else TextSecondary
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text  = sublabel,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ObjectiveCard(
    modifier : Modifier,
    icon     : ImageVector,
    label    : String,
    value    : String,
    selected : Boolean,
    onSelect : (String) -> Unit
) {
    val borderColor = if (selected) CyanPrimary else NavyLight
    val bgColor     = if (selected) CyanPrimary.copy(alpha = 0.10f) else NavyMid

    Column(
        modifier = modifier
            .height(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onSelect(value) }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon, null,
            tint     = if (selected) CyanPrimary else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text      = label,
            style     = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color      = if (selected) TextPrimary else TextSecondary
            ),
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Indicador de pasos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive   = index == currentStep
            val isDone     = index < currentStep
            val color      = when {
                isActive -> CyanPrimary
                isDone   -> CyanPrimary.copy(alpha = 0.4f)
                else     -> NavyLight
            }
            Box(
                modifier = Modifier
                    .width(if (isActive) 32.dp else 16.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
            if (index < totalSteps - 1) Spacer(Modifier.width(6.dp))
        }
    }
}
