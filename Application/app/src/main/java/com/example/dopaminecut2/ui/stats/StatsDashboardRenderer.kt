package com.example.dopaminecut2.ui.stats

import android.content.res.ColorStateList
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.dopaminecut2.R
import com.example.dopaminecut2.databinding.FragmentStatsBinding
import com.example.dopaminecut2.domain.*
import com.example.dopaminecut2.ui.common.PrototypeUi
import com.example.dopaminecut2.ui.common.UiFormatters
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import kotlin.math.abs

/** No Firebase calls: all sections and detail taps use the loaded period. */
class StatsDashboardRenderer(private val binding: FragmentStatsBinding, private val selectDate: (String) -> Unit) {
    private val context get() = binding.root.context
    init {
        binding.btnContentInfo.setOnClickListener {
            MaterialAlertDialogBuilder(context).setTitle("콘텐츠 집계 기준")
                .setMessage("비중은 카테고리가 기록된 내용끼리 비교한 값이에요. 분류 대기·미확인 또는 집계 범위 차이로 전체 사용량과 다를 수 있어요.\n\n일반 숏폼은 시간·영상 수, 라이브·사진 게시물은 시간만 반영해요. 광고는 숏폼 집계에서 제외해요.\n\n형태별 시간은 별도 저장 정보가 부족해 제공하지 않아요. 콘텐츠 카테고리만으로 사용 목적을 판단하지 않아요.")
                .setPositiveButton("확인", null).show()
        }
    }
    private fun text(value: String, size: Float = 12f, color: Int = R.color.ui_text_secondary) = PrototypeUi.text(context, value, size, color).apply {
        (layoutParams as LinearLayout.LayoutParams).topMargin = PrototypeUi.dp(context, 3)
        setLineSpacing(0f, 1f)
    }
    private fun row(label: String, value: String) = PrototypeUi.row(context, label, value).apply {
        for (index in 0 until childCount) (getChildAt(index) as TextView).apply {
            textSize = 12f; (layoutParams as LinearLayout.LayoutParams).topMargin = PrototypeUi.dp(context, 3); setLineSpacing(0f, 1f)
        }
    }
    private fun value(seconds: Double?) = seconds?.let { UiFormatters.duration(it.toLong()) } ?: "—"
    private fun count(number: Double?) = number?.let { String.format(Locale.KOREA, "%.1f개", it) } ?: "—"
    private fun metricValue(seconds: Long, number: Long, metric: StatsMetric) = if (metric == StatsMetric.TIME) UiFormatters.duration(seconds) else "${number}개"

    fun render(value: StatsDashboard, state: StatsUiState) {
        binding.panelUsage.isVisible = state.section == StatsSection.USAGE
        binding.panelContent.isVisible = state.section == StatsSection.CONTENT
        binding.panelGoals.isVisible = state.section == StatsSection.GOALS
        renderUsage(value, state)
        renderContent(value, state)
        renderGoals(state)
    }

    fun renderGoals(state: StatsUiState) {
        binding.tvGoalsNote.text = "변경 전 목표는 소급 평가하지 않아요."
        binding.goalList.removeAllViews()
        if (state.goalEvidenceError != null || !state.goalEvidenceLoaded) {
            binding.goalList.addView(text(state.goalEvidenceError ?: "목표 비교 기록 확인 중")); return
        }
        val goals = state.goals.filter { state.platform == null || state.platform in it.platforms }
        if (goals.isEmpty()) binding.goalList.addView(text("선택한 앱에 설정된 목표가 없어요."))
        goals.forEach { goal ->
            val result = StatsGoalAnalysis.compare(goal, state.plans[goal.metric], state.goalDays, state.quality, state.today)
            val unit = if (goal.metric == GoalMetric.DAILY_COUNT) "개" else "분"
            val label = when (goal.metric) { GoalMetric.DAILY_TIME -> "숏폼 시청 시간"; GoalMetric.DAILY_COUNT -> "숏폼 시청 영상 수"; GoalMetric.APP_TIME -> "앱 사용 시간" }
            fun amount(number: Double?) = number?.let { String.format(Locale.KOREA, "%.1f%s", it, unit) } ?: "—"
            val card = PrototypeUi.column(context).apply { setPadding(PrototypeUi.dp(context, 10), PrototypeUi.dp(context, 8), PrototypeUi.dp(context, 10), PrototypeUi.dp(context, 8)) }
            card.addView(text(label, 14f, R.color.ui_text_primary))
            card.addView(text("${if (goal.status == GoalStatus.ACTIVE) "진행 중" else "중지됨"} · 하루 ${goal.target}$unit · ${goal.platforms.joinToString { it.displayName }}", 11f))
            card.addView(row("기준 → 현재 평균", "${amount(result.baseline)} → ${amount(result.average)}"))
            if (result.average != null && result.baseline != null) {
                val difference = result.average - result.baseline
                card.addView(text("기준 대비 ${amount(abs(difference))} ${if (difference <= 0) "감소" else "증가"}", 12f, R.color.ui_primary))
            }
            card.addView(text(if (result.evaluated > 0) "목표 충족 ${result.achieved} / ${result.evaluated}일" else "목표 충족 여부 확인 불가", 12f))
            card.addView(text(result.notice, 11f))
            binding.goalList.addView(card)
        }
    }

    private fun renderUsage(value: StatsDashboard, state: StatsUiState) {
        val todayOnly = state.period == StatsPeriod.TODAY || state.start == state.end && state.end?.format(HabitInsightsPolicy.format) == state.today
        binding.tvSummaryBasis.text = if (todayOnly) "오늘 사용량 · 진행 중" else "기록일 평균 · 오늘 제외 ${value.averageDays}일"
        binding.summaryCards.removeAllViews()
        val summaries = listOf(
            "숏폼 시간" to if (todayOnly) UiFormatters.duration(value.totalShortformTimeSec) else value(value.averageShortformTimeSec),
            "영상 수" to if (todayOnly) "${value.totalShortformCount}개" else count(value.averageCount),
            "앱 시간" to if (todayOnly) UiFormatters.duration(value.totalAppTimeSec) else value(value.averageAppTimeSec))
        summaries.forEach { (label, amount) -> binding.summaryCards.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(text(label, 11f)); addView(text(amount, 14f, R.color.ui_text_primary))
        }) }
        binding.tvTotals.text = "합계 · 숏폼 ${UiFormatters.duration(value.totalShortformTimeSec)} · ${value.totalShortformCount}개 · 앱 ${UiFormatters.duration(value.totalAppTimeSec)}"
        binding.tvDailyTitle.text = "날짜별 변화 · ${if (state.metric == StatsMetric.TIME) "분" else "개"}"
        val current = if (state.metric == StatsMetric.TIME) value.averageShortformTimeSec else value.averageCount
        val previous = if (state.metric == StatsMetric.TIME) value.previousTimeAverage else value.previousCountAverage
        binding.tvComparison.text = when {
            todayOnly -> "오늘은 진행 중이라 이전 완료일과 비교하지 않아요."
            current == null || previous == null -> "이전 기간과 비교할 기록이 부족해요."
            else -> {
                val difference = current - previous
                val amount = if (state.metric == StatsMetric.TIME) value(abs(difference)) else count(abs(difference))
                "이전 기간 대비 $amount ${if (difference <= 0) "감소" else "증가"} · 이전 ${value.previousAverageDays}개 기록일 평균"
            }
        }
        chart(binding.chartDaily, value.days.mapIndexedNotNull { index, day -> day.statistics?.let {
            BarEntry(index.toFloat(), if (state.metric == StatsMetric.TIME) it.totalShortformTimeSec / 60f else it.totalShortformCount.toFloat())
        } }, value.days.map { "${it.date.substring(4, 6)}/${it.date.takeLast(2)}" }, if (state.metric == StatsMetric.TIME) "분" else "개")
        binding.chartDaily.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(entry: Entry?, highlight: Highlight?) { entry?.x?.toInt()?.let { value.days.getOrNull(it)?.let { day -> selectDate(day.date) } } }
            override fun onNothingSelected() = Unit
        })
        binding.dayDetail.removeAllViews()
        val day = value.days.firstOrNull { it.date == state.selectedDate }
        binding.dayDetail.isVisible = day != null
        day?.let {
            binding.dayDetail.addView(text("${it.date.substring(4, 6)}/${it.date.takeLast(2)}${if (it.date == state.today) " · 진행 중" else ""}", 13f, R.color.ui_text_primary))
            if (it.statistics == null) binding.dayDetail.addView(text("기록 없음"))
            else it.statistics.appUsage.forEach { (key, usage) ->
                binding.dayDetail.addView(text("${SupportedPlatform.fromStorageKey(key)?.displayName ?: key} · 숏폼 ${UiFormatters.duration(usage.shortformTimeSec)} / ${usage.shortformCount}개 · 앱 ${UiFormatters.duration(usage.runTimeSec)}", 12f))
            }
        }
        binding.platformList.removeAllViews()
        if (value.platforms.isEmpty()) binding.platformList.addView(text("선택한 앱의 기록이 없어요."))
        value.platforms.forEach { platform ->
            binding.platformList.addView(row(platform.displayName, metricValue(platform.shortformTimeSec, platform.shortformCount, state.metric)))
            binding.platformList.addView(text("앱 ${UiFormatters.duration(platform.appTimeSec)} · 숏폼 ${UiFormatters.duration(platform.shortformTimeSec)} · ${platform.shortformCount}개", 11f))
        }
        binding.tvUsageRatio.text = if (value.totalAppTimeSec > 0 && value.totalShortformTimeSec <= value.totalAppTimeSec)
            "앱 사용 중 숏폼 ${value.totalShortformTimeSec * 100 / value.totalAppTimeSec}% · 앱 시간에 숏폼 포함" else "앱 시간에 숏폼 시간이 포함돼요."
        binding.chartHourly.isVisible = state.platform == null
        binding.tvHourlyNote.text = if (state.platform != null) "시간대별 기록은 전체 앱 합계만 제공해요." else
            "영상 수 합계 · ${value.hourlyCounts.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { "가장 많은 시간 ${it.index}시" } ?: "기록 없음"}"
        if (state.platform == null) chart(binding.chartHourly, value.hourlyCounts.mapIndexed { hour, number -> BarEntry(hour.toFloat(), number.toFloat()) }, (0..23).map { "${it}시" }, "개")
        else binding.chartHourly.clear()
    }

    private fun renderContent(value: StatsDashboard, state: StatsUiState) {
        binding.categoryList.removeAllViews()
        binding.tvCategoryScope.text = if (state.platform == null) "전체 앱 · ${if (state.metric == StatsMetric.TIME) "시청 시간" else "영상 수"} 기준 · 선택하면 날짜별 기록" else "앱별 카테고리는 아직 저장하지 않아요. ‘전체’를 선택해 주세요."
        if (state.platform != null) { binding.tvQualityNotice.text = ""; return }
        val rows = value.categories.filter { if (state.metric == StatsMetric.TIME) it.durationSec > 0 else it.count > 0 }
            .sortedByDescending { if (state.metric == StatsMetric.TIME) it.durationSec else it.count }
        val total = rows.sumOf { if (state.metric == StatsMetric.TIME) it.durationSec else it.count }
        if (rows.isEmpty()) binding.categoryList.addView(text("아직 콘텐츠 분류 기록이 없어요."))
        rows.forEach { category ->
            val amount = if (state.metric == StatsMetric.TIME) category.durationSec else category.count
            val item = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; minimumHeight = PrototypeUi.dp(context, 48) }
            item.addView(row(category.label, "${metricValue(category.durationSec, category.count, state.metric)} · ${if (total > 0) amount * 100 / total else 0}%"))
            item.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000; progress = if (total > 0) (amount.toDouble() / total * 1000).toInt() else 0
                progressTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ui_primary))
                progressBackgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ui_soft))
                layoutParams = LinearLayout.LayoutParams(-1, PrototypeUi.dp(context, 4)).apply { topMargin = PrototypeUi.dp(context, 4) }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            item.isFocusable = true
            item.setOnClickListener {
                val lines = value.days.map { day ->
                    val matching = day.statistics?.categoryUsage?.filterKeys { ContentCategory.fromStored(it).id == category.id }?.values
                    val amountText = if (day.statistics == null) "기록 없음" else metricValue(matching.orEmpty().sumOf { it.durationSec }, matching.orEmpty().sumOf { it.count }, state.metric)
                    "${day.date.substring(4, 6)}/${day.date.takeLast(2)} · $amountText"
                }
                MaterialAlertDialogBuilder(context).setTitle(category.label).setItems(lines.toTypedArray(), null).setPositiveButton("닫기", null).show()
            }
            binding.categoryList.addView(item)
        }
        binding.tvQualityNotice.text = if (value.categorySampleLimited) "미확인·분류 대기 등으로 전체 사용량과 다를 수 있어요." else "비중은 카테고리 기록 기준이에요."
    }

    private fun chart(chart: BarChart, entries: List<BarEntry>, labels: List<String>, unit: String) {
        val textColor = ContextCompat.getColor(context, R.color.ui_text_secondary)
        chart.setNoDataText("기록 없음"); chart.setNoDataTextColor(textColor)
        if (entries.isEmpty()) { chart.clear(); return }
        chart.data = BarData(BarDataSet(entries, unit).apply { color = ContextCompat.getColor(context, R.color.ui_primary); setDrawValues(false) })
        chart.xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; valueFormatter = IndexAxisValueFormatter(labels); granularity = 1f; setLabelCount(4, false); setDrawGridLines(false); this.textColor = textColor; textSize = 10f; axisMinimum = -0.5f; axisMaximum = labels.lastIndex + 0.5f }
        chart.axisLeft.apply { axisMinimum = 0f; this.textColor = textColor; textSize = 10f }
        chart.axisRight.isEnabled = false; chart.description.isEnabled = false; chart.legend.isEnabled = false
        chart.setScaleEnabled(false); chart.invalidate()
        chart.contentDescription = entries.joinToString { "${labels.getOrNull(it.x.toInt()).orEmpty()} ${it.y}$unit" }
    }
    fun clear() { binding.chartDaily.clear(); binding.chartHourly.clear() }
}
