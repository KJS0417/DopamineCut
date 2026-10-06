package com.example.dopaminecut2.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dopaminecut2.auth.AuthRepository
import com.example.dopaminecut2.data.local.OnboardingStage
import com.example.dopaminecut2.data.local.OnboardingStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GoalViewModel(
    private val authRepository: AuthRepository,
    private val onboardingStore: OnboardingStore,
    private val goalStore: com.example.dopaminecut2.data.local.GoalStore,
    private val insightsRepository: com.example.dopaminecut2.statistics.HabitInsightsRepository? = null,
    private val habitStore: com.example.dopaminecut2.data.local.HabitStore? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()
    private var goalsJob: Job? = null
    private var insights: com.example.dopaminecut2.statistics.InsightSnapshot? = null

    fun onAction(action: GoalAction) {
        when (action) {
            GoalAction.Refresh -> load()
            GoalAction.ContinueWithoutGoal -> continueWithoutGoal()
            is GoalAction.SetStatus -> setStatus(action)
            GoalAction.Sync -> synchronize()
            is GoalAction.ResolveConflict -> synchronize(action.useLocal)
        }
    }

    fun load() {
        goalsJob?.cancel()
        val userId = authRepository.currentUserId()
        if (userId == null) {
            _uiState.value = GoalUiState(
                goalsLoading = false, goalsMessage = "로그인 정보가 없습니다."
            )
            return
        }
        _uiState.value = GoalUiState()
        insights = null
        viewModelScope.launch {
            runCatching { insightsRepository?.load(userId) }.onSuccess { snapshot ->
                if (authRepository.currentUserId() == userId) { insights = snapshot; publishInsights() }
            }.onFailure { if (authRepository.currentUserId() == userId) _uiState.value = _uiState.value.copy(goalsMessage = "기준 통계 조회 실패: ${it.message}") }
        }
        viewModelScope.launch { goalStore.sync(userId) }
        goalsJob = viewModelScope.launch {
            launch {
                goalStore.observeSync(userId).collect { status ->
                    if (authRepository.currentUserId() == userId) _uiState.value = _uiState.value.copy(syncStatus = status)
                }
            }
            try {
                goalStore.observeGoals(userId).collect { goals ->
                    if (authRepository.currentUserId() == userId) _uiState.value = _uiState.value.copy(
                        goals = goals, goalsLoading = false, goalsMessage = null)
                    if (authRepository.currentUserId() == userId) publishInsights()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (authRepository.currentUserId() == userId) _uiState.value = _uiState.value.copy(goalsLoading = false,
                    goalsMessage = error.message ?: "목표를 불러오지 못했습니다.")
            }
        }
    }

    private fun publishInsights() {
        _uiState.value = _uiState.value.copy(insights = insights)
    }

    private fun continueWithoutGoal() {
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            onboardingStore.setOnboardingStage(userId, OnboardingStage.MEASUREMENT)
        }
    }

    private fun setStatus(action: GoalAction.SetStatus) {
        if (_uiState.value.changingGoal != null) return
        val userId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(changingGoal = action.metric, goalsMessage = null)
            try {
                goalStore.setGoalStatus(userId, action.metric, action.revision, action.status)
                val plan = habitStore?.plans(userId)?.get(action.metric)
                if (plan != null && plan.revision == action.revision) {
                    val today = java.time.LocalDate.now()
                    val excluded = if (action.status == com.example.dopaminecut2.domain.GoalStatus.ACTIVE && plan.pausedSince != null)
                        generateSequence(maxOf(today.minusDays(45), java.time.LocalDate.parse(plan.pausedSince, com.example.dopaminecut2.domain.HabitInsightsPolicy.format))) { it.plusDays(1) }
                            .takeWhile { it <= today }.map { it.format(com.example.dopaminecut2.domain.HabitInsightsPolicy.format) }.toSet()
                    else emptySet()
                    habitStore.savePlan(userId, plan.copy(revision = action.revision + 1,
                        excludedDates = plan.excludedDates + excluded,
                        pausedSince = if (action.status == com.example.dopaminecut2.domain.GoalStatus.PAUSED) today.format(com.example.dopaminecut2.domain.HabitInsightsPolicy.format) else null))
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (authRepository.currentUserId() == userId) _uiState.value = _uiState.value.copy(goalsMessage = error.message ?: "목표 상태를 저장하지 못했습니다.")
            } finally {
                if (authRepository.currentUserId() == userId) _uiState.value = _uiState.value.copy(changingGoal = null)
            }
        }
    }

    private fun synchronize(useLocal: Boolean? = null) {
        val uid = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            if (useLocal == null) goalStore.sync(uid) else goalStore.resolveConflict(uid, useLocal)
        }
    }


}
