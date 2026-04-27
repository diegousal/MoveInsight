package com.moveinsight.domain.auth

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.repository.AuthRepository
import javax.inject.Inject

class ResendVerificationUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Resource<String> =
        repository.resendVerification(email)
}
