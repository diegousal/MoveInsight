package com.moveinsight.domain.auth

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.repository.AuthRepository
import javax.inject.Inject

class VerifyEmailUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, code: String): Resource<String> {
        if (code.length != 6 || code.any { !it.isDigit() })
            return Resource.Error("El código debe ser de 6 dígitos.")
        return repository.verifyEmail(email, code)
    }
}
