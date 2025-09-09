package com.example.gamified

import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.gamified.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.*

class OnboardingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var onboardingAdapter: OnboardingAdapter
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val preloadScope = CoroutineScope(Dispatchers.IO + Job())

    private val onboardingItems by lazy {
        listOf(
            OnboardingItem(
                R.drawable.onboard1,
                "Emergency Alert",
                "Instantly send an emergency alert to your trusted contacts in case of danger, ensuring quick help and support."
            ),
            OnboardingItem(
                R.drawable.onboard2,
                "Live Location",
                "Share your real-time location with friends or family, so they can track your safety during your travels or outings."
            ),
            OnboardingItem(
                R.drawable.onboard3,
                "Safety Tips",
                "Learn essential self-defense tips and guidelines to stay safe in various situations, whether at home or out in public."
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Preload images in background
        preloadImages()
        
        // Set up window
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Initialize ViewBinding
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Optimize ViewPager2
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.setPageTransformer { page, position ->
            val absPos = Math.abs(position)
            page.alpha = 1f - (absPos * 0.3f).coerceAtMost(1f)
        }

        // Initialize Adapter with Glide preloading
        onboardingAdapter = OnboardingAdapter(onboardingItems) { position ->
            binding.viewPager.currentItem = position
        }

        binding.viewPager.adapter = onboardingAdapter
        setupDotIndicator()

        // Optimize page change callback
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDotIndicator(position)
                updateButtons(position)
            }
        })

        // Set click listeners
        binding.btnSkip.setOnClickListener { completeOnboarding() }
        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem < onboardingItems.size - 1) {
                binding.viewPager.currentItem++
            } else {
                completeOnboarding()
            }
        }
    }

    private fun setupDotIndicator() {
        binding.dotIndicator.removeAllViews()
        val dots = Array(onboardingItems.size) { index ->
            ImageView(this).apply {
                setImageResource(R.drawable.dot_inactive)
                val size = resources.getDimensionPixelSize(R.dimen.dot_size)
                val margin = resources.getDimensionPixelSize(R.dimen.dot_margin)
                val params = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, 0, margin, 0)
                }
                layoutParams = params
            }
        }
        
        dots.forEach { dot -> binding.dotIndicator.addView(dot) }
        updateDotIndicator(0)
    }

    private fun updateDotIndicator(position: Int) {
        for (i in 0 until binding.dotIndicator.childCount) {
            val dot = binding.dotIndicator.getChildAt(i) as ImageView
            dot.setImageResource(
                if (i == position) R.drawable.dot_active else R.drawable.dot_inactive
            )
            dot.animate().scaleX(if (i == position) 1.5f else 1f).scaleY(if (i == position) 1.5f else 1f).setDuration(150).start()
        }
    }

    private fun updateButtons(position: Int) {
        val isLastPage = position == onboardingItems.size - 1
        binding.btnNext.text = if (isLastPage) getString(R.string.get_started) else getString(R.string.next)
        
        // Animate button change
        binding.btnNext.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(100)
            .withEndAction {
                binding.btnNext.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun preloadImages() {
        preloadScope.launch {
            val drawables = listOf(
                R.drawable.onboard1,
                R.drawable.onboard2,
                R.drawable.onboard3
            )
            
            drawables.forEach { drawableId ->
                Glide.with(this@OnboardingActivity)
                    .load(drawableId)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .preload()
            }
        }
    }
    
    private fun completeOnboarding() {
        coroutineScope.launch {
            // Save onboarding completion
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("onboarded", true)
                .apply()
            
            // Navigate with animation
            startActivity(Intent(this@OnboardingActivity, LoginActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        preloadScope.cancel()
    }
}
