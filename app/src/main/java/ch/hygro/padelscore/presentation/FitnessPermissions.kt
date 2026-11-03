package ch.hygro.padelscore.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Permissions the fitness tracking service needs on the running Android version: the heart-rate
 * permission moved from `BODY_SENSORS` to the Health permission on API 36.
 */
private fun requiredFitnessPermissions(): List<String> = listOf(
    if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS,
    Manifest.permission.ACTIVITY_RECOGNITION
)

/** Returns the fitness permissions that are not granted yet; empty if tracking can start. */
internal fun missingFitnessPermissions(context: Context): List<String> =
    requiredFitnessPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
