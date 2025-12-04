package com.example.gamified

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteModel(context: Context) {
    private val interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModelFile(context))
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        try {
            val fileDescriptor = context.assets.openFd("audio_danger_detection_improved.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.e("TFLiteModel", "Error loading model", e)
            throw RuntimeException("Error loading model", e)
        }
    }

    fun predict(features: Array<Array<Array<FloatArray>>>): FloatArray {
        try {
            val output = Array(1) { FloatArray(2) } // danger or safe
            interpreter.run(features, output)
            Log.d("TFLiteModel", "Prediction result: ${output[0].joinToString()}")
            return output[0]
        } catch (e: Exception) {
            Log.e("TFLiteModel", "Error during prediction", e)
            return floatArrayOf(0f, 0f)
        }
    }

    fun close() {
        interpreter.close()
    }
}
