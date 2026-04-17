package com.moveinsight.core.utils

object Constants {

    // ── DataStore ──────────────────────────────────────────────────────────
    const val USER_PREFERENCES_DATASTORE = "user_preferences"

    // ── Endpoints auth ────────────────────────────────────────────────────
    const val ENDPOINT_LOGIN    = "api/v1/auth/token"
    const val ENDPOINT_REGISTER = "api/v1/auth/register"

    // ── Endpoints sesión ──────────────────────────────────────────────────
    const val ENDPOINT_UPLOAD_SESSION = "api/v1/sessions/upload"
    const val ENDPOINT_SESSIONS       = "api/v1/sessions"
    const val ENDPOINT_SESSION_DETAIL = "api/v1/sessions/{id}"
    const val ENDPOINT_ANALYTICS      = "api/v1/analytics/summary"
    const val ENDPOINT_EXPORT_PDF     = "api/v1/analytics/pdf"

    const val ENDPOINT_GET_SESSIONS = "api/v1/sessions/"

    // ── Endpoints check-in de dolor ───────────────────────────────────────
    // ⚠️ Ajusta según tu APIRouter de FastAPI
    const val ENDPOINT_PAIN_CHECKIN = "api/v1/checkins"   // POST

    // ── Headers ────────────────────────────────────────────────────────────
    const val HEADER_AUTHORIZATION = "Authorization"
    const val TOKEN_PREFIX         = "Bearer "

    // ── Validación ────────────────────────────────────────────────────────
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_VIDEO_SIZE_MB   = 500

    // ── WorkManager ───────────────────────────────────────────────────────
    const val WORKER_SESSION_ID_KEY    = "session_id"
    const val WORKER_HOURS_KEY         = "hours_after"
    const val NOTIFICATION_CHANNEL_ID  = "pain_checkin_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Check-in de Dolor"
    const val PAIN_NOTIFICATION_ID_24H  = 1001
    const val PAIN_NOTIFICATION_ID_48H  = 1002

    // ── Deep link ─────────────────────────────────────────────────────────
    const val DEEP_LINK_SCHEME      = "moveinsight"
    const val DEEP_LINK_HOST_CHECKIN = "checkin"
}