package com.moveinsight.core.network


import com.moveinsight.core.security.UserPreferencesDataStore
import com.moveinsight.core.utils.Constants
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Interceptor de OkHttp que inyecta automáticamente el Bearer Token
 * en la cabecera Authorization de cada petición.
 *
 * El uso de runBlocking aquí es intencional y correcto:
 * los interceptores de OkHttp corren en un hilo de fondo del pool de OkHttp,
 * por lo que bloquear ese hilo para leer del DataStore es el patrón aceptado
 * (a diferencia de la UI thread donde runBlocking está prohibido).
 */
class AuthInterceptor @Inject constructor(
    private val dataStore: UserPreferencesDataStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            dataStore.getAccessToken().firstOrNull()
        }

        val request = if (!token.isNullOrBlank()) {
            chain.request()
                .newBuilder()
                .header(
                    Constants.HEADER_AUTHORIZATION,
                    "${Constants.TOKEN_PREFIX}$token"
                )
                .build()
        } else {
            chain.request()
        }

        return chain.proceed(request)
    }
}