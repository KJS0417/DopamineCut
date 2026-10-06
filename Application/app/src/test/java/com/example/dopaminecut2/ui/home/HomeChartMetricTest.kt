package com.example.dopaminecut2.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeChartMetricTest {
    private val platform = PlatformUsageUi(
        platformId = "youtube",
        displayName = "YouTube",
        appTimeSec = 600L,
        shortformTimeSec = 90L,
        shortformCount = 7L
    )
    private val category = CategoryUsageUi(
        label = "게임",
        count = 4L,
        durationSec = 150L
    )

    @Test
    fun `time metric uses shortform minutes for both charts`() {
        assertEquals(1.5f, HomeChartMetric.TIME.platformValue(platform), 0.001f)
        assertEquals(2.5f, HomeChartMetric.TIME.categoryValue(category), 0.001f)
    }

    @Test
    fun `count metric uses view counts for both charts`() {
        assertEquals(7f, HomeChartMetric.COUNT.platformValue(platform), 0.001f)
        assertEquals(4f, HomeChartMetric.COUNT.categoryValue(category), 0.001f)
    }
}
