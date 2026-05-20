package com.moveinsight.domain.auth

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.UserProfile
import com.moveinsight.domain.repository.AuthRepository
import javax.inject.Inject

class GetUserProfileUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Resource<UserProfile> = repository.getUserProfile()
}
