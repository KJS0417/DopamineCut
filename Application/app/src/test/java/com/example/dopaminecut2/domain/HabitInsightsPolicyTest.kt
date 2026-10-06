package com.example.dopaminecut2.domain

import com.example.dopaminecut2.data.model.*
import org.junit.Assert.*
import org.junit.Test

class HabitInsightsPolicyTest {
    private val apps = setOf(SupportedPlatform.YOUTUBE)
    private fun day(date: String, minutes: Long = 100) = DailyStatistics(date = date,
        appUsage = mapOf("youtube" to AppUsage(minutes * 60, minutes * 60, minutes)))
    private fun quality(date: String) = DayQuality(date, 100, 100, 0, true)
    @Test fun recommendationLevelsMatchApprovedPercentages() {
        val b = GoalBaseline(GoalMetric.DAILY_TIME, apps, mapOf(SupportedPlatform.YOUTUBE to 100.0), listOf("20261001"))
        assertEquals(85, b.target(ReductionLevel.REDUCE)); assertEquals(70, b.target(ReductionLevel.MORE))
        assertEquals(50, b.target(ReductionLevel.CHALLENGE))
    }
    @Test fun requiresThreeValidCompletedDates() {
        assertNull(HabitInsightsPolicy.baseline(listOf(day("20261001"), day("20261002")),
            listOf(quality("20261001"), quality("20261002")), "20261006", GoalMetric.DAILY_TIME, apps))
    }
    @Test fun excludesTodayAndPartialDays() {
        val days = listOf(day("20261001"), day("20261002"), day("20261003"), day("20261006", 999))
        val q = days.map { quality(it.date) }
        assertEquals(100.0, HabitInsightsPolicy.baseline(days, q, "20261006", GoalMetric.DAILY_TIME, apps)!!.average, 0.0)
        assertNull(HabitInsightsPolicy.baseline(days, q.map { if (it.date == "20261001") it.copy(complete = false) else it }, "20261006", GoalMetric.DAILY_TIME, apps))
    }
    @Test fun coverageAndUnknownHistoryAreConservative() {
        assertFalse(DayQuality("20261001", 100, null, 0, true).valid)
        assertFalse(DayQuality("20261001", 89, 100, 0, true).valid)
        assertFalse(DayQuality("20261001", 100, 100, 11, true).valid)
        assertTrue(DayQuality("20261001", 90, 100, 10, true).valid)
    }
    @Test fun latestSevenValidDatesOnly() {
        val days = (1..9).map { day("202610${it.toString().padStart(2, '0')}", it.toLong()) }
        val b = HabitInsightsPolicy.baseline(days, days.map { quality(it.date) }, "20261010", GoalMetric.DAILY_TIME, apps)!!
        assertEquals(7, b.dates.size); assertEquals("20261003", b.dates.first()); assertEquals(6.0, b.average, 0.0)
    }
    @Test fun missingDaysAreNotSynthesizedAsZero() {
        val days = listOf(day("20261001"), day("20261003"))
        assertNull(HabitInsightsPolicy.baseline(days, (1..3).map { quality("2026100$it") }, "20261006", GoalMetric.DAILY_TIME, apps))
    }
    @Test fun recordedZeroIsValidAndHasNoDivisionByZero() {
        val days = (1..3).map { day("2026100$it", 0) }
        val b = HabitInsightsPolicy.baseline(days, days.map { quality(it.date) }, "20261006", GoalMetric.DAILY_TIME, apps)!!
        assertEquals(0, b.target(ReductionLevel.CHALLENGE)); assertFalse(b.describe(0).contains("NaN"))
    }
    @Test fun datesWithGapsAreNotConsecutive() {
        assertTrue(HabitInsightsPolicy.consecutiveDates(listOf("20260930", "20261001", "20261002")))
        assertFalse(HabitInsightsPolicy.consecutiveDates(listOf("20261001", "20261003")))
    }
    @Test fun pausedDatesAndPartialStartDayAreNotGoalEvaluationDays() {
        val plan = com.example.dopaminecut2.data.local.GoalPlan(GoalMetric.DAILY_TIME, 1, "20261001", null,
            setOf("20261003"), "20261005")
        assertFalse(plan.eligible("20261001")); assertTrue(plan.eligible("20261002"))
        assertFalse(plan.eligible("20261003")); assertFalse(plan.eligible("20261005"))
        assertFalse(plan.eligible("20261006"))
    }
}
