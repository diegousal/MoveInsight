package com.moveinsight.core.utils

object Constants {

    // ── DataStore ──────────────────────────────────────────────────────────
    const val USER_PREFERENCES_DATASTORE = "user_preferences"

    // ── Endpoints auth ────────────────────────────────────────────────────
    const val ENDPOINT_LOGIN    = "api/v1/auth/token"
    const val ENDPOINT_REGISTER = "api/v1/auth/register"

    // ── Endpoints sesión ──────────────────────────────────────────────────
    // ⚠️ Ajusta el prefijo según tu APIRouter de FastAPI
    const val ENDPOINT_UPLOAD_SESSION = "api/v1/sessions/upload"

    // ── Headers ────────────────────────────────────────────────────────────
    const val HEADER_AUTHORIZATION = "Authorization"
    const val TOKEN_PREFIX         = "Bearer "

    // ── Validación ────────────────────────────────────────────────────────
    const val MIN_PASSWORD_LENGTH  = 8
    const val MAX_VIDEO_SIZE_MB    = 500   // Límite orientativo para UI feedback
}