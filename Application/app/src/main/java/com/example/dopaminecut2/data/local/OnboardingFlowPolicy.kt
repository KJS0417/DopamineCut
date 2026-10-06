package com.example.dopaminecut2.data.local

import com.example.dopaminecut2.domain.InterventionMode

object OnboardingFlowPolicy {
    const val INTERVENTION_KEY = "default_intervention"
    const val GOAL_MODE_KEY = "goal_mode"
    const val MEASUREMENT_ONLY = "measurement_only"
    const val MANAGED_GOALS = "managed_goals"
    const val MEASUREMENT_STARTED_KEY = "measurement_started_at"

    fun intervention(progress: OnboardingProgress): InterventionMode? =
        progress.answers[INTERVENTION_KEY]?.singleOrNull()?.let { id ->
            InterventionMode.entries.firstOrNull { it.name == id }
        }

    fun withIntervention(progress: OnboardingProgress, mode: InterventionMode): OnboardingProgress {
        val answers = progress.answers + (INTERVENTION_KEY to setOf(mode.name))
        // Editing a completed account's preference must not restart setup or remove an existing goal.
        return if (progress.stage == OnboardingStage.COMPLETE) progress.copy(answers = answers) else {
            progress.copy(
                stage = OnboardingStage.MEASUREMENT,
                answers = answers + (GOAL_MODE_KEY to setOf(
                    if (progress.answers[GOAL_MODE_KEY] == setOf(MANAGED_GOALS)) MANAGED_GOALS else MEASUREMENT_ONLY))
            )
        }
    }

    fun finishMeasurement(progress: OnboardingProgress, deferred: Boolean, nowEpochMs: Long): OnboardingProgress {
        require(nowEpochMs > 0)
        var answers = progress.answers + ("measurement_setup" to setOf(if (deferred) "deferred" else "configured"))
        if (progress.stage != OnboardingStage.COMPLETE && GOAL_MODE_KEY !in answers) {
            answers = answers + (GOAL_MODE_KEY to setOf(MEASUREMENT_ONLY))
        }
        if (!deferred && MEASUREMENT_STARTED_KEY !in answers) {
            answers = answers + (MEASUREMENT_STARTED_KEY to setOf(nowEpochMs.toString()))
        }
        return progress.copy(stage = OnboardingStage.COMPLETE, answers = answers)
    }
}
