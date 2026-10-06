package com.example.dopaminecut2.logic

import com.example.dopaminecut2.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalInterventionExecutionPolicyTest {
    private fun action(mode: InterventionMode, threshold: GoalThreshold = GoalThreshold.REACHED,
                       enabled: Boolean = true, notified: Boolean = false, until: Long = 0,
                       now: Long = 100, status: GoalStatus = GoalStatus.ACTIVE): GoalInterventionAction =
        GoalInterventionExecutionPolicy.action(
            GoalInterventionRequest(ManagedGoal(GoalMetric.DAILY_TIME, 1, setOf(SupportedPlatform.YOUTUBE), mode, status, 1), threshold),
            enabled, notified, until, now
        )

    @Test fun `record only does nothing at any threshold`() {
        GoalThreshold.entries.forEach { assertEquals(GoalInterventionAction.NONE, action(InterventionMode.RECORD_ONLY, it)) }
    }
    @Test fun `notifications work at near and reached thresholds`() {
        GoalThreshold.entries.forEach { assertEquals(GoalInterventionAction.NOTIFY, action(InterventionMode.NOTIFY, it)) }
    }
    @Test fun `disabled or already sent notification is skipped`() {
        assertEquals(GoalInterventionAction.NONE, action(InterventionMode.NOTIFY, enabled = false))
        assertEquals(GoalInterventionAction.NONE, action(InterventionMode.NOTIFY, notified = true))
    }
    @Test fun `confirmation waits for reached threshold`() {
        assertEquals(GoalInterventionAction.NONE, action(InterventionMode.CONFIRM, GoalThreshold.NEAR))
        assertEquals(GoalInterventionAction.CONFIRM, action(InterventionMode.CONFIRM))
    }
    @Test fun `confirmation extension expires exactly at monotonic deadline`() {
        assertEquals(GoalInterventionAction.NONE, action(InterventionMode.CONFIRM, until = 300100, now = 300099))
        assertEquals(GoalInterventionAction.CONFIRM, action(InterventionMode.CONFIRM, until = 300100, now = 300100))
    }
    @Test fun `restriction cannot inherit confirmation extension`() {
        assertEquals(GoalInterventionAction.RESTRICT, action(InterventionMode.RESTRICT, until = 999999))
        assertEquals(GoalInterventionAction.NONE, action(InterventionMode.RESTRICT, GoalThreshold.NEAR))
    }
    @Test fun `notification preference does not disable confirmation or restriction`() {
        assertEquals(GoalInterventionAction.CONFIRM, action(InterventionMode.CONFIRM, enabled = false, notified = true))
        assertEquals(GoalInterventionAction.RESTRICT, action(InterventionMode.RESTRICT, enabled = false, notified = true))
    }
    @Test fun `paused goals never intervene`() {
        InterventionMode.entries.forEach { assertEquals(GoalInterventionAction.NONE, action(it, status = GoalStatus.PAUSED)) }
    }
}
