package com.example.gamified.utils

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private var fileOutputStream: FileOutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Audio recording parameters for WAV format
    companion object {
        private const val SAMPLE_RATE = 44100 // 44.1 kHz - CD quality
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BIT_DEPTH = 16 // 16-bit audio
        private const val BYTES_PER_SAMPLE = 2 // 16-bit = 2 bytes
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Throws(IOException::class, IllegalStateException::class)
    private fun prepareAudioRecord(): AudioRecord {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalArgumentException("Invalid audio parameters")
        }

        if (minBufferSize == AudioRecord.ERROR) {
            throw IllegalStateException("Could not get minimum buffer size")
        }

        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize * 2 // Use double buffer for safety
        )
    }

    @Throws(IOException::class)
    private fun createWavFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context.filesDir // Fallback to internal storage

        return File.createTempFile(
            "EMERGENCY_AUDIO_${timeStamp}_",  // Prefix
            ".wav",
            storageDir
        ).apply {
            outputFile = this
        }
    }

    private fun writeWavHeader(
        file: File,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
        dataSize: Long
    ) {
        val totalDataLen = dataSize + 36 // 36 bytes for WAV header (minus RIFF and WAVE)
        val byteRate = sampleRate * channels * bitDepth / 8
        val blockAlign = channels * bitDepth / 8

        try {
            FileOutputStream(file, true).use { fos ->
                val header = ByteBuffer.allocate(44)
                header.order(ByteOrder.LITTLE_ENDIAN)

                // RIFF header
                header.put("RIFF".toByteArray())
                header.putInt((totalDataLen).toInt())
                header.put("WAVE".toByteArray())

                // fmt chunk
                header.put("fmt ".toByteArray())
                header.putInt(16) // Subchunk1Size (16 for PCM)
                header.putShort(1.toShort()) // AudioFormat (1 = PCM)
                header.putShort(channels.toShort()) // NumChannels
                header.putInt(sampleRate) // SampleRate
                header.putInt(byteRate) // ByteRate
                header.putShort(blockAlign.toShort()) // BlockAlign
                header.putShort(bitDepth.toShort()) // BitsPerSample

                // data chunk
                header.put("data".toByteArray())
                header.putInt(dataSize.toInt()) // Subchunk2Size

                fos.write(header.array())
            }
        } catch (e: Exception) {
            throw IOException("Failed to write WAV header: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Throws(IOException::class, IllegalStateException::class)
    suspend fun startRecording(durationSeconds: Int = 10): File {
        // Stop any existing recording
        stopRecording()

        // Create the WAV file
        val audioFile = createWavFile()

        // Initialize AudioRecord
        audioRecord = prepareAudioRecord()

        return try {
            fileOutputStream = FileOutputStream(audioFile)

            // First write empty WAV header (will be updated later)
            writeWavHeader(audioFile, SAMPLE_RATE, 1, BIT_DEPTH, 0L)

            // Start recording
            audioRecord?.startRecording()
            isRecording = true

            // Start recording in background
            recordingJob = scope.launch {
                recordAudioToFile(durationSeconds)
            }

            audioFile
        } catch (e: Exception) {
            stopRecording()
            throw e
        }
    }

    private suspend fun recordAudioToFile(durationSeconds: Int) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val buffer = ByteArray(bufferSize)
        var totalBytesRead: Long = 0

        try {
            // Record for the specified duration
            val startTime = System.currentTimeMillis()

            while (isRecording && System.currentTimeMillis() - startTime < durationSeconds * 1000L) {
                val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0

                if (bytesRead > 0) {
                    fileOutputStream?.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                }

                // Small delay to prevent CPU overuse
                delay(10)
            }

            // Update WAV header with actual data size
            if (totalBytesRead > 0) {
                updateWavHeader(totalBytesRead)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            withContext(Dispatchers.IO) {
                stopRecording()
            }
        }
    }

    private fun updateWavHeader(dataSize: Long) {
        outputFile?.let { file ->
            try {
                // Re-write the WAV header with correct data size
                writeWavHeader(file, SAMPLE_RATE, 1, BIT_DEPTH, dataSize)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecording(): File? {
        recordingJob?.cancel()
        recordingJob = null

        return try {
            if (isRecording) {
                audioRecord?.apply {
                    try {
                        stop()
                    } catch (e: IllegalStateException) {
                        // Already stopped, ignore
                    }
                    release()
                }

                fileOutputStream?.apply {
                    flush()
                    close()
                }
            }

            isRecording = false
            audioRecord = null
            fileOutputStream = null

            // Return the file if it exists and has content
            outputFile?.takeIf { it.exists() && it.length() > 44 } // At least header size

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun forceStop(): File? {
        return stopRecording()
    }

    fun isRecording(): Boolean {
        return isRecording
    }

    fun getOutputFile(): File? {
        return outputFile?.takeIf { it.exists() && it.length() > 44 }
    }

    fun cleanup() {
        stopRecording()
        outputFile?.delete()
        outputFile = null
    }
}