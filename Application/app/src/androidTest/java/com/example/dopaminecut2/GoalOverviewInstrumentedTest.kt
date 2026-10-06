package com.example.dopaminecut2

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dopaminecut2.databinding.FragmentGoalBinding
import com.example.dopaminecut2.ui.common.PrototypeUi
import com.example.dopaminecut2.ui.goal.GoalOverviewCard
import org.junit.Assert.*
import org.junit.Test

class GoalOverviewInstrumentedTest {
    @Test fun threePopulatedCardsFitOnPhoneAndActionsRemainSeparate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val configuration = android.content.res.Configuration(instrumentation.targetContext.resources.configuration)
                .apply { fontScale = 1f }
            val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(configuration), R.style.Theme_Dopaminecut2)
            val binding = FragmentGoalBinding.inflate(LayoutInflater.from(context))
            binding.progressLoading.visibility = View.GONE
            var selected = false
            var edits = 0
            var pauses = 0
            val cards = listOf("숏폼 시청 시간", "숏폼 시청 영상 수", "앱 사용 시간").map { title ->
                GoalOverviewCard.create(context, title, "107.0분 · 하루 평균",
                    "YouTube 42분 · Instagram 33분 · KakaoTalk 32분",
                    "진행 중 · 60분/일 · 계속 볼지 확인", false, true, "목표 중지",
                    onSelection = { selected = it }, onEdit = { edits++ }, onStatus = { pauses++ })
            }
            cards.forEach(binding.managedGoalList::addView)
            val width = PrototypeUi.dp(context, 360)
            val height = PrototypeUi.dp(context, 640)
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, width, height)
            val content = binding.root.getChildAt(0)
            assertTrue("All three cards must fit at 360×640dp with standard font scale",
                content.top + binding.managedGoalList.top + cards.last().bottom <= height)
            (cards.first().getChildAt(0) as CheckBox).performClick()
            assertTrue(selected)
            val actions = cards.first().getChildAt(4) as LinearLayout
            actions.getChildAt(0).performClick()
            actions.getChildAt(1).performClick()
            assertEquals(1, edits)
            assertEquals(1, pauses)
            assertTrue(actions.getChildAt(0).height >= PrototypeUi.dp(context, 48))
        }
    }
}
