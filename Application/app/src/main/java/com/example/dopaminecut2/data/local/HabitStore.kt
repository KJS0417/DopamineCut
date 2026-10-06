package com.example.dopaminecut2.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dopaminecut2.domain.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.habitData by preferencesDataStore(name = "habit_insights_v1")
data class AnalysisSettings(val measurement: Boolean = true, val content: Boolean = true)
data class GoalPlan(val metric: GoalMetric, val revision: Long, val startedDate: String,
    val baseline: GoalBaseline?, val excludedDates: Set<String> = emptySet(), val pausedSince: String? = null,
    val continuousBreakMinutes: Int = 0) {
    fun eligible(date: String): Boolean = date > startedDate && date !in excludedDates && (pausedSince == null || date < pausedSince)
}

/** Local device evidence and fixed goal baselines are not uploaded as raw activity logs. */
class HabitStore(private val context: Context) {
    private fun key(uid: String) = stringPreferencesKey("habit_$uid")
    private suspend fun mutate(uid: String, block: (JSONObject) -> Unit) {
        require(uid.isNotBlank())
        context.habitData.edit { prefs ->
            val data = JSONObject(prefs[key(uid)] ?: "{}"); block(data); prefs[key(uid)] = data.toString()
        }
    }
    suspend fun snapshot(uid: String): JSONObject = JSONObject(context.habitData.data.first()[key(uid)] ?: "{}")
    fun settings(uid: String) = context.habitData.data.map { prefs ->
        val data = JSONObject(prefs[key(uid)] ?: "{}")
        AnalysisSettings(data.optBoolean("measurement", true), data.optBoolean("content", true))
    }
    suspend fun setSettings(uid: String, settings: AnalysisSettings) = mutate(uid) {
        it.put("measurement", settings.measurement).put("content", settings.content)
    }
    suspend fun heartbeat(uid: String, date: String, seconds: Long, before21: Boolean, now: Long) = mutate(uid) {
        if (!it.has("startedAt")) it.put("startedAt", now)
        val days = it.optJSONObject("quality") ?: JSONObject().also { d -> it.put("quality", d) }
        val day = days.optJSONObject(date) ?: JSONObject().also { d -> days.put(date, d) }
        day.put("observed", day.optLong("observed") + seconds)
        val times = it.optJSONObject("time21") ?: JSONObject().also { d -> it.put("time21", d) }
        if (!times.has(date)) times.put(date, 0)
        if (before21) day.put("observed21", day.optLong("observed21") + seconds)
        it.put("heartbeat", now)
        days.keys().asSequence().sortedDescending().drop(45).toList().forEach(days::remove)
    }
    suspend fun addTime(uid: String, date: String, seconds: Long, before21: Boolean) = mutate(uid) {
        val times = it.optJSONObject("time21") ?: JSONObject().also { d -> it.put("time21", d) }
        if (before21) times.put(date, times.optLong(date) + seconds)
        times.keys().asSequence().sortedDescending().drop(45).toList().forEach(times::remove)
    }
    suspend fun savePlan(uid: String, plan: GoalPlan) = mutate(uid) {
        val plans = it.optJSONObject("plans") ?: JSONObject().also { p -> it.put("plans", p) }
        val row = JSONObject().put("revision", plan.revision).put("startedDate", plan.startedDate)
            .put("excludedDates", org.json.JSONArray(plan.excludedDates.toList()))
            .put("continuousBreakMinutes", plan.continuousBreakMinutes)
        plan.pausedSince?.let { row.put("pausedSince", it) }
        plan.baseline?.let { b ->
            row.put("dates", org.json.JSONArray(b.dates))
            row.put("averages", JSONObject(b.averages.mapKeys { it.key.name }))
        }
        plans.put(plan.metric.name, row)
    }
    suspend fun plans(uid: String): Map<GoalMetric, GoalPlan> {
        val plans = snapshot(uid).optJSONObject("plans") ?: return emptyMap()
        return plans.keys().asSequence().associate { name ->
            val metric = GoalMetric.valueOf(name); val row = plans.getJSONObject(name)
            val values = row.optJSONObject("averages")
            val baseline = values?.let { v ->
                val averages = v.keys().asSequence().associate { SupportedPlatform.valueOf(it) to v.getDouble(it) }
                val dates = row.getJSONArray("dates")
                GoalBaseline(metric, averages.keys, averages, (0 until dates.length()).map { dates.getString(it) })
            }
            val excluded = row.optJSONArray("excludedDates")
            metric to GoalPlan(metric, row.getLong("revision"), row.getString("startedDate"), baseline,
                excluded?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty(),
                row.optString("pausedSince").takeIf { it.isNotBlank() }, row.optInt("continuousBreakMinutes"))
        }
    }
    suspend fun deleteEvidence(uid: String, date: String?) = mutate(uid) { data ->
        if (date == null) { data.remove("quality"); data.remove("time21"); data.remove("plans"); data.remove("startedAt") }
        else { data.optJSONObject("quality")?.remove(date); data.optJSONObject("time21")?.remove(date) }
        data.remove("notifications")
    }
    suspend fun updateNotifications(uid: String, block: (JSONObject) -> Unit) = mutate(uid) {
        val ledger = it.optJSONObject("notifications") ?: JSONObject().also { d -> it.put("notifications", d) }
        block(ledger)
    }
}
