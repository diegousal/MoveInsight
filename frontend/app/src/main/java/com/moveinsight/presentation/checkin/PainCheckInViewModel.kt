package com.moveinsight.presentation.checkin


import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.BodyZone
import com.moveinsight.domain.repository.SubmitPainCheckInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PainCheckInViewModel @Inject constructor(
    private val submitPainCheckInUseCase : SubmitPainCheckInUseCase,
    savedStateHandle                     : SavedStateHandle
) : ViewModel() {

    private val _form    = MutableStateFlow(PainCheckInFormState())
    val form: StateFlow<PainCheckInFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<PainCheckInUiState>(PainCheckInUiState.Idle)
    val uiState: StateFlow<PainCheckInUiState> = _uiState.asStateFlow()

    private val _events  = Channel<PainCheckInUiEvent>(Channel.BUFFERED)
    val events           = _events.receiveAsFlow()

    init {
        // Recibir sessionId e hoursAfter desde los argumentos de navegación
        val sessionId  = savedStateHandle.get<Int>("sessionId")  ?: -1
        val hoursAfter = savedStateHandle.get<Int>("hoursAfter") ?: 24
        _form.update { it.copy(sessionId = sessionId, hoursAfter = hoursAfter) }
    }

    fun onEvaScoreChange(score: Int) {
        _form.update { it.copy(evaScore = score.coerceIn(0, 10)) }
    }

    fun onBodyZoneToggle(zone: BodyZone) {
        _form.update { state ->
            val updated = if (zone in state.selectedZones)
                state.selectedZones - zone
            else
                state.selectedZones + zone
            state.copy(selectedZones = updated)
        }
    }

    fun onBodyViewToggle() {
        _form.update { it.copy(showFrontBody = !it.showFrontBody) }
    }

    fun onSubmit() {
        val f = _form.value

        // No tiene sentido tener zonas marcadas con EVA = 0:
        // o no hay dolor (sin zonas) o si hay molestias deben cuantificarse.
        if (f.selectedZones.isNotEmpty() && f.evaScore == 0) {
            viewModelScope.launch {
                _events.send(
                    PainCheckInUiEvent.ShowSnackbar(
                        "Has marcado zonas con molestias. Sube el nivel EVA por encima de 0, " +
                        "o usa «Sin molestias» si realmente no tienes dolor."
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = PainCheckInUiState.Loading
            when (val result = submitPainCheckInUseCase(
                sessionId  = f.sessionId,
                evaScore   = f.evaScore,
                hoursAfter = f.hoursAfter,
                bodyZones  = f.selectedZones.map { it.id }
            )) {
                is Resource.Success -> {
                    _uiState.value = PainCheckInUiState.Success
                    _events.send(PainCheckInUiEvent.NavigateHome)
                }
                is Resource.Error -> {
                    _uiState.value = PainCheckInUiState.Error(result.message)
                    _events.send(PainCheckInUiEvent.ShowSnackbar(result.message))
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /**
     * El usuario declara que no tiene ninguna molestia.
     * Se guarda un EVA = 0 con zonas vacías para que aparezca en el historial.
     */
    fun onNoPain() {
        val f = _form.value
        viewModelScope.launch {
            _uiState.value = PainCheckInUiState.Loading
            when (val result = submitPainCheckInUseCase(
                sessionId  = f.sessionId,
                evaScore   = 0,
                hoursAfter = f.hoursAfter,
                bodyZones  = emptyList()
            )) {
                is Resource.Success -> {
                    _uiState.value = PainCheckInUiState.Success
                    _events.send(PainCheckInUiEvent.NavigateHome)
                }
                is Resource.Error -> {
                    _uiState.value = PainCheckInUiState.Error(result.message)
                    _events.send(PainCheckInUiEvent.ShowSnackbar(result.message))
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /** Omite el check-in por completo sin guardar nada. */
    fun onSkip() {
        viewModelScope.launch { _events.send(PainCheckInUiEvent.NavigateHome) }
    }
}