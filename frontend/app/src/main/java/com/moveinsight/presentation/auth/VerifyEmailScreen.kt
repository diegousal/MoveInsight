package com.moveinsight.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.presentation.components.NeuroSquatPrimaryButton
import com.moveinsight.presentation.components.NeuroSquatTextField
import com.moveinsight.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyEmailScreen(
    onNavigateToLogin : () -> Unit,
    onNavigateBack    : () -> Unit,
    viewModel         : VerifyEmailViewModel = hiltViewModel()
) {
    val form         by viewModel.form.collectAsStateWithLifecycle()
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val cooldown     by viewModel.resendCooldown.collectAsStateWithLifecycle()
    val snackbarHost =  remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                VerifyEmailEvent.NavigateToLogin      -> onNavigateToLogin()
                is VerifyEmailEvent.ShowSnackbar      -> snackbarHost.showSnackbar(event.msg)
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

            Icon(
                imageVector = Icons.Filled.Pin,
                contentDescription = null,
                tint    = CyanPrimary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Verifica tu cuenta", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Introduce el código de 6 dígitos que enviamos a\n${viewModel.email}",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = TextSecondary
            )

            Spacer(Modifier.height(36.dp))

            NeuroSquatTextField(
                value         = form.code,
                onValueChange = viewModel::onCodeChange,
                label         = "Código de verificación",
                leadingIcon   = Icons.Filled.Pin,
                isError       = uiState is VerifyEmailUiState.Error,
                errorMessage  = (uiState as? VerifyEmailUiState.Error)?.message,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(Modifier.height(28.dp))

            NeuroSquatPrimaryButton(
                text      = "Verificar cuenta",
                isLoading = uiState is VerifyEmailUiState.Loading,
                onClick   = viewModel::onVerifyClick,
                enabled   = form.code.length == 6
            )

            Spacer(Modifier.height(20.dp))

            TextButton(
                onClick  = viewModel::onResendClick,
                enabled  = cooldown == 0
            ) {
                Text(
                    text  = if (cooldown > 0) "Reenviar código (${cooldown}s)" else "No recibí el código — reenviar",
                    color = if (cooldown > 0) TextSecondary else CyanPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
