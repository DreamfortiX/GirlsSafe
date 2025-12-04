package com.example.gamified

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.gamified.data.AppDatabase
import com.example.gamified.data.Contact
import com.example.gamified.databinding.FragmentEmergencyBinding
import com.example.gamified.network.RetrofitClient
import com.example.gamified.utils.AudioRecorder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmergencyFragment : Fragment() {
    private var _binding: FragmentEmergencyBinding? = null
    private val binding get() = _binding!!
    private lateinit var audioRecorder: AudioRecorder
    private val RECORD_AUDIO_PERMISSION_CODE = 1001
    private val LOCATION_PERMISSION_CODE = 1002
    private val SMS_PERMISSION_CODE = 1003
    private var isRecording = false
    private val handler = Handler(Looper.getMainLooper())
    private var recordingTime = 0
    private val recordingDuration = 10 // 10 seconds
    private val apiClient = RetrofitClient.apiClient

    // Location services
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    // Settings and counters
    private var isLocationSharingEnabled = true
    private var dangerCount = 0
    private var safeCount = 0
    private lateinit var sharedPreferences: SharedPreferences

    private val recordingRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                recordingTime++
                updateRecordingUI()
                if (recordingTime < recordingDuration) {
                    handler.postDelayed(this, 1000)
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        audioRecorder = AudioRecorder(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences("emergency_prefs", 0)

        // Load settings and counters
        loadSettings()
        loadCounters()
        updateCounterUI()

        setupSosButton()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSosButton() {
        binding.sosButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Start recording when button is pressed
                    if (checkAudioPermission()) {
                        // Get location before starting recording
                        getLastLocation()
                        startRecording()
                    } else {
                        requestAudioPermission()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Stop recording when button is released
                    if (isRecording) {
                        stopRecording()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun loadSettings() {
        // Load location sharing preference from SharedPreferences or default
        isLocationSharingEnabled = sharedPreferences.getBoolean("location_sharing", true)
    }

    private fun loadCounters() {
        dangerCount = sharedPreferences.getInt("danger_count", 0)
        safeCount = sharedPreferences.getInt("safe_count", 0)
    }

    private fun saveCounters() {
        with(sharedPreferences.edit()) {
            putInt("danger_count", dangerCount)
            putInt("safe_count", safeCount)
            apply()
        }
    }

    private fun updateCounterUI() {
        binding.dangerCountText.text = dangerCount.toString()
        binding.safeCountText.text = safeCount.toString()
    }

    private fun incrementDangerCount() {
        dangerCount++
        saveCounters()
        updateCounterUI()
    }

    private fun incrementSafeCount() {
        safeCount++
        saveCounters()
        updateCounterUI()
    }

    private fun startRecording() {
        if (isRecording) return

        lifecycleScope.launch(Dispatchers.IO) @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO) {
            try {
                // Start recording and get the file where it will be saved
                val audioFile = audioRecorder.startRecording(recordingDuration)

                withContext(Dispatchers.Main) {
                    isRecording = true
                    recordingTime = 0
                    binding.audioStatusText.text = "Recording audio..."
                    binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_safe)

                    // Start UI timer
                    handler.post(recordingRunnable)

                    // Schedule auto-stop after recording duration
                    handler.postDelayed({
                        if (isRecording) {
                            stopRecording()
                        }
                    }, recordingDuration * 1000L)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleRecordingError(e)
                }
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Stop the recorder and get the recorded file
                val audioFile = audioRecorder.stopRecording()

                withContext(Dispatchers.Main) {
                    isRecording = false
                    handler.removeCallbacks(recordingRunnable)

                    if (audioFile != null && audioFile.exists()) {
                        // Display file info
                        val fileSizeKB = audioFile.length() / 1024
                        binding.audioStatusText.text = "WAV file created (${fileSizeKB} KB)"
                        binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_sos_button)

                        // Log file details
                        Log.d("EmergencyFragment", "WAV file: ${audioFile.absolutePath}")
                        Log.d("EmergencyFragment", "File size: ${audioFile.length()} bytes")

                        uploadAudioFile(audioFile)
                    } else {
                        binding.audioStatusText.text = "No audio recorded"
                        binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_danger)
                        resetUI()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.audioStatusText.text = "Error stopping recorder"
                    binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_danger)
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    resetUI()
                }
            }
        }
    }

    private fun handleRecordingError(e: Exception) {
        when (e) {
            is SecurityException -> {
                binding.audioStatusText.text = "Permission denied"
                binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_danger)
                Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_LONG).show()
            }
            is IllegalStateException -> {
                binding.audioStatusText.text = "Recorder error"
                binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_danger)
                Toast.makeText(context, "Recorder error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            is IllegalArgumentException -> {
                binding.audioStatusText.text = "Invalid audio parameters"
                binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_danger)
                Toast.makeText(context, "Audio setup error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            else -> {
                binding.audioStatusText.text = "Recording failed"
                binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_danger)
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        resetUI()
    }

    private fun uploadAudioFile(audioFile: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = apiClient.uploadAudioFile(audioFile)
                result.fold(
                    onSuccess = { responseText ->
                        withContext(Dispatchers.Main) {
                            try {
                                // Parse the JSON response
                                val jsonResponse = JSONObject(responseText)
                                val analysis = jsonResponse.getJSONObject("analysis")

                                // Extract values from the response
                                val isDanger = analysis.getInt("prediction") == 0
                                val confidence = analysis.getDouble("confidence") * 100
                                val classLabel = analysis.getString("class_label")

                                // Update status text and indicator
                                binding.audioStatusText.text = classLabel

                                // Update confidence display
                                binding.confidenceText.text = "AI Confidence: ${"%.1f".format(confidence)}%"
                                binding.confidenceProgress.progress = confidence.toInt()

                                // Update UI based on danger status
                                if (isDanger) {
                                    // Increment danger counter
                                    incrementDangerCount()

                                    // Show danger indicator
                                    binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_danger)
                                    binding.dangerIndicatorLayout.visibility = View.VISIBLE
                                    binding.dangerStatusText.text = "Danger detected! Sending SOS..."

                                    // Visual feedback
                                    binding.audioStatusLayout.setBackgroundResource(R.drawable.bg_stat_danger)

                                    // Send emergency messages to contacts
                                    triggerEmergencyMessages()
                                } else {
                                    // Increment safe counter
                                    incrementSafeCount()

                                    // Show safe status
                                    binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_safe)
                                    binding.dangerIndicatorLayout.visibility = View.GONE
                                    binding.audioStatusLayout.setBackgroundResource(R.drawable.bg_stat_safe)
                                    binding.dangerStatusText.text = "Safe - No danger detected"
                                }

                                // Update last update time
                                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(
                                    Date()
                                )
                                binding.lastUpdateText.text = "Last update: $time"

                                // Log for debugging
                                Log.d("AudioAnalysis", """
                                Class: $classLabel
                                Confidence: $confidence%
                                Is Danger: $isDanger
                            """.trimIndent())

                            } catch (e: JSONException) {
                                // Fallback for non-JSON response
                                binding.audioStatusText.text = "Analysis complete"
                                binding.confidenceText.text = "AI Confidence: N/A"
                                Log.e("AudioAnalysis", "Failed to parse JSON: $responseText", e)
                            }
                        }
                    },
                    onFailure = { exception ->
                        withContext(Dispatchers.Main) {
                            binding.audioStatusText.text = "Upload failed"
                            binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_danger)
                            binding.confidenceText.text = "AI Confidence: Error"
                            binding.dangerStatusText.text = "Error: ${exception.message}"
                            binding.dangerIndicatorLayout.visibility = View.VISIBLE
                            Log.e("EmergencyFragment", "Upload failed", exception)
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.audioStatusText.text = "Error: ${e.message}"
                    binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_danger)
                    binding.confidenceText.text = "AI Confidence: Error"
                    binding.dangerIndicatorLayout.visibility = View.VISIBLE
                    Log.e("EmergencyFragment", "Error in upload", e)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    // Clean up after delay
                    handler.postDelayed({
                        resetUI()
                    }, 10000) // Keep results visible for 10 seconds
                }
            }
        }
    }

    private fun triggerEmergencyMessages() {
        lifecycleScope.launch {
            try {
                // Get emergency message
                val message = buildEmergencyMessage()

                // Get contacts from database
                val contacts: List<Contact> = withContext(Dispatchers.IO) {
                    AppDatabase.get(requireContext()).contactDao().getAll().first()
                }

                val numbers = contacts.mapNotNull { it.phone.takeIf { p -> p.isNotBlank() } }
                if (numbers.isEmpty()) {
                    binding.dangerStatusText.text = "Danger detected! No emergency contacts saved"
                    Toast.makeText(requireContext(), "No emergency contacts saved", Toast.LENGTH_LONG).show()
                    return@launch
                }

                Log.d("EmergencyFragment", "Sending messages to ${numbers.size} contacts")

                // Check SMS permission first
                if (!checkSmsPermission()) {
                    binding.dangerStatusText.text = "Danger detected! Need SMS permission"
                    requestSmsPermission()
                    return@launch
                }

                // Send messages
                var sentCount = 0
                numbers.forEach { number ->
                    Log.d("EmergencyFragment", "Attempting to send to: $number")
                    val ok = sendEmergencySMS(number, message)
                    if (ok) {
                        sentCount++
                        Log.d("EmergencyFragment", "Successfully sent to: $number")
                    } else {
                        Log.e("EmergencyFragment", "Failed to send to: $number")
                    }
                }

                // Update UI
                binding.dangerStatusText.text = "Danger detected! SOS sent to $sentCount contact(s)"
                Toast.makeText(requireContext(), "SOS sent to $sentCount contact(s)", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                binding.dangerStatusText.text = "Danger detected! Failed to send SOS: ${e.message}"
                Toast.makeText(requireContext(), "Failed to send SOS: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("EmergencyFragment", "Failed to send emergency messages", e)
            }
        }
    }

    private fun buildEmergencyMessage(): String {
        var message = "🚨 EMERGENCY ALERT 🚨\n"
        message += "\nDanger detected by AI audio analysis!"
        message += "\nTimestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}"
        message += "\n\n⚠️ Immediate attention required!"

        // Only include location if sharing is enabled
        if (isLocationSharingEnabled && currentLocation != null) {
            val lat = currentLocation!!.latitude
            val lon = currentLocation!!.longitude
            message += "\n\n📍 Location:"
            message += "\nGoogle Maps: https://maps.google.com/?q=$lat,$lon"
            message += "\nCoordinates: $lat, $lon"
        } else {
            message += "\n\n📍 Location sharing is currently disabled."
        }

        message += "\n\n📱 This is an automated emergency alert from the Gamified Safety App."
        message += "\n\nPlease check on me immediately!"
        return message
    }

    private fun getLastLocation() {
        lifecycleScope.launch {
            try {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED) {

                    val location = withContext(Dispatchers.IO) {
                        kotlin.runCatching {
                            fusedLocationClient.lastLocation.await()
                        }.getOrNull()
                    }
                    currentLocation = location

                    if (location == null) {
                        Log.d("EmergencyFragment", "Location unavailable")
                    } else {
                        Log.d("EmergencyFragment", "Location obtained: ${location.latitude}, ${location.longitude}")
                    }
                } else {
                    // Request location permission
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION_PERMISSION_CODE
                    )
                    currentLocation = null
                }
            } catch (e: Exception) {
                Log.e("EmergencyFragment", "Error getting location", e)
                currentLocation = null
            }
        }
    }

    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.SEND_SMS),
            SMS_PERMISSION_CODE
        )
    }

    private fun sendEmergencySMS(phoneNumber: String, message: String): Boolean {
        return try {
            if (checkSmsPermission()) {
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                }
                Log.d("EmergencyFragment", "SMS sent via SmsManager to: $phoneNumber")
                true
            } else {
                // Fallback to SMS app
                val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phoneNumber")).apply {
                    putExtra("sms_body", message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(smsIntent)
                Log.d("EmergencyFragment", "Opened SMS app for: $phoneNumber")
                false
            }
        } catch (e: SecurityException) {
            Log.e("EmergencyFragment", "SMS permission denied", e)
            // Try opening SMS app as fallback
            try {
                val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phoneNumber")).apply {
                    putExtra("sms_body", message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(smsIntent)
                false
            } catch (ex: Exception) {
                Log.e("EmergencyFragment", "Failed to open SMS app", ex)
                false
            }
        } catch (e: Exception) {
            Log.e("EmergencyFragment", "Failed to send SMS", e)
            false
        }
    }

    private fun updateRecordingUI() {
        val timeLeft = recordingDuration - recordingTime
        binding.audioStatusText.text = "Recording WAV... $timeLeft seconds left"

        // Visual feedback - pulsing effect for recording
        if (recordingTime % 2 == 0) {
            binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_safe)
        } else {
            binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_sos_button)
        }
    }

    private fun resetUI() {
        binding.audioStatusText.text = "Ready to record (WAV format)"
        binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_status_indicator_off)
        binding.dangerIndicatorLayout.visibility = View.GONE
        binding.audioStatusLayout.setBackgroundResource(R.drawable.bg_status_indicator_off)
        isRecording = false
        recordingTime = 0
        handler.removeCallbacks(recordingRunnable)
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RECORD_AUDIO_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, get location and start recording
                    getLastLocation()
                    startRecording()
                } else {
                    binding.audioStatusText.text = "Permission denied"
                    binding.audioStatusIndicator.setBackgroundResource(R.drawable.bg_stat_danger)
                    Toast.makeText(context, "Audio recording permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Location permission granted, get location
                    getLastLocation()
                }
            }
            SMS_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "SMS permission granted", Toast.LENGTH_SHORT).show()
                    // If danger was detected and we were waiting for permission, resend messages
                    if (binding.dangerStatusText.text.toString().contains("Need SMS permission")) {
                        triggerEmergencyMessages()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(recordingRunnable)
        audioRecorder.cleanup()
        _binding = null
    }
}