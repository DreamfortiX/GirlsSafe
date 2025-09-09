package com.example.gamified.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.gamified.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Utility class for handling runtime permissions in the app.
 */
object PermissionUtils {
    // Permissions required for AI features
    val AI_REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    // Basic permissions needed for the app to function
    val BASIC_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.SEND_SMS
    )

    // Permission request codes
    const val PERMISSION_REQUEST_CODE = 1001
    const val LOCATION_PERMISSION_REQUEST_CODE = 1002
    const val AUDIO_PERMISSION_REQUEST_CODE = 1003
    const val STORAGE_PERMISSION_REQUEST_CODE = 1004

    // Check if all required permissions are granted
    fun hasRequiredPermissions(context: Context, permissions: Array<String> = AI_REQUIRED_PERMISSIONS): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Check if a specific permission is granted
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    // Request missing permissions
    fun requestMissingPermissions(
        activity: Activity,
        permissions: Array<String> = AI_REQUIRED_PERMISSIONS,
        requestCode: Int = PERMISSION_REQUEST_CODE
    ) {
        val missingPermissions = permissions.filter {
            !isPermissionGranted(activity, it)
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions,
                requestCode
            )
        }
    }

    // Request missing permissions from a Fragment
    fun requestMissingPermissions(
        fragment: Fragment,
        permissions: Array<String> = AI_REQUIRED_PERMISSIONS,
        requestCode: Int = PERMISSION_REQUEST_CODE
    ) {
        val missingPermissions = permissions.filter {
            !isPermissionGranted(fragment.requireContext(), it)
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            fragment.requestPermissions(
                missingPermissions,
                requestCode
            )
        }
    }

    // Check if all permissions are granted from the request result
    fun areAllPermissionsGranted(grantResults: IntArray): Boolean {
        return grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
    }

    // Check if we should show permission rationale
    fun shouldShowRequestPermissionRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    // Check if we should show permission rationale from a Fragment
    fun shouldShowRequestPermissionRationale(fragment: Fragment, permission: String): Boolean {
        return fragment.shouldShowRequestPermissionRationale(permission)
    }

    // Check if the user has permanently denied any of the requested permissions
    fun hasPermanentlyDeniedPermission(
        activity: Activity,
        permissions: Array<String>
    ): Boolean {
        return permissions.any { permission ->
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
                    !isPermissionGranted(activity, permission)
        }
    }

    // Open app settings so the user can grant permissions manually
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // Show a dialog explaining why the permission is needed
    fun showPermissionRationaleDialog(
        context: Context,
        message: String,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.permission_required)
            .setMessage(message)
            .setPositiveButton(R.string.grant) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                onCancel?.invoke()
                dialog.dismiss()
            }
            .setOnCancelListener { onCancel?.invoke() }
            .show()
    }

    // Show a dialog when the user has permanently denied permissions
    fun showPermissionDeniedDialog(
        context: Context,
        message: String,
        onSettingsClick: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.permission_denied)
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ -> onSettingsClick() }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                onCancel?.invoke()
                dialog.dismiss()
            }
            .setOnCancelListener { onCancel?.invoke() }
            .show()
    }

    // Check if we need to show the rationale for any of the requested permissions
    fun shouldShowAnyRationale(activity: Activity, permissions: Array<String>): Boolean {
        return permissions.any { shouldShowRequestPermissionRationale(activity, it) }
    }

    // Check if we should show the rationale for any of the requested permissions from a Fragment
    fun shouldShowAnyRationale(fragment: Fragment, permissions: Array<String>): Boolean {
        return permissions.any { shouldShowRequestPermissionRationale(fragment, it) }
    }
}