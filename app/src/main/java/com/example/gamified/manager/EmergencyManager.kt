package com.example.gamified.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.gamified.R
import com.example.gamified.utils.PermissionUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.ListenerRegistration


class EmergencyManager(private val context: Context) {
    private val smsManager = SmsManager.getDefault()
    private var mediaPlayer: MediaPlayer? = null
    private var isEmergencyActive = false
    private var lastKnownLocation: Pair<Double, Double>? = null
    private var locationUpdateHandler: android.os.Handler? = null
    private val LOCATION_UPDATE_INTERVAL = 300000L // 5 minutes

    private val emergencyContacts = mutableListOf<Map<String, String>>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val locationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    private var contactsListener: ListenerRegistration? = null
    //private var aiManager: AIManager? = null
    private var isAIMonitoringEnabled = false

    init {
        loadEmergencyContacts()
        getLastKnownLocation()
        // AI initialization is disabled
    }
    fun cleanup() {
        contactsListener?.remove()
        contactsListener = null
        mediaPlayer?.release()
        mediaPlayer = null
        // AI monitoring is disabled, no need to stop it
        stopLocationUpdates()
    }

    fun triggerEmergency() {
        if (isEmergencyActive) return

        isEmergencyActive = true
        playAlarm()

        // Try to get fresh location before sending messages
        getCurrentLocation { locationPair ->
            locationPair?.let { (lat, lng) ->
                lastKnownLocation = lat to lng
            }
            // Always send emergency messages, even if location is null
            sendEmergencyMessages()
            
            // Start periodic location updates
            startLocationUpdates()
        }
    }

    private fun playAlarm() {
        mediaPlayer?.release()
        mediaPlayer = null

        // Try to use custom alarm sound first
        try {
            val mediaPlayer = MediaPlayer.create(context, R.raw.sos_alarm)
            if (mediaPlayer != null) {
                this.mediaPlayer = mediaPlayer
                mediaPlayer.isLooping = true
                mediaPlayer.start()
                return
            }
            Log.e("EmergencyManager", "Failed to create media player for custom alarm sound")
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Error playing custom alarm sound", e)
        }

        // Fall back to system default alarm
        try {
            val mediaPlayer = MediaPlayer.create(context, Settings.System.DEFAULT_ALARM_ALERT_URI)
            this.mediaPlayer = mediaPlayer
            mediaPlayer?.apply {
                isLooping = true
                start()
            } ?: Log.e("EmergencyManager", "Failed to create media player for system alarm sound")
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Error playing system alarm sound", e)
        }
    }

    private fun loadEmergencyContacts() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // Remove any existing listener to prevent duplicates
        contactsListener?.remove()
        
        contactsListener = FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("emergency_contacts")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("EmergencyManager", "Listen failed.", e)
                    return@addSnapshotListener
                }

                val newContacts = mutableListOf<Map<String, String>>()
                snapshot?.documents?.forEach { doc ->
                    doc.data?.let { contact ->
                        newContacts.add(contact.mapValues { it.value?.toString() ?: "" })
                    }
                }
                
                // Only update if contacts have actually changed
                if (newContacts != emergencyContacts) {
                    emergencyContacts.clear()
                    emergencyContacts.addAll(newContacts)
                    Log.d("EmergencyManager", "Updated ${emergencyContacts.size} emergency contacts")
                }
            }
    }

    private fun getLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        locationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                lastKnownLocation = it.latitude to it.longitude
            }
        }.addOnFailureListener { e ->
            Log.e("EmergencyManager", "Failed to get last known location", e)
        }
    }

    private fun getCurrentLocation(callback: (Pair<Double, Double>?) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback.invoke(null)
            return
        }

        try {
            // Use getCurrentLocation for more accurate and fresh location
            val cancellationTokenSource = CancellationTokenSource()
            locationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                val locationPair = location?.let { it.latitude to it.longitude }
                locationPair?.let {
                    lastKnownLocation = it
                }
                callback.invoke(locationPair)
            }.addOnFailureListener { e ->
                Log.e("EmergencyManager", "Failed to get current location", e)
                // Fall back to last known location
                callback.invoke(lastKnownLocation)
            }
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Error getting current location", e)
            callback.invoke(lastKnownLocation)
        }
    }

    private fun sendEmergencyMessages() {
        scope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    Log.e("EmergencyManager", "User not authenticated")
                    return@launch
                }
                
                val locationText = lastKnownLocation?.let { (lat, lng) ->
                    "https://www.google.com/maps?q=$lat,$lng"
                } ?: "Location not available"

                Log.d("EmergencyManager", "Fetching emergency contacts for user: $userId")
                val documents = try {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .collection("emergency_contacts")
                        .get()
                        .await()
                } catch (e: Exception) {
                    Log.e("EmergencyManager", "Error fetching contacts from Firestore", e)
                    return@launch
                }

                if (documents.isEmpty) {
                    Log.e("EmergencyManager", "No emergency contacts found")
                    return@launch
                }

                Log.d("EmergencyManager", "Found ${documents.size()} emergency contacts")
                var successCount = 0
                var failCount = 0

                for (document in documents) {
                    val phone = document.getString("phone")?.trim()
                    val name = document.getString("name")?.trim() ?: "Unknown"

                    if (phone.isNullOrBlank()) {
                        Log.e("EmergencyManager", "Skipping contact with empty phone number")
                        failCount++
                        continue
                    }

                    try {
                        val hasSmsPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasSmsPermission) {
                            Log.e("EmergencyManager", "SMS permission not granted")
                            failCount++
                            return@launch
                        }

                        val message = "EMERGENCY: I need help! My location: $locationText"
                        Log.d("EmergencyManager", "Attempting to send SMS to $name ($phone): $message")
                        
                        smsManager.sendTextMessage(
                            phone,
                            null,
                            message,
                            null,
                            null
                        )
                        
                        Log.d("EmergencyManager", "Successfully queued SMS to $name ($phone)")
                        successCount++
                    } catch (e: Exception) {
                        Log.e("EmergencyManager", "Failed to send SMS to $name ($phone)", e)
                        failCount++
                    }
                }
                
                Log.d("EmergencyManager", "SMS sending summary: $successCount succeeded, $failCount failed")
                
            } catch (e: Exception) {
                Log.e("EmergencyManager", "Unexpected error in sendEmergencyMessages", e)
            }
        }
    }

    private fun startLocationUpdates() {
        // Stop any existing updates
        stopLocationUpdates()
        
        locationUpdateHandler = android.os.Handler(Looper.getMainLooper())
        
        val locationUpdateRunnable = object : Runnable {
            override fun run() {
                if (isEmergencyActive) {
                    getCurrentLocation { locationPair ->
                        locationPair?.let { (lat, lng) ->
                            lastKnownLocation = lat to lng
                            sendLocationUpdateToContacts()
                        }
                    }
                    // Schedule next update
                    locationUpdateHandler?.postDelayed(this, LOCATION_UPDATE_INTERVAL)
                }
            }
        }
        
        // Start the first update immediately
        locationUpdateHandler?.post(locationUpdateRunnable)
    }
    
    private fun stopLocationUpdates() {
        locationUpdateHandler?.removeCallbacksAndMessages(null)
        locationUpdateHandler = null
    }
    
    private fun sendLocationUpdateToContacts() {
        scope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val locationText = lastKnownLocation?.let { (lat, lng) ->
                    "https://www.google.com/maps?q=$lat,$lng"
                } ?: return@launch
                
                val documents = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("emergency_contacts")
                    .get()
                    .await()

                for (document in documents) {
                    val phone = document.getString("phone") ?: continue
                    
                    try {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.SEND_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            smsManager.sendTextMessage(
                                phone,
                                null,
                                "LIVE LOCATION UPDATE: My current location: $locationText",
                                null,
                                null
                            )
                            Log.d("EmergencyManager", "Sent location update to $phone")
                        }
                    } catch (e: Exception) {
                        Log.e("EmergencyManager", "Failed to send location update to $phone", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("EmergencyManager", "Error in sendLocationUpdateToContacts", e)
            }
        }
    }
    
    fun stopEmergency() {
        isEmergencyActive = false
        mediaPlayer?.release()
        mediaPlayer = null
        stopLocationUpdates()
    }

    fun isEmergencyActive(): Boolean {
        return isEmergencyActive
    }

    fun getEmergencyContactsCount(): Int {
        return emergencyContacts.size
    }
    
    // AI Monitoring Controls - Currently Disabled
    fun startAIMonitoring(): Boolean {
        Log.d("EmergencyManager", "AI monitoring is currently disabled")
        return false
    }
    
    fun stopAIMonitoring() {
        Log.d("EmergencyManager", "AI monitoring is currently disabled")
    }
    
    fun isAIMonitoringEnabled(): Boolean = false
    
    fun toggleAIMonitoring(): Boolean {
        Log.d("EmergencyManager", "AI monitoring is currently disabled")
        return false
    }
}