package com.moveinsight.presentation.auth

data class RegisterFormState(
    val fullName: String        = "",
    val email: String           = "",
    val password: String        = "",
    val confirmPassword: String = ""
)

sealed class RegisterUiState {
    data object Idle    : RegisterUiState()
    data object Loading : RegisterUiState()
    data object Success : RegisterUiState()
    data class  Error(val message: String) : RegisterUiState()
}

sealed class RegisterUiEvent {
    data object NavigateToLogin              : RegisterUiEvent()
    data class  ShowSnackbar(val msg: String): RegisterUiEvent()
}