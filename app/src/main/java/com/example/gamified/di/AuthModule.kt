package com.example.gamified.di

import com.example.gamified.data.datasource.AuthDataSource
import com.example.gamified.data.datasource.FirebaseAuthDataSource
import com.example.gamified.data.repository.AuthRepository
import com.example.gamified.data.repository.AuthRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    
    @Provides
    @Singleton
    fun provideAuthDataSource(
        firebaseAuth: FirebaseAuth
    ): AuthDataSource = FirebaseAuthDataSource(firebaseAuth)
}
