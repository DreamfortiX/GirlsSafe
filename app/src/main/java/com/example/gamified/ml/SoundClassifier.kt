package com.example.gamified.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundClassifier @Inject constructor(context: Context) {
    
    data class ClassificationResult(
        val isDanger: Boolean,
        val confidence: Float,
        val className: String
    )

    private lateinit var interpreter: Interpreter
    private val inputBuffer: ByteBuffer
    private val outputBuffer: FloatArray
    
    // Model configuration
    private val inputSize = 16000 // 1 second of audio at 16kHz
    private val numClasses = 2 // danger vs non-danger

    init {
        // Load your TensorFlow Lite model
        val model = loadModelFile(context, "sound_classifier.tflite")
        interpreter = Interpreter(model)
        
        inputBuffer = ByteBuffer.allocateDirect(inputSize * 4) // 4 bytes per float
        inputBuffer.order(ByteOrder.nativeOrder())
        
        outputBuffer = FloatArray(numClasses)
    }

    fun classifySound(audioData: FloatArray, sampleRate: Int): ClassificationResult {
        // Preprocess audio data (normalize, feature extraction, etc.)
        val processedData = preprocessAudio(audioData, sampleRate)
        
        // Run inference
        interpreter.run(processedData, outputBuffer)
        
        // Interpret results
        val dangerConfidence = outputBuffer[1] // Assuming index 1 is danger class
        val isDanger = dangerConfidence > 0.5f
        
        return ClassificationResult(
            isDanger = isDanger,
            confidence = dangerConfidence,
            className = if (isDanger) "danger" else "normal"
        )
    }

    private fun preprocessAudio(audioData: FloatArray, sampleRate: Int): ByteBuffer {
        // Implement audio preprocessing (MFCC, spectrogram, etc.)
        // This is a simplified example - you'll need to implement proper feature extraction
        
        inputBuffer.rewind()
        for (i in audioData.indices) {
            inputBuffer.putFloat(audioData[i])
        }
        return inputBuffer
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val assetManager = context.assets
        val assetFileDescriptor = assetManager.openFd(modelPath)
        val inputStream = assetFileDescriptor.createInputStream()
        val modelData = inputStream.readBytes()
        
        return ByteBuffer.allocateDirect(modelData.size).apply {
            order(ByteOrder.nativeOrder())
            put(modelData)
            rewind()
        }
    }
}
