package com.example.dopaminecut2.statistics

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.dopaminecut2.data.local.*
import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.data.repository.UserRepositoryInterface
import com.example.dopaminecut2.domain.*
import java.time.*

data class InsightSnapshot(val days: List<DailyStatistics>, val quality: List<DayQuality>,
    val today: String, val plans: Map<GoalMetric, GoalPlan>, val time21: Map<String, Long>) {
    fun baseline(metric: GoalMetric, platforms: Set<SupportedPlatform>) =
        HabitInsightsPolicy.baseline(days, quality, today, metric, platforms)
    fun valid(date: String) = quality.any { it.date == date && it.valid }
}

class HabitInsightsRepository(private val context: Context, private val repository: UserRepositoryInterface,
    private val local: DataStoreManager, private val store: HabitStore) {
    suspend fun load(uid: String, localOnly: Boolean = false): InsightSnapshot {
        val today = LocalDate.now(); val end = today.format(HabitInsightsPolicy.format)
        val start = today.minusDays(35).format(HabitInsightsPolicy.format)
        val days = if (localOnly) local.getRange(uid, start, end).map { it.toDailyStatistics() }
            else repository.getStatisticsRange(uid, start, end).getOrThrow()
        val data = store.snapshot(uid); val started = data.optLong("startedAt", Long.MAX_VALUE)
        val pending = local.pendingRecognitions(uid).groupBy { it.date }.mapValues { it.value.sumOf { p -> p.durationSec } }
        val raw = data.optJSONObject("quality")
        val quality = days.map { day ->
            val date = LocalDate.parse(day.date, HabitInsightsPolicy.format)
            val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val expected = screenOnSeconds(dayStart, minOf(dayEnd, System.currentTimeMillis()))
            DayQuality(day.date, raw?.optJSONObject(day.date)?.optLong("observed") ?: 0,
                expected, pending[day.date] ?: 0,
                day.date < end && started <= dayStart && System.currentTimeMillis() - started >= 72 * 3_600_000L &&
                    (pending[day.date] ?: 0) <= (day.totalShortformTimeSec + (pending[day.date] ?: 0)) * 0.10)
        }
        val times = data.optJSONObject("time21")
        return InsightSnapshot(days, quality, end, store.plans(uid), times?.keys()?.asSequence()
            ?.associateWith { times.getLong(it) }.orEmpty())
    }

    /** No evidence is safer than declaring a day valid without screen-state history. */
    fun screenOnSeconds(start: Long, end: Long): Long? = runCatching {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(start - 86_400_000, end)
        val event = UsageEvents.Event(); var state: Boolean? = null; var since = start; var total = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val next = when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> false
                else -> continue
            }
            if (event.timeStamp < start) { state = next; continue }
            if (state == null) return@runCatching null
            if (state == true) total += (event.timeStamp - since).coerceAtLeast(0)
            since = event.timeStamp; state = next
        }
        if (state == null) null else (total + if (state == true) end - since else 0).coerceAtLeast(0) / 1000
    }.getOrNull()
}
