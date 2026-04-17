package com.moveinsight

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moveinsight.presentation.navigation.NavGraph
import com.moveinsight.presentation.theme.MoveInsightTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Debe llamarse ANTES de super.onCreate()
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoveInsightTheme {
                NavGraph()
            }
        }
    }

    /**
     * Cuando la app ya está en ejecución y el usuario toca una notificación
     * (p.ej. la de 48h), Android llama a onNewIntent en lugar de onCreate.
     * Actualizamos el intent de la actividad para que el NavController
     * lo procese y navegue al destino correcto con los argumentos del deep link.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}