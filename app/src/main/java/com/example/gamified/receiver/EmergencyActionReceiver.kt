package com.example.gamified.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.gamified.utils.EmergencyActionHandler

/**
 * Broadcast receiver for handling emergency action buttons in notifications.
 */
class EmergencyActionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_EMERGENCY -> {
                Log.d(TAG, "Stop emergency action received")
                // Stop the emergency response
                EmergencyActionHandler(context).stopEmergency()
                
                // Cancel the notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(EmergencyActionHandler.NOTIFICATION_ID)
            }
            
            ACTION_CALL_911 -> {
                Log.d(TAG, "Call 911 action received")
                // This intent will be caught by the notification's pending intent
                // which is set to open the dialer with 911
            }
        }
    }
    
    companion object {
        private const val TAG = "EmergencyActionReceiver"
        
        // Action strings
        const val ACTION_STOP_EMERGENCY = "com.example.gamified.STOP_EMERGENCY"
        const val ACTION_CALL_911 = "com.example.gamified.CALL_911"
    }
}
