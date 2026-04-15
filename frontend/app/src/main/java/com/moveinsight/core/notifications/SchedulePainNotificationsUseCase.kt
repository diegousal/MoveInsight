package com.moveinsight.core.notifications

import android.content.Context
import androidx.work.*
import com.moveinsight.core.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchedulePainNotificationsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Programa dos notificaciones de check-in para una sesión:
     *   - 24 horas después
     *   - 48 horas después
     *
     * Usa ExistingWorkPolicy.REPLACE para cancelar programaciones
     * anteriores de la misma sesión si se llama dos veces.
     */
    operator fun invoke(sessionId: Int) {
        scheduleWorker(
            sessionId  = sessionId,
            hoursAfter = 24,
            tag        = "pain_checkin_24h_$sessionId"
        )
        scheduleWorker(
            sessionId  = sessionId,
            hoursAfter = 48,
            tag        = "pain_checkin_48h_$sessionId"
        )
    }

    private fun scheduleWorker(sessionId: Int, hoursAfter: Int, tag: String) {
        val inputData = workDataOf(
            Constants.WORKER_SESSION_ID_KEY to sessionId,
            Constants.WORKER_HOURS_KEY      to hoursAfter
        )

        val constraints = Constraints.Builder()
            .build()  // sin restricciones de red — es solo una notificación local

        val request = OneTimeWorkRequestBuilder<PainCheckInWorker>()
            .setInputData(inputData)
            .setInitialDelay(hoursAfter.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(tag)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            tag,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}