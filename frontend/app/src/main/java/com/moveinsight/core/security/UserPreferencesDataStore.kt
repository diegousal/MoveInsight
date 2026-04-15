package com.moveinsight.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
        val ACCESS_TOKEN       = stringPreferencesKey("access_token")
        val USER_EMAIL         = stringPreferencesKey("user_email")
        val USER_FULLNAME      = stringPreferencesKey("user_full_name")
        // ── Fase 4: datos de check-in ─────────────────────────────────────
        val LAST_SESSION_ID    = intPreferencesKey("last_session_id")
        val LAST_EVA_SCORE     = intPreferencesKey("last_eva_score")
        val LAST_CHECKIN_DATE  = stringPreferencesKey("last_checkin_date")
    }

    // ── Token ──────────────────────────────────────────────────────────────
    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { it[Keys.ACCESS_TOKEN] = token }
    }
    fun getAccessToken(): Flow<String?> =
        context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }

    // ── Usuario ────────────────────────────────────────────────────────────
    suspend fun saveUserInfo(email: String, fullName: String) {
        context.dataStore.edit {
            it[Keys.USER_EMAIL]    = email
            it[Keys.USER_FULLNAME] = fullName
        }
    }
    fun getUserEmail(): Flow<String?>    = context.dataStore.data.map { it[Keys.USER_EMAIL] }
    fun getUserFullName(): Flow<String?> = context.dataStore.data.map { it[Keys.USER_FULLNAME] }

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

    // ── Cierre de sesión ──────────────────────────────────────────────────
    suspend fun clearSession() { context.dataStore.edit { it.clear() } }
}