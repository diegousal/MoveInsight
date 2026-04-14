package com.moveinsight.core.utils

/**
 * Wrapper genérico para el resultado de operaciones asíncronas.
 * - Success / Error se emiten desde el repositorio.
 * - Loading lo gestiona el ViewModel en su UiState local.
 *
 * 'out T' (covarianza): Resource<Nothing> es subtipo de Resource<T>,
 * lo que permite devolver Error y Loading en funciones tipadas.
 */
sealed class Resource<out T> {
    data class Success<T>(val data: T)                          : Resource<T>()
    data class Error(val message: String, val code: Int? = null): Resource<Nothing>()
    data object Loading                                         : Resource<Nothing>()
}