package com.example.dopaminecut2.ui.stats

import com.example.dopaminecut2.data.local.GoalPlan
import com.example.dopaminecut2.data.model.*
import com.example.dopaminecut2.domain.*
import org.junit.Assert.*
import org.junit.Test

class StatsGoalAnalysisTest {
    private val goal = ManagedGoal(GoalMetric.DAILY_TIME, 5, setOf(SupportedPlatform.YOUTUBE), InterventionMode.NOTIFY, revision = 2)
    private val baseline = GoalBaseline(goal.metric, goal.platforms, mapOf(SupportedPlatform.YOUTUBE to 10.0), listOf("20260929", "20260930"))
    private val plan = GoalPlan(goal.metric, 2, "20261001", baseline)
    private fun day(date: String, seconds: Long) = DailyStatistics(date, appUsage = mapOf("youtube" to AppUsage(1000, seconds, 0)))
    private fun quality(date: String, complete: Boolean = true) = DayQuality(date, 100, 100, 0, complete)

    @Test fun evaluatesOnlyMatchingGoalEligibleValidCompletedDates() {
        val dates = listOf("20261001", "20261002", "20261003", "20261004")
        val value = StatsGoalAnalysis.compare(goal, plan.copy(excludedDates = setOf("20261003")),
            dates.map { day(it, 180) }, dates.map { quality(it) }, "20261004")
        assertEquals(1, value.evaluated)
        assertEquals(1, value.achieved)
        assertEquals(3.0, value.average!!, 0.01)
    }
    @Test fun changedGoalDoesNotApplyCurrentLimitToOldHistory() {
        val value = StatsGoalAnalysis.compare(goal.copy(revision = 3), plan, listOf(day("20261002", 180)), listOf(quality("20261002")), "20261004")
        assertNull(value.average)
        assertEquals(0, value.evaluated)
    }
    @Test fun qualityWithoutEvidenceNeverCountsAsGoalSuccess() {
        val value = StatsGoalAnalysis.compare(goal, plan, listOf(day("20261002", 0)), emptyList(), "20261004")
        assertNull(value.average)
        assertEquals(0, value.achieved)
    }
    @Test fun liveTimeCanExceedTimeGoalWithoutAnyVideoCount() {
        val value = StatsGoalAnalysis.compare(goal, plan, listOf(day("20261002", 600)), listOf(quality("20261002")), "20261004")
        assertEquals(0, value.achieved)
        assertEquals(10.0, value.average!!, 0.01)
    }
    @Test fun excludedPauseDatesAreNotEvaluated() {
        val value = StatsGoalAnalysis.compare(goal, plan.copy(pausedSince = "20261002"), listOf(day("20261002", 0)), listOf(quality("20261002")), "20261004")
        assertEquals(0, value.evaluated)
    }
}
