package com.moveinsight.presentation.auth


data class LoginFormState(
    val email: String    = "",
    val password: String = ""
)

sealed class LoginUiState {
    data object Idle    : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class  Error(val message: String) : LoginUiState()
}

// Eventos de un solo uso (evitan el problema de doble navegación con StateFlow)
sealed class LoginUiEvent {
    data object NavigateToHome        : LoginUiEvent()
    data class  ShowSnackbar(val msg: String) : LoginUiEvent()
}