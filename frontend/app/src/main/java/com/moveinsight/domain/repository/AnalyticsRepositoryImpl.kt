package com.moveinsight.domain.repository

import com.moveinsight.core.utils.Resource
import com.moveinsight.core.utils.safeApiCall
import com.moveinsight.data.remote.AnalyticsApiService
import com.moveinsight.domain.model.Analytics
import com.moveinsight.domain.model.Insight
import com.moveinsight.domain.model.SessionDetail
import com.moveinsight.domain.repository.AnalyticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class AnalyticsRepositoryImpl @Inject constructor(
    private val api: AnalyticsApiService
) : AnalyticsRepository {

    override suspend fun getSessions(): Resource<List<SessionDetail>> {
        val result = safeApiCall { api.getSessions() }
        return when (result) {
            is Resource.Success -> Resource.Success(result.data.map { dto ->
                SessionDetail(
                    id           = dto.id,
                    status       = dto.status,
                    message      = dto.message,
                    createdAt    = dto.createdAt ?: "",
                    weightKg     = dto.weightKg  ?: 0f,
                    borgScore    = dto.borgScore  ?: 0,
                    userNotes    = dto.userNotes,
                    overallScore = dto.overallScore,
                    repCount     = dto.repCount,
                    results      = dto.results.map { r ->
                        com.moveinsight.domain.model.RepResult(
                            repNumber      = r.repNumber,
                            depthScore     = r.depthScore,
                            torsoScore     = r.torsoScore,
                            stabilityScore = r.stabilityScore,
                            kneesScore     = r.kneesScore,
                            rhythmScore    = r.rhythmScore,
                            overallScore   = r.overallScore
                        )
                    }
                )
            })
            is Resource.Error   -> result
            is Resource.Loading -> result
        }
    }

    override suspend fun getSessionDetail(id: Int): Resource<SessionDetail> {
        val result = safeApiCall { api.getSessionDetail(id) }
        return when (result) {
            is Resource.Success -> {
                val dto = result.data
                val reps = dto.results.map { r ->
                    com.moveinsight.domain.model.RepResult(
                        repNumber      = r.repNumber,
                        depthScore     = r.depthScore,
                        torsoScore     = r.torsoScore,
                        stabilityScore = r.stabilityScore,
                        kneesScore     = r.kneesScore,
                        rhythmScore    = r.rhythmScore,
                        overallScore   = r.overallScore
                    )
                }
                Resource.Success(
                    SessionDetail(
                        id           = dto.id,
                        status       = dto.status,
                        message      = dto.message,
                        createdAt    = dto.createdAt ?: "",
                        weightKg     = dto.weightKg  ?: 0f,
                        borgScore    = dto.borgScore  ?: 0,
                        userNotes    = dto.userNotes,
                        results      = reps,
                        overallScore = if (reps.isNotEmpty()) reps.map { it.overallScore }.average().toFloat() else null,
                        repCount     = if (reps.isNotEmpty()) reps.size else null
                    )
                )
            }
            is Resource.Error   -> result
            is Resource.Loading -> result
        }
    }

    override suspend fun getAnalytics(): Resource<Analytics> {
        val result = safeApiCall { api.getAnalytics() }
        return when (result) {
            is Resource.Success -> {
                val d = result.data
                Resource.Success(
                    Analytics(
                        totalSessions      = d.totalSessions,
                        avgTechniqueScore  = d.avgTechniqueScore,
                        avgVelocity        = d.avgVelocity,
                        maxWeightKg        = d.maxWeightKg,
                        bestTechniqueScore = d.bestTechniqueScore,
                        maxReps            = d.maxReps,
                        overtrainingRisk   = d.overtrainingRisk,
                        readinessScore     = d.readinessScore,
                        readinessLabel     = d.readinessLabel,
                        insights           = d.insights.map { Insight(it.type, it.title, it.message) }
                    )
                )
            }
            is Resource.Error   -> result
            is Resource.Loading -> result
        }
    }

    override suspend fun exportPdf(destFile: File): Resource<File> {
        return try {
            val response = api.exportPdf()
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Resource.Error("Respuesta vacía del servidor.")
                withContext(Dispatchers.IO) {
                    destFile.outputStream().use { body.byteStream().copyTo(it) }
                }
                Resource.Success(destFile)
            } else {
                Resource.Error("Error al exportar (${response.code()})")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error de red al exportar.")
        }
    }
}