package com.moveinsight.core.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canal global para eventos de sesión.
 *
 * Cuando el TokenAuthenticator agota los intentos de renovación (refresh token
 * expirado / inválido), emite [sessionExpired]. La UI observa este flow y
 * redirige al usuario a la pantalla de login sin necesidad de que cada
 * ViewModel gestione manualmente los errores 401.
 */
@Singleton
class SessionManager @Inject constructor() {

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /** Llamado desde [TokenAuthenticator] en hilo de OkHttp. Thread-safe. */
    fun notifySessionExpired() {
        _sessionExpired.tryEmit(Unit)
    }
}
