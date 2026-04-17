package com.moveinsight.core.utils

import retrofit2.Response

/**
 * Ejecuta una llamada a la API de forma segura.
 * Mapea códigos HTTP a mensajes de error legibles.
 * Captura excepciones de red (sin conexión, timeout, etc.)
 */
suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): Resource<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            val body = response.body()
            @Suppress("UNCHECKED_CAST")
            if (body != null) {
                Resource.Success(body)
            } else if (response.code() in 200..204) {
                // 204 No Content — treat Unit responses as success
                Resource.Success(Unit as T)
            } else {
                Resource.Error("Respuesta vacía del servidor.", response.code())
            }
        } else {
            // Try to read the actual `detail` message from the FastAPI error body first
            val backendDetail = tryParseErrorDetail(response)
            val message = backendDetail ?: when (response.code()) {
                400  -> "Datos incorrectos. Revisa el formulario."
                401  -> "Credenciales incorrectas. Inténtalo de nuevo."
                403  -> "Acceso denegado."
                404  -> "Recurso no encontrado."
                409  -> "El elemento ya existe."
                422  -> "Error de validación. Revisa los datos introducidos."
                500  -> "Error interno del servidor. Inténtalo más tarde."
                503  -> "Servicio no disponible temporalmente."
                else -> "Error inesperado (${response.code()})."
            }
            Resource.Error(message, response.code())
        }
    } catch (e: java.net.UnknownHostException) {
        Resource.Error("Sin conexión a internet. Verifica tu red.")
    } catch (e: java.net.SocketTimeoutException) {
        Resource.Error("El servidor tardó demasiado en responder.")
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Error de conexión desconocido.")
    }
}

/**
 * Intenta extraer el campo "detail" del cuerpo de error JSON de FastAPI.
 * Ejemplo: {"detail": "Ya existe un check-in de 24h para esta sesión."}
 */
private fun tryParseErrorDetail(response: Response<*>): String? {
    return try {
        val errorBody = response.errorBody()?.string() ?: return null
        val match = """"detail"\s*:\s*"([^"]+)"""".toRegex().find(errorBody)
        match?.groupValues?.get(1)
    } catch (_: Exception) {
        null
    }
}