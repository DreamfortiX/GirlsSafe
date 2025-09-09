package com.example.gamified

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Looper
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.gamified.data.AppDatabase
import com.example.gamified.databinding.FragmentEmergencyBinding
import com.example.gamified.manager.EmergencyManager
import com.example.gamified.service.SafetyService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EmergencyFragment : Fragment() {
    private var _binding: FragmentEmergencyBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var emergencyManager: EmergencyManager
    private var countDownTimer: CountDownTimer? = null
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var isEmergencyActive = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val deniedPermissions = permissions.filter { !it.value }.keys
        
        if (deniedPermissions.isEmpty()) {
            // All permissions granted, start the countdown
            startCountdown()
        } else {
            // Check if we should show rationale for any of the denied permissions
            val shouldShowRationale = deniedPermissions.any { permission ->
                shouldShowRequestPermissionRationale(permission)
            }
            
            if (shouldShowRationale) {
                // Show explanation why we need these permissions
                showPermissionRationale()
            } else {
                // User checked 'Don't ask again' or this is the first time
                if (deniedPermissions.any { it == Manifest.permission.ACCESS_BACKGROUND_LOCATION }) {
                    // Special handling for background location permission
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED) {
                        // If we have fine location but not background, we can still proceed
                        startCountdown()
                    } else {
                        showPermissionRationale()
                    }
                } else {
                    // For other permissions, show settings dialog
                    showPermissionSettingsDialog()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyBinding.inflate(inflater, container, false)
        emergencyManager = EmergencyManager(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Add a test button (temporary for debugging)
        binding.sosButton.setOnClickListener {
            testSendSMS()
        }

        binding.sosButton.setOnLongClickListener {
            if (isEmergencyActive) {
                stopEmergency()
            } else {
                if (checkPermissions()) {
                    startCountdown()
                } else {
                    requestPermissions()
                }
            }
            true
        }
    }

    private fun startCountdown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.sosButton.text = getString(R.string.cancel_sos, millisUntilFinished / 1000)
            }

            override fun onFinish() {
                triggerEmergency()
            }
        }.start()

        binding.sosButton.setBackgroundResource(R.drawable.bg_sos_button_active)
    }

    private fun triggerEmergency() {
        isEmergencyActive = true
        binding.sosButton.text = getString(R.string.sos_active)
        
        try {
            startSafetyService()
            
            // Get current location and send to emergency contacts
            getLastLocation { location ->
                currentLocation = location
                val lat = location?.latitude ?: 0.0
                val lon = location?.longitude ?: 0.0
                val message = "EMERGENCY! I need help! My location: https://maps.google.com/?q=$lat,$lon"

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val contacts = AppDatabase.get(requireContext()).contactDao().getAll().first()
                        val numbers = contacts.mapNotNull { it.phone.takeIf { p -> p.isNotBlank() } }
                        
                        if (numbers.isEmpty()) {
                            showError("No emergency contacts saved")
                            stopEmergency()
                            return@launch
                        }
                        
                        var sentCount = 0
                        numbers.forEach { number ->
                            val ok = sendEmergencySMS(number, message)
                            if (ok) sentCount++
                        }
                        
                        if (sentCount > 0) {
                            showError("SOS sent to $sentCount contact(s)")
                        } else {
                            showError("Failed to send SOS to any contacts")
                            stopEmergency()
                        }
                    } catch (e: Exception) {
                        Log.e("EmergencyFragment", "Error sending SOS", e)
                        showError("Failed to send SOS: ${e.message}")
                        stopEmergency()
                    }
                }
            }
            
        } catch (e: SecurityException) {
            Log.e("EmergencyFragment", "SecurityException: ${e.message}")
            showError("Missing required permissions. Please check app permissions in settings.")
            stopEmergency()
        } catch (e: Exception) {
            Log.e("EmergencyFragment", "Error triggering emergency", e)
            showError("Failed to send emergency alert. Please try again.")
            stopEmergency()
        }
    }

    private fun stopEmergency() {
        isEmergencyActive = false
        countDownTimer?.cancel()
        binding.sosButton.text = getString(R.string.hold_for_sos)
        binding.sosButton.setBackgroundResource(R.drawable.bg_sos_button)
        
        try {
            emergencyManager.stopEmergency()
            stopSafetyService()
        } catch (e: Exception) {
            Log.e("EmergencyFragment", "Error stopping emergency", e)
        }
    }
    
    private fun getLastLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                requireContext(), 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location -> callback(location) }
                .addOnFailureListener { 
                    Log.e("EmergencyFragment", "Failed to get location", it)
                    callback(null) 
                }
        } else {
            Log.d("EmergencyFragment", "Location permission not granted")
            callback(null)
        }
    }
    
    private fun sendEmergencySMS(phoneNumber: String, message: String): Boolean {
        return if (ContextCompat.checkSelfPermission(
                requireContext(), 
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                }
                Log.d("EmergencyFragment", "SMS sent to $phoneNumber")
                true
            } catch (e: Exception) {
                Log.e("EmergencyFragment", "Failed to send SMS to $phoneNumber", e)
                // Fallback to intent if direct SMS fails
                try {
                    val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phoneNumber")).apply {
                        putExtra("sms_body", message)
                    }
                    startActivity(smsIntent)
                    true
                } catch (e: Exception) {
                    Log.e("EmergencyFragment", "Failed to open SMS app", e)
                    false
                }
            }
        } else {
            Log.d("EmergencyFragment", "SMS permission not granted")
            false
        }
    }
    
    private fun showError(message: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Test function to verify SMS sending works independently
     */
    private fun testSendSMS() {
        try {
            val smsManager: SmsManager = SmsManager.getDefault()
            val testNumber = "1234567890" // Replace with a test number
            val testMessage = "Test message from Gamified App at ${System.currentTimeMillis()}"
            
            smsManager.sendTextMessage(
                testNumber,
                null,
                testMessage,
                null,
                null
            )
            
            showError("Test SMS sent to $testNumber")
            Log.d("EmergencyFragment", "Test SMS sent to $testNumber")
        } catch (e: SecurityException) {
            showError("SMS permission denied: ${e.message}")
            Log.e("EmergencyFragment", "SecurityException sending test SMS", e)
        } catch (e: Exception) {
            showError("Failed to send test SMS: ${e.message}")
            Log.e("EmergencyFragment", "Error sending test SMS", e)
        }
    }

    private fun checkPermissions(): Boolean {
        // Check SMS permission
        val smsGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!smsGranted) {
            Log.d("EmergencyFragment", "SMS permission not granted")
        }
        
        // Check fine location permission
        val locationGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!locationGranted) {
            Log.d("EmergencyFragment", "Location permission not granted")
        }
        
        // Background location is nice to have but not strictly required for basic functionality
        val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            // On Android 10 and above, we can still work with just fine location
            // but with reduced functionality
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // On Android 9 and below, we don't need to request background location separately
            true
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundLocationGranted) {
            Log.d("EmergencyFragment", "Background location permission not granted")
        }
        
        // We require at least SMS and fine location to be granted
        return smsGranted && locationGranted
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        // Always request SMS permission if not granted
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
            Log.d("EmergencyFragment", "Requesting SMS permission")
        }
        
        // Always request fine location permission if not granted
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            Log.d("EmergencyFragment", "Requesting fine location permission")
        }
        
        // Only request background location if we're on Android 10+ and don't have it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            Log.d("EmergencyFragment", "Requesting background location permission")
        }
        
        if (permissions.isNotEmpty()) {
            Log.d("EmergencyFragment", "Requesting permissions: $permissions")
            try {
                requestPermissionLauncher.launch(permissions.toTypedArray())
            } catch (e: Exception) {
                Log.e("EmergencyFragment", "Error requesting permissions", e)
                showError("Failed to request permissions. Please try again.")
            }
        } else {
            Log.d("EmergencyFragment", "All permissions already granted, starting countdown")
            // If we already have all permissions, start the countdown
            startCountdown()
        }
    }
    
    private fun showPermissionRationale() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permissions Required")
            .setMessage("""
                This app needs the following permissions to function properly:
                
                • SMS: To send emergency alerts to your contacts
                • Location: To share your location with emergency contacts
                • Background Location: To continue sharing your location even when the app is in the background
                
                Please grant these permissions to ensure the app works as intended.
            """.trimIndent())
            .setPositiveButton("Grant Permissions") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // If user cancels, we'll still try to proceed with whatever permissions we have
                if (checkPermissions()) {
                    startCountdown()
                } else {
                    showPermissionSettingsDialog()
                }
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showPermissionSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permissions Required")
            .setMessage("Some permissions are denied. Please enable them in app settings to use all features.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Continue Anyway") { _, _ ->
                // Try to proceed with whatever permissions we have
                if (checkPermissions()) {
                    startCountdown()
                }
            }
            .setCancelable(false)
            .show()
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }
    
    private fun startSafetyService() {
        val serviceIntent = Intent(requireContext(), SafetyService::class.java)
        ContextCompat.startForegroundService(requireContext(), serviceIntent)
    }
    
    private fun stopSafetyService() {
        val serviceIntent = Intent(requireContext(), SafetyService::class.java)
        requireContext().stopService(serviceIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        emergencyManager.cleanup()
        _binding = null
    }
}
