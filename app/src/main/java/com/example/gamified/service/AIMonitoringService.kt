package com.example.gamified.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.gamified.R
// import com.example.gamified.ai.AIManager
import com.example.gamified.manager.LocationManager
import com.example.gamified.utils.PermissionUtils
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

private const val EXTRA_SOUND_ENABLED = "extra_sound_enabled"
private const val EXTRA_SOUND_SENSITIVITY = "extra_sound_sensitivity"

class AIMonitoringService : Service() {
    // private lateinit var aiManager: AIManager
    private var isServiceRunning = false
    private var locationManager: LocationManager? = null
    private var locationCallback: LocationCallback? = null
    private var config: AIMonitoringConfig? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    
    data class AIMonitoringConfig(
        val soundEnabled: Boolean,
        val soundSensitivity: Int
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AIMonitoringService created")
        
        // Start as foreground service immediately
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Initialize AIManager with default config (will be updated in onStartCommand)
        // aiManager = AIManager(applicationContext, ::onEmergencyDetected)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with intent: $intent")
        
        try {
            // Start as foreground service first to prevent ANR
            try {
                val notification = createNotification()
                startForeground(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service", e)
                // Try to recover by stopping and restarting the service
                stopSelf()
                val restartIntent = Intent(applicationContext, AIMonitoringService::class.java).apply {
                    if (intent?.extras != null) {
                        putExtras(intent.extras!!)
                    } else {
                        // Add default extras if needed
                        putExtra(EXTRA_SOUND_ENABLED, true)
                        putExtra(EXTRA_SOUND_SENSITIVITY, 70)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restartIntent)
                } else {
                    startService(restartIntent)
                }
                return START_STICKY
            }
            
            // Extract configuration from intent
            val soundEnabled = intent?.getBooleanExtra(EXTRA_SOUND_ENABLED, true) ?: true
            val soundSensitivity = intent?.getIntExtra(EXTRA_SOUND_SENSITIVITY, 70) ?: 70
            
            config = AIMonitoringConfig(
                soundEnabled = soundEnabled,
                soundSensitivity = soundSensitivity
            )
            
            if (!isServiceRunning) {
                isServiceRunning = true
//                startAIMonitoring()
            } else {
                // Service is already running, update configuration
//                updateAIMonitoring()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            // Try to recover by stopping and restarting the service
            stopSelf()
            val restartIntent = Intent(applicationContext, AIMonitoringService::class.java)
            startForegroundService(restartIntent)
        }
        
        return START_STICKY
    }

    // private fun startAIMonitoring() {
    //     try {
    //         config?.let { config ->
    //             // Configure AI manager with current settings
    //             aiManager.apply {
    //                 setSoundDetectionEnabled(config.soundEnabled)
    //                 setSoundSensitivity(config.soundSensitivity)
    //             }
    //             
    //             // Start monitoring
    //             aiManager.startMonitoring()
    //             
    //             // Set up location updates if permissions are granted
    //             if (PermissionUtils.hasRequiredPermissions(
    //                     this,
    //                     arrayOf(
    //                         android.Manifest.permission.ACCESS_FINE_LOCATION,
    //                         android.Manifest.permission.ACCESS_COARSE_LOCATION
    //                     )
    //                 )
    //             ) {
    //                 setupLocationUpdates()
    //             }
    //             
    //             Log.d(TAG, "AI monitoring started with config: $config")
    //         } ?: run {
    //             Log.e(TAG, "Cannot start monitoring: Configuration is null")
    //             stopSelf()
    //         }
    //     } catch (e: Exception) {
    //         Log.e(TAG, "Error starting AI monitoring", e)
    //         stopSelf()
    //     }
    // }
    
    // private fun updateAIMonitoring() {
    //     try {
    //         config?.let { config ->
    //             // Configuration update logic here
    //         }
    //     } catch (e: Exception) {
    //         Log.e(TAG, "Error updating AI monitoring", e)
    //     }
    // }
    
    // private fun onEmergencyDetected() {
    //     Log.d(TAG, "Emergency detected!")
    //     // AIManager will handle the emergency through EmergencyActionHandler
    // }

    private fun createNotification(): Notification {
        return try {
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                "default_channel"
            }

            // Create a pending intent to launch the app when notification is tapped
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                packageManager.getLaunchIntentForPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.safety_service_active))
                .setContentText(getString(R.string.tracking_your_location))
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Using a default icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
        } catch (e: Exception) {
            // Fallback notification in case of any error
            NotificationCompat.Builder(this, "default_channel")
                .setContentTitle("Safety Service")
                .setContentText("Running in the background")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    @Suppress("DEPRECATION")
    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channelId = "ai_monitoring_channel"
                val channelName = "AI Monitoring Service"
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background service for AI monitoring"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }

                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
                return channelId
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel", e)
                return "default_channel"
            }
        }
        return "default_channel"
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        
        // Stop AI monitoring
        // aiManager.stopMonitoring()
        
        // Stop location updates
        stopLocationUpdates()
        
        Log.d(TAG, "AI monitoring service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupLocationUpdates() {
        try {
            locationManager = LocationManager(applicationContext)
            
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    // locationResult.lastLocation?.let { location ->
                    //     // Update AIManager with the latest location
                    //     aiManager.updateLocation(location)
                    // }
                }
            }
            
            // Request location updates
            locationManager?.requestLocationUpdates(
                locationCallback!!,
                30000,  // 30 seconds
                50f     // 50 meters
            )
            
        } catch (e: SecurityException) {
            Log.e("AIMonitoringService", "Location permission not granted", e)
        } catch (e: Exception) {
            Log.e("AIMonitoringService", "Error setting up location updates", e)
        }
    }
    
    private fun stopLocationUpdates() {
        try {
            locationCallback?.let { callback ->
                locationManager?.removeLocationUpdates(callback)
            }
            locationCallback = null
        } catch (e: Exception) {
            Log.e("AIMonitoringService", "Error stopping location updates", e)
        }
    }
    
    companion object {
        private const val TAG = "AIMonitoringService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ai_monitoring_channel"
        
        fun startService(
            context: Context,
            soundEnabled: Boolean = true,
            motionEnabled: Boolean = true,
            soundSensitivity: Int = 70,
            motionSensitivity: Int = 60
        ) {
            // val intent = Intent(context, AIMonitoringService::class.java).apply {
            //     putExtra(EXTRA_SOUND_ENABLED, soundEnabled)
            //     putExtra(EXTRA_SOUND_SENSITIVITY, soundSensitivity)
            // }
            // 
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //     context.startForegroundService(intent)
            // } else {
            //     context.startService(intent)
            // }
            // 
            // Log.d(TAG, "Starting AIMonitoringService with sound: $soundEnabled, motion: $motionEnabled, " +
            //         "soundSens: $soundSensitivity, motionSens: $motionSensitivity")
            Log.d(TAG, "AI Monitoring is currently disabled")
        }
        
        fun stopService(context: Context) {
            // val intent = Intent(context, AIMonitoringService::class.java)
            // context.stopService(intent)
            Log.d(TAG, "AI Monitoring service stop requested (service is disabled)")
        }
        
        fun isServiceRunning(context: Context): Boolean {
            // val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            // return manager.getRunningServices(Integer.MAX_VALUE)
            //     .any { it.service.className == AIMonitoringService::class.java.name }
            return false
        }
    }
}
