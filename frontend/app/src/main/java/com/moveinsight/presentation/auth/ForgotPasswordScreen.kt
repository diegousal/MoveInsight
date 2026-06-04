package com.moveinsight.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onNavigateToReset : (email: String) -> Unit,
    onNavigateBack    : () -> Unit,
    viewModel         : ForgotPasswordViewModel = hiltViewModel()
) {
    val email        by viewModel.email.collectAsStateWithLifecycle()
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost =  remember { SnackbarHostState() }
    val focusManager =  LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ForgotPasswordEvent.NavigateToReset -> onNavigateToReset(event.email)
                is ForgotPasswordEvent.ShowSnackbar    -> snackbarHost.showSnackbar(event.msg)
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
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Filled.Email, null, tint = CyanPrimary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("¿Olvidaste tu contraseña?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Introduce tu email y te enviaremos un código para restablecerla.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = TextSecondary
            )
            Spacer(Modifier.height(36.dp))

            NeuroSquatTextField(
                value         = email,
                onValueChange = viewModel::onEmailChange,
                label         = "Correo electrónico",
                leadingIcon   = Icons.Filled.Email,
                isError       = uiState is ForgotPasswordUiState.Error,
                errorMessage  = (uiState as? ForgotPasswordUiState.Error)?.message,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus(); viewModel.onSubmitClick() }
                )
            )

            Spacer(Modifier.height(28.dp))

            NeuroSquatPrimaryButton(
                text      = "Enviar código",
                isLoading = uiState is ForgotPasswordUiState.Loading,
                onClick   = { focusManager.clearFocus(); viewModel.onSubmitClick() }
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
