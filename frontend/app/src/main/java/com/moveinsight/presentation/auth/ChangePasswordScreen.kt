package com.moveinsight.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
fun ChangePasswordScreen(
    onNavigateBack : () -> Unit,
    viewModel      : ChangePasswordViewModel = hiltViewModel()
) {
    val form         by viewModel.form.collectAsStateWithLifecycle()
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost =  remember { SnackbarHostState() }
    val errorMsg     =  (uiState as? ChangePasswordUiState.Error)?.message
    val isError      =  errorMsg != null

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                ChangePasswordEvent.NavigateBack       -> onNavigateBack()
                is ChangePasswordEvent.ShowSnackbar    -> snackbarHost.showSnackbar(event.msg)
            }
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = NavyDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cambiar contraseña",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            Text(
                text      = "Introduce tu contraseña actual y elige una nueva.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = TextSecondary
            )

            Spacer(Modifier.height(32.dp))

            // ── Contraseña actual ─────────────────────────────────────────
            val currentPassError = errorMsg?.takeIf {
                it.contains("actual", ignoreCase = true) ||
                it.contains("incorrecta", ignoreCase = true)
            }
            NeuroSquatTextField(
                value         = form.currentPassword,
                onValueChange = viewModel::onCurrentPasswordChange,
                label         = "Contraseña actual",
                leadingIcon   = Icons.Filled.LockOpen,
                isPassword    = true,
                isError       = isError,
                errorMessage  = currentPassError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(14.dp))

            // ── Nueva contraseña ──────────────────────────────────────────
            val newPassError = errorMsg?.takeIf {
                it.contains("8 caracteres", ignoreCase = true) ||
                it.contains("diferente", ignoreCase = true)
            }
            NeuroSquatTextField(
                value         = form.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label         = "Nueva contraseña",
                leadingIcon   = Icons.Filled.Lock,
                isPassword    = true,
                isError       = isError,
                errorMessage  = newPassError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(14.dp))

            // ── Confirmar contraseña ──────────────────────────────────────
            val confirmPassError = errorMsg?.takeIf {
                it.contains("coinciden", ignoreCase = true)
            }
            NeuroSquatTextField(
                value         = form.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label         = "Confirmar nueva contraseña",
                leadingIcon   = Icons.Filled.Lock,
                isPassword    = true,
                isError       = isError,
                errorMessage  = confirmPassError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(Modifier.height(32.dp))

            NeuroSquatPrimaryButton(
                text      = "Guardar cambios",
                isLoading = uiState is ChangePasswordUiState.Loading,
                onClick   = viewModel::onSubmitClick
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
