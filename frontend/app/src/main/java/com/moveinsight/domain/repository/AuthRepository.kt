package com.moveinsight.domain.repository


import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.AuthToken
import com.moveinsight.domain.model.User
import kotlinx.coroutines.flow.Flow

/** Contrato puro — el dominio no sabe nada de Retrofit ni DataStore */
interface AuthRepository {
    suspend fun login(email: String, password: String): Resource<AuthToken>
    suspend fun register(email: String, password: String, fullName: String): Resource<User>
    suspend fun verifyEmail(email: String, code: String): Resource<String>
    suspend fun resendVerification(email: String): Resource<String>
    suspend fun forgotPassword(email: String): Resource<String>
    suspend fun resetPassword(email: String, code: String, newPassword: String): Resource<String>
    suspend fun changePassword(currentPassword: String, newPassword: String): Resource<String>
    fun getAccessToken(): Flow<String?>
    suspend fun logout()
}