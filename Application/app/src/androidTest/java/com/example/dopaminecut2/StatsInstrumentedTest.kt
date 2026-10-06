package com.example.dopaminecut2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dopaminecut2.auth.AuthRepository
import com.example.dopaminecut2.data.model.*
import com.example.dopaminecut2.data.repository.UserRepositoryInterface
import com.example.dopaminecut2.databinding.FragmentStatsBinding
import com.example.dopaminecut2.domain.SupportedPlatform
import com.example.dopaminecut2.domain.GoalMetric
import com.example.dopaminecut2.domain.GoalBaseline
import com.example.dopaminecut2.domain.ManagedGoal
import com.example.dopaminecut2.domain.InterventionMode
import com.example.dopaminecut2.domain.DayQuality
import com.example.dopaminecut2.data.local.GoalPlan
import com.example.dopaminecut2.time.DateIdProvider
import com.example.dopaminecut2.ui.common.ContentState
import com.example.dopaminecut2.ui.common.PrototypeUi
import com.example.dopaminecut2.ui.stats.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class StatsInstrumentedTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val end = LocalDate.of(2026, 10, 6)
    private val days = listOf(
        DailyStatistics("20260929", appUsage = mapOf("youtube" to AppUsage(7200, 3600, 12))),
        DailyStatistics("20261001", appUsage = mapOf("youtube" to AppUsage(3600, 1800, 10), "instagram" to AppUsage(2400, 600, 6)),
            categoryUsage = mapOf("GAME" to CategoryUsage(12, 1800), "UNKNOWN" to CategoryUsage(4, 600)), hourlyShortformCount = mapOf("12" to 16)),
        DailyStatistics("20261006", appUsage = mapOf("youtube" to AppUsage(1200, 600, 3)), categoryUsage = mapOf("SPORTS" to CategoryUsage(3, 600)), hourlyShortformCount = mapOf("20" to 3)))

    @Test fun panelsRenderAtPhoneAndWideWidthsWithoutInventedAppCategories() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Dopaminecut2)
            val output = File(context.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
            for (width in listOf(360, 720)) for (section in StatsSection.entries) {
                val binding = FragmentStatsBinding.inflate(LayoutInflater.from(context))
                val value = StatsAnalysis.dashboard(days, start, end, "20261006", null)
                val goal = ManagedGoal(GoalMetric.DAILY_TIME, 20, setOf(SupportedPlatform.YOUTUBE), InterventionMode.NOTIFY, revision = 1)
                val plan = GoalPlan(goal.metric, 1, "20260930", GoalBaseline(goal.metric, goal.platforms,
                    mapOf(SupportedPlatform.YOUTUBE to 40.0), listOf("20260927", "20260928", "20260929")))
                val state = StatsUiState(start = start, end = end, today = "20261006", section = section,
                    goals = listOf(goal), plans = mapOf(goal.metric to plan), goalDays = days,
                    quality = listOf(DayQuality("20261001", 100, 100, 0, true)),
                    goalEvidenceLoaded = true, content = ContentState.Data(value))
                val renderer = StatsDashboardRenderer(binding) {}
                binding.contentGroup.isVisible = true
                binding.tvRange.text = "렌더링 검증 · 예시 기록 2/6일"
                binding.metricGroup.isVisible = section != StatsSection.GOALS
                binding.sectionGroup.check(when (section) { StatsSection.USAGE -> R.id.btn_section_usage; StatsSection.CONTENT -> R.id.btn_section_content; StatsSection.GOALS -> R.id.btn_section_goals })
                renderer.render(value, state)
                assertEquals(2, binding.chartDaily.data.entryCount)
                assertEquals(section == StatsSection.USAGE, binding.panelUsage.isVisible)
                assertEquals(section == StatsSection.CONTENT, binding.panelContent.isVisible)
                assertEquals(section == StatsSection.GOALS, binding.panelGoals.isVisible)
                assertEquals(1, value.averageDays)
                val w = PrototypeUi.dp(context, width); val h = PrototypeUi.dp(context, 780)
                binding.root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                binding.root.layout(0, 0, w, h)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                binding.root.draw(Canvas(bitmap))
                File(output, "stats-${section.name.lowercase()}-$width.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                renderer.render(value, state.copy(section = StatsSection.CONTENT, metric = StatsMetric.COUNT))
                assertTrue(binding.panelContent.isVisible)
                val filtered = StatsAnalysis.dashboard(days, start, end, "20261006", SupportedPlatform.YOUTUBE)
                renderer.render(filtered, state.copy(section = StatsSection.CONTENT, platform = SupportedPlatform.YOUTUBE))
                assertTrue(binding.tvCategoryScope.text.contains("아직 저장하지"))
                assertEquals(0, binding.categoryList.childCount)
                renderer.clear()
            }
        }
    }

    @Test fun filtersReuseOneReadAndInvalidRangeDoesNotQuery() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val repository = FakeRepository(days)
            val vm = StatsViewModel(repository, FakeAuth("a"), DateIdProvider { "20261006" })
            val store = androidx.lifecycle.ViewModelStore().apply { put("stats", vm) }
            try {
                assertEquals(1, repository.calls)
                vm.onAction(StatsAction.SelectPlatform(SupportedPlatform.YOUTUBE))
                vm.onAction(StatsAction.SelectMetric(StatsMetric.COUNT))
                vm.onAction(StatsAction.SelectSection(StatsSection.CONTENT))
                vm.onAction(StatsAction.SelectDate("20261001"))
                assertEquals(1, repository.calls)
                assertEquals(StatsMetric.COUNT, vm.uiState.value.metric)
                assertEquals(StatsSection.CONTENT, vm.uiState.value.section)
                vm.onAction(StatsAction.SelectRange(end.minusDays(90), end))
                assertEquals(1, repository.calls)
                assertNotNull(vm.uiState.value.message)
                assertTrue(vm.uiState.value.content is ContentState.Data)
            } finally { store.clear() }
        }
    }

    @Test fun queryFailureRemainsErrorAndAccountSwitchClearsOldData() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val auth = FakeAuth("a")
            val repository = FakeRepository(days)
            val vm = StatsViewModel(repository, auth, DateIdProvider { "20261006" })
            val store = androidx.lifecycle.ViewModelStore().apply { put("stats", vm) }
            try {
                repository.failed = true
                vm.onAction(StatsAction.Retry)
                assertTrue(vm.uiState.value.content is ContentState.Error)
                vm.onAction(StatsAction.SelectPlatform(SupportedPlatform.YOUTUBE))
                assertTrue(vm.uiState.value.content is ContentState.Error)
                repository.failed = false
                vm.onAction(StatsAction.Retry)
                auth.uid = null
                vm.onAction(StatsAction.SelectMetric(StatsMetric.COUNT))
                assertTrue(vm.uiState.value.content is ContentState.Error)
                assertTrue(vm.uiState.value.goals.isEmpty())
                assertTrue(vm.uiState.value.goalDays.isEmpty())
            } finally { store.clear() }
        }
    }

    private class FakeRepository(private val days: List<DailyStatistics>) : UserRepositoryInterface {
        var calls = 0; var failed = false
        override suspend fun getStatisticsRange(userId: String, startDate: String, endDate: String): Result<List<DailyStatistics>> {
            calls++
            return if (failed) Result.failure(IllegalStateException("검증용 실패")) else Result.success(days)
        }
        override suspend fun getUserInfo(userId: String): Result<User> = Result.failure(UnsupportedOperationException())
        override suspend fun getDailyStatistics(userId: String, date: String) = Result.success<DailyStatistics?>(null)
    }
    private class FakeAuth(var uid: String?) : AuthRepository {
        override fun currentUserId() = uid
        override suspend fun login(email: String, password: String): Result<Unit> = Result.failure(UnsupportedOperationException())
        override suspend fun signup(email: String, password: String, nickname: String): Result<Unit> = Result.failure(UnsupportedOperationException())
        override fun logout() = Result.success(Unit).also { uid = null }
    }
}
