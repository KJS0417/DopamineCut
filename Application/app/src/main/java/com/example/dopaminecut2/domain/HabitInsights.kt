package com.example.dopaminecut2.domain

import com.example.dopaminecut2.data.model.AppUsage
import com.example.dopaminecut2.data.model.DailyStatistics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

enum class ReductionLevel(val label: String, val percent: Int) {
    REDUCE("줄이기", 15), MORE("더 줄이기", 30), CHALLENGE("도전", 50)
}

data class DayQuality(val date: String, val observedSec: Long, val expectedSec: Long?, val pendingSec: Long,
    val complete: Boolean) {
    val valid: Boolean get() = complete && expectedSec != null && expectedSec > 0 &&
        observedSec.toDouble() / expectedSec >= 0.90 &&
        pendingSec.toDouble() / expectedSec <= 0.10
}

data class GoalBaseline(val metric: GoalMetric, val platforms: Set<SupportedPlatform>,
    val averages: Map<SupportedPlatform, Double>, val dates: List<String>) {
    val average: Double get() = averages.values.sum()
    fun target(level: ReductionLevel): Int = ceil(average * (100 - level.percent) / 100)
        .toInt().coerceAtLeast(0)
    fun describe(target: Int): String {
        val unit = if (metric == GoalMetric.DAILY_COUNT) "회" else "분"
        val amount = average - target
        val percent = if (average > 0) " · 약 ${kotlin.math.abs(amount / average * 100).toInt()}%" else ""
        return if (amount >= 0) "하루 ${"%.1f".format(amount)}$unit 감소$percent\n7일 모두 달성하면 ${"%.1f".format(amount * 7)}$unit 감소 가능"
        else "하루 ${"%.1f".format(-amount)}$unit 완화$percent"
    }
}

object HabitInsightsPolicy {
    val format: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    fun value(usage: AppUsage, metric: GoalMetric): Double = when (metric) {
        GoalMetric.APP_TIME -> usage.runTimeSec / 60.0
        GoalMetric.DAILY_TIME -> usage.shortformTimeSec / 60.0
        GoalMetric.DAILY_COUNT -> usage.shortformCount.toDouble()
    }
    fun baseline(days: List<DailyStatistics>, quality: List<DayQuality>, today: String,
        metric: GoalMetric, platforms: Set<SupportedPlatform>): GoalBaseline? {
        if (platforms.isEmpty()) return null
        val valid = quality.filter { it.valid && it.date < today }.map { it.date }.toSet()
        val selected = days.filter { it.date in valid && it.date < today }.distinctBy { it.date }
            .sortedByDescending { it.date }.take(7)
        if (selected.size < 3) return null
        return GoalBaseline(metric, platforms, platforms.associateWith { p ->
            selected.map { value(it.appUsage[p.storageKey] ?: AppUsage(), metric) }.average()
        }, selected.map { it.date }.sorted())
    }
    fun consecutiveDates(dates: List<String>): Boolean = dates.sorted().zipWithNext().all { (a, b) ->
        LocalDate.parse(a, format).plusDays(1) == LocalDate.parse(b, format)
    }
}
