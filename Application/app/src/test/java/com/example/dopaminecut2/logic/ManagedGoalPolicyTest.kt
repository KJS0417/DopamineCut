package com.example.dopaminecut2.logic

import com.example.dopaminecut2.data.model.AppUsage
import com.example.dopaminecut2.domain.*
import org.junit.Assert.*
import org.junit.Test

class ManagedGoalPolicyTest {
    private fun goal(metric: GoalMetric = GoalMetric.DAILY_TIME, mode: InterventionMode = InterventionMode.NOTIFY) =
        ManagedGoal(metric, 1, setOf(SupportedPlatform.YOUTUBE), mode, revision = 1)
    private fun evaluate(goal: ManagedGoal, value: AppUsage, eligible: Boolean = true, normal: Boolean = true,
                         foreground: SupportedPlatform = SupportedPlatform.YOUTUBE) =
        ManagedGoalPolicy.evaluate(listOf(goal), mapOf("youtube" to value), foreground, eligible, normal)
    @Test fun `record only never intervenes`() { assertTrue(evaluate(goal(mode = InterventionMode.RECORD_ONLY), AppUsage(100, 100, 100)).isEmpty()) }
    @Test fun `paused goals never intervene`() { assertTrue(evaluate(goal().copy(status = GoalStatus.PAUSED), AppUsage(100, 100, 100)).isEmpty()) }
    @Test fun `near threshold starts at eighty percent`() {
        assertTrue(evaluate(goal(), AppUsage(shortformTimeSec = 47)).isEmpty())
        assertEquals(GoalThreshold.NEAR, evaluate(goal(), AppUsage(shortformTimeSec = 48)).single().threshold)
        assertEquals(GoalThreshold.REACHED, evaluate(goal(), AppUsage(shortformTimeSec = 60)).single().threshold)
    }
    @Test fun `confirm waits until reached`() {
        assertTrue(evaluate(goal(mode = InterventionMode.CONFIRM), AppUsage(shortformTimeSec = 59)).isEmpty())
        assertEquals(1, evaluate(goal(mode = InterventionMode.CONFIRM), AppUsage(shortformTimeSec = 60)).size)
    }
    @Test fun `long form ad and unknown do not trigger shortform targets`() {
        assertTrue(evaluate(goal(), AppUsage(shortformTimeSec = 100), eligible = false).isEmpty())
    }
    @Test fun `live time counts but live does not trigger count targets`() {
        assertEquals(1, evaluate(goal(), AppUsage(shortformTimeSec = 60), normal = false).size)
        assertTrue(evaluate(goal(GoalMetric.DAILY_COUNT), AppUsage(shortformCount = 10), normal = false).isEmpty())
    }
    @Test fun `app time applies outside shorts only to selected apps`() {
        assertEquals(1, evaluate(goal(GoalMetric.APP_TIME), AppUsage(runTimeSec = 60), eligible = false).size)
        assertTrue(evaluate(goal(GoalMetric.APP_TIME), AppUsage(runTimeSec = 60), foreground = SupportedPlatform.INSTAGRAM).isEmpty())
    }
    @Test fun `selected platforms are summed once`() {
        val target = goal().copy(platforms = setOf(SupportedPlatform.YOUTUBE, SupportedPlatform.INSTAGRAM))
        assertEquals(GoalThreshold.REACHED, ManagedGoalPolicy.evaluate(listOf(target),
            mapOf("youtube" to AppUsage(shortformTimeSec = 30), "instagram" to AppUsage(shortformTimeSec = 30), "kakaotalk" to AppUsage(shortformTimeSec = 999)),
            SupportedPlatform.YOUTUBE, true, true).single().threshold)
    }
    @Test fun `zero target is reached without division`() {
        assertEquals(GoalThreshold.REACHED, evaluate(goal().copy(target = 0), AppUsage()).single().threshold)
    }
    @Test fun `restrict takes priority over confirm and notifications`() {
        val targets = listOf(goal(GoalMetric.APP_TIME), goal(GoalMetric.DAILY_COUNT, InterventionMode.CONFIRM), goal(mode = InterventionMode.RESTRICT))
        val requests = ManagedGoalPolicy.evaluate(targets, mapOf("youtube" to AppUsage(100, 100, 100)), SupportedPlatform.YOUTUBE, true, true)
        assertEquals(listOf(InterventionMode.RESTRICT, InterventionMode.CONFIRM, InterventionMode.NOTIFY), requests.map { it.goal.intervention })
    }
}
