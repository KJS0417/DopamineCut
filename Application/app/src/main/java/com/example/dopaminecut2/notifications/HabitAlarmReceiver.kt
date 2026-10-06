package com.example.dopaminecut2.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.dopaminecut2.DopamineCutApplication
import com.example.dopaminecut2.R
import com.example.dopaminecut2.auth.AuthActivity
import com.example.dopaminecut2.data.local.*
import com.example.dopaminecut2.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

object HabitAlarmScheduler {
    private fun intent(context: Context, hour: Int) = PendingIntent.getBroadcast(context, hour,
        Intent(context, HabitAlarmReceiver::class.java).putExtra("hour", hour), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (hour in listOf(8, 12, 21, -1)) {
            val now = ZonedDateTime.now()
            val target = if (hour == -1) now.plusHours(2).let { candidate ->
                if (candidate.hour >= 22 || candidate.hour < 8) now.withHour(8).withMinute(0).withSecond(0).withNano(0).let { if (it <= now) it.plusDays(1) else it }
                else candidate
            } else now.withHour(hour).withMinute(0).withSecond(0).withNano(0).let { if (it <= now) it.plusDays(1) else it }
            // Approximate alarms: no exact-alarm permission or exemption from Android battery policies.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.toInstant().toEpochMilli(), intent(context, hour))
        }
    }
    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        listOf(8, 12, 21, -1).forEach { alarms.cancel(intent(context, it)) }
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }
}

class HabitAlarmReceiver : BroadcastReceiver() {
    private companion object { val evaluationMutex = Mutex() }
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(25_000) { evaluationMutex.withLock {
                val d = (context.applicationContext as DopamineCutApplication).dependenciesOrNull() ?: return@withTimeout
                val uid = d.authRepository.currentUserId() ?: run { HabitAlarmScheduler.cancel(context); return@withTimeout }
                if (intent.action != null) { HabitAlarmScheduler.schedule(context); return@withTimeout }
                val settings = d.habitStore.settings(uid).first()
                if (!settings.measurement) { HabitAlarmScheduler.schedule(context); return@withTimeout }
                val hour = intent.getIntExtra("hour", -1)
                val now = ZonedDateTime.now()
                // Late alarms must not deliver a backlog or compare today's later data with 21:00 data.
                if (hour >= 0 && (now.hour != hour || now.minute >= 30)) { HabitAlarmScheduler.schedule(context); return@withTimeout }
                val prefs = d.notificationSettingsStore.observeNotificationSettings(uid).first()
                val snapshot = d.habitInsights.load(uid, localOnly = true)
                val goals = d.goalStore.observeGoals(uid).first()
                val data = d.habitStore.snapshot(uid)
                val today = snapshot.today
                val ledger = data.optJSONObject("notifications") ?: org.json.JSONObject()
                val dateLedger = ledger.optJSONObject(today) ?: org.json.JSONObject()
                val used = dateLedger.optJSONArray("handled")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
                val morning = HabitNotificationPolicy.morning(snapshot, goals)
                val order = dateLedger.optJSONArray("order")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                    ?: (if (hour == 8 || hour == 12) morning.shuffled().map { it.id }.also { ids -> d.habitStore.updateNotifications(uid) { l ->
                        val row = l.optJSONObject(today) ?: org.json.JSONObject().also { l.put(today, it) }; row.put("order", org.json.JSONArray(ids))
                    } } else emptyList())
                val milestones = HabitNotificationPolicy.milestones(snapshot, goals).filter { it.onceKey == null || !ledger.has(it.onceKey) }
                val slot = when (hour) { 8 -> NotificationOption.MORNING; 12 -> NotificationOption.LUNCH; 21 -> NotificationOption.EVENING; else -> null }
                var notice: HabitNotice? = null
                if (slot != null && prefs.isEnabled(slot) && !dateLedger.optBoolean("slot_$hour")) {
                    notice = when (hour) {
                        8 -> HabitNotificationPolicy.pickMorningOrLunch(morning, order, used)
                        12 -> HabitNotificationPolicy.pickMorningOrLunch(morning, order, used) ?: milestones.filter { it.id in setOf("N13", "N14") }.shuffled().firstOrNull()
                        else -> HabitNotificationPolicy.evening(snapshot, activelyUsing(context))
                    }
                }
                if (hour == -1 && now.hour in 8..21 && prefs.isEnabled(NotificationOption.MEASUREMENT_INTERRUPTION) &&
                    d.onboardingStore.observeOnboarding(uid).first().stage == OnboardingStage.COMPLETE &&
                    data.optLong("heartbeat") > 0 && (!d.measurementPermissionChecker.currentState().accessibilityGranted ||
                        context.getSystemService(android.os.PowerManager::class.java).isInteractive && System.currentTimeMillis() - data.optLong("heartbeat") > 120_000) &&
                    dateLedger.optInt("interruptions") < 3 && System.currentTimeMillis() - dateLedger.optLong("lastInterruption") >= 7_200_000)
                    notice = HabitNotice("N17", "측정 연결이 끊겼어요. 설정을 확인하면 기록을 이어갈 수 있어요.", "settings")
                if (notice == null && hour == -1 && prefs.isEnabled(NotificationOption.GOAL_PROGRESS) && now.hour in 8..21) {
                    if (!ledger.optBoolean("ready") && goals.isEmpty() && snapshot.baseline(GoalMetric.DAILY_TIME, ManagedGoal.SUPPORTED) != null)
                        notice = HabitNotice("N01", "기록이 쌓였어요. 줄이고 싶은 항목을 골라보세요.", "goals", "ready")
                    else notice = milestones.firstOrNull { it.id == "N15" }
                }
                if (notice == null && hour == -1 && prefs.isEnabled(NotificationOption.GOAL_PROGRESS) && now.hour in 8..21 && activelyUsing(context)) {
                    val todayUsage = snapshot.days.firstOrNull { it.date == today }
                    val interventionHistory = d.interventionLedgerStore.loadInterventionLedger(uid, today)
                    for (goal in goals.filter { it.status == GoalStatus.ACTIVE && it.intervention != InterventionMode.RECORD_ONLY }) {
                        if (!activelyUsing(context, goal.platforms)) continue
                        val goalKey = "${goal.metric}_${goal.revision}"
                        val reachedKey = "${goalKey}_REACHED"
                        if (reachedKey !in interventionHistory.notified) continue
                        val usedAmount = todayUsage?.let { day -> goal.platforms.sumOf { p -> HabitInsightsPolicy.value(
                            day.appUsage[p.storageKey] ?: com.example.dopaminecut2.data.model.AppUsage(), goal.metric) } } ?: continue
                        val reachedAt = dateLedger.optLong("reached_$goalKey")
                        if (reachedAt == 0L) d.habitStore.updateNotifications(uid) { l ->
                            val row = l.optJSONObject(today) ?: org.json.JSONObject().also { l.put(today, it) }
                            row.put("reached_$goalKey", System.currentTimeMillis())
                        }
                        else if (System.currentTimeMillis() - reachedAt >= 3_600_000 && usedAmount >= goal.target + 10 && !dateLedger.optBoolean("followup_$goalKey")) {
                            notice = HabitNotice("N04", "오늘 ${if (goal.metric == GoalMetric.DAILY_COUNT) "숏폼 영상 수" else "사용 시간"}는 목표보다 ${(usedAmount - goal.target).toInt()}${if (goal.metric == GoalMetric.DAILY_COUNT) "회" else "분"} 많아요. 잠깐 쉬어갈까요?", "goals")
                            d.habitStore.updateNotifications(uid) { l -> l.getJSONObject(today).put("followup_$goalKey", true) }
                            break
                        }
                    }
                }
                // Reserve before notification: restart/retry cannot send the same content twice.
                d.habitStore.updateNotifications(uid) { l ->
                    val row = l.optJSONObject(today) ?: org.json.JSONObject().also { l.put(today, it) }
                    if (hour >= 0) row.put("slot_$hour", true)
                    notice?.let { n ->
                        val handledNow = row.optJSONArray("handled")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
                        row.put("handled", org.json.JSONArray((handledNow + n.id).toList()))
                        n.onceKey?.let { l.put(it, true) }
                        if (n.id == "N17") row.put("interruptions", row.optInt("interruptions") + 1).put("lastInterruption", System.currentTimeMillis())
                    }
                    l.keys().asSequence().filter { it.matches(Regex("[0-9]{8}")) }.sortedDescending().drop(14).toList().forEach(l::remove)
                }
                if (d.authRepository.currentUserId() == uid && d.habitStore.settings(uid).first().measurement &&
                    !(context.applicationContext as DopamineCutApplication).isUiVisible) notice?.let { show(context, uid, it) }
                HabitAlarmScheduler.schedule(context)
            } } } catch (e: Exception) {
                android.util.Log.w("HabitAlarm", "알림 평가 실패; 오래된 알림은 재발송하지 않습니다.", e)
                HabitAlarmScheduler.schedule(context)
            } finally { pending.finish() }
        }
    }
    private fun activelyUsing(context: Context, apps: Set<SupportedPlatform> = ManagedGoal.SUPPORTED): Boolean = runCatching {
        val now = System.currentTimeMillis()
        (context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager)
            .queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 120_000, now)
            .any { it.packageName in apps.map { app -> app.packageName } && now - it.lastTimeUsed < 120_000 }
    }.getOrDefault(false)
    private fun show(context: Context, uid: String, notice: HabitNotice) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("habit_changes", "사용 습관 변화", NotificationManager.IMPORTANCE_DEFAULT).apply { lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE })
        val tap = PendingIntent.getActivity(context, notice.id.hashCode(), Intent(context, AuthActivity::class.java)
            .putExtra("habit_destination", notice.destination).putExtra("habit_owner", uid), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(notice.id.hashCode(), NotificationCompat.Builder(context, "habit_changes")
            .setSmallIcon(R.drawable.ic_goal_notification).setContentTitle("DopamineCut")
            .setContentText(notice.text).setStyle(NotificationCompat.BigTextStyle().bigText(notice.text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, "habit_changes").setSmallIcon(R.drawable.ic_goal_notification).setContentTitle("DopamineCut").setContentText("오늘 사용 기록을 확인해 보세요.").build())
            .setContentIntent(tap).setAutoCancel(true).build())
    }
}
