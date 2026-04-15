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
                    id             = dto.id,
                    createdAt      = dto.createdAt,
                    weightKg       = dto.weightKg,
                    borgScore      = dto.borgScore,
                    status         = dto.status,
                    techniqueScore = dto.techniqueScore,
                    avgVelocity    = dto.avgVelocity,
                    depthDeg       = dto.depthDeg,
                    symmetryPct    = dto.symmetryPct,
                    repCount       = dto.repCount
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
                Resource.Success(
                    SessionDetail(dto.id, dto.createdAt, dto.weightKg, dto.borgScore,
                        dto.status, dto.techniqueScore, dto.avgVelocity,
                        dto.depthDeg, dto.symmetryPct, dto.repCount)
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
                        totalSessions     = d.totalSessions,
                        avgTechniqueScore = d.avgTechniqueScore,
                        avgVelocity       = d.avgVelocity,
                        maxWeightKg       = d.maxWeightKg,
                        readinessScore    = d.readinessScore,
                        readinessLabel    = d.readinessLabel,
                        insights          = d.insights.map { Insight(it.type, it.title, it.message) }
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