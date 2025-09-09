package com.example.gamified.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
// Remove unused imports
import com.example.gamified.MainActivity
import com.example.gamified.R
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class SafetyService : Service() {
    private val CHANNEL_ID = "safety_service_channel"
    private val NOTIFICATION_ID = 1001
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    
    private var currentLocation: Location? = null
    
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        createLocationCallback()
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationUpdates()
    }

    private fun createNotification(): Notification {
        try {
            createNotificationChannel()
            
            val notificationIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.safety_service_active))
                .setContentText(getString(R.string.tracking_your_location))
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Using a default icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
        } catch (e: Exception) {
            // Fallback notification if there's an error
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Safety Service")
                .setContentText("Running in the background")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 30000
            fastestInterval = 15000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
    }
    
    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    updateLocation(location.latitude, location.longitude)
                }
            }
        }
    }
    
    private fun startLocationUpdates() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Stop the service if we don't have location permissions
                stopSelf()
                return
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
            // Get last known location
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    currentLocation = it
                    updateLocation(it.latitude, it.longitude)
                }
            }.addOnFailureListener { e ->
                // Handle failure to get last location
                e.printStackTrace()
            }
        } catch (e: SecurityException) {
            // Handle security exception - likely permission was revoked
            e.printStackTrace()
            stopSelf() // Stop service if we don't have permissions
        }
    }

    private fun updateLocation(lat: Double, lng: Double) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        val locationData = hashMapOf(
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to System.currentTimeMillis(),
            "batteryLevel" to getBatteryLevel()
        )
        
        FirebaseFirestore.getInstance()
            .collection("user_locations")
            .document(userId)
            .set(locationData)
            .addOnSuccessListener {
                // Location updated successfully
            }
            .addOnFailureListener { e ->
                // Handle error
                e.printStackTrace()
            }
    }
    
    private fun getBatteryLevel(): Float {
        val batteryStatus = registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        
        val batteryLevel = batteryStatus?.getIntExtra(
            android.os.BatteryManager.EXTRA_LEVEL,
            -1
        ) ?: -1
        
        val batteryScale = batteryStatus?.getIntExtra(
            android.os.BatteryManager.EXTRA_SCALE,
            -1
        ) ?: -1
        
        return if (batteryLevel != -1 && batteryScale != -1) {
            batteryLevel * 100 / batteryScale.toFloat()
        } else {
            -1f
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Safety Service Channel",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Channel for safety service notifications"
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(serviceChannel)
            } catch (e: Exception) {
                // Log error or handle it appropriately
                e.printStackTrace()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
    
    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
