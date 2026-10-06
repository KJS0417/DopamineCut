package com.example.dopaminecut2.ui.home

import com.example.dopaminecut2.data.model.AppUsage
import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.domain.*
import org.junit.Assert.*
import org.junit.Test

class HomeGoalProgressTest {
    private fun goal(metric: GoalMetric, target: Int = 10) = ManagedGoal(metric, target,
        setOf(SupportedPlatform.YOUTUBE), InterventionMode.NOTIFY)
    private val stats = DailyStatistics(appUsage = mapOf(
        "youtube" to AppUsage(runTimeSec = 900, shortformTimeSec = 120, shortformCount = 4),
        "instagram" to AppUsage(runTimeSec = 5000, shortformTimeSec = 3000, shortformCount = 100)))

    @Test fun eachMetricUsesOnlySelectedPlatformsAndCorrectUnit() {
        val rows = HomeGoalProgressMapper.map(GoalMetric.entries.map { goal(it) }, stats)
        assertEquals(listOf(120L, 4L, 900L), rows.map { it.used })
        assertEquals(listOf(600L, 10L, 600L), rows.map { it.limit })
        assertEquals(200, rows[0].progress)
        assertEquals(400, rows[1].progress)
        assertEquals(1000, rows[2].progress)
        assertTrue(rows[2].exceeded)
    }
    @Test fun missingStatisticsStayUnknownNotZero() {
        val row = HomeGoalProgressMapper.map(listOf(goal(GoalMetric.DAILY_TIME)), null).single()
        assertNull(row.used)
        assertNull(row.progress)
        assertFalse(row.exceeded)
    }
    @Test fun pausedGoalsAreNotShownAsActiveProgress() {
        val rows = HomeGoalProgressMapper.map(listOf(goal(GoalMetric.APP_TIME).copy(status = GoalStatus.PAUSED)), stats)
        assertTrue(rows.isEmpty())
    }
    @Test fun zeroLimitDoesNotDivideByZeroOrMeanUnlimited() {
        val row = HomeGoalProgressMapper.map(listOf(goal(GoalMetric.DAILY_COUNT, 0)), stats).single()
        assertTrue(row.exceeded)
        assertEquals(1000, row.progress)
        assertEquals(0, HomeGoalProgressMapper.map(listOf(goal(GoalMetric.DAILY_TIME, 0)), DailyStatistics()).single().progress)
    }
    @Test fun liveAndPhotoTimeWithZeroCountsRemainTimeOnly() {
        val live = DailyStatistics(appUsage = mapOf("youtube" to AppUsage(120, 120, 0)))
        val rows = HomeGoalProgressMapper.map(listOf(goal(GoalMetric.DAILY_TIME), goal(GoalMetric.DAILY_COUNT)), live)
        assertEquals(120L, rows[0].used)
        assertEquals(0L, rows[1].used)
    }
}
