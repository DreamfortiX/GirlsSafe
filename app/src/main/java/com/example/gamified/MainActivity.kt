package com.example.gamified

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.gamified.databinding.ActivityMainBinding
import com.example.gamified.service.SafetyService
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import com.example.gamified.viewmodels.AuthViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val authViewModel: AuthViewModel by viewModels()

    companion object {
        private const val PERMISSION_REQUEST_CODE_ESSENTIAL = 1001
        private const val PERMISSION_REQUEST_CODE_OPTIONAL = 1002
        
        @RequiresApi(Build.VERSION_CODES.O)
        fun getRequiredPermissions(): Array<String> {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.WAKE_LOCK
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
                permissions.add(Manifest.permission.READ_PHONE_STATE)
            }
            
            return permissions.toTypedArray()
        }
        
        private val ESSENTIAL_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Edge-to-edge and hide status bar
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            // Set up navigation
            setupNavigation()

            // Check and request permissions
            if (checkPermissions()) {
                // Delay service start to ensure UI is ready
                Handler(Looper.getMainLooper()).postDelayed({
                    startSafetyService()
                }, 500)
            } else {
                requestPermissions()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            // Try to recover by restarting the activity
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
                startActivity(intent)
            }, 1000)
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Set up bottom navigation
        val bottomNav = binding.bottomNavigation
        bottomNav.setupWithNavController(navController)

        // Set up action bar with nav controller
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.emergencyFragment,
                R.id.profileFragment
            )
        )

        // Collect authentication state
        lifecycleScope.launch {
            authViewModel.authenticationState.collect { authenticationState ->
                when (authenticationState) {
                    AuthViewModel.AuthenticationState.AUTHENTICATED -> {
                        // User is authenticated, show bottom navigation
                        bottomNav.visibility = android.view.View.VISIBLE
                    }
                    
                    AuthViewModel.AuthenticationState.AUTHENTICATING -> {
                        // Show loading state if needed
                        bottomNav.visibility = android.view.View.GONE
                    }

                    AuthViewModel.AuthenticationState.UNAUTHENTICATED -> {
                        // User is not authenticated
                        if (bottomNav.visibility != View.GONE) {
                            bottomNav.visibility = android.view.View.GONE
                            // Only navigate to login if we're not already on the login screen
                            if (this@MainActivity !is LoginActivity) {
                                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestPermissions() {
        requestEssentialPermissions()
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestEssentialPermissions() {
        val permissionsToRequest = ESSENTIAL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isEmpty()) {
            // Essential permissions already granted, request optional ones
            requestOptionalPermissions()
            return
        }
        
        // Check if we should show rationale
        val shouldShowRationale = permissionsToRequest.any {
            ActivityCompat.shouldShowRequestPermissionRationale(this, it)
        }
        
        if (shouldShowRationale) {
            showPermissionExplanationDialog(permissionsToRequest, PERMISSION_REQUEST_CODE_ESSENTIAL)
        } else {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest,
                PERMISSION_REQUEST_CODE_ESSENTIAL
            )
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestOptionalPermissions() {
        val optionalPermissions = getRequiredPermissions().filter {
            it !in ESSENTIAL_PERMISSIONS && 
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (optionalPermissions.isEmpty()) {
            // All permissions granted
            startSafetyService()
            return
        }
        
        ActivityCompat.requestPermissions(
            this,
            optionalPermissions,
            PERMISSION_REQUEST_CODE_OPTIONAL
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showPermissionExplanationDialog(permissions: Array<String>, requestCode: Int) {
        val message = buildString {
            append("This app needs the following permissions to function properly:\n\n")
            
            permissions.forEach { permission ->
                when (permission) {
                    Manifest.permission.ACCESS_FINE_LOCATION ->
                        append("• Location: To track your location in case of emergencies\n")
                    Manifest.permission.SEND_SMS ->
                        append("• SMS: To send emergency alerts to your contacts\n")
                    Manifest.permission.READ_CONTACTS ->
                        append("• Contacts: To access emergency contacts\n")
                    Manifest.permission.RECORD_AUDIO ->
                        append("• Microphone: For audio recording features\n")
                    Manifest.permission.READ_PHONE_STATE ->
                        append("• Phone: To detect phone calls\n")
                    Manifest.permission.ACTIVITY_RECOGNITION ->
                        append("• Activity: To detect if you're in a vehicle\n")
                }
            }
            
            append("\nPlease grant these permissions for the app to work properly.")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(message)
            .setPositiveButton("Continue") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    requestCode
                )
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                if (requestCode == PERMISSION_REQUEST_CODE_ESSENTIAL) {
                    // Check if we have minimum required permissions
                    val hasEssentialPermissions = ESSENTIAL_PERMISSIONS.all { permission ->
                        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
                    }
                    
                    if (!hasEssentialPermissions) {
                        Toast.makeText(
                            this,
                            "App may not function properly without essential permissions",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        requestOptionalPermissions()
                    }
                }
            }
            .setCancelable(false)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_REQUEST_CODE_ESSENTIAL -> handleEssentialPermissionResult(permissions, grantResults)
            PERMISSION_REQUEST_CODE_OPTIONAL -> handleOptionalPermissionResult()
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleEssentialPermissionResult(permissions: Array<out String>, grantResults: IntArray) {
        val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        
        if (allGranted) {
            // All essential permissions granted, request optional ones
            requestOptionalPermissions()
        } else {
            // Check if essential permissions are granted
            val hasEssentialPermissions = ESSENTIAL_PERMISSIONS.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
            
            if (hasEssentialPermissions) {
                // Essential permissions are granted, proceed with optional ones
                requestOptionalPermissions()
            } else {
                // Essential permissions missing, show rationale
                showPermissionRationale()
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleOptionalPermissionResult() {
        // Optional permissions result - we can proceed even if some were denied
        try {
            startSafetyService()
            
            // Check if any optional permissions were denied
            val optionalPermissions = getRequiredPermissions()
                .filter { it !in ESSENTIAL_PERMISSIONS }
                .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
                
            if (optionalPermissions.isNotEmpty()) {
                Log.d("MainActivity", "Some optional permissions were denied: $optionalPermissions")
                Toast.makeText(
                    this,
                    "App started with limited functionality. Some features may not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start safety service", e)
        }
    }
    
    private fun showPermissionRationale() {
        // You can implement a dialog here that explains why permissions are needed
        // and provides a button to open app settings or request permissions again
        // This is a better UX than automatically requesting permissions in a loop
        // Example:
        /*
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs certain permissions to function properly. Please grant the permissions in App Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
        */
    }

    private fun startSafetyService() {
        try {
            val serviceIntent = Intent(this, SafetyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start safety service", e)
            // Try to restart the service after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val serviceIntent = Intent(this, SafetyService::class.java)
                    startService(serviceIntent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to restart safety service", e)
                }
            }, 1000)
        }
    }
    // Removed duplicate permission request methods as they've been replaced with the new implementation

    override fun onBackPressed() {
        // If the current destination is the start destination, let the system handle the back press
        if (navController.currentDestination?.id == navController.graph.startDestinationId) {
            finish()
        } else {
            super.onBackPressed()
        }
    }
}