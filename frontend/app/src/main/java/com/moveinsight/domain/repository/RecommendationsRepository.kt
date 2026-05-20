package com.moveinsight.domain.repository

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Recommendation

interface RecommendationsRepository {
    suspend fun getRecommendations(): Resource<List<Recommendation>>
}
