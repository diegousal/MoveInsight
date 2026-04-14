package com.moveinsight.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moveinsight.core.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Delegado a nivel de fichero — garantiza una única instancia del DataStore
private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = Constants.USER_PREFERENCES_DATASTORE)

/**
 * Capa de abstracción sobre DataStore.
 * Centraliza toda la persistencia local de sesión.
 *
 * Nota de seguridad: DataStore almacena en el directorio privado de la app
 * (protegido por el sandbox de Android). Para entornos con requisitos clínicos
 * más estrictos (ej. dispositivos no encriptados o con root), se puede añadir
 * cifrado adicional con EncryptedFile de Security Crypto.
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ACCESS_TOKEN  = stringPreferencesKey("access_token")
        val USER_EMAIL    = stringPreferencesKey("user_email")
        val USER_FULLNAME = stringPreferencesKey("user_full_name")
    }

    // ── Token ──────────────────────────────────────────────────────────────

    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { it[Keys.ACCESS_TOKEN] = token }
    }

    fun getAccessToken(): Flow<String?> =
        context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }

    // ── Datos del usuario ─────────────────────────────────────────────────

    suspend fun saveUserInfo(email: String, fullName: String) {
        context.dataStore.edit {
            it[Keys.USER_EMAIL]    = email
            it[Keys.USER_FULLNAME] = fullName
        }
    }

    fun getUserEmail(): Flow<String?> =
        context.dataStore.data.map { it[Keys.USER_EMAIL] }

    fun getUserFullName(): Flow<String?> =
        context.dataStore.data.map { it[Keys.USER_FULLNAME] }

    // ── Cierre de sesión ──────────────────────────────────────────────────

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}