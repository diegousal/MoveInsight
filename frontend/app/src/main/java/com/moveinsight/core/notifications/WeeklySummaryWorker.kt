package com.moveinsight.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.repository.GetAnalyticsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker semanal que obtiene el resumen de analytics y muestra
 * una notificación con las estadísticas de la semana.
 *
 * Se programa con PeriodicWorkRequest de 7 días desde
 * [ScheduleWeeklySummaryUseCase].
 */
@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted private val context      : Context,
    @Assisted private val workerParams : WorkerParameters,
    private   val getAnalyticsUseCase  : GetAnalyticsUseCase,
    private   val notificationHelper   : NotificationHelper
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            when (val result = getAnalyticsUseCase()) {
                is Resource.Success -> {
                    val analytics = result.data
                    // Mostrar alerta de sobreentrenamiento si aplica
                    if (analytics.overtrainingRisk) {
                        val recentBorg = if (analytics.readinessScore < 40) {
                            // estimar avg_borg a partir del readiness: readiness = 100 - borg*8
                            ((100 - analytics.readinessScore) / 8f)
                        } else 8f
                        notificationHelper.showOvertrainingAlert(recentBorg, analytics.readinessScore)
                    }
                    // Mostrar resumen semanal
                    notificationHelper.showWeeklySummaryNotification(
                        totalSessions = analytics.totalSessions,
                        avgTechnique  = analytics.avgTechniqueScore,
                        readiness     = analytics.readinessScore
                    )
                    Result.success()
                }
                else -> {
                    // Sin conectividad o sesión expirada — mostrar notificación genérica
                    notificationHelper.showWeeklySummaryNotification(
                        totalSessions = 0,
                        avgTechnique  = null,
                        readiness     = 0
                    )
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
