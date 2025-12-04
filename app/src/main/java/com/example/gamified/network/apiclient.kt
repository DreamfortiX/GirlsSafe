package com.example.gamified.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class ApiClient(private val apiService: ApiService) {

    suspend fun uploadAudioFile(audioFile: File): Result<String> {
        return try {
            // Validate the file
            if (!audioFile.exists() || !audioFile.canRead()) {
                return Result.failure(IOException("File doesn't exist or cannot be read"))
            }

            // Check file size (WAV files can be larger)
            val fileSizeMB = audioFile.length() / (1024.0 * 1024.0)
            if (fileSizeMB > 5.0) {
                return Result.failure(IOException("File too large (${"%.2f".format(fileSizeMB)} MB)"))
            }

            // Create request body with correct MIME type for WAV audio
            val requestFile = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            
            // Log file details for debugging
            println("Uploading audio file: ${audioFile.name}")
            println("File size: ${audioFile.length()} bytes")
            println("File path: ${audioFile.absolutePath}")
            
            if (!audioFile.exists()) {
                println("ERROR: File does not exist at specified path")
                return Result.failure(IOException("File does not exist at specified path"))
            }

            // Create multipart part - "file" should match your Flask server expectation
            val body = MultipartBody.Part.createFormData(
                "file",
                audioFile.name,
                requestFile
            )

            // Make API call
            val response = apiService.uploadAudioFile(body)

            // Handle response
            if (response.isSuccessful) {
                val responseText = response.body()?.string() ?: "Upload successful"
                println("Upload successful. Server response: $responseText")
                Result.success(responseText)
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                val errorText = "HTTP ${response.code()}: $errorBody"
                println("Upload failed: $errorText")
                Result.failure(Exception(errorText))
            }

        } catch (e: IOException) {
            Result.failure(IOException("Network error: ${e.message}", e))
        } catch (e: Exception) {
            println("Exception during upload: ${e.message}")
            e.printStackTrace()
            Result.failure(Exception("Upload failed: ${e.message}", e))
        }
    }
}