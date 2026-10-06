package com.example.dopaminecut2.logic

import com.example.dopaminecut2.data.model.AppUsage
import com.example.dopaminecut2.domain.*
import com.example.dopaminecut2.logic.shortform.*
import org.junit.Assert.*
import org.junit.Test

class ManagedGoalProgressTest {
    private val platform = SupportedPlatform.YOUTUBE
    private fun progress(seconds: Long, count: Long = 1L, kind: ShortformContentKind = ShortformContentKind.NORMAL) =
        ShortformSessionCheckpoint("session", platform, "video", kind, seconds, count)

    @Test fun `no accepted goals means no intervention even with high usage`() {
        val usage = mapOf("youtube" to AppUsage(86400, 80000, 9999))
        assertTrue(ManagedGoalPolicy.evaluate(emptyList(), usage, platform, true, true).isEmpty())
        val paused = ManagedGoal(GoalMetric.DAILY_TIME, 1, setOf(platform), InterventionMode.RESTRICT,
            status = GoalStatus.PAUSED, revision = 1)
        assertTrue(ManagedGoalPolicy.evaluate(listOf(paused), usage, platform, true, true).isEmpty())
    }

    @Test fun `unflushed app time and checkpoint remainder are reflected once`() {
        val stored = mapOf("youtube" to AppUsage(100, 50, 3))
        val result = ManagedGoalProgress.effectiveUsage(stored, platform, 3, progress(12), progress(10))
        assertEquals(AppUsage(103, 52, 3), result["youtube"])
        assertEquals(AppUsage(100, 50, 3), stored["youtube"])
        assertEquals(result, ManagedGoalProgress.effectiveUsage(stored, platform, 3, progress(12), progress(10)))
    }

    @Test fun `first qualified video adds its count before next checkpoint`() {
        assertEquals(AppUsage(5, 5, 1), ManagedGoalProgress.effectiveUsage(emptyMap(), platform, 5, progress(5), null)["youtube"])
    }

    @Test fun `live never adds count even when incoming progress contains a count`() {
        val live = progress(80, 2, ShortformContentKind.LIVE)
        assertEquals(AppUsage(80, 80, 0), ManagedGoalProgress.effectiveUsage(emptyMap(), platform, 80, live, null)["youtube"])
    }

    @Test fun `ad and unknown add only app time`() {
        for (kind in listOf(ShortformContentKind.AD, ShortformContentKind.UNKNOWN)) {
            assertEquals(AppUsage(20, 0, 0), ManagedGoalProgress.effectiveUsage(emptyMap(), platform, 20, progress(20, kind = kind), null)["youtube"])
        }
    }

    @Test fun `previous session checkpoint cannot subtract from current video`() {
        val previous = progress(100).copy(viewSessionId = "previous")
        assertEquals(AppUsage(0, 5, 1), ManagedGoalProgress.effectiveUsage(emptyMap(), platform, 0, progress(5), previous)["youtube"])
    }

    @Test fun `foreground change excludes stale shortform progress`() {
        val result = ManagedGoalProgress.effectiveUsage(emptyMap(), SupportedPlatform.INSTAGRAM, 2, progress(10), null)
        assertEquals(AppUsage(runTimeSec = 2), result["instagram"])
        assertNull(result["youtube"])
    }

    @Test fun `three simultaneous goals use the same live usage snapshot`() {
        val usage = ManagedGoalProgress.effectiveUsage(mapOf("youtube" to AppUsage(59, 59, 0)), platform, 1, progress(5), progress(4, 0))
        val goals = GoalMetric.entries.map { ManagedGoal(it, 1, setOf(platform), InterventionMode.NOTIFY, revision = 1) }
        assertEquals(3, ManagedGoalPolicy.evaluate(goals, usage, platform, true, true).count { it.threshold == GoalThreshold.REACHED })
    }
}
