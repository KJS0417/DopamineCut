package com.example.dopaminecut2.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dopaminecut2.auth.AuthRepository
import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.data.repository.UserRepositoryInterface
import com.example.dopaminecut2.domain.SupportedPlatform
import com.example.dopaminecut2.statistics.UsageSummary
import com.example.dopaminecut2.time.DateIdProvider
import com.example.dopaminecut2.ui.common.ContentState
import com.example.dopaminecut2.ui.common.MeasurementPermissionChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: UserRepositoryInterface,
    private val authRepository: AuthRepository,
    private val dateIdProvider: DateIdProvider,
    private val permissionChecker: MeasurementPermissionChecker,
    private val goalStore: com.example.dopaminecut2.data.local.GoalStore,
    private val habitStore: com.example.dopaminecut2.data.local.HabitStore? = null,
    private val insightsRepository: com.example.dopaminecut2.statistics.HabitInsightsRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var activeKey: String? = null
    private var statistics: DailyStatistics? = null
    private var statisticsFailed = false
    private var statisticsLoading = true
    private var goals: List<com.example.dopaminecut2.domain.ManagedGoal> = emptyList()
    private val errors = linkedSetOf<String>()

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> load(force = true)
            is HomeAction.SelectChartMetric -> {
                if (_uiState.value.chartMetric != action.metric) {
                    _uiState.value = _uiState.value.copy(chartMetric = action.metric)
                }
            }
        }
    }

    fun load(force: Boolean = false) {
        val userId = authRepository.currentUserId()
        if (userId == null) {
            clear()
            return
        }
        val date = dateIdProvider.currentDateId()
        val key = "$userId|$date"
        if (!force && activeKey == key && loadJob?.isActive == true) {
            refreshPermission()
            return
        }
        activeKey = key
        loadJob?.cancel()
        statistics = null
        statisticsFailed = false
        statisticsLoading = true
        goals = emptyList()
        errors.clear()
        _uiState.value = HomeUiState(
            measurement = permissionChecker.currentState(),
            chartMetric = _uiState.value.chartMetric
        )
        loadJob = viewModelScope.launch {
            launch {
                // Existing local evidence only; the new dashboard makes no additional Firestore queries.
                runCatching { insightsRepository?.load(userId, localOnly = true) }.onSuccess { snapshot ->
                    if (authRepository.currentUserId() == userId && activeKey == key) {
                        _uiState.value = _uiState.value.copy(validPreparationDays = snapshot?.quality
                            ?.filter { it.date < snapshot.today && it.valid }?.map { it.date }?.distinct()?.size?.coerceAtMost(3))
                    }
                }
            }
            launch {
                runCatching { habitStore?.settings(userId)?.collect { settings ->
                    if (authRepository.currentUserId() == userId && activeKey == key) _uiState.value = _uiState.value.copy(analysis = settings)
                } }.onFailure { report("기록 설정", it, userId, key) }
            }
            launch {
                runCatching {
                    goalStore.observeGoals(userId).collect { value ->
                        if (authRepository.currentUserId() == userId && activeKey == key) {
                            goals = value
                            _uiState.value = _uiState.value.copy(goalsLoaded = true)
                            publish(userId)
                        }
                    }
                }.onFailure { report("목표", it, userId, key) }
            }
            launch {
                repository.getDailyStatistics(userId, date)
                    .onSuccess { value ->
                        if (authRepository.currentUserId() == userId && activeKey == key) {
                            statisticsLoading = false
                            statistics = value
                            publish(userId)
                        }
                    }
                    .onFailure {
                        if (authRepository.currentUserId() == userId && activeKey == key) {
                            statisticsLoading = false
                            statisticsFailed = true
                            report("오늘 통계", it, userId, key)
                        }
                    }
            }
        }
    }

    fun refreshPermission() {
        _uiState.value = _uiState.value.copy(measurement = permissionChecker.currentState())
    }

    private fun publish(expectedUserId: String) {
        if (authRepository.currentUserId() != expectedUserId) return
        val stats = statistics
        val summary = UsageSummary.from(listOfNotNull(stats))
        val usages = summary.appUsage.map { (key, value) ->
            PlatformUsageUi(
                platformId = key,
                displayName = SupportedPlatform.fromStorageKey(key)?.displayName ?: key,
                appTimeSec = value.runTimeSec,
                shortformTimeSec = value.shortformTimeSec,
                shortformCount = value.shortformCount
            )
        }.sortedByDescending(PlatformUsageUi::shortformTimeSec)
        val categoryValues = summary.categoryUsage
            .map { (category, usage) ->
                CategoryUsageUi(
                    com.example.dopaminecut2.domain.ContentCategory.fromStored(category).label,
                    usage.count,
                    usage.durationSec
                )
            }
            .sortedByDescending(CategoryUsageUi::count)

        val content = if (statisticsLoading) {
            ContentState.Loading
        } else if (statisticsFailed) {
            ContentState.Error("오늘 사용량을 불러오지 못했어요. 연결을 확인하고 다시 시도해 주세요.")
        } else if (stats == null) {
            ContentState.Empty("아직 오늘 기록이 없습니다. 권한을 확인한 뒤 평소처럼 앱을 사용해 보세요.")
        } else {
            ContentState.Data(
                HomeDashboard(
                    totalAppTimeSec = summary.totalAppTimeSec,
                    totalShortformTimeSec = summary.totalShortformTimeSec,
                    shortformCount = summary.totalShortformCount,
                    platforms = usages,
                    categories = categoryValues,
                    categorySampleLimited = (summary.categoryUsage[com.example.dopaminecut2.domain.ContentCategory.UNKNOWN.id]?.let {
                        it.count > 0 || it.durationSec > 0
                    } ?: false) || summary.categoryUsage.values.sumOf { it.count } < summary.totalShortformCount ||
                        summary.categoryUsage.values.sumOf { it.durationSec } < summary.totalShortformTimeSec
                )
            )
        }
        _uiState.value = _uiState.value.copy(
            goalProgress = HomeGoalProgressMapper.map(goals, stats.takeUnless { statisticsFailed }),
            hasPausedGoals = goals.any { it.status == com.example.dopaminecut2.domain.GoalStatus.PAUSED },
            measurement = permissionChecker.currentState(),
            content = content,
            sectionMessage = errors.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        )
    }

    private fun report(operation: String, error: Throwable, userId: String, expectedKey: String? = activeKey) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (authRepository.currentUserId() != userId || activeKey != expectedKey) return
        if (operation == "목표") _uiState.value = _uiState.value.copy(goalsFailed = true)
        errors += "$operation 조회 실패"
        if (statistics == null) {
            _uiState.value = _uiState.value.copy(
                content = ContentState.Error(
                    error.localizedMessage ?: "$operation 정보를 불러오지 못했습니다."
                ),
                sectionMessage = errors.joinToString(" · ")
            )
        } else {
            publish(userId)
        }
    }

    private fun clear() {
        goals = emptyList()
        loadJob?.cancel()
        loadJob = null
        activeKey = null
        statistics = null
        _uiState.value = HomeUiState(
            content = ContentState.Error("로그인 정보가 없습니다.", canRetry = false)
        )
    }
}
