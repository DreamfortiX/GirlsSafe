package com.example.gamified

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.*

class AudioClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var isInitialized = false
    private var audioRecorder: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    companion object {
        private const val MODEL_FILE = "audio_danger_detection_improved.tflite"
        const val SAMPLE_RATE = 22050
        const val DURATION = 4.0f
        const val NUM_SAMPLES = (SAMPLE_RATE * DURATION).toInt()
        private const val N_MFCC = 13
        private const val N_FFT = 2048
        private const val HOP_LENGTH = 512
        private const val MIN_BUFFER_SIZE = 8192
        
        // Mel filter bank parameters
        private const val MEL_FILTERS_START = 20.0f
        private const val MEL_FILTERS_END = 8000.0f
        private const val MEL_FILTERS_NUM = 40
        
        // MFCC normalization parameters (26 values: 13 means + 13 stds)
        // These should be replaced with actual values from your training data
        val MFCC_MEANS = floatArrayOf(
            // MFCC means (13 values)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            // MFCC standard deviations (13 values)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f
        )
        
        // For convenience, split into separate arrays for means and stds
        val MFCC_MEAN = MFCC_MEANS.copyOfRange(0, N_MFCC)
        val MFCC_STD = MFCC_MEANS.copyOfRange(N_MFCC, N_MFCC * 2)
        
        private val TAG = AudioClassifier::class.java.simpleName
    }

    fun initialize(): Boolean {
        return try {
            val model = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseNNAPI(true)
            }

            interpreter = Interpreter(model, options)
            isInitialized = true
            Log.d("AudioClassifier", "Model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e("AudioClassifier", "Error initializing model", e)
            isInitialized = false
            false
        }
    }

    fun classifyAudio(audioData: FloatArray): Pair<String, Float> {
        if (!isInitialized || interpreter == null) {
            Log.e(TAG, "Classifier not initialized")
            return Pair("NOT_INITIALIZED", 0f)
        }

        return try {
            // 1. Preprocess audio (trim/pad to 4 seconds, apply pre-emphasis, check silence)
            val processedAudio = processAudio(audioData)
            
            // Check if the audio is silent after processing
            if (isSilent(processedAudio)) {
                Log.w(TAG, "Audio signal is too quiet after processing")
                return Pair("SILENT", 0f)
            }

            // 2. Extract MFCC features
            val mfccs = extractMFCC(processedAudio)

            // 3. Calculate mean and std across time
            val mfccsMean = FloatArray(N_MFCC) { 0f }
            val mfccsStd = FloatArray(N_MFCC) { 0f }
            
            // Calculate mean
            for (i in 0 until N_MFCC) {
                var sum = 0.0
                for (j in mfccs[i].indices) {
                    sum += mfccs[i][j]
                }
                mfccsMean[i] = (sum / mfccs[i].size).toFloat()
            }
            
            // Calculate standard deviation
            for (i in 0 until N_MFCC) {
                var sumSq = 0.0
                for (j in mfccs[i].indices) {
                    val diff = mfccs[i][j] - mfccsMean[i]
                    sumSq += (diff * diff)
                }
                mfccsStd[i] = sqrt(sumSq / mfccs[i].size).toFloat()
            }

            // 4. Normalize features (z-score normalization)
            val normalizedFeatures = FloatArray(N_MFCC * 2) { 0f }
            for (i in 0 until N_MFCC) {
                // Normalize mean and std with pre-calculated statistics
                normalizedFeatures[i] = (mfccsMean[i] - MFCC_MEAN[i]) / MFCC_STD[i]
                normalizedFeatures[i + N_MFCC] = (mfccsStd[i] - MFCC_MEAN[i]) / MFCC_STD[i]
            }

            // 5. Run inference
            val input = Array(1) { normalizedFeatures }
            val output = Array(1) { FloatArray(2) }
            interpreter?.run(input, output)

            // 6. Apply softmax to get probabilities
            val (dangerScore, safeScore) = output[0]
            val maxScore = maxOf(dangerScore, safeScore)
            val expDanger = exp(dangerScore - maxScore)
            val expSafe = exp(safeScore - maxScore)
            val sumExp = expDanger + expSafe
            val dangerProb = (expDanger / sumExp).toFloat()
            val safeProb = (expSafe / sumExp).toFloat()

            val confidence = maxOf(dangerProb, safeProb)
            val prediction = if (dangerProb > safeProb) "DANGER" else "SAFE"

            Log.d(TAG, "Raw scores - DANGER: $dangerScore, SAFE: $safeScore")
            Log.d(TAG, "Probabilities - DANGER: $dangerProb, SAFE: $safeProb")
            Log.d(TAG, "Prediction: $prediction (Confidence: $confidence)")
            
            Pair(prediction, confidence)
        } catch (e: Exception) {
            Log.e(TAG, "Classification error", e)
            Pair("ERROR", 0f)
        }
    }
    
    private fun processAudio(audio: FloatArray): FloatArray {
        // Apply pre-emphasis filter
        val emphasizedAudio = applyPreEmphasis(audio)
        
        // Check if audio is silent or too quiet
        if (isSilent(emphasizedAudio)) {
            Log.w(TAG, "Warning: Audio signal is too quiet or silent")
            // Return a zero array of the correct size to avoid crashes
            return FloatArray(NUM_SAMPLES) { 0f }
        }
        
        return if (emphasizedAudio.size > NUM_SAMPLES) {
            // Take the middle segment
            val start = (emphasizedAudio.size - NUM_SAMPLES) / 2
            emphasizedAudio.copyOfRange(start, start + NUM_SAMPLES)
        } else if (emphasizedAudio.size < NUM_SAMPLES) {
            // Pad with zeros
            FloatArray(NUM_SAMPLES).also {
                System.arraycopy(emphasizedAudio, 0, it, 0, emphasizedAudio.size)
            }
        } else {
            emphasizedAudio
        }
    }
    
    /**
     * Applies a pre-emphasis filter to the audio signal to amplify high frequencies
     * @param signal The input audio signal
     * @param coefficient The pre-emphasis coefficient (default: 0.97)
     * @return The pre-emphasized audio signal
     */
    private fun applyPreEmphasis(signal: FloatArray, coefficient: Float = 0.97f): FloatArray {
        val result = FloatArray(signal.size)
        result[0] = signal[0]
        for (i in 1 until signal.size) {
            result[i] = signal[i] - coefficient * signal[i - 1]
        }
        return result
    }
    
    /**
     * Checks if the audio signal is silent or too quiet
     * @param audio The audio signal to check
     * @param threshold The energy threshold below which the audio is considered silent (default: 0.02)
     * @return true if the audio is silent or too quiet, false otherwise
     */
    private fun isSilent(audio: FloatArray, threshold: Float = 0.02f): Boolean {
        if (audio.isEmpty()) return true
        
        // Calculate RMS (Root Mean Square) energy
        var sumSquared = 0.0
        for (sample in audio) {
            sumSquared += sample * sample
        }
        val rms = sqrt(sumSquared / audio.size).toFloat()
        
        Log.d(TAG, "Audio RMS energy: $rms (threshold: $threshold)")
        return rms < threshold
    }

    private fun extractMFCC(audio: FloatArray): Array<FloatArray> {
        // 1. Pre-emphasis
        val emphasized = FloatArray(audio.size)
        val preEmphasis = 0.97f
        emphasized[0] = audio[0]
        for (i in 1 until audio.size) {
            emphasized[i] = audio[i] - preEmphasis * audio[i - 1]
        }

        // 2. Framing
        val frameSize = N_FFT
        val hopSize = HOP_LENGTH
        val nFrames = 1 + (emphasized.size - frameSize) / hopSize
        
        // 3. Hamming window
        val window = hammingWindow(frameSize)
        
        // 4. FFT and Power Spectrum
        val fftSize = frameSize
        val powerSpectrumFrames = Array(nFrames) { FloatArray(fftSize / 2 + 1) }
        
        for (i in 0 until nFrames) {
            val frame = FloatArray(frameSize)
            val start = i * hopSize
            val end = (start + frameSize).coerceAtMost(emphasized.size)
            
            // Apply window function
            for (j in start until end) {
                frame[j - start] = emphasized[j] * window[j - start]
            }
            
            // Calculate power spectrum
            val fft = fft(frame, fftSize)
            for (k in 0 until fftSize / 2 + 1) {
                val re = fft[2 * k]
                val im = fft[2 * k + 1]
                powerSpectrumFrames[i][k] = re * re + im * im
            }
        }
        
        // 5. Mel Filter Bank
        val melFilterBank = createMelFilterBank(
            numFilters = MEL_FILTERS_NUM,
            fftSize = fftSize,
            sampleRate = SAMPLE_RATE,
            lowFreq = MEL_FILTERS_START,
            highFreq = MEL_FILTERS_END
        )
        
        // 6. Apply Mel filters and take log
        val filterBankEnergies = Array(nFrames) { FloatArray(MEL_FILTERS_NUM) }
        for (i in 0 until nFrames) {
            for (j in 0 until MEL_FILTERS_NUM) {
                var sum = 0.0f
                for (k in 0 until fftSize / 2 + 1) {
                    sum += powerSpectrumFrames[i][k] * melFilterBank[j][k]
                }
                // Apply log to avoid negative values
                filterBankEnergies[i][j] = ln(maxOf(sum, 1e-10f).toDouble()).toFloat()
            }
        }
        
        // 7. DCT to get MFCCs
        val mfccs = Array(N_MFCC) { FloatArray(nFrames) }
        for (i in 0 until nFrames) {
            for (j in 0 until N_MFCC) {
                var sum = 0.0
                for (k in 0 until MEL_FILTERS_NUM) {
                    val angle = Math.PI * j * (k + 0.5) / MEL_FILTERS_NUM
                    sum += filterBankEnergies[i][k] * cos(angle)
                }
                mfccs[j][i] = (sum * sqrt(2.0 / MEL_FILTERS_NUM)).toFloat()
            }
        }
        
        return mfccs
    }

    private fun loadModelFile(): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Helper functions for MFCC calculation
    private fun hammingWindow(size: Int): FloatArray {
        return FloatArray(size) { i ->
            (0.54f - 0.46f * cos(2.0f * Math.PI.toFloat() * i / (size - 1)))
        }
    }
    
    private fun createMelFilterBank(
        numFilters: Int,
        fftSize: Int,
        sampleRate: Int,
        lowFreq: Float,
        highFreq: Float
    ): Array<FloatArray> {
        val filterBank = Array(numFilters) { FloatArray(fftSize / 2 + 1) }
        val lowMel = hzToMel(lowFreq)
        val highMel = hzToMel(highFreq)
        
        val melPoints = FloatArray(numFilters + 2) { i ->
            lowMel + i * (highMel - lowMel) / (numFilters + 1)
        }
        
        val bin = FloatArray(numFilters + 2) { i ->
            (fftSize + 1) * melToHz(melPoints[i]) / sampleRate
        }
        
        for (i in 0 until numFilters) {
            val left = bin[i].toInt()
            val center = bin[i + 1].toInt()
            val right = bin[i + 2].toInt()
            
            for (j in left until center) {
                filterBank[i][j] = (j - left).toFloat() / (center - left)
            }
            
            for (j in center until right) {
                filterBank[i][j] = (right - j).toFloat() / (right - center)
            }
        }
        
        return filterBank
    }
    
    private fun hzToMel(hz: Float): Float {
        return 2595f * log10(1f + hz / 700f)
    }
    
    private fun melToHz(mel: Float): Float {
        return 700f * (10f.pow(mel / 2595f) - 1f)
    }
    
    // Simple FFT implementation (Cooley-Tukey algorithm)
    private fun fft(input: FloatArray, fftSize: Int): FloatArray {
        val n = fftSize
        val output = FloatArray(2 * n)
        
        // Bit-reversal permutation
        val bits = (log2(n.toFloat())).toInt()
        for (i in 0 until n) {
            val j = Integer.reverse(i) ushr (32 - bits)
            output[2 * j] = if (i < input.size) input[i] else 0f
            output[2 * j + 1] = 0f
        }
        
        // Cooley-Tukey FFT
        for (s in 1..bits) {
            val m = 1 shl s
            val m2 = m / 2
            val wmr = cos(-Math.PI / m2).toFloat()
            val wmi = sin(-Math.PI / m2).toFloat()
            
            for (k in 0 until n step m) {
                var wr = 1f
                var wi = 0f
                
                for (j in 0 until m2) {
                    val index1 = 2 * (k + j + m2)
                    val index2 = 2 * (k + j)
                    
                    val tr = wr * output[index1] - wi * output[index1 + 1]
                    val ti = wr * output[index1 + 1] + wi * output[index1]
                    
                    output[index1] = output[index2] - tr
                    output[index1 + 1] = output[index2 + 1] - ti
                    
                    output[index2] += tr
                    output[index2 + 1] += ti
                    
                    val wmrt = wr * wmr - wi * wmi
                    val wmit = wr * wmi + wi * wmr
                    wr = wmrt
                    wi = wmit
                }
            }
        }
        
        return output
    }
    
    private fun log2(x: Float): Float {
        return (ln(x.toDouble()) / ln(2.0)).toFloat()
    }
    
    fun startRecording(callback: (FloatArray) -> Unit) {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return
        }
        
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(MIN_BUFFER_SIZE)
        
        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        
        audioRecorder?.startRecording()
        isRecording = true
        
        recordingThread = Thread {
            val buffer = ShortArray(bufferSize)
            val audioBuffer = FloatArray(NUM_SAMPLES)
            var offset = 0
            
            while (isRecording) {
                val read = audioRecorder?.read(buffer, 0, bufferSize) ?: 0
                
                if (read > 0) {
                    // Convert to float and normalize to [-1, 1]
                    for (i in 0 until read) {
                        if (offset + i < NUM_SAMPLES) {
                            audioBuffer[offset + i] = buffer[i] / 32768.0f
                        }
                    }
                    
                    offset += read
                    
                    // If we have enough samples, process and reset
                    if (offset >= NUM_SAMPLES) {
                        callback(audioBuffer.copyOf())
                        offset = 0
                    }
                }
            }
        }
        
        recordingThread?.start()
    }
    
    fun stopRecording() {
        isRecording = false
        recordingThread?.join()
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null
    }
    
    fun close() {
        stopRecording()
        interpreter?.close()
        isInitialized = false
    }
}