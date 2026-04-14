package com.moveinsight.domain.repository


import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.AuthToken
import com.moveinsight.domain.model.User
import kotlinx.coroutines.flow.Flow

/** Contrato puro — el dominio no sabe nada de Retrofit ni DataStore */
interface AuthRepository {
    suspend fun login(email: String, password: String): Resource<AuthToken>
    suspend fun register(email: String, password: String, fullName: String): Resource<User>
    fun getAccessToken(): Flow<String?>
    suspend fun logout()
}