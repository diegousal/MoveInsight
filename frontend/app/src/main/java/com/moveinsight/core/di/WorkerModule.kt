package com.moveinsight.core.di


import com.moveinsight.core.notifications.SchedulePainNotificationsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt ya gestiona PainCheckInWorker con @HiltWorker + @AssistedInject.
 * Este EntryPoint expone SchedulePainNotificationsUseCase para
 * acceso desde fuera del grafo de DI si fuera necesario.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerEntryPoint {
    fun schedulePainNotificationsUseCase(): SchedulePainNotificationsUseCase
}