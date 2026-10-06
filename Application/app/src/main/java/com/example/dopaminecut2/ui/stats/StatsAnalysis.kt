package com.example.dopaminecut2.ui.stats

import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.domain.ContentCategory
import com.example.dopaminecut2.domain.SupportedPlatform
import com.example.dopaminecut2.domain.HabitInsightsPolicy
import com.example.dopaminecut2.statistics.UsageSummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Missing dates stay null, not zero. Today is excluded from completed-record averages. */
object StatsAnalysis {
    fun validRange(start: LocalDate, end: LocalDate, today: LocalDate) =
        !end.isBefore(start) && !end.isAfter(today) && ChronoUnit.DAYS.between(start, end) < 90

    fun dashboard(raw: List<DailyStatistics>, start: LocalDate, end: LocalDate, today: String,
        platform: SupportedPlatform?): StatsDashboard {
        val format = HabitInsightsPolicy.format
        val current = raw.filter { it.date in start.format(format)..end.format(format) }.distinctBy { it.date }
        fun scope(values: List<DailyStatistics>) = values.map { day ->
            if (platform == null) day else day.copy(appUsage = day.appUsage.filterKeys { it == platform.storageKey },
                categoryUsage = emptyMap(), hourlyShortformCount = emptyMap())
        }
        val scoped = scope(current)
        val completed = scoped.filter { it.date < today }
        val length = ChronoUnit.DAYS.between(start, end) + 1
        val previous = scope(raw.filter { it.date in start.minusDays(length).format(format)..start.minusDays(1).format(format) })
            .distinctBy { it.date }.filter { it.date < today }
        val summary = UsageSummary.from(scoped)
        fun average(days: List<DailyStatistics>, value: (DailyStatistics) -> Long): Double? =
            days.takeIf { it.isNotEmpty() }?.map { value(it).toDouble() }?.average()
        val byDate = scoped.associateBy { it.date }
        val categories = summary.categoryUsage.entries.groupBy { ContentCategory.fromStored(it.key) }.map { (category, entries) ->
            StatsCategoryUi(category.id, category.label, entries.sumOf { it.value.count }, entries.sumOf { it.value.durationSec })
        }
        return StatsDashboard(
            platforms = summary.appUsage.map { (key, usage) -> StatsPlatformUi(
                SupportedPlatform.fromStorageKey(key)?.displayName ?: key, usage.runTimeSec,
                usage.shortformTimeSec, usage.shortformCount) }.sortedByDescending { it.shortformTimeSec },
            hourlyCounts = summary.hourlyShortformCount, categories = categories,
            days = (0 until length.toInt()).map { offset -> start.plusDays(offset.toLong()).format(format).let { StatsDayUi(it, byDate[it]) } },
            recordedDays = scoped.size, averageDays = completed.size,
            totalAppTimeSec = summary.totalAppTimeSec, totalShortformTimeSec = summary.totalShortformTimeSec,
            totalShortformCount = summary.totalShortformCount,
            averageAppTimeSec = average(completed) { it.totalAppTimeSec },
            averageShortformTimeSec = average(completed) { it.totalShortformTimeSec },
            averageCount = average(completed) { it.totalShortformCount },
            previousTimeAverage = average(previous) { it.totalShortformTimeSec },
            previousCountAverage = average(previous) { it.totalShortformCount }, previousAverageDays = previous.size,
            categorySampleLimited = categories.any { it.id == ContentCategory.UNKNOWN.id } ||
                categories.sumOf { it.durationSec } != summary.totalShortformTimeSec ||
                categories.sumOf { it.count } != summary.totalShortformCount
        )
    }
}
