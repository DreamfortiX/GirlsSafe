package com.example.gamified.utils

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.gamified.MainActivity
import com.example.gamified.R
import com.example.gamified.receiver.EmergencyActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles emergency actions when AI detects a potential emergency.
 * This includes notifying contacts, playing alarms, and sharing location.
 */
class EmergencyActionHandler(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.IO)

    private var mediaPlayer: MediaPlayer? = null
    private var isHandlingEmergency = false

    companion object {
        private const val TAG = "EmergencyActionHandler"
        const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "emergency_channel"
        private val VIBRATE_PATTERN = longArrayOf(0, 1000, 1000, 1000, 1000) // 1s on, 1s off, repeat

        // Request codes for pending intents
        private const val REQUEST_CODE_STOP_EMERGENCY = 1001
        private const val REQUEST_CODE_CALL_911 = 1002
    }

    /**
     * Handle an emergency situation
     * @param location The current location when emergency was detected (can be null)
     */
    fun handleEmergency(location: Location? = null) {
        if (isHandlingEmergency) return
        isHandlingEmergency = true

        Log.d(TAG, "Handling emergency with location: $location")

        // Create notification channel
        createNotificationChannel()

        // Start alarm and vibration
        startAlarm()
        startVibration()

        // Show persistent notification
        showEmergencyNotification(location)

        // Notify emergency contacts
        notifyEmergencyContacts(location)
    }

    /**
     * Stop the emergency response
     */
    fun stopEmergency() {
        if (!isHandlingEmergency) return

        Log.d(TAG, "Stopping emergency response")

        // Stop alarm and vibration
        stopAlarm()
        stopVibration()

        // Cancel notification
        notificationManager.cancel(NOTIFICATION_ID)

        isHandlingEmergency = false
    }

    private fun startAlarm() {
        try {
            // Ensure we have audio focus
            val result = audioManager.requestAudioFocus(
                { /* Audio focus change listener */ },
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, alarmSound)
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "Alarm started successfully")
            } else {
                Log.w(TAG, "Audio focus not granted for alarm")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm", e)
            // Fallback to system default notification sound
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, notification)
                r.play()
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback alarm also failed", fallbackError)
            }
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            audioManager.abandonAudioFocus(null)
            Log.d(TAG, "Alarm stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm", e)
        }
    }

    private fun startVibration() {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Device does not have vibrator")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(VIBRATE_PATTERN, 0), // 0 = repeat indefinitely
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATE_PATTERN, 0)
            }
            Log.d(TAG, "Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration", e)
        }
    }

    private fun stopVibration() {
        try {
            vibrator.cancel()
            Log.d(TAG, "Vibration stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibration", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.emergency_notification_channel_name)
            val descriptionText = context.getString(R.string.emergency_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
                setSound(null, null) // We handle sound separately
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showEmergencyNotification(location: Location?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create stop emergency action
        val stopIntent = Intent(context, EmergencyActionReceiver::class.java).apply {
            action = "STOP_EMERGENCY"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STOP_EMERGENCY,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create call 911 action
        val call911Intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:911")
        }
        val call911PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_CALL_911,
            call911Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val locationText = if (location != null) {
            "Location: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}"
        } else {
            "Location not available"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Use system icon as fallback
            .setContentTitle("Emergency Alert!")
            .setContentText("Potential emergency detected")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Potential emergency detected!\n\n$locationText")
            )
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "CALL 911", call911PendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        // Show notification
        try {
            with(NotificationManagerCompat.from(context)) {
                if (areNotificationsEnabled()) {
                    notify(NOTIFICATION_ID, notification)
                    Log.d(TAG, "Emergency notification shown")
                } else {
                    Log.w(TAG, "Notifications are disabled")
                    // If notifications are disabled, try to open settings
                    val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(settingsIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }

    private fun notifyEmergencyContacts(location: Location?) {
        scope.launch {
            try {
                // For now, just log that we would notify contacts
                // In a real implementation, you would access your database and send notifications

                val locationText = if (location != null) {
                    "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                } else {
                    "Location not available"
                }

                val message = "Emergency detected!\n\n" +
                        "Location: $locationText\n" +
                        "Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date())}"

                Log.i(TAG, "Would notify emergency contacts: $message")

                // TODO: Implement actual emergency contact notification
                // This would involve:
                // 1. Getting contacts from database
                // 2. Sending SMS/emails/notifications
                // 3. Handling permissions for SMS sending

            } catch (e: Exception) {
                Log.e(TAG, "Error notifying emergency contacts", e)
            }
        }
    }

    /**
     * Check if we have all required permissions for emergency features
     */
    fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun isHandlingEmergency(): Boolean = isHandlingEmergency
}

// Move the BroadcastReceiver to a separate file for better organization
// EmergencyActionReceiver.kt