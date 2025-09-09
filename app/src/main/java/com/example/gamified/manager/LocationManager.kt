package com.example.gamified.manager

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Manages location-related functionality for the app.
 * Handles requesting location updates and providing the last known location.
 */
class LocationManager(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private var locationCallback: LocationCallback? = null
    private var cancellationTokenSource: CancellationTokenSource? = null
    
    /**
     * Request location updates with the specified parameters.
     * @param callback The callback to receive location updates
     * @param minTimeMs Minimum time interval between location updates in milliseconds
     * @param minDistanceM Minimum distance between location updates in meters
     */
    @SuppressLint("MissingPermission") // Caller is responsible for checking permissions
    fun requestLocationUpdates(
        callback: LocationCallback,
        minTimeMs: Long = 30000, // 30 seconds
        minDistanceM: Float = 50f // 50 meters
    ) {
        // Cancel any existing updates
        removeLocationUpdates(callback)
        
        // Store the callback for later removal
        locationCallback = callback
        
        // Create a new cancellation token source
        cancellationTokenSource = CancellationTokenSource()
        
        // Create location request
        val locationRequest = LocationRequest.create().apply {
            interval = minTimeMs
            fastestInterval = minTimeMs / 2
            priority = Priority.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = minDistanceM
        }
        
        // Request location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )
        
        Log.d(TAG, "Requesting location updates with interval=${minTimeMs}ms, minDistance=${minDistanceM}m")
    }
    
    /**
     * Remove location updates for the current callback.
     */
    fun removeLocationUpdates(callback1: LocationCallback) {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
            Log.d(TAG, "Location updates removed")
        }
        
        // Cancel any pending location requests
        cancellationTokenSource?.cancel()
        cancellationTokenSource = null
    }
    
    /**
     * Get the last known location.
     * @param onSuccess Callback invoked with the last known location, or null if not available
     * @param onError Callback invoked if an error occurs
     */
    @SuppressLint("MissingPermission") // Caller is responsible for checking permissions
    fun getLastLocation(
        onSuccess: (Location?) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                Log.d(TAG, "Last known location: $location")
                onSuccess(location)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error getting last location", exception)
                onError(exception)
            }
    }
    
    /**
     * Check if location permissions are granted.
     */
    fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if location is enabled on the device.
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }
    
    companion object {
        private const val TAG = "LocationManager"
    }
}
