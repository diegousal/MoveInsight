package com.moveinsight.presentation.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.moveinsight.presentation.auth.ChangePasswordScreen
import com.moveinsight.presentation.auth.ForgotPasswordScreen
import com.moveinsight.presentation.auth.LoginScreen
import com.moveinsight.presentation.auth.RegisterScreen
import com.moveinsight.presentation.auth.ResetPasswordScreen
import com.moveinsight.presentation.auth.VerifyEmailScreen
import com.moveinsight.presentation.capture.CaptureScreen
import com.moveinsight.presentation.checkin.PainCheckInScreen
import com.moveinsight.presentation.dashboard.DashboardScreen
import com.moveinsight.presentation.home.HomeScreen
import com.moveinsight.presentation.results.ResultsScreen
import com.moveinsight.presentation.skeleton.SkeletonVideoScreen
import com.moveinsight.presentation.splash.SplashScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val context       = LocalContext.current

    // Reenvía los deep links recibidos mientras la app ya está en ejecución
    // (p.ej. notificación EVA de 48h cuando la de 24h ya estaba abierta)
    DisposableEffect(navController) {
        val activity = context as? ComponentActivity
        val listener = Consumer<Intent> { intent ->
            navController.handleDeepLink(intent)
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    NavHost(navController = navController, startDestination = Routes.Splash.route) {

        // ── Auth ──────────────────────────────────────────────────────────
        composable(Routes.Splash.route) {
            SplashScreen(
                onNavigateToHome  = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Login.route) {
            LoginScreen(
                onLoginSuccess       = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Routes.Register.route) },
                onNavigateToVerify   = { email ->
                    navController.navigate(Routes.VerifyEmail.route(email))
                },
                onNavigateToForgot   = { navController.navigate(Routes.ForgotPassword.route) }
            )
        }

        composable(Routes.Register.route) {
            RegisterScreen(
                onNavigateToVerify = { email ->
                    navController.navigate(Routes.VerifyEmail.route(email)) {
                        popUpTo(Routes.Register.route) { inclusive = true }
                    }
                },
                onNavigateToLogin  = { navController.popBackStack() }
            )
        }

        // ── Verificación de email ─────────────────────────────────────────
        composable(
            route     = Routes.VerifyEmail.route,
            arguments = listOf(navArgument(Routes.VerifyEmail.ARG_EMAIL) {
                type         = NavType.StringType
                defaultValue = ""
            })
        ) {
            VerifyEmailScreen(
                onNavigateToLogin = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Login.route) { inclusive = false }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Contraseña olvidada ───────────────────────────────────────────
        composable(Routes.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateToReset = { email ->
                    navController.navigate(Routes.ResetPassword.route(email))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Resetear contraseña ───────────────────────────────────────────
        composable(
            route     = Routes.ResetPassword.route,
            arguments = listOf(navArgument(Routes.ResetPassword.ARG_EMAIL) {
                type         = NavType.StringType
                defaultValue = ""
            })
        ) {
            ResetPasswordScreen(
                onNavigateToLogin = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Login.route) { inclusive = false }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Cambiar contraseña (autenticado) ──────────────────────────────
        composable(Routes.ChangePassword.route) {
            ChangePasswordScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Home ──────────────────────────────────────────────────────────
        composable(Routes.Home.route) {
            HomeScreen(
                onLogout                   = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Home.route) { inclusive = true }
                    }
                },
                onNavigateToRecord         = { navController.navigate(Routes.Capture.record()) },
                onNavigateToUpload         = { navController.navigate(Routes.Capture.upload()) },
                onNavigateToDashboard      = { navController.navigate(Routes.Dashboard.route) },
                onNavigateToChangePassword = { navController.navigate(Routes.ChangePassword.route) }
            )
        }

        // ── Captura ───────────────────────────────────────────────────────
        composable(
            route     = Routes.Capture.route,
            arguments = listOf(
                navArgument(Routes.Capture.ARG_MODE) {
                    type         = NavType.StringType
                    defaultValue = Routes.Capture.MODE_RECORD
                }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString(Routes.Capture.ARG_MODE)
            CaptureScreen(
                isUploadMode    = (mode == Routes.Capture.MODE_UPLOAD),
                onNavigateBack  = { navController.popBackStack() },
                onUploadSuccess = { sessionId ->
                    navController.navigate(Routes.Results.route(sessionId)) {
                        popUpTo(Routes.Home.route) { inclusive = false }
                    }
                }
            )
        }

        // ── Resultados del análisis ───────────────────────────────────────
        composable(
            route     = Routes.Results.route,
            arguments = listOf(
                navArgument(Routes.Results.ARG_SESSION_ID) {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getInt(Routes.Results.ARG_SESSION_ID) ?: -1
            ResultsScreen(
                onNavigateHome   = {
                    navController.popBackStack(Routes.Home.route, inclusive = false)
                },
                onViewSkeleton   = { id ->
                    navController.navigate(Routes.Skeleton.route(id))
                },
                sessionIdForNav  = sessionId
            )
        }

        // ── Skeleton video overlay ────────────────────────────────────────
        composable(
            route     = Routes.Skeleton.route,
            arguments = listOf(
                navArgument(Routes.Skeleton.ARG_SESSION_ID) {
                    type = NavType.IntType
                }
            )
        ) {
            SkeletonVideoScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Dashboard ─────────────────────────────────────────────────────
        composable(Routes.Dashboard.route) {
            DashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResults = { sessionId ->
                    navController.navigate(Routes.Results.route(sessionId))
                }
            )
        }

        // ── Check-in EVA (Fase 4) — soporta deep link desde notificación ──
        composable(
            route      = Routes.CheckIn.route,
            arguments  = listOf(
                navArgument(Routes.CheckIn.ARG_SESSION_ID) {
                    type         = NavType.IntType
                    defaultValue = -1
                },
                navArgument(Routes.CheckIn.ARG_HOURS_AFTER) {
                    type         = NavType.IntType
                    defaultValue = 24
                }
            ),
            deepLinks  = listOf(
                navDeepLink { uriPattern = Routes.CheckIn.DEEP_LINK_URI }
            )
        ) {
            PainCheckInScreen(
                onNavigateHome = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Home.route) { inclusive = false }
                    }
                }
            )
        }
    }
}