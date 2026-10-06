package com.example.dopaminecut2.statistics

import com.example.dopaminecut2.data.model.AppUsage
import com.example.dopaminecut2.data.model.CategoryUsage
import com.example.dopaminecut2.data.model.DailyStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSummaryTest {
    @Test
    fun `combines days without adding shortform time to app time again`() {
        val summary = UsageSummary.from(listOf(
            DailyStatistics(
                date = "20261001",
                appUsage = mapOf("youtube" to AppUsage(600, 120, 3)),
                categoryUsage = mapOf("GAME" to CategoryUsage(2, 80)),
                hourlyShortformCount = mapOf("09" to 3)
            ),
            DailyStatistics(
                date = "20261002",
                appUsage = mapOf(
                    "youtube" to AppUsage(300, 60, 2),
                    "instagram" to AppUsage(200, 100, 4)
                ),
                categoryUsage = mapOf("GAME" to CategoryUsage(1, 20)),
                hourlyShortformCount = mapOf("09" to 2, "10" to 4, "24" to 999, "invalid" to 999)
            )
        ))

        assertEquals(1100L, summary.totalAppTimeSec)
        assertEquals(280L, summary.totalShortformTimeSec)
        assertEquals(9L, summary.totalShortformCount)
        assertEquals(AppUsage(900, 180, 5), summary.appUsage["youtube"])
        assertEquals(CategoryUsage(3, 100), summary.categoryUsage["GAME"])
        assertEquals(24, summary.hourlyShortformCount.size)
        assertEquals(5L, summary.hourlyShortformCount[9])
        assertEquals(4L, summary.hourlyShortformCount[10])
        assertEquals(9L, summary.hourlyShortformCount.sum())
    }

    @Test
    fun `empty range has zero totals and 24 empty hours`() {
        val summary = UsageSummary.from(emptyList())

        assertTrue(summary.appUsage.isEmpty())
        assertTrue(summary.categoryUsage.isEmpty())
        assertEquals(0L, summary.totalAppTimeSec)
        assertEquals(0L, summary.totalShortformTimeSec)
        assertEquals(0L, summary.totalShortformCount)
        assertEquals(List(24) { 0L }, summary.hourlyShortformCount)
    }

    @Test
    fun `large counts remain Long until presentation`() {
        val count = Int.MAX_VALUE.toLong()
        val summary = UsageSummary.from(List(2) {
            DailyStatistics(
                appUsage = mapOf("youtube" to AppUsage(shortformCount = count)),
                categoryUsage = mapOf("GAME" to CategoryUsage(count, 0)),
                hourlyShortformCount = mapOf("00" to count)
            )
        })

        assertEquals(count * 2, summary.totalShortformCount)
        assertEquals(count * 2, summary.categoryUsage["GAME"]?.count)
        assertEquals(count * 2, summary.hourlyShortformCount[0])
    }
}
