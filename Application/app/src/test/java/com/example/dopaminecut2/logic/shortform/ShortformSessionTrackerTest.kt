package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.domain.SupportedPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortformSessionTrackerTest {
    @Test
    fun `layout revalidation pause preserves session and counted video`() {
        var now = 0L
        val deltas = mutableListOf<ShortformCountDelta>()
        val completed = mutableListOf<CompletedShortformSession>()
        val tracker = tracker({ now }, deltas, completed)
        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "same-video", ShortformContentKind.NORMAL)
        now = 6_000L
        tracker.tick()
        tracker.setPaused(true)
        now = 12_000L
        tracker.tick()
        // 배치 재확인 중 UNKNOWN은 onScreenChanged로 전달하지 않고 일시중지만 유지한다.
        tracker.setPaused(true)
        now = 15_000L
        tracker.setPaused(false)
        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "same-video", ShortformContentKind.NORMAL)
        now = 20_000L
        tracker.release()
        assertEquals(1, completed.size)
        assertEquals(11L, completed.single().durationSec)
        assertEquals(listOf(1L), deltas.map(ShortformCountDelta::count))
    }

    @Test
    fun `normal view is counted once and completed with full duration`() {
        var now = 0L
        val deltas = mutableListOf<ShortformCountDelta>()
        val completed = mutableListOf<CompletedShortformSession>()
        val tracker = tracker({ now }, deltas, completed)

        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "video-1", ShortformContentKind.NORMAL)
        now = 5_000L
        tracker.tick()
        tracker.tick()
        now = 12_000L
        tracker.release()

        assertEquals(listOf(1L), deltas.map(ShortformCountDelta::count))
        assertEquals(12L, completed.single().durationSec)
        assertEquals(1L, completed.single().count)
    }

    @Test
    fun `normal view emits cumulative checkpoints without duplicating count`() {
        var now = 0L
        val checkpoints = mutableListOf<ShortformSessionCheckpoint>()
        val completed = mutableListOf<CompletedShortformSession>()
        val tracker = ShortformSessionTracker(
            clockMs = { now },
            idFactory = { "session" },
            onCheckpoint = checkpoints::add,
            onSessionCompleted = completed::add
        )

        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "video", ShortformContentKind.NORMAL)
        now = 4_000L
        tracker.tick()
        now = 5_000L
        tracker.tick()
        tracker.tick()
        now = 9_000L
        tracker.tick()
        now = 10_000L
        tracker.tick()
        now = 12_000L
        tracker.release()

        assertEquals(listOf(5L, 10L, 12L), checkpoints.map { it.durationSec })
        assertEquals(listOf(1L, 1L, 1L), checkpoints.map { it.count })
        assertEquals(12L, completed.single().durationSec)
    }

    @Test
    fun `live checkpoints preserve duration without increasing count`() {
        var now = 0L
        val checkpoints = mutableListOf<ShortformSessionCheckpoint>()
        val tracker = ShortformSessionTracker(
            clockMs = { now },
            idFactory = { "live-session" },
            onCheckpoint = checkpoints::add,
            onSessionCompleted = {}
        )

        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "live", ShortformContentKind.LIVE)
        listOf(5_000L, 10_000L, 40_000L, 45_000L, 80_000L).forEach {
            now = it
            tracker.tick()
        }
        tracker.release()

        assertEquals(listOf(5L, 10L, 40L, 45L, 80L), checkpoints.map { it.durationSec })
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), checkpoints.map { it.count })
    }

    @Test
    fun `live view never emits count even past forty seconds`() {
        var now = 0L
        val deltas = mutableListOf<ShortformCountDelta>()
        val completed = mutableListOf<CompletedShortformSession>()
        val tracker = tracker({ now }, deltas, completed)

        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "live-1", ShortformContentKind.LIVE)
        now = 39_000L
        tracker.tick()
        now = 40_000L
        tracker.tick()
        now = 81_000L
        tracker.tick()
        tracker.release()

        assertEquals(emptyList<Long>(), deltas.map(ShortformCountDelta::count))
        assertEquals(81L, completed.single().durationSec)
        assertEquals(0L, completed.single().count)
    }

    @Test
    fun `ad does not produce time or count records`() {
        var now = 0L
        val deltas = mutableListOf<ShortformCountDelta>()
        val completed = mutableListOf<CompletedShortformSession>()
        val tracker = tracker({ now }, deltas, completed)

        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "ad-1", ShortformContentKind.AD)
        now = 120_000L
        tracker.tick()
        tracker.release()

        assertTrue(deltas.isEmpty())
        assertTrue(completed.isEmpty())
    }

    @Test
    fun `overlay time is excluded without creating a second view`() {
        var now = 0L
        val deltas = mutableListOf<ShortformCountDelta>()
        val completed = mutableListOf<CompletedShortformSession>()
        val tracker = tracker({ now }, deltas, completed)
        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "video", ShortformContentKind.NORMAL)
        now = 3000L
        tracker.setPaused(true)
        now = 30000L
        tracker.tick()
        assertTrue(deltas.isEmpty())
        tracker.setPaused(false)
        now = 32000L
        tracker.tick()
        now = 33000L
        tracker.setPaused(true)
        now = 60000L
        tracker.release()
        assertEquals(listOf(1L), deltas.map { it.count })
        assertEquals(6L, completed.single().durationSec)
    }

    @Test
    fun `unknown content never becomes a counted normal view`() {
        var now = 0L
        val deltas = mutableListOf<ShortformCountDelta>()
        val completed = mutableListOf<CompletedShortformSession>()
        val tracker = tracker({ now }, deltas, completed)
        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "video", ShortformContentKind.UNKNOWN)
        now = 60000L
        tracker.tick()
        tracker.release()
        assertTrue(deltas.isEmpty())
        assertTrue(completed.isEmpty())
    }

    @Test
    fun `changing content closes the previous session and creates a unique session id`() {
        var now = 0L
        var nextId = 0
        val completed = mutableListOf<CompletedShortformSession>()
        val tracker = ShortformSessionTracker(
            clockMs = { now },
            idFactory = { "session-${++nextId}" },
            onSessionCompleted = completed::add
        )

        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "video-1", ShortformContentKind.NORMAL)
        now = 6_000L
        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "video-2", ShortformContentKind.NORMAL)
        now = 12_000L
        tracker.release()

        assertEquals(listOf("session-1", "session-2"), completed.map { it.viewSessionId })
        assertEquals(listOf("video-1", "video-2"), completed.map { it.contentKey })
    }

    private fun tracker(
        clock: () -> Long,
        deltas: MutableList<ShortformCountDelta>,
        completed: MutableList<CompletedShortformSession>
    ) = ShortformSessionTracker(
        clockMs = clock,
        idFactory = { "session" },
        onCountDelta = deltas::add,
        onSessionCompleted = completed::add
    )
}
