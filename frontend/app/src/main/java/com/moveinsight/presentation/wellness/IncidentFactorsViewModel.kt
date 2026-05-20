package com.moveinsight.presentation.wellness

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Incident
import com.moveinsight.domain.model.IncidentFactors
import com.moveinsight.domain.wellness.GetIncidentFactorsUseCase
import com.moveinsight.domain.wellness.GetIncidentUseCase
import com.moveinsight.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncidentFactorsUiState(
    val isLoading : Boolean           = true,
    val incident  : Incident?         = null,
    val factors   : IncidentFactors?  = null,
    val errorMsg  : String?           = null
)

@HiltViewModel
class IncidentFactorsViewModel @Inject constructor(
    savedStateHandle              : SavedStateHandle,
    private val getIncidentUseCase: GetIncidentUseCase,
    private val getFactorsUseCase : GetIncidentFactorsUseCase,
) : ViewModel() {

    private val incidentId: Int =
        savedStateHandle.get<String>(Routes.IncidentFactors.ARG_INCIDENT_ID)?.toIntOrNull()
            ?: -1

    private val _state = MutableStateFlow(IncidentFactorsUiState())
    val state: StateFlow<IncidentFactorsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = IncidentFactorsUiState(isLoading = true)
            // 1) Detalles de la incidencia
            val incRes = getIncidentUseCase(incidentId)
            val incident = if (incRes is Resource.Success) incRes.data else null
            if (incident == null) {
                _state.value = IncidentFactorsUiState(
                    isLoading = false,
                    errorMsg  = (incRes as? Resource.Error)?.message ?: "No se pudo cargar la incidencia."
                )
                return@launch
            }
            // 2) Factores observados
            when (val factorsRes = getFactorsUseCase(incidentId)) {
                is Resource.Success -> _state.value = IncidentFactorsUiState(
                    isLoading = false, incident = incident, factors = factorsRes.data
                )
                is Resource.Error   -> _state.value = IncidentFactorsUiState(
                    isLoading = false, incident = incident, errorMsg = factorsRes.message
                )
                is Resource.Loading -> Unit
            }
        }
    }
}
