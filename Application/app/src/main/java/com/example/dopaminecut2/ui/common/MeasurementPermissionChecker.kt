package com.example.dopaminecut2.ui.common

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.provider.Settings
import com.example.dopaminecut2.service.AppBlockService

data class MeasurementPermissionState(
    val accessibilityGranted: Boolean,
    val usageAccessGranted: Boolean
)

class MeasurementPermissionChecker(private val context: Context) {
    fun currentState(): MeasurementPermissionState = MeasurementPermissionState(
        accessibilityGranted = isAccessibilityEnabled(),
        usageAccessGranted = hasUsageAccess()
    )

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(context, AppBlockService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
