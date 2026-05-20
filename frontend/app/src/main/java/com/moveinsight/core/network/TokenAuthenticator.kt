package com.moveinsight.core.network

import com.moveinsight.BuildConfig
import com.moveinsight.core.security.UserPreferencesDataStore
import com.moveinsight.core.session.SessionManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [okhttp3.Authenticator] que intercepta las respuestas 401 y
 * renueva el access token de forma transparente usando el refresh token.
 *
 * Flujo:
 *  1. La petición original falla con 401.
 *  2. OkHttp llama a [authenticate].
 *  3. Se obtiene el refresh token del DataStore.
 *  4. Se llama a POST /api/v1/auth/refresh con un cliente OkHttp limpio
 *     (sin interceptores, evita bucle infinito).
 *  5a. Éxito → se guardan los nuevos tokens y se reintenta la petición original.
 *  5b. Fallo → se borra la sesión, se emite [SessionManager.sessionExpired]
 *       y se devuelve null para que OkHttp propague el 401 a la capa superior.
 *
 * La URL del endpoint de refresh NO pasa por este autenticador porque
 * lo detectamos por la ruta antes de intentar nada.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val dataStore      : UserPreferencesDataStore,
    private val sessionManager : SessionManager,
) : okhttp3.Authenticator {

    /** Cliente limpio que se usa SOLO para la llamada de refresh. */
    private val refreshClient: OkHttpClient by lazy { OkHttpClient() }

    override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): Request? {
        // 1. Si la petición que falló ES el propio endpoint de refresh → sesión expirada
        if (response.request.url.encodedPath.contains("/auth/refresh")) {
            expireSession()
            return null
        }

        // 2. Obtener refresh token (sincrónico — estamos en hilo de OkHttp, no en UI)
        val refreshToken = runBlocking {
            dataStore.getRefreshToken().firstOrNull()
        }
        if (refreshToken.isNullOrBlank()) {
            expireSession()
            return null
        }

        // 3. Llamar al endpoint de refresh
        val newTokens = callRefreshEndpoint(refreshToken) ?: run {
            expireSession()
            return null
        }

        // 4. Persistir los nuevos tokens
        runBlocking {
            dataStore.saveTokens(
                accessToken  = newTokens.first,
                refreshToken = newTokens.second,
            )
        }

        // 5. Reintentar la petición original con el nuevo access token
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.first}")
            .build()
    }

    /**
     * Realiza la llamada HTTP de refresh de forma sincrónica.
     * @return Par (accessToken, refreshToken) o null si falla.
     */
    private fun callRefreshEndpoint(refreshToken: String): Pair<String, String>? {
        return try {
            val body = JSONObject().apply {
                put("refresh_token", refreshToken)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BuildConfig.BASE_URL}api/v1/auth/refresh")
                .post(body)
                .build()

            val response = refreshClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val json = JSONObject(response.body?.string() ?: return null)
            val newAccess  = json.getString("access_token")
            val newRefresh = json.getString("refresh_token")
            Pair(newAccess, newRefresh)
        } catch (e: Exception) {
            null
        }
    }

    private fun expireSession() {
        runBlocking { dataStore.clearSession() }
        sessionManager.notifySessionExpired()
    }
}
