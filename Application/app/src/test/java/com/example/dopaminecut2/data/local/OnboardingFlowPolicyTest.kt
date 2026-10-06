package com.example.dopaminecut2.data.local

import com.example.dopaminecut2.domain.InterventionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingFlowPolicyTest {
    @Test fun `only the current three onboarding stages are supported`() {
        assertEquals(listOf("INTERVENTION", "MEASUREMENT", "COMPLETE"), OnboardingStage.entries.map { it.name })
    }
    @org.junit.Test fun `restored managed goals retain their marker during new device permission setup`() {
        val restored = OnboardingProgress(answers = mapOf(OnboardingFlowPolicy.GOAL_MODE_KEY to setOf(OnboardingFlowPolicy.MANAGED_GOALS)))
        val selected = OnboardingFlowPolicy.withIntervention(restored, com.example.dopaminecut2.domain.InterventionMode.RECORD_ONLY)
        org.junit.Assert.assertEquals(setOf(OnboardingFlowPolicy.MANAGED_GOALS), selected.answers[OnboardingFlowPolicy.GOAL_MODE_KEY])
    }
    @Test
    fun `new account starts with no preselected intervention and no limits`() {
        val progress = OnboardingProgress()
        assertEquals(OnboardingStage.INTERVENTION, progress.stage)
        assertNull(OnboardingFlowPolicy.intervention(progress))
    }

    @Test
    fun `every choice leads directly to goal free measurement setup`() {
        InterventionMode.entries.forEach { mode ->
            val next = OnboardingFlowPolicy.withIntervention(OnboardingProgress(), mode)
            assertEquals(OnboardingStage.MEASUREMENT, next.stage)
            assertEquals(mode, OnboardingFlowPolicy.intervention(next))
        }
    }

    @Test
    fun `measurement completion preserves setup metadata and intervention preference`() {
        val choice = OnboardingFlowPolicy.withIntervention(OnboardingProgress(), InterventionMode.RESTRICT)
        val completed = OnboardingFlowPolicy.finishMeasurement(choice, false, 1000L)
        assertEquals(OnboardingStage.COMPLETE, completed.stage)
        assertEquals(setOf("configured"), completed.answers["measurement_setup"])
        assertEquals(setOf("1000"), completed.answers[OnboardingFlowPolicy.MEASUREMENT_STARTED_KEY])
        assertEquals(InterventionMode.RESTRICT, OnboardingFlowPolicy.intervention(completed))
    }

    @Test
    fun `deferring permissions completes entry without inventing a measurement start time`() {
        val choice = OnboardingFlowPolicy.withIntervention(OnboardingProgress(), InterventionMode.RECORD_ONLY)
        val completed = OnboardingFlowPolicy.finishMeasurement(choice, true, 1000L)
        assertEquals(OnboardingStage.COMPLETE, completed.stage)
        assertEquals(setOf("deferred"), completed.answers["measurement_setup"])
        assertNull(completed.answers[OnboardingFlowPolicy.MEASUREMENT_STARTED_KEY])
    }

    @Test
    fun `measurement timestamp is preserved on subsequent setup`() {
        val first = OnboardingFlowPolicy.finishMeasurement(OnboardingProgress(), false, 1000L)
        val second = OnboardingFlowPolicy.finishMeasurement(first, false, 2000L)
        assertEquals(setOf("1000"), second.answers[OnboardingFlowPolicy.MEASUREMENT_STARTED_KEY])
    }

    @Test
    fun `existing completed account keeps previous goals when changing default intervention`() {
        val previous = OnboardingProgress(OnboardingStage.COMPLETE, mapOf(OnboardingFlowPolicy.GOAL_MODE_KEY to setOf(OnboardingFlowPolicy.MANAGED_GOALS)))
        val next = OnboardingFlowPolicy.withIntervention(previous, InterventionMode.NOTIFY)
        assertEquals(OnboardingStage.COMPLETE, next.stage)
        assertEquals(setOf(OnboardingFlowPolicy.MANAGED_GOALS), next.answers[OnboardingFlowPolicy.GOAL_MODE_KEY])
    }

    @Test
    fun `goal free completed account remains goal free when preference changes`() {
        val initial = OnboardingFlowPolicy.withIntervention(OnboardingProgress(), InterventionMode.RECORD_ONLY)
        val complete = OnboardingFlowPolicy.finishMeasurement(initial, false, 1000L)
        val changed = OnboardingFlowPolicy.withIntervention(complete, InterventionMode.RESTRICT)
        assertEquals(OnboardingStage.COMPLETE, changed.stage)
    }

    @Test
    fun `unknown stored intervention does not select a fallback without consent`() {
        val invalid = OnboardingProgress(answers = mapOf(OnboardingFlowPolicy.INTERVENTION_KEY to setOf("UNKNOWN")))
        assertNull(OnboardingFlowPolicy.intervention(invalid))
    }
}
