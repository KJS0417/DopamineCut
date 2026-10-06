package com.example.dopaminecut2.ui.goal

import com.example.dopaminecut2.ui.common.MeasurementPermissionState

data class GoalMeasurementPresentation(val primary: String, val detail: String, val missingPermissions: String) {
    val permissionRequired get() = missingPermissions.isNotEmpty()

    companion object {
        fun from(permissions: MeasurementPermissionState, average: String?, platforms: String): GoalMeasurementPresentation {
            val missing = buildList {
                if (!permissions.accessibilityGranted) add("접근성")
                if (!permissions.usageAccessGranted) add("사용정보 접근")
            }.joinToString(" · ")
            return if (missing.isNotEmpty()) GoalMeasurementPresentation(
                "권한 필요", average?.let { "기존 평균: $it" }.orEmpty(), missing
            ) else GoalMeasurementPresentation(average ?: "기록 수집 중", platforms, "")
        }
    }
}
