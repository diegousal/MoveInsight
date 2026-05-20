package com.moveinsight.presentation.skeleton

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.data.remote.SessionApiService
import com.moveinsight.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class SkeletonUiState {
    /** Cargando o esperando a que el backend genere el overlay. `attempt` empieza en 1. */
    data class  Loading(val attempt: Int = 1) : SkeletonUiState()
    data class  Ready(val videoFile: File) : SkeletonUiState()
    data object NotAvailable : SkeletonUiState()
    data class  Error(val message: String) : SkeletonUiState()
}

@HiltViewModel
class SkeletonVideoViewModel @Inject constructor(
    savedStateHandle              : SavedStateHandle,
    private val sessionApiService : SessionApiService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val sessionId: Int = savedStateHandle[Routes.Skeleton.ARG_SESSION_ID] ?: -1

    private val _uiState = MutableStateFlow<SkeletonUiState>(SkeletonUiState.Loading())
    val uiState: StateFlow<SkeletonUiState> = _uiState.asStateFlow()

    init { loadVideo() }

    fun retry() {
        _uiState.value = SkeletonUiState.Loading()
        loadVideo()
    }

    /**
     * Polling: si el backend devuelve 404 (overlay aún no generado), reintentamos
     * hasta `MAX_ATTEMPTS` con `POLL_INTERVAL_MS` entre intentos.
     *
     * Esto es necesario porque ahora el análisis principal hace commit de "completed"
     * antes de generar el vídeo de esqueleto (paso 7), por lo que la app puede llegar
     * a la pantalla del esqueleto antes de que el archivo exista en disco.
     */
    private fun loadVideo() {
        if (sessionId == -1) {
            _uiState.value = SkeletonUiState.Error("ID de sesión inválido")
            return
        }
        viewModelScope.launch {
            var attempt = 1
            while (attempt <= MAX_ATTEMPTS) {
                _uiState.value = SkeletonUiState.Loading(attempt)
                try {
                    val response = sessionApiService.getSkeletonVideo(sessionId)
                    when {
                        response.isSuccessful -> {
                            val body = response.body()
                            if (body == null) {
                                _uiState.value = SkeletonUiState.NotAvailable
                                return@launch
                            }
                            // Escribir a caché local
                            val file = withContext(Dispatchers.IO) {
                                val cacheDir = File(context.cacheDir, "skeleton").also { it.mkdirs() }
                                val out      = File(cacheDir, "skeleton_$sessionId.mp4")
                                out.outputStream().use { body.byteStream().copyTo(it) }
                                out
                            }
                            _uiState.value = SkeletonUiState.Ready(file)
                            return@launch
                        }
                        response.code() == 404 -> {
                            // Aún no generado — reintentar tras un pequeño delay
                            if (attempt == MAX_ATTEMPTS) {
                                _uiState.value = SkeletonUiState.NotAvailable
                                return@launch
                            }
                            delay(POLL_INTERVAL_MS)
                            attempt++
                        }
                        else -> {
                            _uiState.value = SkeletonUiState.Error("Error del servidor: ${response.code()}")
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    _uiState.value = SkeletonUiState.Error(e.localizedMessage ?: "Error de red")
                    return@launch
                }
            }
        }
    }

    companion object {
        // Polling de hasta ~3 minutos (36 × 5 s = 180 s).
        // El skeleton se genera en background tras el paso 6 del pipeline en el backend.
        private const val MAX_ATTEMPTS    = 36
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
