package com.example.gamified.data.repository

import com.example.gamified.data.datasource.AuthDataSource
import javax.inject.Inject

interface AuthRepository {
    suspend fun isUserAuthenticated(): Boolean
    suspend fun signIn(email: String, password: String): Boolean
    suspend fun signUp(email: String, password: String): Boolean
    suspend fun signOut()
}

class AuthRepositoryImpl @Inject constructor(
    private val authDataSource: AuthDataSource
) : AuthRepository {
    
    override suspend fun isUserAuthenticated(): Boolean {
        return authDataSource.isUserAuthenticated()
    }

    override suspend fun signIn(email: String, password: String): Boolean {
        return authDataSource.signIn(email, password)
    }

    override suspend fun signUp(email: String, password: String): Boolean {
        return authDataSource.signUp(email, password)
    }

    override suspend fun signOut() {
        authDataSource.signOut()
    }
}
