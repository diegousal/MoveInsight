package com.moveinsight.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moveinsight.presentation.auth.LoginScreen
import com.moveinsight.presentation.auth.RegisterScreen
import com.moveinsight.presentation.capture.CaptureScreen
import com.moveinsight.presentation.home.HomeScreen
import com.moveinsight.presentation.splash.SplashScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = Routes.Splash.route
    ) {

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
                onNavigateToRegister = { navController.navigate(Routes.Register.route) }
            )
        }

        composable(Routes.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Register.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.Home.route) {
            HomeScreen(
                onLogout           = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Home.route) { inclusive = true }
                    }
                },
                onNavigateToRecord = { navController.navigate(Routes.Capture.record()) },
                onNavigateToUpload = { navController.navigate(Routes.Capture.upload()) }
            )
        }

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
                onUploadSuccess = {
                    navController.popBackStack(Routes.Home.route, inclusive = false)
                }
            )
        }
    }
}