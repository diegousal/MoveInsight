package com.moveinsight.presentation.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moveinsight.presentation.components.MoveInsightLogo
import com.moveinsight.presentation.theme.*

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        when (state) {
            AuthCheckState.Authenticated    -> onNavigateToHome()
            AuthCheckState.Unauthenticated  -> onNavigateToLogin()
            AuthCheckState.Checking         -> Unit
        }
    }

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label         = "splash_alpha"
    )
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier         = Modifier.fillMaxSize().background(NavyDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier.alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Wordmark ──────────────────────────────────────────────────
            MoveInsightLogo(fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))

            // ── Tagline ───────────────────────────────────────────────────
            Text(
                text  = "Clinical Performance Platform",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(60.dp))

            CircularProgressIndicator(
                color       = CyanPrimary,
                modifier    = Modifier.size(26.dp),
                strokeWidth = 2.dp
            )
        }
    }
}