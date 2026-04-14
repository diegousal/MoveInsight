package com.moveinsight.core.di

import com.moveinsight.data.repository.AuthRepositoryImpl
import com.moveinsight.data.repository.SessionRepositoryImpl
import com.moveinsight.domain.repository.AuthRepository
import com.moveinsight.domain.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
}