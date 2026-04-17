package com.moveinsight.core.notifications

import android.content.Context
import androidx.work.*
import com.moveinsight.core.security.UserPreferencesDataStore
import com.moveinsight.core.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchedulePainNotificationsUseCase @Inject constructor(
    @ApplicationContext private val context  : Context,
    private val dataStore                    : UserPreferencesDataStore
) {
    /**
     * Programa dos notificaciones de check-in para una sesión:
     *   - 24 horas después
     *   - 48 horas después
     *
     * [createdAt] debe ser el timestamp ISO devuelto por el backend
     * (ej. "2024-12-15T18:32:00"). Se incluye en la notificación para que
     * el usuario sepa a qué sesión corresponde.
     *
     * Usa ExistingWorkPolicy.REPLACE para cancelar programaciones
     * anteriores de la misma sesión si se llama dos veces.
     */
    suspend operator fun invoke(sessionId: Int, createdAt: String = "") {
        // Respetar las preferencias del usuario antes de programar nada
        val generalEnabled = dataStore.getNotifGeneral().first()
        if (!generalEnabled) return

        if (dataStore.getNotif24h().first()) {
            scheduleWorker(
                sessionId  = sessionId,
                hoursAfter = 24,
                createdAt  = createdAt,
                tag        = "pain_checkin_24h_$sessionId"
            )
        }
        if (dataStore.getNotif48h().first()) {
            scheduleWorker(
                sessionId  = sessionId,
                hoursAfter = 48,
                createdAt  = createdAt,
                tag        = "pain_checkin_48h_$sessionId"
            )
        }
    }

    private fun scheduleWorker(sessionId: Int, hoursAfter: Int, createdAt: String, tag: String) {
        val inputData = workDataOf(
            Constants.WORKER_SESSION_ID_KEY         to sessionId,
            Constants.WORKER_HOURS_KEY              to hoursAfter,
            Constants.WORKER_SESSION_CREATED_AT_KEY to createdAt
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

    /**
     * Dispara notificaciones de prueba a las 10s (24h) y 25s (48h).
     * Usar solo para desarrollo/QA — NO llamar en producción.
     */
    fun triggerTest(sessionId: Int) {
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        // 24h → ~10 segundos
        scheduleSingleTest(sessionId, hoursAfter = 24, createdAt = nowIso, delaySeconds = 10L,  tag = "pain_test_24h_$sessionId")
        // 48h → ~25 segundos (suficiente para completar el 24h primero)
        scheduleSingleTest(sessionId, hoursAfter = 48, createdAt = nowIso, delaySeconds = 25L, tag = "pain_test_48h_$sessionId")
    }

    private fun scheduleSingleTest(sessionId: Int, hoursAfter: Int, createdAt: String, delaySeconds: Long, tag: String) {
        val inputData = workDataOf(
            Constants.WORKER_SESSION_ID_KEY         to sessionId,
            Constants.WORKER_HOURS_KEY              to hoursAfter,
            Constants.WORKER_SESSION_CREATED_AT_KEY to createdAt
        )
        val request = OneTimeWorkRequestBuilder<PainCheckInWorker>()
            .setInputData(inputData)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .addTag(tag)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            tag,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}