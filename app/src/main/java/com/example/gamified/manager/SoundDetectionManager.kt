package com.example.gamified.manager

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.gamified.ml.SoundClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundDetectionManager @Inject constructor(
    private val context: Context,
    private val soundClassifier: SoundClassifier
) {
    
    sealed class DetectionResult {
        data class DangerDetected(val confidence: Float) : DetectionResult()
        data class Error(val message: String) : DetectionResult()
        object Processing : DetectionResult()
    }

    private val _detectionResults = MutableStateFlow<DetectionResult?>(null)
    val detectionResults: StateFlow<DetectionResult?> = _detectionResults

    private var audioRecord: AudioRecord? = null
    private var isDetecting = false
    private var detectionJob: Job? = null
    private var sensitivity: Int = 50 // Default sensitivity (0-100)

    // Audio configuration
    private val sampleRate = 16000 // 16kHz
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    fun setSensitivity(sensitivity: Int) {
        this.sensitivity = sensitivity.coerceIn(0, 100)
    }

    fun startDetection(sensitivity: Int = this.sensitivity) {
        if (isDetecting) return
        
        setSensitivity(sensitivity)
        isDetecting = true
        
        detectionJob = CoroutineScope(Dispatchers.IO).launch {
            initializeAudioRecorder()
            startAudioProcessing()
        }
    }

    fun stopDetection() {
        isDetecting = false
        detectionJob?.cancel()
        releaseAudioRecorder()
    }

    fun pauseDetection() {
        isDetecting = false
        releaseAudioRecorder()
    }

    private fun initializeAudioRecorder() {
        try {
            // Check for required permissions
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    _detectionResults.value = DetectionResult.Error("Microphone permission not granted")
                    return
                }
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _detectionResults.value = DetectionResult.Error("Audio recorder initialization failed")
                releaseAudioRecorder()
                return
            }
        } catch (e: Exception) {
            _detectionResults.value = DetectionResult.Error("Audio recorder error: ${e.message}")
        }
    }

    private fun startAudioProcessing() {
        val audioBuffer = ShortArray(bufferSize / 2)
        audioRecord?.startRecording()

        while (isDetecting) {
            try {
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                
                if (bytesRead > 0) {
                    processAudioData(audioBuffer, bytesRead)
                }
                
                // Add small delay to prevent overwhelming the system
                Thread.sleep(100)
            } catch (e: Exception) {
                _detectionResults.value = DetectionResult.Error("Audio processing error: ${e.message}")
                break
            }
        }
    }

    private fun processAudioData(audioBuffer: ShortArray, bytesRead: Int) {
        _detectionResults.value = DetectionResult.Processing
        
        // Convert to float array for ML model
        val floatBuffer = FloatArray(bytesRead)
        for (i in 0 until bytesRead) {
            floatBuffer[i] = audioBuffer[i] / 32768.0f // Normalize to [-1, 1]
        }

        // Analyze with ML model
        val result = soundClassifier.classifySound(floatBuffer, sampleRate)
        
        // Check if danger is detected based on sensitivity threshold
        val threshold = sensitivity / 100.0f
        if (result.isDanger && result.confidence >= threshold) {
            _detectionResults.value = DetectionResult.DangerDetected(result.confidence)
        }
    }

    private fun releaseAudioRecorder() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e("SoundDetectionManager", "Error releasing audio recorder", e)
        }
    }
}
