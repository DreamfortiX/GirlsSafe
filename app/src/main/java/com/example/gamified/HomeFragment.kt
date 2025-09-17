package com.example.gamified

// AndroidX
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.example.gamified.data.AppDatabase
import com.example.gamified.data.Contact

// Google Play Services
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
// Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

// Kotlin Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Local

// Java
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFilePath: String? = null
    private lateinit var storageRef: StorageReference

    private lateinit var tvWelcome: TextView
    private lateinit var sosCard: MaterialCardView
    private lateinit var cardFakeCall: MaterialCardView
    private lateinit var cardContacts: MaterialCardView
    private lateinit var btnRecordAudio: MaterialButton
    private lateinit var safetyToggle: com.google.android.material.switchmaterial.SwitchMaterial
    private var isLocationSharingEnabled = true

    companion object {
        private const val PERMISSION_REQUEST_CODE = 201
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        setupView(view)
        return view
    }

    private fun setupView(view: View) {
        // Bind views
        tvWelcome = view.findViewById(R.id.tvWelcome)
        sosCard = view.findViewById(R.id.sosCard)
        cardFakeCall = view.findViewById(R.id.cardFakeCall)
        cardContacts = view.findViewById(R.id.cardContacts)
        btnRecordAudio = view.findViewById(R.id.btnRecordAudio)
        safetyToggle = view.findViewById(R.id.safetyToggle)

        // Set up safety toggle listener
        safetyToggle.setOnCheckedChangeListener { _, isChecked ->
            isLocationSharingEnabled = isChecked
            // Update UI or perform any additional actions when toggle state changes
        }

        // Initialize Firebase Storage reference
        storageRef = FirebaseStorage.getInstance().reference.child("recordings")

        // Welcome text from Activity intent extras
        val username = activity?.intent?.getStringExtra("USER_NAME") ?: "User"
        tvWelcome.text = "Hello, $username!"

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        sosCard.setOnClickListener { triggerEmergencySOS() }
        cardFakeCall.setOnClickListener { simulateFakeCall() }
        cardContacts.setOnClickListener {
            // Using Navigation component to navigate to contacts
            findNavController().navigate(R.id.action_homeFragment_to_contactsFragment)
        }
        btnRecordAudio.setOnClickListener { toggleAudioRecording() }

        // Proactively ask for permissions (non-blocking UX)
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val toRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(Manifest.permission.SEND_SMS)
        }
        if (toRequest.isNotEmpty()) {
            requestPermissions(toRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if any of the requested permissions were denied
            val deniedPermissions = permissions.zip(grantResults.toTypedArray())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }
                
            if (deniedPermissions.isNotEmpty()) {
                // Only show toast if permissions were actually denied (not just requested)
                if (deniedPermissions.any { 
                    ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_DENIED 
                }) {
                    Toast.makeText(
                        requireContext(), 
                        "Please grant the required permissions for all features to work properly.", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun triggerEmergencySOS() {
        getLastLocation { location ->
            currentLocation = location
            var message = "EMERGENCY! I need help!"

            // Only include location if sharing is enabled
            if (isLocationSharingEnabled && location != null) {
                val lat = location.latitude
                val lon = location.longitude
                message += " My location: https://maps.google.com/?q=$lat,$lon"
            } else {
                message += " Location sharing is currently disabled."
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val contacts: List<Contact> = AppDatabase.get(requireContext()).contactDao().getAll().first()
                    val numbers = contacts.mapNotNull { it.phone.takeIf { p -> p.isNotBlank() } }
                    if (numbers.isEmpty()) {
                        Toast.makeText(requireContext(), "No emergency contacts saved", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    var sentCount = 0
                    numbers.forEach { number ->
                        val ok = sendEmergencySMS(number, message)
                        if (ok) sentCount++
                    }
                    Toast.makeText(requireContext(), "SOS sent to $sentCount contact(s)", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to send SOS: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getLastLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location -> callback(location) }
                .addOnFailureListener { callback(null) }
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
            callback(null)
        }
    }

    private fun sendEmergencySMS(phoneNumber: String, message: String): Boolean {
        return if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                }
                true
            } catch (e: Exception) {
                // Fallback to intent if direct SMS fails
                val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phoneNumber")).apply {
                    putExtra("sms_body", message)
                }
                startActivity(smsIntent)
                false
            }
        } else {
            // No permission: fall back to SMS app
            val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phoneNumber")).apply {
                putExtra("sms_body", message)
            }
            startActivity(smsIntent)
            false
        }
    }

    private fun simulateFakeCall() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get contacts from DB safely inside coroutine
                val contacts = withContext(Dispatchers.IO) {
                    AppDatabase.get(requireContext())
                        .contactDao()
                        .getAll()
                        .first() // Get the first emission from the Flow
                }

                val validContacts = contacts.filter { contact ->
                    contact.firstName.isNotBlank() && contact.phone.isNotBlank()
                }

                if (validContacts.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "No valid contacts found!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val selectedContact = validContacts.random()

                // Show preparing message
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Fake call incoming in 3 seconds!", Toast.LENGTH_LONG).show()
                }

                // Delay before showing fake call
                delay(3000)

                // Launch the fake call activity
                val intent = Intent(requireContext(), FakeCallActivity::class.java).apply {
                    putExtra("CALLER_NAME", selectedContact.firstName)
                    putExtra("CALLER_NUMBER", selectedContact.phone)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                startActivity(intent)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("FakeCall", "Error in simulateFakeCall", e)
            }
        }
    }

    private fun toggleAudioRecording() {
        if (!isRecording) {
            if (startRecording()) {
                isRecording = true
                btnRecordAudio.text = "Stop Recording"
                btnRecordAudio.isEnabled = false
                Handler(Looper.getMainLooper()).postDelayed({
                    btnRecordAudio.isEnabled = true
                }, 1000) // Prevent multiple rapid clicks
                Toast.makeText(requireContext(), "Recording started...", Toast.LENGTH_SHORT).show()
            }
        } else {
            stopRecording()
            isRecording = false
            btnRecordAudio.text = "Uploading..."
            btnRecordAudio.isEnabled = false
            
            // Upload the recorded file
            currentAudioFilePath?.let { filePath ->
                uploadAudioFile(File(filePath))
            } ?: run {
                btnRecordAudio.text = "Start Recording"
                btnRecordAudio.isEnabled = true
                Toast.makeText(requireContext(), "Error: No recording found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording(): Boolean {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
            Toast.makeText(requireContext(), "Microphone permission needed", Toast.LENGTH_SHORT).show()
            return false
        }
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            currentAudioFilePath = getOutputFile()
            setOutputFile(currentAudioFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            try {
                prepare()
                start()
                return true
            } catch (e: IOException) {
                Toast.makeText(requireContext(), "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) { }
        mediaRecorder = null
    }

    private fun getOutputFile(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        return "${storageDir?.absolutePath}/AUDIO_${timeStamp}.3gp"
    }
    
    private fun uploadAudioFile(audioFile: File) {
        if (!audioFile.exists()) {
            showUploadResult(false, "Audio file not found")
            return
        }

        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: run {
                showUploadResult(false, "User not authenticated")
                return
            }

        val fileName = "${userId}_${UUID.randomUUID()}.3gp"
        val audioRef = storageRef.child(fileName)
        val fileUri = Uri.fromFile(audioFile)

        val uploadTask = audioRef.putFile(fileUri)
        
        uploadTask.addOnSuccessListener {
            // Get the download URL
            audioRef.downloadUrl.addOnSuccessListener { uri ->
                val downloadUrl = uri.toString()
                // You can save this URL to Firestore if needed
                // saveAudioReferenceToFirestore(userId, downloadUrl, audioFile.name)
                showUploadResult(true, "Audio uploaded successfully")
                
                // Delete the local file after successful upload
                try {
                    if (audioFile.exists()) {
                        audioFile.delete()
                    }
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Error deleting local file", e)
                }
            }.addOnFailureListener { e ->
                Log.e("HomeFragment", "Error getting download URL", e)
                showUploadResult(false, "Upload failed: ${e.message}")
            }
        }.addOnFailureListener { e ->
            Log.e("HomeFragment", "Upload failed", e)
            showUploadResult(false, "Upload failed: ${e.message}")
        }.addOnProgressListener { taskSnapshot ->
            // Show upload progress if needed
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
            btnRecordAudio.text = "Uploading: ${progress.toInt()}%"
        }
    }
    
    private fun showUploadResult(success: Boolean, message: String) {
        activity?.runOnUiThread {
            btnRecordAudio.text = "Start Recording"
            btnRecordAudio.isEnabled = true
            
            val status = if (success) "Success" else "Error"
            val icon = if (success) android.R.drawable.ic_dialog_info else android.R.drawable.ic_dialog_alert
            
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            
            // Show a more prominent status message
            val statusView = layoutInflater.inflate(
                android.R.layout.simple_list_item_1,
                view?.findViewById(android.R.id.content),
                false
            ) as TextView
            statusView.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
            statusView.text = "$status: ${message.take(30)}..."
            statusView.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
            
            Toast(requireContext()).apply {
                duration = Toast.LENGTH_LONG
                view = statusView
                show()
            }
        }
    }
    
    private fun saveAudioReferenceToFirestore(userId: String, downloadUrl: String, fileName: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val audioData = hashMapOf(
            "userId" to userId,
            "url" to downloadUrl,
            "fileName" to fileName,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "size" to File(currentAudioFilePath).length()
        )
        
        db.collection("audio_recordings")
            .add(audioData)
            .addOnSuccessListener {
                Log.d("HomeFragment", "Audio reference saved to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Error saving audio reference", e)
            }
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording()
            isRecording = false
        }
    }
}
