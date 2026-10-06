package com.example.dopaminecut2.ui.home

import com.example.dopaminecut2.ui.common.ContentState
import com.example.dopaminecut2.ui.common.MeasurementPermissionState

data class PlatformUsageUi(
    val platformId: String,
    val displayName: String,
    val appTimeSec: Long,
    val shortformTimeSec: Long,
    val shortformCount: Long
)

data class CategoryUsageUi(
    val label: String,
    val count: Long,
    val durationSec: Long
)

enum class HomeChartMetric {
    TIME,
    COUNT;

    fun platformValue(usage: PlatformUsageUi): Float = when (this) {
        TIME -> usage.shortformTimeSec / 60f
        COUNT -> usage.shortformCount.toFloat()
    }

    fun categoryValue(usage: CategoryUsageUi): Float = when (this) {
        TIME -> usage.durationSec / 60f
        COUNT -> usage.count.toFloat()
    }
}

data class HomeDashboard(
    val totalAppTimeSec: Long,
    val totalShortformTimeSec: Long,
    val shortformCount: Long,
    val platforms: List<PlatformUsageUi>,
    val categories: List<CategoryUsageUi>,
    val categorySampleLimited: Boolean
)

data class HomeUiState(
    val goalProgress: List<HomeGoalProgress> = emptyList(),
    val goalsLoaded: Boolean = false,
    val goalsFailed: Boolean = false,
    val hasPausedGoals: Boolean = false,
    val validPreparationDays: Int? = null,
    val analysis: com.example.dopaminecut2.data.local.AnalysisSettings? = null,
    val measurement: MeasurementPermissionState = MeasurementPermissionState(false, false),
    val chartMetric: HomeChartMetric = HomeChartMetric.TIME,
    val content: ContentState<HomeDashboard> = ContentState.Loading,
    val sectionMessage: String? = null
)

sealed interface HomeAction {
    data object Refresh : HomeAction
    data class SelectChartMetric(val metric: HomeChartMetric) : HomeAction
}
