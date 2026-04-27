package com.moveinsight.domain.auth

import com.moveinsight.core.utils.Constants
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.repository.AuthRepository
import javax.inject.Inject

class ChangePasswordUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(
        currentPassword: String, newPassword: String, confirmPassword: String
    ): Resource<String> {
        if (currentPassword.isBlank()) return Resource.Error("Introduce tu contraseña actual.")
        if (newPassword.length < Constants.MIN_PASSWORD_LENGTH)
            return Resource.Error("La nueva contraseña debe tener al menos ${Constants.MIN_PASSWORD_LENGTH} caracteres.")
        if (newPassword != confirmPassword) return Resource.Error("Las contraseñas no coinciden.")
        if (currentPassword == newPassword) return Resource.Error("La nueva contraseña debe ser diferente.")
        return repository.changePassword(currentPassword, newPassword)
    }
}
