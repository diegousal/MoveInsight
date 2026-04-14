package com.moveinsight.presentation.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.core.utils.getDisplayName
import com.moveinsight.core.utils.toTempFile
import com.moveinsight.presentation.components.MoveInsightLogo
import com.moveinsight.presentation.components.NeuroSquatPrimaryButton
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Entry point — decide modo
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CaptureScreen(
    isUploadMode    : Boolean = false,
    onNavigateBack  : () -> Unit,
    onUploadSuccess : () -> Unit,
    viewModel       : CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val form    by viewModel.form.collectAsStateWithLifecycle()

    // ── Diálogo Borg — compartido por ambos modos ─────────────────────────
    if (uiState is CaptureUiState.RecordingStopped) {
        BorgScaleDialog(
            initialScore = form.borgScore,
            onConfirm    = { score ->
                viewModel.onBorgScoreChange(score)
                viewModel.onBorgConfirmed()
            },
            onDismiss    = viewModel::onBorgDismissed
        )
    }

    if (isUploadMode) {
        UploadModeContent(
            viewModel      = viewModel,
            onNavigateBack = onNavigateBack
        )
    } else {
        RecordModeWithPermissions(
            viewModel       = viewModel,
            onNavigateBack  = onNavigateBack,
            onUploadSuccess = onUploadSuccess
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODO SUBIR VÍDEO
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UploadModeContent(
    viewModel      : CaptureViewModel,
    onNavigateBack : () -> Unit
) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val form    by viewModel.form.collectAsStateWithLifecycle()

    var isCopying        by remember { mutableStateOf(false) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is CaptureUiEvent.ShowSnackbar -> snackbarHost.showSnackbar(event.message)
                else                           -> Unit
            }
        }
    }

    // Launcher para seleccionar vídeo de la galería
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isCopying = true
            try {
                val displayName = uri.getDisplayName(context)
                val file = withContext(Dispatchers.IO) { uri.toTempFile(context) }
                selectedFileName = displayName
                viewModel.onVideoFileSelected(file, displayName)
            } catch (e: Exception) {
                snackbarHost.showSnackbar("Error al cargar el vídeo. Inténtalo de nuevo.")
            } finally {
                isCopying = false
            }
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = NavyDeep,
        topBar         = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title             = { MoveInsightLogo(fontSize = 22.sp) },
                navigationIcon    = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary)
                    }
                },
                colors            = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDeep
                )
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
            Spacer(Modifier.height(12.dp))

            Text(
                text  = "Subir vídeo",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = "Selecciona un vídeo de tu galería e introduce la carga utilizada",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // ── Selector de vídeo ─────────────────────────────────────
            VideoPickerCard(
                isCopying        = isCopying,
                selectedFileName = selectedFileName,
                onPickVideo      = { videoPickerLauncher.launch("video/*") }
            )

            Spacer(Modifier.height(24.dp))

            // ── Campo de carga ────────────────────────────────────────
            OutlinedTextField(
                value           = form.weightKg,
                onValueChange   = viewModel::onWeightChange,
                label           = { Text("Carga (kg)") },
                placeholder     = { Text("Ej: 80.5", color = TextSecondary) },
                isError         = form.isWeightError,
                supportingText  = if (form.isWeightError) {
                    { Text("Introduce la carga antes de continuar", color = MaterialTheme.colorScheme.error) }
                } else null,
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = OrangePower,
                    unfocusedBorderColor = DividerColor,
                    focusedLabelColor    = OrangePower,
                    unfocusedLabelColor  = TextSecondary,
                    cursorColor          = OrangePower
                ),
                modifier        = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))

            // ── CTA: Continuar ────────────────────────────────────────
            val videoReady = uiState is CaptureUiState.VideoSelected
            val isUploading = uiState is CaptureUiState.Uploading

            Button(
                onClick  = viewModel::onVideoReadyToSubmit,
                enabled  = videoReady && !isUploading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = OrangePower,
                    contentColor           = NavyDeep,
                    disabledContainerColor = OrangePower.copy(alpha = 0.35f)
                )
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(22.dp),
                        color       = NavyDeep,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text  = if (videoReady) "Continuar →" else "Selecciona un vídeo primero",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPickerCard(
    isCopying        : Boolean,
    selectedFileName : String?,
    onPickVideo      : () -> Unit
) {
    Card(
        onClick    = onPickVideo,
        modifier   = Modifier
            .fillMaxWidth()
            .height(140.dp),
        colors     = CardDefaults.cardColors(containerColor = NavyLight),
        shape      = MaterialTheme.shapes.large,
        border     = if (selectedFileName == null)
            androidx.compose.foundation.BorderStroke(1.5.dp, DividerColor)
        else
            androidx.compose.foundation.BorderStroke(1.5.dp, OrangePower)
    ) {
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isCopying            -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = OrangePower, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Cargando vídeo…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                selectedFileName != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint               = OrangePower,
                            modifier           = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text      = selectedFileName,
                            style     = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines  = 2
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = "Pulsa para cambiar",
                            style = MaterialTheme.typography.bodyMedium.copy(color = OrangePower)
                        )
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector        = Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            tint               = OrangePower,
                            modifier           = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text  = "Seleccionar vídeo",
                            style = MaterialTheme.typography.titleLarge,
                            color = OrangePower
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("desde tu galería", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODO GRABAR — gestión de permisos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecordModeWithPermissions(
    viewModel       : CaptureViewModel,
    onNavigateBack  : () -> Unit,
    onUploadSuccess : () -> Unit
) {
    val context = LocalContext.current

    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)       == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionsGranted =
            perms[Manifest.permission.CAMERA]       == true &&
                    perms[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    if (permissionsGranted) {
        RecordModeContent(viewModel, onNavigateBack, onUploadSuccess)
    } else {
        PermissionRequestUI(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            },
            onNavigateBack = onNavigateBack
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODO GRABAR — pantalla de cámara
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecordModeContent(
    viewModel       : CaptureViewModel,
    onNavigateBack  : () -> Unit,
    onUploadSuccess : () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHost   = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val form    by viewModel.form.collectAsStateWithLifecycle()

    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            cameraController.unbind()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                CaptureUiEvent.UploadSuccess     -> onUploadSuccess()
                CaptureUiEvent.NavigateBack      -> onNavigateBack()
                is CaptureUiEvent.ShowSnackbar   -> snackbarHost.showSnackbar(event.message)
            }
        }
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return
        if (!viewModel.validateBeforeRecording()) return
        val file = File(context.cacheDir, "mi_${System.currentTimeMillis()}.mp4")
        activeRecording = cameraController.startRecording(
            FileOutputOptions.Builder(file).build(),
            AudioConfig.create(true),
            ContextCompat.getMainExecutor(context)
        ) { event ->
            when (event) {
                is VideoRecordEvent.Start    -> viewModel.onRecordingStarted()
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    if (!event.hasError()) viewModel.onRecordingFinalized(file)
                    else { viewModel.onRecordingError("Error al guardar (código ${event.error})"); file.delete() }
                }
                else -> {}
            }
        }
    }

    fun stopRecording() { activeRecording?.stop() }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        controller         = cameraController
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            TopOverlay(
                isRecording     = uiState is CaptureUiState.Recording,
                durationSeconds = form.recordingDurationSeconds,
                onBack          = { activeRecording?.stop(); onNavigateBack() }
            )
            BottomControlsOverlay(
                uiState          = uiState,
                form             = form,
                onWeightChange   = viewModel::onWeightChange,
                onStartRecording = ::startRecording,
                onStopRecording  = ::stopRecording,
                modifier         = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Subcomponentes de la cámara (sin cambios respecto a Fase 2)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopOverlay(
    isRecording     : Boolean,
    durationSeconds : Int,
    onBack          : () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick  = onBack,
            modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
        }
        if (isRecording) RecordingBadge(durationSeconds)
    }
}

@Composable
private fun RecordingBadge(durationSeconds: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "dot_pulse"
    )
    val mm = durationSeconds / 60
    val ss = durationSeconds % 60
    Row(
        modifier          = Modifier.clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(8.dp)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(RedAlert)
        )
        Text(
            text  = "%02d:%02d".format(mm, ss),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
        )
    }
}

@Composable
private fun BottomControlsOverlay(
    uiState          : CaptureUiState,
    form             : CaptureFormState,
    onWeightChange   : (String) -> Unit,
    onStartRecording : () -> Unit,
    onStopRecording  : () -> Unit,
    modifier         : Modifier = Modifier
) {
    val isRecording = uiState is CaptureUiState.Recording
    val isUploading = uiState is CaptureUiState.Uploading
    val isIdle      = uiState is CaptureUiState.Idle

    Column(
        modifier            = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(NavyDeep.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value           = form.weightKg,
            onValueChange   = onWeightChange,
            label           = { Text("Carga (kg)") },
            placeholder     = { Text("Ej: 80.5", color = TextSecondary) },
            isError         = form.isWeightError,
            supportingText  = if (form.isWeightError) {
                { Text("Introduce la carga antes de grabar", color = MaterialTheme.colorScheme.error) }
            } else null,
            singleLine      = true,
            enabled         = isIdle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors          = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = CyanPrimary,
                unfocusedBorderColor = DividerColor,
                focusedLabelColor    = CyanPrimary,
                unfocusedLabelColor  = TextSecondary,
                cursorColor          = CyanPrimary
            ),
            modifier        = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text      = when {
                isUploading -> "Subiendo al servidor…"
                isRecording -> "Graba toda la serie. Pulsa ■ al terminar."
                else        -> "Introduce la carga y pulsa ● para grabar"
            },
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        RecordButton(
            isRecording = isRecording,
            isUploading = isUploading,
            onStart     = onStartRecording,
            onStop      = onStopRecording
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RecordButton(
    isRecording : Boolean,
    isUploading : Boolean,
    onStart     : () -> Unit,
    onStop      : () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "record_pulse")
    infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (isRecording) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(if (isRecording) 600 else 1, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "record_scale"
    )
    Box(contentAlignment = Alignment.Center) {
        if (isUploading) {
            CircularProgressIndicator(color = CyanPrimary, modifier = Modifier.size(72.dp), strokeWidth = 3.dp)
        } else {
            FloatingActionButton(
                onClick        = { if (isRecording) onStop() else onStart() },
                modifier       = Modifier.size(72.dp).border(3.dp, if (isRecording) RedAlert else CyanPrimary, CircleShape),
                shape          = CircleShape,
                containerColor = if (isRecording) RedAlert.copy(alpha = 0.2f) else NavyLight,
                contentColor   = if (isRecording) RedAlert else CyanPrimary,
                elevation      = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(
                    imageVector        = if (isRecording) Icons.Filled.Stop else Icons.Filled.CameraAlt,
                    contentDescription = if (isRecording) "Detener" else "Grabar",
                    modifier           = Modifier.size(32.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pantalla de solicitud de permisos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionRequestUI(
    onRequestPermission : () -> Unit,
    onNavigateBack      : () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(NavyDeep), contentAlignment = Alignment.Center) {
        IconButton(
            onClick  = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(36.dp)
        ) {
            MoveInsightLogo(fontSize = 28.sp)
            Spacer(Modifier.height(32.dp))
            Text("Permisos necesarios", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                text      = "MoveInsight necesita acceso a la cámara y al micrófono para grabar tus series.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onRequestPermission,
                colors  = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = NavyDeep)
            ) {
                Text("Conceder permisos", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}