package com.example.dopaminecut2.ui.navigation

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import com.example.dopaminecut2.R

class AppNavigator(private val navController: NavController) {
    fun navigate(destination: AppDestination, clearCurrentFlow: Boolean = false) {
        val args = when (destination) {
            is AppDestination.GoalCandidates -> Bundle().apply {
                putString(ARG_SOURCE, destination.source.name)
            }
            is AppDestination.GoalEditor -> Bundle().apply {
                putString(ARG_MODE, destination.mode.name)
                putString(ARG_CANDIDATE_ID, destination.candidateId)
                putString(ARG_GOAL_METRIC, destination.goalMetricId)
                destination.selectedMetricIds?.let { putStringArrayList(ARG_SELECTED_METRICS, ArrayList(it)) }
            }
            else -> null
        }
        val options = if (clearCurrentFlow) {
            NavOptions.Builder()
                .setPopUpTo(navController.graph.id, true)
                .setLaunchSingleTop(true)
                .build()
        } else if (destination.destinationId in TOP_LEVEL_DESTINATIONS) {
            NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(
                    navController.graph.findStartDestination().id,
                    inclusive = false,
                    saveState = true
                )
                .build()
        } else {
            NavOptions.Builder().setLaunchSingleTop(true).build()
        }
        navController.navigate(destination.destinationId, args, options)
    }

    fun back(): Boolean = navController.popBackStack()

    companion object {
        const val ARG_SOURCE = "source"
        const val ARG_MODE = "mode"
        const val ARG_CANDIDATE_ID = "candidate_id"
        const val ARG_GOAL_METRIC = "goal_metric"
        const val ARG_SELECTED_METRICS = "selected_metrics"
        const val ARG_SETTINGS_SECTION = "settings_section"
        private val TOP_LEVEL_DESTINATIONS = setOf(
            R.id.nav_home,
            R.id.nav_goal,
            R.id.nav_stats,
            R.id.nav_settings
        )
    }
}
