package com.example.dopaminecut2.ui.goal

data class GoalUiState(
    val insights: com.example.dopaminecut2.statistics.InsightSnapshot? = null,
    val goals: List<com.example.dopaminecut2.domain.ManagedGoal> = emptyList(),
    val goalsLoading: Boolean = true,
    val goalsMessage: String? = null,
    val changingGoal: GoalMetric? = null,
    val syncStatus: com.example.dopaminecut2.data.local.GoalSyncStatus = com.example.dopaminecut2.data.local.GoalSyncStatus()
)

sealed interface GoalAction {
    data object Refresh : GoalAction
    data object ContinueWithoutGoal : GoalAction
    data class SetStatus(val metric: GoalMetric, val revision: Long, val status: com.example.dopaminecut2.domain.GoalStatus) : GoalAction
    data object Sync : GoalAction
    data class ResolveConflict(val useLocal: Boolean) : GoalAction
}
