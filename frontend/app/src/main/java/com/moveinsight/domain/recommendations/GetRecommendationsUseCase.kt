package com.moveinsight.domain.recommendations

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Recommendation
import com.moveinsight.domain.repository.RecommendationsRepository
import javax.inject.Inject

class GetRecommendationsUseCase @Inject constructor(
    private val repository: RecommendationsRepository
) {
    suspend operator fun invoke(): Resource<List<Recommendation>> =
        repository.getRecommendations()
}
