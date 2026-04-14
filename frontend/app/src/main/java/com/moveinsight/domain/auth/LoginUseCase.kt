package com.moveinsight.domain.auth


import android.util.Patterns
import com.moveinsight.core.utils.Constants
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.AuthToken
import com.moveinsight.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Resource<AuthToken> {
        // Validaciones antes de hacer la petición de red
        if (email.isBlank() || password.isBlank())
            return Resource.Error("El email y la contraseña son obligatorios.")
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return Resource.Error("Introduce un email válido.")
        if (password.length < Constants.MIN_PASSWORD_LENGTH)
            return Resource.Error("La contraseña debe tener al menos ${Constants.MIN_PASSWORD_LENGTH} caracteres.")

        return repository.login(email.trim(), password)
    }
}