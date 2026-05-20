package com.moveinsight.domain.auth

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.repository.AuthRepository
import javax.inject.Inject

class CompleteOnboardingUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(
        age           : Int?,
        bodyWeightKg  : Float?,
        level         : String,
        objective     : String
    ): Resource<String> = repository.completeOnboarding(age, bodyWeightKg, level, objective)
}
