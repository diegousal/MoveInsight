package com.moveinsight.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.presentation.components.NeuroSquatPrimaryButton
import com.moveinsight.presentation.components.NeuroSquatTextField
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RegisterScreen(
    onNavigateToVerify : (email: String) -> Unit,
    onNavigateToLogin  : () -> Unit,
    viewModel          : RegisterViewModel = hiltViewModel()
) {
    val form         by viewModel.form.collectAsStateWithLifecycle()
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager =  LocalFocusManager.current
    val snackbarHost =  remember { SnackbarHostState() }
    val isError      =  uiState is RegisterUiState.Error

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is RegisterUiEvent.NavigateToVerifyEmail -> onNavigateToVerify(event.email)
                RegisterUiEvent.NavigateToLogin          -> onNavigateToLogin()
                is RegisterUiEvent.ShowSnackbar          -> snackbarHost.showSnackbar(event.msg)
            }
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = NavyDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyDeep)
                .padding(padding)
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(52.dp))

            Text("Crear cuenta", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Únete a NeuroSquat y empieza tu seguimiento",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            NeuroSquatTextField(
                value = form.fullName, onValueChange = viewModel::onFullNameChange,
                label = "Nombre completo", leadingIcon = Icons.Filled.Person,
                isError = isError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(Modifier.height(14.dp))
            NeuroSquatTextField(
                value = form.email, onValueChange = viewModel::onEmailChange,
                label = "Correo electrónico", leadingIcon = Icons.Filled.Email,
                isError = isError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(Modifier.height(14.dp))
            NeuroSquatTextField(
                value = form.password, onValueChange = viewModel::onPasswordChange,
                label = "Contraseña", leadingIcon = Icons.Filled.Lock,
                isPassword = true, isError = isError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(Modifier.height(14.dp))
            NeuroSquatTextField(
                value = form.confirmPassword, onValueChange = viewModel::onConfirmPasswordChange,
                label = "Confirmar contraseña", leadingIcon = Icons.Filled.Lock,
                isPassword = true, isError = isError,
                errorMessage = (uiState as? RegisterUiState.Error)?.message,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus(); viewModel.onRegisterClick() }
                )
            )

            Spacer(Modifier.height(32.dp))

            NeuroSquatPrimaryButton(
                text      = "Crear cuenta",
                isLoading = uiState is RegisterUiState.Loading,
                onClick   = { focusManager.clearFocus(); viewModel.onRegisterClick() }
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¿Ya tienes cuenta?", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onNavigateToLogin) {
                    Text("Inicia sesión", color = CyanPrimary,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}