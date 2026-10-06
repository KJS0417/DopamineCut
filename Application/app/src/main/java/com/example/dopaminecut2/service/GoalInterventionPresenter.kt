package com.example.dopaminecut2.service

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dopaminecut2.R
import com.example.dopaminecut2.domain.*
import com.example.dopaminecut2.logic.*
import com.example.dopaminecut2.main.MainActivity

class GoalInterventionPresenter(private val service: AccessibilityService) {
    private var overlay: View? = null
    val isShowing get() = overlay != null
    private val windows get() = service.getSystemService(WindowManager::class.java)
    fun notify(request: GoalInterventionRequest): Boolean {
        require(request.goal.intervention == InterventionMode.NOTIFY)
        val manager = NotificationManagerCompat.from(service)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) service.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "목표 임박·도달 안내", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 26 && service.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val pending = PendingIntent.getActivity(service, 0, Intent(service, MainActivity::class.java).putExtra("open_goal_settings", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = "${label(request.goal.metric)} 목표에 ${if (request.threshold == GoalThreshold.NEAR) "가까워졌어요" else "도달했어요"}. 잠깐 쉬어볼까요?"
        try {
            manager.notify(request.goal.metric.ordinal + 400, NotificationCompat.Builder(service, CHANNEL)
                .setSmallIcon(R.drawable.ic_goal_notification).setContentTitle("DopamineCut 사용 안내").setContentText(text)
                .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
            return true
        } catch (_: SecurityException) { return false }
    }
    fun notifyRest(minutes: Int): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(service, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        service.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "목표 임박·도달 안내", NotificationManager.IMPORTANCE_DEFAULT))
        val tap = PendingIntent.getActivity(service, 401, Intent(service, MainActivity::class.java).putExtra("open_goal_settings", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return runCatching {
            NotificationManagerCompat.from(service).notify(491, NotificationCompat.Builder(service, CHANNEL)
                .setSmallIcon(R.drawable.ic_goal_notification).setContentTitle("DopamineCut 사용 안내")
                .setContentText("숏폼을 ${minutes}분 연속 보고 있어요. 잠깐 쉬었다 볼까요?")
                .setContentIntent(tap).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).build())
            true
        }.getOrDefault(false)
    }
    fun show(goal: ManagedGoal, finish: () -> Unit, extend: () -> Unit, settings: () -> Unit) {
        require(goal.intervention == InterventionMode.CONFIRM || goal.intervention == InterventionMode.RESTRICT)
        if (isShowing) return
        val content = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }
        content.addView(TextView(service).apply {
            text = "${label(goal.metric)} 목표에 도달했어요\n${goal.platforms.joinToString { it.displayName }} · 일일 ${goal.target}${if (goal.metric == GoalMetric.DAILY_COUNT) "개" else "분"}\n${if (goal.metric == GoalMetric.APP_TIME) "선택한 앱 사용" else "숏폼 시청"}에만 적용합니다.\n${if (goal.intervention == InterventionMode.CONFIRM) "여기서 마치거나, 이 목표의 개입을 5분 미룰 수 있어요. 다른 목표는 그대로 적용돼요." else "이 목표에는 5분 연장이 없어요. 변경하려면 목표 설정에서 수정하거나 중지해 주세요."}"
            setTextColor(Color.BLACK); textSize = 18f
        })
        fun button(label: String, action: () -> Unit) {
            content.addView(Button(service).apply { text = label; setOnClickListener { action() } })
        }
        button("여기서 마치기", finish)
        if (goal.intervention == InterventionMode.CONFIRM) button("5분 더 보기", extend)
        button("목표 설정·중지", settings)
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        windows.addView(content, params)
        overlay = content
    }
    fun dismiss() { overlay?.let { runCatching { windows.removeView(it) } }; overlay = null }
    fun clearNotifications() {
        GoalMetric.entries.forEach { NotificationManagerCompat.from(service).cancel(it.ordinal + 400) }
        NotificationManagerCompat.from(service).cancel(491)
    }
    fun clearNotification(metric: GoalMetric) { NotificationManagerCompat.from(service).cancel(metric.ordinal + 400) }
    private fun label(metric: GoalMetric) = when (metric) { GoalMetric.DAILY_TIME -> "숏폼 시청 시간"; GoalMetric.DAILY_COUNT -> "시청 영상 수"; GoalMetric.APP_TIME -> "앱 사용시간" }
    private companion object { const val CHANNEL = "goal_interventions" }
}
