package com.example.dopaminecut2.ui.change

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dopaminecut2.di.AppDependencies
import com.example.dopaminecut2.domain.*
import com.example.dopaminecut2.ui.common.ContentState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MyChangeViewModel(private val dependencies: AppDependencies? = null) : ViewModel() {
    private val _uiState = MutableStateFlow(MyChangeUiState())
    val uiState: StateFlow<MyChangeUiState> = _uiState.asStateFlow()

    fun onAction(action: MyChangeAction) {
        when (action) {
            MyChangeAction.Refresh -> load()
        }
    }
    private fun load() {
        val d = dependencies ?: return
        val uid = d.authRepository.currentUserId() ?: return
        _uiState.value = MyChangeUiState(ContentState.Loading)
        viewModelScope.launch {
            runCatching {
                val insights = d.habitInsights.load(uid)
                val goals = d.goalStore.observeGoals(uid).first()
                val yesterday = java.time.LocalDate.parse(insights.today, HabitInsightsPolicy.format).minusDays(1).format(HabitInsightsPolicy.format)
                val prior = insights.copy(today = yesterday, days = insights.days.filter { it.date < yesterday })
                val previous = insights.days.firstOrNull { it.date == yesterday }
                val dailyRows = if (insights.valid(yesterday) && previous != null) GoalMetric.entries.mapNotNull { metric ->
                    val b = prior.baseline(metric, ManagedGoal.SUPPORTED) ?: return@mapNotNull null
                    val used = ManagedGoal.SUPPORTED.sumOf { p -> HabitInsightsPolicy.value(previous.appUsage[p.storageKey] ?: com.example.dopaminecut2.data.model.AppUsage(), metric) }
                    val label = when (metric) { GoalMetric.DAILY_TIME -> "숏폼 시간"; GoalMetric.DAILY_COUNT -> "숏폼 영상 수"; GoalMetric.APP_TIME -> "앱 사용 시간" }
                    val unit = if (metric == GoalMetric.DAILY_COUNT) "회" else "분"
                    "어제 $label ${"%.1f".format(used)}$unit · 이전 유효 ${b.dates.size}일 평균 ${"%.1f".format(b.average)}$unit"
                } else emptyList()
                val rows = goals.map { goal ->
                    val plan = insights.plans[goal.metric]
                    val base = plan?.baseline?.takeIf { plan.revision == goal.revision }
                    val days = insights.days.filter { insights.valid(it.date) && plan?.eligible(it.date) == true }
                    val label = when (goal.metric) { GoalMetric.DAILY_TIME -> "숏폼 시간"; GoalMetric.DAILY_COUNT -> "숏폼 영상 수"; GoalMetric.APP_TIME -> "앱 사용 시간" }
                    if (base == null || days.isEmpty()) "$label: 고정 기준과 완료된 유효 수행일이 더 필요해요."
                    else {
                        val amounts = days.map { day -> goal.platforms.sumOf { p -> HabitInsightsPolicy.value(day.appUsage[p.storageKey] ?: com.example.dopaminecut2.data.model.AppUsage(), goal.metric) } }
                        val mean = amounts.average(); val success = amounts.count { it <= goal.target }
                        val unit = if (goal.metric == GoalMetric.DAILY_COUNT) "회" else "분"
                        "$label\n기준 ${"%.1f".format(base.average)}$unit → 수행 평균 ${"%.1f".format(mean)}$unit\n유효 ${days.size}일 · 달성 $success/${days.size}일 (${success * 100 / days.size}%)\n" +
                            (if (base.average == 0.0) "기준값이 0이므로 변화율은 계산하지 않아요."
                            else "${"%.1f".format(kotlin.math.abs((base.average - mean) / base.average * 100))}% ${if (mean <= base.average) "감소" else "증가"}")
                    }
                }
                val missing = insights.quality.count { !it.valid && it.date < insights.today }
                val todayPending = d.pendingRecognitionStore.pendingRecognitions(uid).filter { it.date == insights.today }.sumOf { it.durationSec }
                val warning = "오늘은 마감 전이라 성공·실패를 확정하지 않아요.\n측정 품질 부족으로 제외된 날짜 ${missing}일 · 오늘 미확인 ${todayPending}초\n숏폼 시간과 앱 시간의 감소량은 합산하지 않아요."
                ChangeSummaryUi(warning, (dailyRows + rows).joinToString("\n\n").ifEmpty { "비교할 유효 기록이 부족합니다. 기록을 확인하고 목표를 직접 설정할 수 있어요." }, 0)
            }.onSuccess { if (d.authRepository.currentUserId() == uid) _uiState.value = MyChangeUiState(ContentState.Data(it)) }
                .onFailure { if (d.authRepository.currentUserId() == uid) _uiState.value = MyChangeUiState(ContentState.Error(it.message ?: "변화를 불러오지 못했습니다.")) }
        }
    }
}
