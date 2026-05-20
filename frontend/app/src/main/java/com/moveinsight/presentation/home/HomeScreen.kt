package com.moveinsight.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.domain.model.ReadinessLevel
import com.moveinsight.presentation.components.MoveInsightLogo
import com.moveinsight.presentation.components.RecommendationsSection
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout                    : () -> Unit,
    onNavigateToRecord          : () -> Unit,
    onNavigateToUpload          : () -> Unit,
    onNavigateToDashboard       : () -> Unit,
    onNavigateToChangePassword  : () -> Unit = {},
    onNavigateToReadiness       : () -> Unit = {},
    onNavigateToWellness        : () -> Unit = {},
    viewModel                   : HomeViewModel = hiltViewModel()
) {
    val data         by viewModel.data.collectAsStateWithLifecycle()
    val snackbarHost =  remember { SnackbarHostState() }

    var showTrainingSheet      by remember { mutableStateOf(false) }
    var showUserMenu           by remember { mutableStateOf(false) }
    var showHelpSheet          by remember { mutableStateOf(false) }
    var showNotifSheet         by remember { mutableStateOf(false) }
    var showDeleteDialog       by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // Refresca el readiness cada vez que la pantalla vuelve al foco
    // (p.ej. al volver de subir un ejercicio)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                HomeUiEvent.NavigateToLogin       -> onLogout()
                is HomeUiEvent.ShowSnackbar       -> snackbarHost.showSnackbar(event.message)
            }
        }
    }

    // ── Training bottom sheet ──────────────────────────────────────────────
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

    // ── Help bottom sheet ──────────────────────────────────────────────────
    if (showHelpSheet) {
        HelpBottomSheet(onDismiss = { showHelpSheet = false })
    }

    // ── Notification preferences bottom sheet ─────────────────────────────
    if (showNotifSheet) {
        NotificationPrefsBottomSheet(
            notifGeneral    = data.notifGeneral,
            notif24h        = data.notif24h,
            notif48h        = data.notif48h,
            onGeneralToggle = viewModel::onNotifGeneralToggle,
            on24hToggle     = viewModel::onNotif24hToggle,
            on48hToggle     = viewModel::onNotif48hToggle,
            onDismiss       = { showNotifSheet = false }
        )
    }

    // ── Delete account dialog ──────────────────────────────────────────────
    if (showDeleteDialog) {
        ConfirmActionDialog(
            title       = "Eliminar cuenta",
            message     = "Se eliminarán permanentemente tu cuenta y todos los datos (sesiones, análisis, check-ins). Esta acción es irreversible.",
            confirmText = "Eliminar cuenta",
            dangerColor = RedAlert,
            onConfirm   = { showDeleteDialog = false; viewModel.deleteAccount() },
            onDismiss   = { showDeleteDialog = false }
        )
    }

    // ── Clear history dialog ───────────────────────────────────────────────
    if (showClearHistoryDialog) {
        ConfirmActionDialog(
            title       = "Borrar historial",
            message     = "Se eliminarán todas tus sesiones, análisis y check-ins EVA. Esta acción no se puede deshacer.",
            confirmText = "Borrar historial",
            dangerColor = OrangePower,
            onConfirm   = { showClearHistoryDialog = false; viewModel.clearHistory() },
            onDismiss   = { showClearHistoryDialog = false }
        )
    }

    val readinessColor = when (data.readinessLevel) {
        ReadinessLevel.HIGH   -> GreenReady
        ReadinessLevel.MEDIUM -> YellowCaution
        ReadinessLevel.LOW    -> RedAlert
    }

    Scaffold(
        containerColor = NavyDeep,
        snackbarHost   = { SnackbarHost(snackbarHost) },
        topBar         = {
            TopAppBar(
                title  = { MoveInsightLogo(fontSize = 22.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDeep),
                actions = {
                    Box {
                        UserAvatarButton(
                            initials = data.userInitials,
                            onClick  = { showUserMenu = true }
                        )
                        UserDropdownMenu(
                            expanded           = showUserMenu,
                            userName           = data.userFullName,
                            userEmail          = data.userEmail,
                            isProcessing       = data.isProcessingAction,
                            onDismiss          = { showUserMenu = false },
                            onHelp             = { showUserMenu = false; showHelpSheet = true },
                            onNotifSettings    = { showUserMenu = false; showNotifSheet = true },
                            onChangePassword   = { showUserMenu = false; onNavigateToChangePassword() },
                            onClearHistory     = { showUserMenu = false; showClearHistoryDialog = true },
                            onDeleteAccount    = { showUserMenu = false; showDeleteDialog = true },
                            onLogout           = { showUserMenu = false; viewModel.onLogoutClick() }
                        )
                    }
                }
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
            Spacer(Modifier.height(16.dp))

            // ── Readiness ──────────────────────────────────────────────────
            ReadinessSection(
                data            = data,
                readinessColor  = readinessColor,
                onScoreClick    = onNavigateToReadiness
            )

            Spacer(Modifier.height(36.dp))

            // ── Section label ──────────────────────────────────────────────
            Text(
                text     = "¿Qué quieres hacer?",
                style    = MaterialTheme.typography.labelLarge.copy(
                    color         = TextSecondary,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // ── Card 1: Analizar ───────────────────────────────────────────
            HeroActionCard(
                title       = "Analizar Sentadilla",
                subtitle    = "Graba o sube tu vídeo · Análisis por IA",
                icon        = Icons.Filled.FitnessCenter,
                accentColor = CyanPrimary,
                onClick     = { showTrainingSheet = true }
            )

            Spacer(Modifier.height(14.dp))

            // ── Card 2: Historial ──────────────────────────────────────────
            HeroActionCard(
                title       = "Mi Historial",
                subtitle    = "Sesiones · Gráficos · Insights · Informe PDF",
                icon        = Icons.Filled.BarChart,
                accentColor = OrangePower,
                onClick     = onNavigateToDashboard
            )

            Spacer(Modifier.height(14.dp))

            // ── Card 3: Bienestar ──────────────────────────────────────────
            HeroActionCard(
                title       = "Bienestar",
                subtitle    = "Estado físico · Incidencias · Análisis temporal",
                icon        = Icons.Filled.HealthAndSafety,
                accentColor = GreenReady,
                onClick     = onNavigateToWellness
            )

            // ── Recomendaciones personalizadas (F6/F7) ─────────────────────
            if (data.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                RecommendationsSection(
                    recommendations = data.recommendations,
                    maxVisible      = 2
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Readiness section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadinessSection(
    data           : HomeUiData,
    readinessColor : Color,
    onScoreClick   : () -> Unit
) {
    val readinessText = when (data.readinessLevel) {
        ReadinessLevel.HIGH   -> "Óptimo para entrenar"
        ReadinessLevel.MEDIUM -> "Entrena con moderación"
        ReadinessLevel.LOW    -> "Necesitas descanso"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (data.isLoading) {
            Spacer(Modifier.height(40.dp))
            CircularProgressIndicator(color = CyanPrimary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(40.dp))
        } else {
            // Score circle (clickable → abre breakdown)
            Box(
                modifier         = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(readinessColor.copy(alpha = 0.12f))
                    .clickable(onClick = onScoreClick),
                contentAlignment = Alignment.Center
            ) {
                val score = data.analytics?.readinessScore?.toString()
                if (score != null) {
                    Text(
                        text  = score,
                        style = MaterialTheme.typography.displayMedium.copy(
                            color      = readinessColor,
                            fontWeight = FontWeight.Black,
                            fontSize   = 38.sp
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(readinessColor)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Hint visual de que es clickable
            Text(
                text  = "Toca para ver detalle",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text  = readinessText,
                style = MaterialTheme.typography.titleLarge.copy(
                    color      = readinessColor,
                    fontWeight = FontWeight.SemiBold
                )
            )

            // Quick stats row
            data.analytics?.let { analytics ->
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickStatChip(
                        value = "${analytics.totalSessions}",
                        label = "Sesiones",
                        color = CyanPrimary
                    )
                    analytics.maxWeightKg?.let {
                        QuickStatChip(
                            value = "${it.toInt()} kg",
                            label = "Máx. carga",
                            color = OrangePower
                        )
                    }
                    analytics.avgTechniqueScore?.let {
                        QuickStatChip(
                            value = "%.1f".format(it),
                            label = "Técnica",
                            color = GreenReady
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatChip(value: String, label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color      = color
                )
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero action cards
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroActionCard(
    title       : String,
    subtitle    : String,
    icon        : ImageVector,
    accentColor : Color,
    onClick     : () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().height(112.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.16f), NavyMid)
                    )
                )
        ) {
            // Left accent stripe
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(accentColor)
            )
            Row(
                modifier          = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon box
                Box(
                    modifier         = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        tint               = accentColor,
                        modifier           = Modifier.size(28.dp)
                    )
                }
                // Text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color      = TextPrimary,
                            fontSize   = 18.sp
                        )
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text  = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1
                    )
                }
                // Chevron
                Icon(
                    imageVector        = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint               = accentColor.copy(alpha = 0.55f),
                    modifier           = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// User avatar + dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UserAvatarButton(initials: String, onClick: () -> Unit) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(NavyLight)
                .border(1.5.dp, CyanPrimary.copy(alpha = 0.65f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (initials.isNotEmpty()) {
                Text(
                    text  = initials,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color      = CyanPrimary,
                        fontSize   = 13.sp
                    )
                )
            } else {
                Icon(
                    Icons.Filled.AccountCircle, null,
                    tint     = CyanPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun UserDropdownMenu(
    expanded           : Boolean,
    userName           : String,
    userEmail          : String,
    isProcessing       : Boolean,
    onDismiss          : () -> Unit,
    onHelp             : () -> Unit,
    onNotifSettings    : () -> Unit,
    onChangePassword   : () -> Unit,
    onClearHistory     : () -> Unit,
    onDeleteAccount    : () -> Unit,
    onLogout           : () -> Unit
) {
    DropdownMenu(
        expanded         = expanded,
        onDismissRequest = onDismiss,
        modifier         = Modifier
            .background(NavyMid)
            .widthIn(min = 230.dp)
    ) {
        // Header
        Column(
            modifier = Modifier
                .background(NavyDeep)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text  = userName.ifEmpty { "Usuario" },
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary
                )
            )
            if (userEmail.isNotEmpty()) {
                Text(
                    text  = userEmail,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }
        }
        HorizontalDivider(color = NavyLight, thickness = 0.5.dp)

        // Help
        MenuItemRow(
            icon    = Icons.Filled.Help,
            label   = "Ayuda y parámetros",
            color   = CyanPrimary,
            onClick = onHelp
        )
        // Notification preferences
        MenuItemRow(
            icon    = Icons.Filled.NotificationsNone,
            label   = "Notificaciones EVA",
            color   = CyanPrimary,
            onClick = onNotifSettings
        )

        // Change password
        MenuItemRow(
            icon    = Icons.Filled.Lock,
            label   = "Cambiar contraseña",
            color   = CyanPrimary,
            onClick = onChangePassword
        )

        HorizontalDivider(color = NavyLight, thickness = 0.5.dp)

        // Clear history
        MenuItemRow(
            icon    = Icons.Filled.DeleteSweep,
            label   = "Borrar historial",
            color   = YellowCaution,
            enabled = !isProcessing,
            onClick = onClearHistory
        )
        // Delete account
        MenuItemRow(
            icon    = Icons.Filled.PersonRemove,
            label   = "Eliminar cuenta",
            color   = RedAlert,
            enabled = !isProcessing,
            onClick = onDeleteAccount
        )

        HorizontalDivider(color = NavyLight, thickness = 0.5.dp)

        // Logout
        MenuItemRow(
            icon    = Icons.Filled.Logout,
            label   = "Cerrar sesión",
            color   = TextSecondary,
            onClick = onLogout
        )
    }
}

@Composable
private fun MenuItemRow(
    icon     : ImageVector,
    label    : String,
    color    : Color,
    subtitle : String? = null,
    enabled  : Boolean = true,
    onClick  : () -> Unit
) {
    val effectiveColor = if (enabled) color else color.copy(alpha = 0.38f)
    DropdownMenuItem(
        text    = {
            Row(
                verticalAlignment      = Alignment.CenterVertically,
                horizontalArrangement  = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, null, tint = effectiveColor, modifier = Modifier.size(18.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = effectiveColor))
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    }
                }
            }
        },
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.background(NavyMid)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Help bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpBottomSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = NavyMid,
        tonalElevation   = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 44.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Guía de MoveInsight",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary
                    )
                )
                Icon(Icons.Filled.School, null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = CyanPrimary.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(Modifier.height(20.dp))

            HelpSection(
                icon  = Icons.Filled.FitnessCenter,
                color = CyanPrimary,
                title = "¿Cómo funciona?",
                items = listOf(
                    "Graba o sube un vídeo de tu sentadilla",
                    "La IA analiza la postura fotograma a fotograma",
                    "Obtienes 5 KPIs puntuados de 0 a 10",
                    "Recibes notificaciones a las 24h y 48h para registrar el dolor"
                )
            )

            HelpSection(
                icon      = Icons.Filled.Speed,
                color     = OrangePower,
                title     = "Escala de Borg  ·  Esfuerzo percibido (0 – 10)",
                keyWeight = 0.22f,
                items     = listOf(
                    "0 – 3  ·  Esfuerzo muy ligero",
                    "4 – 6  ·  Moderado (zona ideal de entrenamiento)",
                    "7 – 9  ·  Intenso · Vigila la fatiga acumulada",
                    "10     ·  Esfuerzo máximo · Necesitas descanso"
                )
            )

            HelpSection(
                icon      = Icons.Filled.Healing,
                color     = YellowCaution,
                title     = "Escala EVA  ·  Dolor post-entreno (0 – 10)",
                keyWeight = 0.22f,
                items     = listOf(
                    "0      ·  Sin dolor",
                    "1 – 3  ·  Leve · Normal tras un entreno intenso",
                    "4 – 6  ·  Moderado · Reduce la carga la próxima sesión",
                    "7 – 10 ·  Intenso · Consulta con un profesional"
                )
            )

            HelpSection(
                icon      = Icons.Filled.Analytics,
                color     = GreenReady,
                title     = "KPIs de Técnica  ·  Puntuación 0 – 10",
                keyWeight = 0.30f,
                items     = listOf(
                    "Profundidad  ·  Ángulo de cadera en el punto más bajo",
                    "Torso        ·  Inclinación del tronco respecto a la vertical",
                    "Estabilidad  ·  Control del desplazamiento lateral de cadera",
                    "Rodillas     ·  Alineación con la punta de los pies",
                    "Ritmo        ·  Control de la fase excéntrica (bajada)"
                )
            )

            HelpSection(
                icon      = Icons.Filled.MonitorHeart,
                color     = CyanPrimary,
                title     = "Readiness  ·  Nivel de preparación",
                keyWeight = 0.35f,
                items     = listOf(
                    "Alto (70–100)  ·  Óptimo para entrenar fuerte",
                    "Medio (40–69)  ·  Entrena con moderación",
                    "Bajo (< 40)    ·  Prioriza el descanso hoy"
                )
            )
        }
    }
}

@Composable
private fun HelpSection(
    icon      : ImageVector,
    color     : Color,
    title     : String,
    items     : List<String>,
    keyWeight : Float = 0.38f   // proporción de la columna clave; ajustar por sección
) {
    Column(modifier = Modifier.padding(bottom = 22.dp)) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(
                text  = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color      = color
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        items.forEach { item ->
            val dotIdx = item.indexOf('·')
            if (dotIdx > 0) {
                // Two-column layout: key · description — all rows align at the same column
                val key  = item.substring(0, dotIdx).trim()
                val desc = item.substring(dotIdx + 1).trim()
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text     = key,
                        style    = MaterialTheme.typography.bodySmall.copy(
                            color      = TextSecondary,
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.weight(keyWeight)
                    )
                    Text(
                        text     = "·",
                        color    = color.copy(alpha = 0.6f),
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Text(
                        text     = desc,
                        style    = MaterialTheme.typography.bodySmall.copy(
                            color      = TextSecondary,
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.weight(1f - keyWeight)
                    )
                }
            } else {
                // Plain bullet item (no key/value separator)
                Row(
                    modifier              = Modifier.padding(start = 6.dp, bottom = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.Top
                ) {
                    Text(
                        text     = "·",
                        color    = color.copy(alpha = 0.6f),
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Text(
                        text  = item,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color      = TextSecondary,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirm dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfirmActionDialog(
    title       : String,
    message     : String,
    confirmText : String,
    dangerColor : Color,
    onConfirm   : () -> Unit,
    onDismiss   : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = NavyMid,
        icon             = {
            Icon(Icons.Filled.Warning, null, tint = dangerColor, modifier = Modifier.size(28.dp))
        },
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color      = dangerColor
                )
            )
        },
        text = {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = dangerColor, contentColor = NavyDeep)
            ) {
                Text(confirmText, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Notification preferences bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationPrefsBottomSheet(
    notifGeneral    : Boolean,
    notif24h        : Boolean,
    notif48h        : Boolean,
    onGeneralToggle : (Boolean) -> Unit,
    on24hToggle     : (Boolean) -> Unit,
    on48hToggle     : (Boolean) -> Unit,
    onDismiss       : () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = NavyMid,
        tonalElevation   = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 44.dp)
        ) {
            // ── Cabecera ──────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Notificaciones EVA",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary
                    )
                )
                Icon(
                    Icons.Filled.NotificationsNone, null,
                    tint     = CyanPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Elige cuándo quieres recibir recordatorios de dolor post-entrenamiento",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = CyanPrimary.copy(alpha = 0.4f), thickness = 1.dp)
            Spacer(Modifier.height(16.dp))

            // ── Interruptor maestro ───────────────────────────────────────
            NotifToggleRow(
                icon        = Icons.Filled.Notifications,
                iconColor   = CyanPrimary,
                title       = "Activar notificaciones",
                subtitle    = "Recordatorios automáticos tras cada sesión",
                checked     = notifGeneral,
                onToggle    = onGeneralToggle,
                enabled     = true
            )

            // ── Sub-opciones (solo accesibles si general está ON) ─────────
            AnimatedVisibility(visible = notifGeneral) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(
                        color     = NavyLight,
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(start = 48.dp)
                    )

                    NotifToggleRow(
                        icon        = Icons.Filled.AccessTime,
                        iconColor   = OrangePower,
                        title       = "Recordatorio 24h",
                        subtitle    = "Un día después del entrenamiento",
                        checked     = notif24h,
                        onToggle    = on24hToggle,
                        enabled     = notifGeneral,
                        indented    = true
                    )

                    HorizontalDivider(
                        color     = NavyLight,
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(start = 48.dp)
                    )

                    NotifToggleRow(
                        icon        = Icons.Filled.HourglassBottom,
                        iconColor   = YellowCaution,
                        title       = "Recordatorio 48h",
                        subtitle    = "Dos días después del entrenamiento",
                        checked     = notif48h,
                        onToggle    = on48hToggle,
                        enabled     = notifGeneral,
                        indented    = true
                    )
                }
            }
        }
    }
}

@Composable
private fun NotifToggleRow(
    icon      : androidx.compose.ui.graphics.vector.ImageVector,
    iconColor : Color,
    title     : String,
    subtitle  : String,
    checked   : Boolean,
    onToggle  : (Boolean) -> Unit,
    enabled   : Boolean,
    indented  : Boolean = false
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(
                start   = if (indented) 12.dp else 0.dp,
                top     = 12.dp,
                bottom  = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icono
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = if (enabled) 0.12f else 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = iconColor.copy(alpha = if (enabled) 1f else 0.35f),
                modifier           = Modifier.size(18.dp)
            )
        }
        // Texto
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color      = if (enabled) TextPrimary else TextPrimary.copy(alpha = 0.38f)
                )
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.38f)
                )
            )
        }
        // Switch
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            enabled         = enabled,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = NavyDeep,
                checkedTrackColor   = iconColor,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = NavyLight,
                disabledCheckedTrackColor   = iconColor.copy(alpha = 0.25f),
                disabledUncheckedTrackColor = NavyLight.copy(alpha = 0.4f)
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Training mode sheet (preserved)
// ─────────────────────────────────────────────────────────────────────────────

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
            "¿Cómo quieres registrar la serie?",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TrainingModeCard(
                modifier    = Modifier.weight(1f),
                icon        = Icons.Filled.Videocam,
                iconTint    = CyanPrimary,
                title       = "Grabar\nvídeo",
                description = "Usa la cámara",
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
    icon        : ImageVector,
    iconTint    : Color,
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
            Text(
                title,
                style     = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color     = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
