package com.example.gamified.di

import android.content.Context
import com.example.gamified.manager.EmergencyManager
import com.example.gamified.utils.DataStoreManager
import com.example.gamified.utils.PermissionUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStoreManager(
        @ApplicationContext context: Context
    ): DataStoreManager {
        return DataStoreManager(context)
    }

    @Provides
    @Singleton
    fun provideEmergencyManager(
        @ApplicationContext context: Context,
        dataStoreManager: DataStoreManager
    ): EmergencyManager {
        return EmergencyManager(context).apply {
            // Initialize with any required settings
        }
    }

    @Provides
    @Singleton
    fun providePermissionUtils(
        @ApplicationContext context: Context
    ): PermissionUtils {
        return PermissionUtils
    }
}
