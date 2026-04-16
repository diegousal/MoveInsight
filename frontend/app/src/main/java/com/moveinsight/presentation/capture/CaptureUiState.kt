package com.moveinsight.presentation.capture

import java.io.File

data class CaptureFormState(
    val weightKg                 : String  = "",
    val borgScore                : Int     = 5,
    val recordingDurationSeconds : Int     = 0,
    val isWeightError            : Boolean = false,
    val isBodyweight             : Boolean = false
)

sealed class CaptureUiState {
    /** Pantalla en reposo */
    data object Idle : CaptureUiState()

    /** Grabando activamente con CameraX */
    data object Recording : CaptureUiState()

    /** Copiando el vídeo elegido desde la galería al caché */
    data object CopyingFile : CaptureUiState()

    /**
     * Vídeo listo (procedente de galería) — el usuario aún debe
     * confirmar el peso antes de pasar al diálogo Borg.
     */
    data class VideoSelected(
        val videoFile   : File,
        val displayName : String
    ) : CaptureUiState()

    /**
     * Grabación parada o vídeo confirmado — dispara el diálogo Borg.
     */
    data class RecordingStopped(val videoFile: File) : CaptureUiState()

    /** Subiendo al servidor */
    data object Uploading : CaptureUiState()

    /** Subida completada */
    data class Success(val sessionId: Int) : CaptureUiState()

    /** Error en cualquier paso */
    data class Error(val message: String) : CaptureUiState()
}

sealed class CaptureUiEvent {
    data object NavigateBack                        : CaptureUiEvent()
    data class  UploadSuccess(val sessionId: Int)   : CaptureUiEvent()
    data class  ShowSnackbar(val message: String)   : CaptureUiEvent()
}