package com.example.gamified.data.datasource

interface AuthDataSource {
    suspend fun isUserAuthenticated(): Boolean
    suspend fun signIn(email: String, password: String): Boolean
    suspend fun signUp(email: String, password: String): Boolean
    suspend fun signOut()
}
