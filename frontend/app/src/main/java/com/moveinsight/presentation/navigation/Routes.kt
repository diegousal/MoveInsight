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

    data object VerifyEmail : Routes("verify_email?email={email}") {
        const val ARG_EMAIL = "email"
        fun route(email: String) = "verify_email?email=${android.net.Uri.encode(email)}"
    }

    data object ForgotPassword : Routes("forgot_password")

    data object ResetPassword : Routes("reset_password?email={email}") {
        const val ARG_EMAIL = "email"
        fun route(email: String) = "reset_password?email=${android.net.Uri.encode(email)}"
    }

    data object ChangePassword     : Routes("change_password")
    data object Onboarding         : Routes("onboarding")
    data object ReadinessBreakdown : Routes("readiness_breakdown")
    data object Wellness           : Routes("wellness")

    data object IncidentFactors : Routes("incident_factors/{incidentId}") {
        const val ARG_INCIDENT_ID = "incidentId"
        fun route(incidentId: Int) = "incident_factors/$incidentId"
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