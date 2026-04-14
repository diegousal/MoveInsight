package com.moveinsight.presentation.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.session.UploadSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val uploadSessionUseCase: UploadSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _form   = MutableStateFlow(CaptureFormState())
    val form: StateFlow<CaptureFormState> = _form.asStateFlow()

    private val _events = Channel<CaptureUiEvent>(Channel.BUFFERED)
    val events          = _events.receiveAsFlow()

    private var timerJob: Job? = null

    // ── Formulario ────────────────────────────────────────────────────────

    fun onWeightChange(value: String) {
        val sanitized = value.filter { it.isDigit() || it == '.' }
        _form.update { it.copy(weightKg = sanitized, isWeightError = false) }
    }

    fun onBorgScoreChange(value: Int) {
        _form.update { it.copy(borgScore = value.coerceIn(0, 10)) }
    }

    // ── Validación compartida ─────────────────────────────────────────────

    /**
     * Devuelve true si el peso es válido.
     * Si no, marca el campo con error y emite un Snackbar.
     */
    fun validateBeforeRecording(): Boolean {
        val weight = _form.value.weightKg.toFloatOrNull()
        return if (weight == null || weight <= 0f) {
            _form.update { it.copy(isWeightError = true) }
            viewModelScope.launch {
                _events.send(CaptureUiEvent.ShowSnackbar("Introduce la carga (kg) antes de continuar"))
            }
            false
        } else true
    }

    // ── Modo GRABAR ───────────────────────────────────────────────────────

    fun onRecordingStarted() {
        _uiState.value = CaptureUiState.Recording
        startTimer()
    }

    fun onRecordingFinalized(file: File) {
        stopTimer()
        _uiState.value = CaptureUiState.RecordingStopped(file)
    }

    fun onRecordingError(message: String) {
        stopTimer()
        _uiState.value = CaptureUiState.Idle
        viewModelScope.launch { _events.send(CaptureUiEvent.ShowSnackbar(message)) }
    }

    // ── Modo SUBIR VÍDEO ──────────────────────────────────────────────────

    /** Llamado desde la UI cuando el archivo ya fue copiado al caché */
    fun onVideoFileSelected(file: File, displayName: String) {
        _uiState.value = CaptureUiState.VideoSelected(file, displayName)
    }

    /** El usuario pulsó "Continuar" en modo upload → validar peso y abrir Borg */
    fun onVideoReadyToSubmit() {
        val state = _uiState.value as? CaptureUiState.VideoSelected ?: return
        if (!validateBeforeRecording()) return
        _uiState.value = CaptureUiState.RecordingStopped(state.videoFile)
    }

    // ── Borg y Upload (común a ambos modos) ───────────────────────────────

    fun onBorgConfirmed() {
        val stopped  = _uiState.value as? CaptureUiState.RecordingStopped ?: return
        val weightKg = _form.value.weightKg.toFloatOrNull() ?: return

        viewModelScope.launch {
            _uiState.value = CaptureUiState.Uploading
            when (val result = uploadSessionUseCase(stopped.videoFile, weightKg, _form.value.borgScore)) {
                is Resource.Success -> {
                    stopped.videoFile.delete()
                    _uiState.value = CaptureUiState.Success(result.data.id)
                    _events.send(CaptureUiEvent.UploadSuccess)
                }
                is Resource.Error -> {
                    // Volver al estado anterior para no perder el archivo
                    _uiState.value = CaptureUiState.RecordingStopped(stopped.videoFile)
                    _events.send(CaptureUiEvent.ShowSnackbar(result.message))
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun onBorgDismissed() {
        val stopped = _uiState.value as? CaptureUiState.RecordingStopped
        stopped?.videoFile?.delete()
        _uiState.value = CaptureUiState.Idle
        _form.update { it.copy(recordingDurationSeconds = 0) }
    }

    // ── Timer ─────────────────────────────────────────────────────────────

    private fun startTimer() {
        timerJob?.cancel()
        _form.update { it.copy(recordingDurationSeconds = 0) }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                _form.update { it.copy(recordingDurationSeconds = it.recordingDurationSeconds + 1) }
            }
        }
    }

    private fun stopTimer() { timerJob?.cancel(); timerJob = null }

    override fun onCleared() { super.onCleared(); stopTimer() }
}