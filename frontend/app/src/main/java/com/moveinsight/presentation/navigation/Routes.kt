package com.moveinsight.presentation.navigation

sealed class Routes(val route: String) {
    data object Splash    : Routes("splash")
    data object Login     : Routes("login")
    data object Register  : Routes("register")
    data object Home      : Routes("home")
    data object Dashboard : Routes("dashboard")

    data object Capture : Routes("capture?mode={mode}") {
        const val ARG_MODE    = "mode"
        const val MODE_RECORD = "record"
        const val MODE_UPLOAD = "upload"
        fun record() = "capture?mode=$MODE_RECORD"
        fun upload() = "capture?mode=$MODE_UPLOAD"
    }

    data object Results : Routes("results/{sessionId}") {
        const val ARG_SESSION_ID = "sessionId"
        fun route(sessionId: Int) = "results/$sessionId"
    }

    data object Skeleton : Routes("skeleton/{sessionId}") {
        const val ARG_SESSION_ID = "sessionId"
        fun route(sessionId: Int) = "skeleton/$sessionId"
    }

    /** Deep link: moveinsight://checkin/{sessionId}?hours={hoursAfter} */
    data object CheckIn : Routes("checkin?sessionId={sessionId}&hours={hoursAfter}") {
        const val ARG_SESSION_ID  = "sessionId"
        const val ARG_HOURS_AFTER = "hoursAfter"
        const val DEEP_LINK_URI   = "moveinsight://checkin/{sessionId}?hours={hoursAfter}"
        fun route(sessionId: Int, hoursAfter: Int) =
            "checkin?sessionId=$sessionId&hours=$hoursAfter"
    }
}