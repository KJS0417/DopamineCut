package com.example.dopaminecut2.ui.navigation

import com.example.dopaminecut2.R

sealed class AppDestination(val destinationId: Int) {
    data object Signup : AppDestination(R.id.nav_signup)
    data object Login : AppDestination(R.id.nav_login)
    data object Home : AppDestination(R.id.nav_home)
    data object Goal : AppDestination(R.id.nav_goal)
    data object Stats : AppDestination(R.id.nav_stats)
    data object Settings : AppDestination(R.id.nav_settings)
    data object InterventionSetup : AppDestination(R.id.nav_intervention_setup)
    data class GoalCandidates(val source: GoalCandidateSource) :
        AppDestination(R.id.nav_goal_candidates)
    data class GoalEditor(val mode: GoalEditorMode, val candidateId: String? = null, val goalMetricId: String? = null,
        val selectedMetricIds: List<String>? = null) :
        AppDestination(R.id.nav_goal_editor)
    data object MeasurementSetup : AppDestination(R.id.nav_measurement_setup)
    data object MyChange : AppDestination(R.id.nav_my_change)
}

enum class GoalCandidateSource { INITIAL, PERSONALIZED, NEXT_PLAN }
enum class GoalEditorMode { CREATE, ACCEPT_CANDIDATE, EDIT }
