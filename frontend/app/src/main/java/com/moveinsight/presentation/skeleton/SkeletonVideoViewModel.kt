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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class SkeletonUiState {
    data object Loading : SkeletonUiState()
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

    private val _uiState = MutableStateFlow<SkeletonUiState>(SkeletonUiState.Loading)
    val uiState: StateFlow<SkeletonUiState> = _uiState.asStateFlow()

    init { loadVideo() }

    fun retry() {
        _uiState.value = SkeletonUiState.Loading
        loadVideo()
    }

    private fun loadVideo() {
        if (sessionId == -1) {
            _uiState.value = SkeletonUiState.Error("ID de sesión inválido")
            return
        }
        viewModelScope.launch {
            try {
                val response = sessionApiService.getSkeletonVideo(sessionId)
                when {
                    response.code() == 404 -> {
                        _uiState.value = SkeletonUiState.NotAvailable
                    }
                    response.isSuccessful -> {
                        val body = response.body()
                        if (body == null) {
                            _uiState.value = SkeletonUiState.NotAvailable
                            return@launch
                        }
                        // Escribir a caché
                        val file = withContext(Dispatchers.IO) {
                            val cacheDir = File(context.cacheDir, "skeleton").also { it.mkdirs() }
                            val out      = File(cacheDir, "skeleton_$sessionId.mp4")
                            out.outputStream().use { body.byteStream().copyTo(it) }
                            out
                        }
                        _uiState.value = SkeletonUiState.Ready(file)
                    }
                    else -> {
                        _uiState.value = SkeletonUiState.Error("Error del servidor: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = SkeletonUiState.Error(e.localizedMessage ?: "Error de red")
            }
        }
    }
}
