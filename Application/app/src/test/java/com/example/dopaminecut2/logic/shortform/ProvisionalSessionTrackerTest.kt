package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.domain.SupportedPlatform
import org.junit.Assert.*
import org.junit.Test

class ProvisionalSessionTrackerTest {
    private class Fixture {
        var now = 0L
        var nextId = 0
        val checkpoints = mutableListOf<ShortformSessionCheckpoint>()
        val counts = mutableListOf<ShortformCountDelta>()
        val pending = mutableListOf<ProvisionalViewCheckpoint>()
        val settled = mutableListOf<String>()
        val completed = mutableListOf<CompletedShortformSession>()
        val tracker = ShortformSessionTracker(
            clockMs = { now }, idFactory = { "s${++nextId}" },
            onCheckpoint = checkpoints::add, onCountDelta = counts::add,
            onSessionCompleted = completed::add,
            onProvisionalCheckpoint = pending::add, onProvisionalSettled = settled::add
        )
        fun begin(key: String = "A") = tracker.beginProvisional(SupportedPlatform.YOUTUBE, key)
        fun resolve(kind: ShortformContentKind, key: String = "A") =
            tracker.resolveProvisional(tracker.activeViewSessionId()!!, kind, key)
    }

    @Test fun cvWaitIsBackfilledAndNormalCountsOnlyOnce() {
        val f = Fixture(); f.begin(); val id = f.tracker.activeViewSessionId()
        f.now = 3_000; assertTrue(f.resolve(ShortformContentKind.NORMAL))
        f.now = 5_000; f.tracker.tick(); f.now = 12_000; f.tracker.tick(); f.tracker.release()
        assertEquals(id, f.completed.single().viewSessionId)
        assertEquals(12L, f.completed.single().durationSec)
        assertEquals(1L, f.counts.sumOf { it.count })
    }

    @Test fun lateAResultCannotPromoteBOrRevisitedA() {
        val f = Fixture(); f.begin(); val first = f.tracker.activeViewSessionId()!!
        f.now = 3_000; f.begin("B")
        assertFalse(f.tracker.resolveProvisional(first, ShortformContentKind.NORMAL, "A"))
        f.now = 4_000; f.begin("A")
        assertFalse(f.tracker.resolveProvisional(first, ShortformContentKind.NORMAL, "A"))
        assertTrue(f.pending.isEmpty()); assertTrue(f.counts.isEmpty())
    }

    @Test fun unconfirmedLongViewStaysPendingWithoutTotals() {
        val f = Fixture(); f.begin(); f.now = 6_000; f.tracker.tick(); f.begin("B")
        assertTrue(f.pending.last().ended); assertEquals(6L, f.pending.last().durationSec)
        assertTrue(f.checkpoints.isEmpty()); assertTrue(f.counts.isEmpty()); assertTrue(f.completed.isEmpty())
    }

    @Test fun underFiveSecondsIsDiscarded() {
        val f = Fixture(); f.begin(); f.now = 4_999; f.tracker.release()
        assertTrue(f.pending.isEmpty()); assertEquals(listOf("s1"), f.settled)
    }

    @Test fun liveAndPhotoBackfillTimeWithoutCount() {
        for (kind in listOf(ShortformContentKind.LIVE, ShortformContentKind.PHOTO_POST)) {
            val f = Fixture(); f.begin(); f.now = 9_000; assertTrue(f.resolve(kind, "time_only"))
            f.tracker.release()
            assertEquals(9L, f.completed.single().durationSec)
            assertEquals(kind, f.completed.single().contentKind)
            assertTrue(f.counts.isEmpty())
        }
    }

    @Test fun adRemovesPendingAndNeverProducesUsage() {
        val f = Fixture(); f.begin(); f.now = 7_000; f.tracker.tick()
        assertTrue(f.resolve(ShortformContentKind.AD)); f.tracker.release()
        assertEquals(listOf("s1"), f.settled)
        assertNull(f.tracker.activeViewSessionId()); assertTrue(f.checkpoints.isEmpty())
        assertTrue(f.counts.isEmpty()); assertTrue(f.completed.isEmpty())
    }

    @Test fun confirmationSettlesPendingWithSameIdAndCumulativeTime() {
        val f = Fixture(); f.begin(); f.now = 6_000; f.tracker.tick()
        assertEquals(0L, f.tracker.activeProgress()!!.durationSec)
        assertTrue(f.resolve(ShortformContentKind.NORMAL)); f.tracker.tick()
        assertEquals(listOf("s1"), f.settled)
        assertEquals(6L, f.checkpoints.single().durationSec)
        assertEquals("s1", f.checkpoints.single().viewSessionId)
        assertEquals(1, f.counts.size)
    }

    @Test fun overlayTimeIsNotBackfilled() {
        val f = Fixture(); f.begin(); f.now = 2_000; f.tracker.setPaused(true)
        f.now = 10_000; f.tracker.setPaused(false); f.resolve(ShortformContentKind.NORMAL)
        f.now = 13_000; f.tracker.tick()
        assertEquals(5L, f.checkpoints.single().durationSec)
        assertEquals(1L, f.counts.single().count)
    }

    @Test fun promotionThatTriggersInterventionDoesNotRecreateSession() {
        var now = 0L
        lateinit var tracker: ShortformSessionTracker
        tracker = ShortformSessionTracker(clockMs = { now }, onCountDelta = { tracker.release() },
            onSessionCompleted = {})
        tracker.beginProvisional(SupportedPlatform.YOUTUBE, "A"); now = 6_000
        assertTrue(tracker.resolveProvisional(tracker.activeViewSessionId()!!, ShortformContentKind.NORMAL, "A"))
        assertNull(tracker.activeViewSessionId())
    }
}
