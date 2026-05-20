package com.moveinsight.core.di

import com.moveinsight.BuildConfig
import com.moveinsight.core.network.AuthInterceptor
import com.moveinsight.core.network.TokenAuthenticator
import com.moveinsight.data.remote.AnalyticsApiService
import com.moveinsight.data.remote.AuthApiService
import com.moveinsight.data.remote.PainCheckInApiService
import com.moveinsight.data.remote.RecommendationsApiService
import com.moveinsight.data.remote.SessionApiService
import com.moveinsight.data.remote.WellnessApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else                   HttpLoggingInterceptor.Level.NONE
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor    : AuthInterceptor,
        tokenAuthenticator : TokenAuthenticator,
        loggingInterceptor : HttpLoggingInterceptor
    ): OkHttpClient =
        OkHttpClient.Builder()
            .authenticator(tokenAuthenticator)   // ← renueva tokens en 401 automáticamente
            .addInterceptor(authInterceptor)     // ← añade Bearer a cada petición
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30,  TimeUnit.SECONDS)
            .readTimeout(30,     TimeUnit.SECONDS)
            .writeTimeout(120,   TimeUnit.SECONDS)  // Generoso para vídeos
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    // ── Servicios ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)

    @Provides
    @Singleton
    fun provideSessionApiService(retrofit: Retrofit): SessionApiService =
        retrofit.create(SessionApiService::class.java)
    @Provides @Singleton
    fun providePainCheckInApiService(retrofit: Retrofit): PainCheckInApiService =
        retrofit.create(PainCheckInApiService::class.java)
    @Provides
    @Singleton
    fun provideAnalyticsApiService(retrofit: Retrofit): AnalyticsApiService =
        retrofit.create(AnalyticsApiService::class.java)

    @Provides
    @Singleton
    fun provideWellnessApiService(retrofit: Retrofit): WellnessApiService =
        retrofit.create(WellnessApiService::class.java)

    @Provides
    @Singleton
    fun provideRecommendationsApiService(retrofit: Retrofit): RecommendationsApiService =
        retrofit.create(RecommendationsApiService::class.java)
}