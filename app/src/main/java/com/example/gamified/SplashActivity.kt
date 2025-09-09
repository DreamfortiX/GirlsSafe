package com.example.gamified

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val fallbackDelayMs = 3500L // in case the animation fails to end
    private val minimumDurationMs = 2200L // ensure splash shows at least ~2.2s
    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false
    private var startTimeMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // One-time initialization to ensure first run starts logged out and not onboarded
        val initPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!initPrefs.getBoolean("initialized", false)) {
            initPrefs.edit {
                putBoolean("logged_in", false)
                    .putBoolean("onboarded", false)
                    .putBoolean("initialized", true)
            }
        }

        val splashIcon = findViewById<ImageView>(R.id.splashIcon)
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        pulse.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) { maybeNavigateAfterMinimum() }
            override fun onAnimationRepeat(animation: Animation) {}
        })
        splashIcon.startAnimation(pulse)

        startTimeMs = System.currentTimeMillis()

        // Fallback in case animation listener doesn't trigger (e.g., user background/resume)
        handler.postDelayed({
            if (!isFinishing) maybeNavigateAfterMinimum()
        }, fallbackDelayMs)
    }

    private fun maybeNavigateAfterMinimum() {
        if (hasNavigated) return
        val elapsed = System.currentTimeMillis() - startTimeMs
        val remaining = (minimumDurationMs - elapsed).coerceAtLeast(0L)
        handler.postDelayed({ navigateByAuthState() }, remaining)
    }

    private fun navigateByAuthState() {
        if (hasNavigated) return
        hasNavigated = true
        handler.removeCallbacksAndMessages(null)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val loggedIn = prefs.getBoolean("logged_in", false)
        val onboarded = prefs.getBoolean("onboarded", false)
        val target = when {
            loggedIn -> MainActivity::class.java
            onboarded -> LoginActivity::class.java
            else -> OnboardingActivity::class.java
        }
        startActivity(Intent(this, target))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}


