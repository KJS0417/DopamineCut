package com.example.dopaminecut2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dopaminecut2.ui.common.PrototypeUi
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Native layout smoke test. No login, Firebase writes, goal saves or permission changes. */
class PrototypeLayoutInstrumentedTest {
    @Test fun prototypeLayoutsInflateAndRenderAtPhoneAndWideWidths() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Dopaminecut2)
            val output = File(context.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
            val cases = listOf("intervention" to R.layout.fragment_intervention_setup,
                "goal-editor" to R.layout.fragment_goal_editor, "notifications" to R.layout.fragment_settings,
                "goals" to R.layout.fragment_goal, "goals-permission" to R.layout.fragment_goal,
                "home-summary" to R.layout.fragment_home)
            for (width in listOf(360, 720)) for ((name, resource) in cases) {
                val root = LayoutInflater.from(context).inflate(resource, null) as android.view.ViewGroup
                when (name) {
                    "home-summary" -> {
                        root.findViewById<View>(R.id.progress_loading).visibility = View.GONE
                        root.findViewById<View>(R.id.summary_card).visibility = View.VISIBLE
                        root.findViewById<View>(R.id.content_group).visibility = View.VISIBLE
                        root.findViewById<View>(R.id.goal_progress_list).visibility = View.GONE
                        root.findViewById<View>(R.id.goal_preparation_card).visibility = View.VISIBLE
                        root.findViewById<TextView>(R.id.tv_measurement_status).text = "기록 설정 켜짐"
                        root.findViewById<TextView>(R.id.tv_shortform_time).text = "48분"
                        root.findViewById<TextView>(R.id.tv_shortform_count).text = "72개"
                        root.findViewById<TextView>(R.id.tv_app_time).text = "1시간 38분"
                        root.findViewById<TextView>(R.id.tv_goal_preparation).text = "유효 기록 1 / 3일"
                        root.findViewById<TextView>(R.id.tv_goal_summary).text = "오늘 사용량부터 확인할 수 있어요."
                        assertNotNull(root.findViewById<View>(R.id.btn_my_change))
                        assertNotNull(root.findViewById<View>(R.id.btn_detailed_stats))
                    }
                    "goal-editor" -> {
                        listOf(R.id.metric_time, R.id.metric_count, R.id.metric_app_time).forEach {
                            assertNotNull(root.findViewById<CheckBox>(it))
                        }
                        root.findViewById<LinearLayout>(R.id.card_time).isSelected = true
                        root.findViewById<CheckBox>(R.id.metric_time).isChecked = true
                        root.findViewById<TextView>(R.id.summary_time).text = "기록 수집 중"
                        root.findViewById<TextView>(R.id.summary_count).text = "기록 수집 중"
                        root.findViewById<TextView>(R.id.summary_app_time).text = "기록 수집 중"
                        root.findViewById<TextView>(R.id.tv_editing_card).text = "숏폼 시청 시간 · 앱과 개입 방식 설정"
                    }
                    "intervention" -> {
                        root.findViewById<LinearLayout>(R.id.card_record).isSelected = true
                        root.findViewById<android.widget.RadioButton>(R.id.mode_record).isChecked = true
                    }
                    "notifications" -> {
                        com.example.dopaminecut2.ui.settings.SettingsTabs(
                            com.example.dopaminecut2.databinding.FragmentSettingsBinding.bind(root),
                            com.example.dopaminecut2.ui.settings.SettingsSection.NOTIFICATIONS)
                        assertEquals(context.getString(R.string.notification_measurement_interruption_description),
                            "2시간 간격으로 하루 최대 3회 전송해요")
                    }
                    "goals", "goals-permission" -> {
                        root.findViewById<View>(R.id.progress_loading).visibility = View.GONE
                        val permissionRequired = name == "goals-permission"
                        root.findViewById<View>(R.id.permission_notice).visibility = if (permissionRequired) View.VISIBLE else View.GONE
                        root.findViewById<TextView>(R.id.tv_permission_notice).text = "접근성 · 사용정보 접근 권한 필요"
                        val list = root.findViewById<LinearLayout>(R.id.managed_goal_list)
                        listOf("숏폼 시청 시간", "숏폼 시청 영상 수", "앱 사용 시간").forEachIndexed { index, title ->
                            val card = com.example.dopaminecut2.ui.goal.GoalOverviewCard.create(context,
                                title, if (permissionRequired) "권한 필요" else "기록 수집 중",
                                if (permissionRequired) "" else "유효 3일 기록 후 추천", "",
                                index == 0, true, onSelection = {}, onEdit = {}, permissionRequired = permissionRequired)
                            list.addView(card)
                            if (permissionRequired) assertEquals("권한 필요", (card.getChildAt(1) as TextView).text)
                        }
                    }
                }
                val w = PrototypeUi.dp(context, width)
                val h = PrototypeUi.dp(context, 780)
                root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, w, h)
                fun settle(view: View) {
                    view.jumpDrawablesToCurrentState()
                    if (view is android.view.ViewGroup) for (index in 0 until view.childCount) settle(view.getChildAt(index))
                }
                settle(root)
                assertTrue(root.getChildAt(0).measuredHeight > 0)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                File(output, "$name-$width.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (name == "goal-editor") {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(androidx.core.content.ContextCompat.getColor(context, R.color.ui_background))
                    canvas.translate(0f, -root.findViewById<View>(R.id.detail_panel).top.toFloat())
                    root.getChildAt(0).draw(canvas)
                    File(output, "goal-details-$width.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                bitmap.recycle()
            }
        }
    }
}
