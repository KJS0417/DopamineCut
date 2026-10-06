package com.example.dopaminecut2.ui.stats

import com.example.dopaminecut2.data.model.*
import com.example.dopaminecut2.domain.SupportedPlatform
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatsAnalysisTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val end = LocalDate.of(2026, 10, 3)
    private fun day(date: String, time: Long, count: Long = 1) = DailyStatistics(date,
        appUsage = mapOf("youtube" to AppUsage(time * 2, time, count)))

    @Test fun missingDatesRemainUnknownAndTodayIsExcludedFromAverage() {
        val value = StatsAnalysis.dashboard(listOf(day("20261001", 120), day("20261003", 600)), start, end, "20261003", null)
        assertNull(value.days[1].statistics)
        assertEquals(2, value.recordedDays)
        assertEquals(1, value.averageDays)
        assertEquals(120.0, value.averageShortformTimeSec!!, 0.001)
        assertEquals(720L, value.totalShortformTimeSec)
    }
    @Test fun explicitZeroRecordIsDifferentFromMissingRecord() {
        val value = StatsAnalysis.dashboard(listOf(day("20261001", 0)), start, end, "20261003", null)
        assertNotNull(value.days[0].statistics)
        assertEquals(0.0, value.averageShortformTimeSec!!, 0.001)
        assertNull(value.days[1].statistics)
    }
    @Test fun noCompletedRecordsHasNoAverage() {
        val value = StatsAnalysis.dashboard(listOf(day("20261003", 600)), start, end, "20261003", null)
        assertNull(value.averageShortformTimeSec)
        assertEquals(0, value.averageDays)
    }
    @Test fun selectedAppDoesNotInventPerAppCategoriesOrHours() {
        val raw = day("20261001", 120).copy(appUsage = mapOf("youtube" to AppUsage(240, 120, 2), "instagram" to AppUsage(900, 600, 20)),
            categoryUsage = mapOf("GAME" to CategoryUsage(22, 720)), hourlyShortformCount = mapOf("12" to 22))
        val value = StatsAnalysis.dashboard(listOf(raw), start, end, "20261003", SupportedPlatform.YOUTUBE)
        assertEquals(120L, value.totalShortformTimeSec)
        assertEquals(2L, value.totalShortformCount)
        assertTrue(value.categories.isEmpty())
        assertTrue(value.hourlyCounts.all { it == 0L })
    }
    @Test fun precedingPeriodUsesItsOwnRecordedDayDenominator() {
        val value = StatsAnalysis.dashboard(listOf(day("20260929", 300), day("20261001", 120)), start, end, "20261003", null)
        assertEquals(300.0, value.previousTimeAverage!!, 0.001)
        assertEquals(1, value.previousAverageDays)
        assertEquals(120L, value.totalShortformTimeSec)
    }
    @Test fun countsRemainLongAndLiveTimeDoesNotCreateVideoCount() {
        val value = StatsAnalysis.dashboard(listOf(day("20261001", 600, 0)), start, end, "20261003", null)
        assertEquals(0L, value.totalShortformCount)
        assertEquals(600L, value.totalShortformTimeSec)
    }
    @Test fun rangesRejectFutureReversedAndOverNinetyDays() {
        assertFalse(StatsAnalysis.validRange(end, start, end))
        assertFalse(StatsAnalysis.validRange(start, end.plusDays(1), end))
        assertTrue(StatsAnalysis.validRange(end.minusDays(89), end, end))
        assertFalse(StatsAnalysis.validRange(end.minusDays(90), end, end))
    }
    @Test fun unknownAndUnclassifiedTimeAreNotHidden() {
        val raw = day("20261001", 120, 2).copy(categoryUsage = mapOf("UNKNOWN" to CategoryUsage(1, 20)))
        val value = StatsAnalysis.dashboard(listOf(raw), start, end, "20261003", null)
        assertTrue(value.categorySampleLimited)
        assertEquals("미확인", value.categories.single().label)
    }
}
