package com.moveinsight.presentation.navigation

sealed class Routes(val route: String) {
    data object Splash   : Routes("splash")
    data object Login    : Routes("login")
    data object Register : Routes("register")
    data object Home     : Routes("home")

    // Fase 2: modo como parámetro de ruta
    data object Capture  : Routes("capture?mode={mode}") {
        const val ARG_MODE   = "mode"
        const val MODE_RECORD = "record"
        const val MODE_UPLOAD = "upload"
        fun record() = "capture?mode=$MODE_RECORD"
        fun upload() = "capture?mode=$MODE_UPLOAD"
    }
    // Fase 3: data object Analysis : Routes("analysis/{sessionId}")
}