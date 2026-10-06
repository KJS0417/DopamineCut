package com.example.dopaminecut2.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.dopaminecut2.DopamineCutApplication
import com.example.dopaminecut2.R
import com.example.dopaminecut2.databinding.FragmentHomeBinding
import com.example.dopaminecut2.di.ViewModelFactory
import com.example.dopaminecut2.domain.GoalMetric
import com.example.dopaminecut2.domain.InterventionMode
import com.example.dopaminecut2.ui.common.ContentState
import com.example.dopaminecut2.ui.common.PrototypeUi
import com.example.dopaminecut2.ui.common.UiFormatters
import com.example.dopaminecut2.ui.navigation.AppDestination
import com.example.dopaminecut2.ui.navigation.AppNavigator
import kotlinx.coroutines.launch
import kotlin.math.abs

open class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val viewModel: HomeViewModel by viewModels {
        val dependencies = (requireActivity().application as DopamineCutApplication).requireDependencies()
        ViewModelFactory {
            HomeViewModel(dependencies.userRepository, dependencies.authRepository, dependencies.dateIdProvider,
                dependencies.measurementPermissionChecker, dependencies.goalStore,
                dependencies.habitStore, dependencies.habitInsights)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentHomeBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val navigator = AppNavigator(findNavController())
        binding.btnGoalManagement.setOnClickListener { navigator.navigate(AppDestination.Goal) }
        binding.btnSetGoal.setOnClickListener { navigator.navigate(AppDestination.Goal) }
        binding.btnDetailedStats.setOnClickListener { navigator.navigate(AppDestination.Stats) }
        binding.btnMyChange.setOnClickListener { navigator.navigate(AppDestination.MyChange) }
        binding.btnMeasurementSettings.setOnClickListener { navigator.navigate(AppDestination.Settings) }
        binding.btnRetry.setOnClickListener { viewModel.onAction(HomeAction.Refresh) }
        binding.metricGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) viewModel.onAction(HomeAction.SelectChartMetric(
                if (id == R.id.btn_metric_count) HomeChartMetric.COUNT else HomeChartMetric.TIME))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::render) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermission()
        viewModel.load(force = true)
    }

    private fun render(state: HomeUiState) {
        binding.tvMeasurementStatus.text = when {
            state.analysis == null -> "기록 설정 확인 중"
            !state.analysis.measurement -> "사용 기록 중지됨"
            !state.measurement.accessibilityGranted -> "접근성 연결 필요"
            !state.measurement.usageAccessGranted -> "사용정보 권한 확인 필요"
            else -> "기록 설정 켜짐"
        }
        // Permission-enabled is not proof that the accessibility service is currently connected.
        binding.tvMeasurementStatus.setTextColor(ContextCompat.getColor(requireContext(),
            if (state.analysis?.measurement == true && state.measurement.accessibilityGranted && state.measurement.usageAccessGranted)
                R.color.ui_success else R.color.ui_warning))
        binding.progressLoading.isVisible = state.content is ContentState.Loading
        binding.summaryCard.isVisible = state.content is ContentState.Data
        binding.contentGroup.isVisible = state.content is ContentState.Data
        binding.emptyGroup.isVisible = state.content is ContentState.Empty || state.content is ContentState.Error || state.content is ContentState.Unavailable
        binding.btnRetry.isVisible = state.content is ContentState.Error && state.content.canRetry
        binding.tvSectionMessage.isVisible = state.sectionMessage != null
        binding.tvSectionMessage.text = state.sectionMessage.orEmpty()
        val button = if (state.chartMetric == HomeChartMetric.TIME) R.id.btn_metric_time else R.id.btn_metric_count
        if (binding.metricGroup.checkedButtonId != button) binding.metricGroup.check(button)
        renderGoals(state)
        when (val content = state.content) {
            is ContentState.Data -> renderDashboard(content.value, state.chartMetric)
            is ContentState.Empty -> binding.tvEmptyMessage.text = content.message
            is ContentState.Error -> binding.tvEmptyMessage.text = content.message
            is ContentState.Unavailable -> binding.tvEmptyMessage.text = content.reason
            ContentState.Loading -> Unit
        }
    }

    private fun renderGoals(state: HomeUiState) {
        binding.goalProgressList.isVisible = state.goalProgress.isNotEmpty()
        binding.goalPreparationCard.isVisible = state.goalProgress.isEmpty()
        binding.tvGoalTitle.text = if (state.goalProgress.isEmpty()) "내 목표 준비" else "오늘 목표 진행"
        binding.goalProgressList.removeAllViews()
        state.goalProgress.forEachIndexed { index, item ->
            val goal = item.goal
            val count = goal.metric == GoalMetric.DAILY_COUNT
            val label = when (goal.metric) {
                GoalMetric.DAILY_TIME -> "숏폼 시청 시간"
                GoalMetric.DAILY_COUNT -> "숏폼 시청 영상 수"
                GoalMetric.APP_TIME -> "앱 사용 시간"
            }
            if (index > 0) binding.goalProgressList.addView(PrototypeUi.divider(requireContext()).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = PrototypeUi.dp(context, 4)
            })
            val used = item.used?.let { if (count) "${it}개" else UiFormatters.duration(it) } ?: "확인 불가"
            binding.goalProgressList.addView(compactRow(label, "$used / ${goal.target}${if (count) "개" else "분"}"))
            item.progress?.let { binding.goalProgressList.addView(progress(it, if (item.exceeded) R.color.ui_warning else R.color.ui_primary).apply {
                contentDescription = "$label $used, 하루 한도 ${goal.target}${if (count) "개" else "분"}"
            }) }
            val remaining = item.used?.let { value ->
                val delta = item.limit - value
                when {
                    item.limit == 0L && value == 0L -> "사용하지 않기 · 현재 기록 없음"
                    else -> (if (count) "${abs(delta)}개" else UiFormatters.duration(abs(delta))) + if (delta < 0) " 초과" else " 남음"
                }
            } ?: "사용량을 다시 불러와 주세요."
            val mode = when (goal.intervention) {
                InterventionMode.RECORD_ONLY -> "기록만 보기"
                InterventionMode.NOTIFY -> "알림 받기"
                InterventionMode.CONFIRM -> "계속 볼지 확인"
                InterventionMode.RESTRICT -> "사용 제한"
            }
            binding.goalProgressList.addView(PrototypeUi.text(requireContext(), "$remaining · $mode", 11f).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = 0
                setLineSpacing(0f, 1f)
            })
        }
        binding.tvGoalPreparation.text = when {
            state.goalsFailed -> "목표 조회 실패"
            !state.goalsLoaded -> "목표 확인 중"
            state.hasPausedGoals -> "목표 중지됨"
            state.validPreparationDays == null -> "추천 기준 확인 필요"
            state.validPreparationDays >= 3 -> "목표 추천 준비"
            else -> "유효 기록 ${state.validPreparationDays} / 3일"
        }
        binding.tvGoalSummary.text = when {
            state.goalsFailed -> "목표 관리에서 연결 상태를 확인하고 다시 시도해 주세요."
            !state.goalsLoaded -> "목표를 불러오는 중이에요."
            state.hasPausedGoals -> "중지한 목표는 개입하지 않아요. 목표 관리에서 다시 시작할 수 있어요."
            else -> "오늘 사용량부터 확인할 수 있어요.\n유효 기록이 쌓이면 여러 목표를 제안해요. 직접 목표를 설정할 수도 있어요."
        }
        binding.btnSetGoal.isEnabled = state.goalsLoaded
        binding.preparationProgress.isVisible = state.goalProgress.isEmpty() && state.goalsLoaded &&
            !state.hasPausedGoals && state.validPreparationDays != null
        binding.preparationProgress.progress = state.validPreparationDays ?: 0
        binding.btnSetGoal.text = if (state.hasPausedGoals) "목표 관리하기" else "목표 설정하기"
    }

    private fun renderDashboard(dashboard: HomeDashboard, metric: HomeChartMetric) {
        binding.tvAppTime.text = UiFormatters.duration(dashboard.totalAppTimeSec)
        binding.tvShortformTime.text = UiFormatters.duration(dashboard.totalShortformTimeSec)
        binding.tvShortformCount.text = "${dashboard.shortformCount.coerceAtLeast(0)}개"
        binding.tvPlatformUsageTitle.text = if (metric == HomeChartMetric.TIME) "숏폼 시청 시간 · 분" else "숏폼 시청 횟수 · 회"
        binding.tvCategoryUsageTitle.text = if (metric == HomeChartMetric.TIME) "시청 시간 기준" else "영상 수 기준"
        binding.tvCategoryNotice.isVisible = dashboard.categorySampleLimited
        renderPlatforms(dashboard.platforms, metric)
        renderCategories(dashboard.categories, metric)
    }

    private fun renderPlatforms(values: List<PlatformUsageUi>, metric: HomeChartMetric) {
        binding.platformUsageList.removeAllViews()
        val rows = values.filter { metric.platformValue(it) > 0f }.sortedByDescending(metric::platformValue)
        binding.tvPlatformEmpty.isVisible = rows.isEmpty()
        binding.tvPlatformEmpty.text = if (metric == HomeChartMetric.TIME) "기록된 숏폼 시청 시간이 없어요." else "기록된 일반 숏폼 시청 횟수가 없어요."
        val maximum = rows.maxOfOrNull(metric::platformValue) ?: 1f
        rows.forEach { usage ->
            binding.platformUsageList.addView(compactRow(usage.displayName, format(metric.platformValue(usage), metric)))
            binding.platformUsageList.addView(progress((metric.platformValue(usage) / maximum * 1000).toInt(), R.color.ui_primary).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }
    }

    private fun renderCategories(values: List<CategoryUsageUi>, metric: HomeChartMetric) {
        binding.categoryUsageList.removeAllViews()
        binding.categorySegments.removeAllViews()
        val rows = values.filter { metric.categoryValue(it) > 0f }.sortedByDescending(metric::categoryValue)
        binding.tvCategoryEmpty.isVisible = rows.isEmpty()
        binding.categorySegments.isVisible = rows.isNotEmpty()
        binding.tvCategoryEmpty.text = "아직 콘텐츠 분류 기록이 없어요. 사용량은 따로 기록돼요."
        val total = rows.sumOf { metric.categoryValue(it).toDouble() }.toFloat()
        rows.forEach { item ->
            val value = metric.categoryValue(item)
            val color = CATEGORY_COLORS[Math.floorMod(item.label.hashCode(), CATEGORY_COLORS.size)]
            binding.categorySegments.addView(View(requireContext()).apply {
                setBackgroundColor(ContextCompat.getColor(context, color))
                layoutParams = LinearLayout.LayoutParams(0, -1, value / total)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(View(requireContext()).apply {
                setBackgroundColor(ContextCompat.getColor(context, color))
                layoutParams = LinearLayout.LayoutParams(PrototypeUi.dp(context, 8), PrototypeUi.dp(context, 8)).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    marginEnd = PrototypeUi.dp(context, 8)
                }
            })
            row.addView(compactRow(item.label, format(value, metric)).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            binding.categoryUsageList.addView(row)
        }
        binding.categorySegments.contentDescription = rows.joinToString { "${it.label} ${format(metric.categoryValue(it), metric)}" }
    }

    private fun progress(value: Int, color: Int) = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progress = value.coerceIn(0, 1000)
        progressTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
        progressBackgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ui_soft))
        layoutParams = LinearLayout.LayoutParams(-1, PrototypeUi.dp(context, 6)).apply {
            topMargin = PrototypeUi.dp(context, 4)
            bottomMargin = PrototypeUi.dp(context, 4)
        }
    }

    private fun compactRow(label: String, value: String) = PrototypeUi.row(requireContext(), label, value).apply {
        for (index in 0 until childCount) (getChildAt(index) as android.widget.TextView).apply {
            textSize = 12f
            (layoutParams as LinearLayout.LayoutParams).topMargin = PrototypeUi.dp(context, 2)
            setLineSpacing(0f, 1f)
        }
    }

    private fun format(value: Float, metric: HomeChartMetric): String =
        if (metric == HomeChartMetric.TIME) "%.1f분".format(java.util.Locale.KOREA, value) else "${value.toLong()}회"

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val CATEGORY_COLORS = intArrayOf(R.color.ui_primary, R.color.ui_chart_secondary, R.color.ui_chart_third, R.color.ui_chart_fourth)
    }
}
