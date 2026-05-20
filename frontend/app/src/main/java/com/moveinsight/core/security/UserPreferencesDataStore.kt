package com.moveinsight.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moveinsight.core.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = Constants.USER_PREFERENCES_DATASTORE)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ACCESS_TOKEN            = stringPreferencesKey("access_token")
        val REFRESH_TOKEN           = stringPreferencesKey("refresh_token")
        val USER_EMAIL              = stringPreferencesKey("user_email")
        val USER_FULLNAME           = stringPreferencesKey("user_full_name")
        // ── Onboarding ────────────────────────────────────────────────────
        val ONBOARDING_COMPLETED    = booleanPreferencesKey("onboarding_completed")
        // ── Fase 4: datos de check-in ─────────────────────────────────────
        val LAST_SESSION_ID         = intPreferencesKey("last_session_id")
        val LAST_EVA_SCORE          = intPreferencesKey("last_eva_score")
        val LAST_CHECKIN_DATE       = stringPreferencesKey("last_checkin_date")
        // ── Preferencias de notificación ──────────────────────────────────
        val NOTIF_GENERAL           = booleanPreferencesKey("notif_general_enabled")
        val NOTIF_24H               = booleanPreferencesKey("notif_24h_enabled")
        val NOTIF_48H               = booleanPreferencesKey("notif_48h_enabled")
    }

    // ── Tokens ─────────────────────────────────────────────────────────────
    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { it[Keys.ACCESS_TOKEN] = token }
    }
    fun getAccessToken(): Flow<String?> =
        context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }

    suspend fun saveRefreshToken(token: String) {
        context.dataStore.edit { it[Keys.REFRESH_TOKEN] = token }
    }
    fun getRefreshToken(): Flow<String?> =
        context.dataStore.data.map { it[Keys.REFRESH_TOKEN] }

    /** Guarda ambos tokens de una vez (llamado tras login / refresh). */
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit {
            it[Keys.ACCESS_TOKEN]  = accessToken
            it[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    // ── Usuario ────────────────────────────────────────────────────────────
    suspend fun saveUserInfo(email: String, fullName: String) {
        context.dataStore.edit {
            it[Keys.USER_EMAIL]    = email
            it[Keys.USER_FULLNAME] = fullName
        }
    }
    fun getUserEmail(): Flow<String?>    = context.dataStore.data.map { it[Keys.USER_EMAIL] }
    fun getUserFullName(): Flow<String?> = context.dataStore.data.map { it[Keys.USER_FULLNAME] }

    // ── Onboarding ────────────────────────────────────────────────────────
    suspend fun saveOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }
    }
    fun getOnboardingCompleted(): Flow<Boolean?> =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] }

    // ── Sesión + Check-in (Fase 4) ────────────────────────────────────────
    suspend fun saveLastSessionId(id: Int) {
        context.dataStore.edit { it[Keys.LAST_SESSION_ID] = id }
    }
    fun getLastSessionId(): Flow<Int?> =
        context.dataStore.data.map { it[Keys.LAST_SESSION_ID] }

    suspend fun saveLastEvaScore(score: Int, date: String) {
        context.dataStore.edit {
            it[Keys.LAST_EVA_SCORE]    = score
            it[Keys.LAST_CHECKIN_DATE] = date
        }
    }
    fun getLastEvaScore(): Flow<Int?> =
        context.dataStore.data.map { it[Keys.LAST_EVA_SCORE] }

    // ── Preferencias de notificación ─────────────────────────────────────
    suspend fun saveNotifPrefs(general: Boolean, h24: Boolean, h48: Boolean) {
        context.dataStore.edit {
            it[Keys.NOTIF_GENERAL] = general
            it[Keys.NOTIF_24H]     = h24
            it[Keys.NOTIF_48H]     = h48
        }
    }
    /** Por defecto activo — un usuario nuevo recibe todas las notificaciones. */
    fun getNotifGeneral(): Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIF_GENERAL] ?: true }
    fun getNotif24h(): Flow<Boolean>     = context.dataStore.data.map { it[Keys.NOTIF_24H]     ?: true }
    fun getNotif48h(): Flow<Boolean>     = context.dataStore.data.map { it[Keys.NOTIF_48H]     ?: true }

    // ── Cierre de sesión ──────────────────────────────────────────────────
    suspend fun clearSession() { context.dataStore.edit { it.clear() } }
}