package com.moveinsight.domain.auth


import com.moveinsight.domain.repository.AuthRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/** Devuelve true si hay un token válido guardado en sesión */
class CheckAuthUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Boolean {
        return !repository.getAccessToken().firstOrNull().isNullOrBlank()
    }
}