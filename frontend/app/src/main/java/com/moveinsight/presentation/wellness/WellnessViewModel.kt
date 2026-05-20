package com.moveinsight.presentation.wellness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Incident
import com.moveinsight.domain.model.ValidationResult
import com.moveinsight.domain.model.WellnessTimeline
import com.moveinsight.domain.wellness.CreateIncidentUseCase
import com.moveinsight.domain.wellness.DeleteIncidentUseCase
import com.moveinsight.domain.wellness.GetTimelineUseCase
import com.moveinsight.domain.wellness.GetValidationUseCase
import com.moveinsight.domain.wellness.RecoverIncidentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Estado ──────────────────────────────────────────────────────────────────

data class WellnessUiState(
    val isLoading          : Boolean           = true,
    val timeline           : WellnessTimeline? = null,
    val days               : Int               = 90,
    val errorMsg           : String?           = null,
    val showRegisterDialog : Boolean           = false,
    val isSaving           : Boolean           = false,
    // F8 — Validación retrospectiva
    val validation         : ValidationResult? = null,
    val isLoadingValidation: Boolean           = false,
    val showValidation     : Boolean           = false,
)

sealed class WellnessEvent {
    data class ShowSnackbar(val msg: String) : WellnessEvent()
}

// ── ViewModel ──────────────────────────────────────────────────────────────

@HiltViewModel
class WellnessViewModel @Inject constructor(
    private val getTimelineUseCase    : GetTimelineUseCase,
    private val createIncidentUseCase : CreateIncidentUseCase,
    private val recoverIncidentUseCase: RecoverIncidentUseCase,
    private val deleteIncidentUseCase : DeleteIncidentUseCase,
    private val getValidationUseCase  : GetValidationUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(WellnessUiState())
    val state: StateFlow<WellnessUiState> = _state.asStateFlow()

    private val _events = Channel<WellnessEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMsg = null) }
            when (val r = getTimelineUseCase(_state.value.days)) {
                is Resource.Success -> _state.update { it.copy(isLoading = false, timeline = r.data) }
                is Resource.Error   -> _state.update { it.copy(isLoading = false, errorMsg = r.message) }
                is Resource.Loading -> Unit
            }
        }
    }

    fun changeRange(days: Int) {
        _state.update { it.copy(days = days) }
        reload()
    }

    fun openRegisterDialog()  { _state.update { it.copy(showRegisterDialog = true) } }
    fun closeRegisterDialog() { _state.update { it.copy(showRegisterDialog = false) } }

    fun createIncident(
        bodyZone: String, severity: Int, incidentType: String,
        startDate: String, endDate: String? = null, notes: String
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val r = createIncidentUseCase(bodyZone, severity, incidentType, startDate, endDate, notes)) {
                is Resource.Success -> {
                    _state.update { it.copy(isSaving = false, showRegisterDialog = false) }
                    _events.send(WellnessEvent.ShowSnackbar("Incidencia registrada."))
                    reload()
                }
                is Resource.Error -> {
                    _state.update { it.copy(isSaving = false) }
                    _events.send(WellnessEvent.ShowSnackbar(r.message))
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun recoverIncident(incident: Incident) {
        viewModelScope.launch {
            when (val r = recoverIncidentUseCase(incident.id)) {
                is Resource.Success -> {
                    _events.send(WellnessEvent.ShowSnackbar("Marcada como recuperada."))
                    reload()
                }
                is Resource.Error -> _events.send(WellnessEvent.ShowSnackbar(r.message))
                is Resource.Loading -> Unit
            }
        }
    }

    fun deleteIncident(incident: Incident) {
        viewModelScope.launch {
            when (val r = deleteIncidentUseCase(incident.id)) {
                is Resource.Success -> {
                    _events.send(WellnessEvent.ShowSnackbar("Incidencia eliminada."))
                    reload()
                }
                is Resource.Error -> _events.send(WellnessEvent.ShowSnackbar(r.message))
                is Resource.Loading -> Unit
            }
        }
    }

    // ── Validación retrospectiva (F8) ─────────────────────────────────────────

    fun loadValidation() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingValidation = true, showValidation = true) }
            when (val r = getValidationUseCase()) {
                is Resource.Success -> _state.update {
                    it.copy(isLoadingValidation = false, validation = r.data)
                }
                is Resource.Error   -> {
                    _state.update { it.copy(isLoadingValidation = false) }
                    _events.send(WellnessEvent.ShowSnackbar(r.message))
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun toggleValidation() {
        val showing = _state.value.showValidation
        if (!showing && _state.value.validation == null) {
            loadValidation()
        } else {
            _state.update { it.copy(showValidation = !showing) }
        }
    }
}
