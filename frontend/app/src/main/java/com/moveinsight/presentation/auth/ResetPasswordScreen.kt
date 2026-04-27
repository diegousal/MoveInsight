package com.moveinsight.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.presentation.components.NeuroSquatPrimaryButton
import com.moveinsight.presentation.components.NeuroSquatTextField
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    onNavigateToLogin : () -> Unit,
    onNavigateBack    : () -> Unit,
    viewModel         : ResetPasswordViewModel = hiltViewModel()
) {
    val form         by viewModel.form.collectAsStateWithLifecycle()
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost =  remember { SnackbarHostState() }
    val isError      =  uiState is ResetPasswordUiState.Error

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                ResetPasswordEvent.NavigateToLogin  -> onNavigateToLogin()
                is ResetPasswordEvent.ShowSnackbar  -> snackbarHost.showSnackbar(event.msg)
            }
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = NavyDeep,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDeep)
            )
        }
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
            Spacer(Modifier.height(16.dp))
            Icon(Icons.Filled.Lock, null, tint = CyanPrimary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Nueva contraseña", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Introduce el código enviado a\n${viewModel.email}\ny elige una nueva contraseña.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = TextSecondary
            )
            Spacer(Modifier.height(32.dp))

            NeuroSquatTextField(
                value         = form.code,
                onValueChange = viewModel::onCodeChange,
                label         = "Código de 6 dígitos",
                leadingIcon   = Icons.Filled.Pin,
                isError       = isError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(14.dp))
            NeuroSquatTextField(
                value         = form.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label         = "Nueva contraseña",
                leadingIcon   = Icons.Filled.Lock,
                isPassword    = true,
                isError       = isError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(14.dp))
            NeuroSquatTextField(
                value         = form.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label         = "Confirmar contraseña",
                leadingIcon   = Icons.Filled.Lock,
                isPassword    = true,
                isError       = isError,
                errorMessage  = (uiState as? ResetPasswordUiState.Error)?.message,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(28.dp))

            NeuroSquatPrimaryButton(
                text      = "Cambiar contraseña",
                isLoading = uiState is ResetPasswordUiState.Loading,
                onClick   = viewModel::onSubmitClick
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
