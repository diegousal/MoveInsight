package com.moveinsight.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moveinsight.MainActivity
import com.moveinsight.R
import com.moveinsight.core.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Crea el canal de notificación (obligatorio en Android 8+).
     * Llamar desde Application.onCreate().
     */
    fun createNotificationChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Canal check-in dolor
        val painChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Recordatorios para el seguimiento del dolor post-entrenamiento"
        }
        manager.createNotificationChannel(painChannel)

        // Canal resumen semanal / alertas
        val summaryChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_SUMMARY_ID,
            Constants.NOTIFICATION_CHANNEL_SUMMARY_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Resumen semanal de entrenamiento y alertas de sobreentrenamiento"
        }
        manager.createNotificationChannel(summaryChannel)
    }

    /** Muestra el resumen semanal con estadísticas clave. */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showWeeklySummaryNotification(
        totalSessions  : Int,
        avgTechnique   : Float?,
        readiness      : Int
    ) {
        val intent = Intent(context, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = PendingIntent.getActivity(
            context, Constants.WEEKLY_SUMMARY_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val avgStr = avgTechnique?.let { "%.1f/10".format(it) } ?: "—"
        val body   = "Esta semana: $totalSessions sesiones · Técnica media $avgStr · Readiness $readiness%"

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_SUMMARY_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🏋️ Resumen semanal MoveInsight")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(Constants.WEEKLY_SUMMARY_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) { }
    }

    /** Alerta inmediata de riesgo de sobreentrenamiento. */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showOvertrainingAlert(avgBorg: Float, readiness: Int) {
        val intent = Intent(context, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = PendingIntent.getActivity(
            context, Constants.OVERTRAINING_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = "Borg medio %.1f con readiness %d%%. Toma al menos 48h de descanso.".format(avgBorg, readiness)

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_SUMMARY_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ Riesgo de sobreentrenamiento")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(Constants.OVERTRAINING_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) { }
    }

    /**
     * Muestra la notificación de check-in.
     * El tap abre la app con deep link a la pantalla EVA.
     *
     * [sessionCreatedAt] es el timestamp ISO de la sesión (ej. "2024-12-15T18:32:00").
     * Se usa para indicar al usuario a qué sesión pertenece la notificación.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showPainCheckInNotification(sessionId: Int, hoursAfter: Int, sessionCreatedAt: String = "") {
        val deepLinkUri = Uri.parse(
            "${Constants.DEEP_LINK_SCHEME}://${Constants.DEEP_LINK_HOST_CHECKIN}/$sessionId?hours=$hoursAfter"
        )

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, deepLinkUri, context, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }

        val pendingIntent = PendingIntent.getActivity(
            context,
            sessionId + hoursAfter,       // requestCode único
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Formatear fecha/hora de la sesión ─────────────────────────────
        // Input:  "2024-12-15T18:32:00.123"
        // Output: "15/12 · 18:32"
        val sessionLabel = sessionCreatedAt.formatSessionLabel()

        val (title, body) = when (hoursAfter) {
            24 -> {
                val t = "Seguimiento 24h · $sessionLabel".trimEnd(' ', '·')
                val b = if (sessionLabel.isNotEmpty())
                    "Sesión del $sessionLabel — ¿Notas alguna molestia?"
                else
                    "Registra tu nivel de dolor para optimizar tu readiness"
                t to b
            }
            else -> {
                val t = "Seguimiento 48h · $sessionLabel".trimEnd(' ', '·')
                val b = if (sessionLabel.isNotEmpty())
                    "Sesión del $sessionLabel — ¿Persiste alguna molestia?"
                else
                    "¿Persiste alguna molestia? Registra cómo te sientes"
                t to b
            }
        }

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = if (hoursAfter == 24)
            Constants.PAIN_NOTIFICATION_ID_24H
        else
            Constants.PAIN_NOTIFICATION_ID_48H

        // POST_NOTIFICATIONS permission se pide en runtime (Android 13+)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) { /* Permiso no concedido */ }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * "2024-12-15T18:32:00.123"  →  "15/12 · 18:32"
     * Devuelve cadena vacía si el formato no es reconocible.
     */
    private fun String.formatSessionLabel(): String {
        return try {
            // Tomamos los primeros 16 caracteres: "2024-12-15T18:32"
            val raw = this.take(16)
            val parts = raw.split("T")
            if (parts.size != 2) return ""
            val dateParts = parts[0].split("-")
            if (dateParts.size != 3) return ""
            val day   = dateParts[2]   // "15"
            val month = dateParts[1]   // "12"
            val time  = parts[1]       // "18:32"
            "$day/$month · $time"
        } catch (_: Exception) { "" }
    }
}