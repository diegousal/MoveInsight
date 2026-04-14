package com.moveinsight.domain.auth


import android.util.Patterns
import com.moveinsight.core.utils.Constants
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.User
import com.moveinsight.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        confirmPassword: String,
        fullName: String
    ): Resource<User> {
        if (listOf(email, password, confirmPassword, fullName).any { it.isBlank() })
            return Resource.Error("Todos los campos son obligatorios.")
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return Resource.Error("Introduce un email válido.")
        if (password.length < Constants.MIN_PASSWORD_LENGTH)
            return Resource.Error("La contraseña debe tener al menos ${Constants.MIN_PASSWORD_LENGTH} caracteres.")
        if (password != confirmPassword)
            return Resource.Error("Las contraseñas no coinciden.")
        if (fullName.trim().length < 2)
            return Resource.Error("Introduce tu nombre completo.")

        return repository.register(email.trim(), password, fullName.trim())
    }
}