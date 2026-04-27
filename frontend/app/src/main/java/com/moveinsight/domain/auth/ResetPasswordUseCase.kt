package com.moveinsight.domain.auth

import com.moveinsight.core.utils.Constants
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.repository.AuthRepository
import javax.inject.Inject

class ResetPasswordUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(
        email: String, code: String, newPassword: String, confirmPassword: String
    ): Resource<String> {
        if (code.length != 6) return Resource.Error("El código debe ser de 6 dígitos.")
        if (newPassword.length < Constants.MIN_PASSWORD_LENGTH)
            return Resource.Error("La contraseña debe tener al menos ${Constants.MIN_PASSWORD_LENGTH} caracteres.")
        if (newPassword != confirmPassword) return Resource.Error("Las contraseñas no coinciden.")
        return repository.resetPassword(email, code, newPassword)
    }
}
