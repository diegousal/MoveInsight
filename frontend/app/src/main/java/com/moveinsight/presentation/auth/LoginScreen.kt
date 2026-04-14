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
import androidx.compose.ui.unit.sp
import com.moveinsight.presentation.components.MoveInsightLogo
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val form          by viewModel.form.collectAsStateWithLifecycle()
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager  =  LocalFocusManager.current
    val snackbarHost  =  remember { SnackbarHostState() }
    val isError       =  uiState is LoginUiState.Error

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                LoginUiEvent.NavigateToHome       -> onLoginSuccess()
                is LoginUiEvent.ShowSnackbar      -> snackbarHost.showSnackbar(event.msg)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(56.dp))

            // ── Cabecera ──────────────────────────────────────────────────

            MoveInsightLogo(fontSize = 36.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text  = "Bienvenido de vuelta",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Accede a tu panel de seguimiento clínico",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))

            // ── Formulario ────────────────────────────────────────────────
            NeuroSquatTextField(
                value         = form.email,
                onValueChange = viewModel::onEmailChange,
                label         = "Email",
                leadingIcon   = Icons.Filled.Email,
                isError       = isError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )
            Spacer(Modifier.height(16.dp))
            NeuroSquatTextField(
                value         = form.password,
                onValueChange = viewModel::onPasswordChange,
                label         = "Contraseña",
                leadingIcon   = Icons.Filled.Lock,
                isPassword    = true,
                isError       = isError,
                errorMessage  = (uiState as? LoginUiState.Error)?.message,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus(); viewModel.onLoginClick() }
                )
            )

            Spacer(Modifier.height(32.dp))

            // ── CTA ───────────────────────────────────────────────────────
            NeuroSquatPrimaryButton(
                text      = "Iniciar Sesión",
                isLoading = uiState is LoginUiState.Loading,
                onClick   = { focusManager.clearFocus(); viewModel.onLoginClick() }
            )

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¿No tienes cuenta?", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onNavigateToRegister) {
                    Text(
                        text  = "Regístrate",
                        color = CyanPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}