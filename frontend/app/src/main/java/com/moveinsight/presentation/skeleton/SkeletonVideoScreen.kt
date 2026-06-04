package com.moveinsight.presentation.skeleton

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkeletonVideoScreen(
    onNavigateBack : () -> Unit,
    viewModel      : SkeletonVideoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = NavyDeep,
        topBar         = {
            TopAppBar(
                title = {
                    Text(
                        "Vídeo con esqueleto",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
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
                .padding(padding)
                .background(NavyDeep),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is SkeletonUiState.Loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CyanPrimary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text  = if (state.attempt == 1)
                                "Descargando vídeo de esqueleto…"
                            else
                                "Generando el vídeo con esqueleto en el servidor…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        if (state.attempt > 1) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text  = "Esto puede tardar hasta 1-2 min",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                is SkeletonUiState.Ready -> {
                    Column(
                        modifier            = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setVideoURI(Uri.fromFile(state.videoFile))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text  = "Esqueleto MediaPipe superpuesto al vídeo original",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                }
                is SkeletonUiState.NotAvailable -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.padding(32.dp)
                    ) {
                        Text(
                            "Vídeo de esqueleto no disponible",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "El análisis todavía no generó el vídeo con esqueleto o la sesión aún está procesando.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors  = ButtonDefaults.buttonColors(
                                containerColor = CyanPrimary,
                                contentColor   = NavyDeep
                            )
                        ) {
                            Text("Volver")
                        }
                    }
                }
                is SkeletonUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.padding(32.dp)
                    ) {
                        Text(
                            "Error al cargar el vídeo",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = RedAlert
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = viewModel::retry,
                            colors  = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = NavyDeep)
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
            }
        }
    }
}
