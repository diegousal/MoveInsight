package com.moveinsight.domain.auth

import android.util.Patterns
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.repository.AuthRepository
import javax.inject.Inject

class ForgotPasswordUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Resource<String> {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return Resource.Error("Introduce un email válido.")
        return repository.forgotPassword(email.trim())
    }
}
