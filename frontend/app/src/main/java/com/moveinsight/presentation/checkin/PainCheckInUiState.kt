package com.moveinsight.presentation.checkin

import com.moveinsight.domain.model.BodyZone

data class PainCheckInFormState(
    val sessionId          : Int            = -1,
    val hoursAfter         : Int            = 24,
    val evaScore           : Int            = 0,
    val selectedZones      : Set<BodyZone>  = emptySet(),
    val showFrontBody      : Boolean        = true
)

sealed class PainCheckInUiState {
    data object Idle     : PainCheckInUiState()
    data object Loading  : PainCheckInUiState()
    data object Success  : PainCheckInUiState()
    data class  Error(val message: String) : PainCheckInUiState()
}

sealed class PainCheckInUiEvent {
    data object NavigateHome                      : PainCheckInUiEvent()
    data class  ShowSnackbar(val message: String) : PainCheckInUiEvent()
}
