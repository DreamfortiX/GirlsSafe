package com.example.gamified

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View.OnClickListener
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class FakeCallActivity : AppCompatActivity(), GestureDetector.OnGestureListener {
    companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
    
    private lateinit var gestureDetector: GestureDetector
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var vibrator: Vibrator
    private var isCallActive = false

    private lateinit var callTimer: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make it appear over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        
        setContentView(R.layout.activity_fake_call)
        enableEdgeToEdge()
        
        // Initialize gesture detector
        gestureDetector = GestureDetector(this, this)

        // Get contact info
        val callerName = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
        val callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: ""

        // Update UI
        findViewById<TextView>(R.id.caller_name).text = callerName
        findViewById<TextView>(R.id.caller_number).text = callerNumber
        callTimer = findViewById(R.id.call_timer)

        // Get button references
        val answerButton = findViewById<ImageButton>(R.id.answer_button)
        val declineButton = findViewById<ImageButton>(R.id.decline_button)
        val endCallButton = findViewById<ImageButton>(R.id.end_call_button)
        
        // Set up click and swipe listeners for answer button
        answerButton.setOnClickListener { answerCall() }
        answerButton.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            v.performClick()
            true
        }
        
        // Set up click and swipe listeners for decline button
        declineButton.setOnClickListener { endCall() }
        declineButton.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            v.performClick()
            true
        }
        
        // Set up click listener for end call button
        endCallButton.setOnClickListener { endCall() }

        // Start ringing
        startRinging()

        // Auto-answer after 10 seconds if not answered
        handler.postDelayed({
            if (!isFinishing && !isCallActive) {
                answerCall()
            }
        }, 10000)
    }

    private fun startRinging() {
        try {
            // Play ringtone
            mediaPlayer = MediaPlayer.create(this, Settings.System.DEFAULT_RINGTONE_URI).apply {
                isLooping = true
                setVolume(1.0f, 1.0f)
                start()
            }

            // Vibrate if available
            val vibratorService = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibratorService?.hasVibrator() == true) {
                vibrator = vibratorService
                val pattern = longArrayOf(0, 1000, 1000) // wait 0, vibrate 1s, sleep 1s
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error starting call: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val updateTimer = object : Runnable {
        override fun run() {
            if (isCallActive) {
                val millis = System.currentTimeMillis() - startTime
                val seconds = millis / 1000
                val minutes = seconds / 60
                callTimer.text = String.format("%02d:%02d", minutes, seconds % 60)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun answerCall() {
        if (isCallActive) return
        isCallActive = true
        
        try {
            // Stop ringing and vibration
            mediaPlayer.stop()
            mediaPlayer.release()
            
            if (::vibrator.isInitialized) {
                vibrator.cancel()
            }
            
            // Update UI for active call
            val incomingControls = findViewById<View>(R.id.incoming_controls)
            val activeControls = findViewById<View>(R.id.active_call_controls)
            val callStatus = findViewById<TextView>(R.id.call_status)
            
            // Hide incoming call controls and show active call controls
            incomingControls.visibility = View.GONE
            activeControls.visibility = View.VISIBLE
            
            // Update call status
            callStatus.text = "In call"
            
            // Show and start timer
            callTimer.visibility = View.VISIBLE
            startTime = System.currentTimeMillis()
            handler.post(updateTimer)

            // Auto-end after 30 seconds
            handler.postDelayed({
                if (!isFinishing) {
                    endCall()
                }
            }, 30000)
            
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    private fun endCall() {
        isCallActive = false
        try {
            if (::mediaPlayer.isInitialized) {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            }
            
            if (::vibrator.isInitialized) {
                vibrator.cancel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            finish()
        }
    }

    // Gesture detector methods
    override fun onDown(e: MotionEvent): Boolean = true

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean = true

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean = true

    override fun onLongPress(e: MotionEvent) {}

    private var startY: Float = 0f
    
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 == null || e2 == null) return false
        
        try {
            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x
            
            // Only process vertical swipes
            if (Math.abs(diffX) > Math.abs(diffY)) {
                return false
            }
            
            // Check if it's a valid vertical swipe
            if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                // Check if the touch started on a button
                val answerButton = findViewById<ImageButton>(R.id.answer_button)
                val declineButton = findViewById<ImageButton>(R.id.decline_button)
                
                val answerRect = android.graphics.Rect()
                val declineRect = android.graphics.Rect()
                answerButton.getHitRect(answerRect)
                declineButton.getHitRect(declineRect)
                
                // Check if the touch started within the button bounds
                if (answerRect.contains(e1.x.toInt(), e1.y.toInt())) {
                    if (diffY < 0) { // Swipe up on answer button
                        if (!isCallActive) {
                            answerCall()
                            return true
                        }
                    }
                } else if (declineRect.contains(e1.x.toInt(), e1.y.toInt())) {
                    if (diffY > 0) { // Swipe down on decline button
                        if (!isCallActive) {
                            endCall()
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTimer)
        try {
            if (::mediaPlayer.isInitialized) {
                mediaPlayer.release()
            }
            if (::vibrator.isInitialized) {
                vibrator.cancel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}