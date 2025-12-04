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
import androidx.navigation.ui.setupWithNavController
import com.example.gamified.databinding.ActivityMainBinding
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
        
        // Show bottom nav by default
        bottomNav.visibility = View.VISIBLE

        // Set up action bar with nav controller
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.emergencyFragment,
                R.id.profileFragment
            )
        )

        // Add navigation listener to handle fragment transactions
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Show/hide bottom nav based on destination
            when (destination.id) {
                R.id.homeFragment, R.id.emergencyFragment, R.id.profileFragment -> {
                    bottomNav.visibility = View.VISIBLE
                }
                else -> {
                    bottomNav.visibility = View.GONE
                }
            }
        }

        // Collect authentication state
        lifecycleScope.launch {
            authViewModel.authenticationState.collect { authenticationState ->
                Log.d("AuthState", "Authentication state changed: $authenticationState")
                when (authenticationState) {
                    AuthViewModel.AuthenticationState.AUTHENTICATED -> {
                        // User is authenticated, ensure bottom navigation is visible
                        bottomNav.visibility = View.VISIBLE
                    }
                    
                    AuthViewModel.AuthenticationState.AUTHENTICATING -> {
                        // Keep bottom nav visible during authentication
                        bottomNav.visibility = View.VISIBLE
                    }

                    AuthViewModel.AuthenticationState.UNAUTHENTICATED -> {
                        // Only navigate to login if we're not already on the login screen
                        if (this@MainActivity !is LoginActivity) {
                            bottomNav.visibility = View.GONE
                            val intent = Intent(this@MainActivity, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
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
    private fun requestEssentialPermissions(permissionIndex: Int = 0) {
        val permissions = ESSENTIAL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissions.isEmpty() || permissionIndex >= permissions.size) {
            // All essential permissions processed, move to optional ones
            requestOptionalPermissions()
            return
        }
        
        val currentPermission = permissions[permissionIndex]
        
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, currentPermission)) {
            showPermissionExplanationDialog(arrayOf(currentPermission)) { 
                requestSinglePermission(currentPermission, permissionIndex)
            }
        } else {
            requestSinglePermission(currentPermission, permissionIndex)
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestOptionalPermissions(permissionIndex: Int = 0) {
        val optionalPermissions = getRequiredPermissions().filter {
            it !in ESSENTIAL_PERMISSIONS && 
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (optionalPermissions.isEmpty() || permissionIndex >= optionalPermissions.size) {
            // All permissions processed
            return
        }
        
        val currentPermission = optionalPermissions[permissionIndex]
        
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, currentPermission)) {
            showPermissionExplanationDialog(arrayOf(currentPermission), showOptional = true) { 
                requestSinglePermission(currentPermission, permissionIndex, isOptional = true)
            }
        } else {
            requestSinglePermission(currentPermission, permissionIndex, isOptional = true)
        }
    }

    private fun requestSinglePermission(permission: String, currentIndex: Int, isOptional: Boolean = false) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            if (isOptional) PERMISSION_REQUEST_CODE_OPTIONAL else PERMISSION_REQUEST_CODE_ESSENTIAL
        )
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun showPermissionExplanationDialog(
        permissions: Array<String>,
        requestCode: Int = 0,
        showOptional: Boolean = false,
        onContinue: () -> Unit = {}
    ) {
        val message = buildString {
            append("This app needs the following permission:\n\n")
            
            permissions.firstOrNull()?.let { permission ->
                when (permission) {
                    Manifest.permission.ACCESS_FINE_LOCATION ->
                        append("• Location: To track your location in case of emergencies")
                    Manifest.permission.SEND_SMS ->
                        append("• SMS: To send emergency alerts to your contacts")
                    Manifest.permission.READ_CONTACTS ->
                        append("• Contacts: To access emergency contacts")
                    Manifest.permission.RECORD_AUDIO ->
                        append("• Microphone: For audio recording features")
                    Manifest.permission.READ_PHONE_STATE ->
                        append("• Phone: To detect phone calls")
                    Manifest.permission.ACTIVITY_RECOGNITION ->
                        append("• Activity: To detect if you're in a vehicle")
                }
            }
            
            append("\n\nPlease grant this permission for the app to work properly.")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(message)
            .setPositiveButton("Continue") { _, _ ->
                onContinue()
            }
            .setNegativeButton("Skip") { dialog, _ ->
                dialog.dismiss()
                if (showOptional) {
                    requestOptionalPermissions()
                } else {
                    requestEssentialPermissions()
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
        
        // Convert Array<out String> to Array<String> for type safety
        val permissionsArray = permissions.map { it }.toTypedArray()
        
        when (requestCode) {
            PERMISSION_REQUEST_CODE_ESSENTIAL -> handleEssentialPermissionResult(permissionsArray, grantResults)
            PERMISSION_REQUEST_CODE_OPTIONAL -> handleOptionalPermissionResult(permissionsArray, grantResults)
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleEssentialPermissionResult(permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.isEmpty()) return
        
        val currentIndex = ESSENTIAL_PERMISSIONS.indexOfFirst { it == permissions[0] }
        if (currentIndex == -1) return
        
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, move to next one
            requestEssentialPermissions(currentIndex + 1)
        } else {
            // Permission denied, move to next one
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                // User denied, show rationale again
                showPermissionExplanationDialog(permissions) {
                    requestSinglePermission(permissions[0], currentIndex)
                }
            } else {
                // User checked 'Don't ask again', move to next permission
                requestEssentialPermissions(currentIndex + 1)
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleOptionalPermissionResult(permissions: Array<String> = arrayOf(), grantResults: IntArray = intArrayOf()) {
        if (grantResults.isEmpty() || permissions.isEmpty()) {
            requestOptionalPermissions()
            return
        }
        
        val currentIndex = getRequiredPermissions()
            .filter { it !in ESSENTIAL_PERMISSIONS }
            .indexOfFirst { it == permissions[0] }
            
        if (currentIndex != -1) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, move to next one
                requestOptionalPermissions(currentIndex + 1)
            } else {
                // Permission denied, check if we should show rationale
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                    // User denied, show rationale again
                    showPermissionExplanationDialog(permissions, showOptional = true) {
                        requestSinglePermission(permissions[0], currentIndex, isOptional = true)
                    }
                } else {
                    // User checked 'Don't ask again', move to next permission
                    requestOptionalPermissions(currentIndex + 1)
                }
            }
        } else {
            // If we couldn't find the current permission, just move to the next one
            requestOptionalPermissions()
        }
    }
    
    private fun showPermissionRationale(permission: String) {
        val message = when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION ->
                "Location permission is required to track your location in case of emergencies."
            Manifest.permission.SEND_SMS ->
                "SMS permission is required to send emergency alerts to your contacts."
            Manifest.permission.READ_CONTACTS ->
                "Contacts permission is required to access your emergency contacts."
            else -> "This permission is required for the app to function properly."
        }

        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
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