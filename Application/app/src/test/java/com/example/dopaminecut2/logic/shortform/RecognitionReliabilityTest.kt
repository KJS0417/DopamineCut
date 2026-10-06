package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.logic.manager.ScreenSnapshot
import org.junit.Assert.*
import org.junit.Test

class RecognitionReliabilityTest {
    @Test fun `deleted ids retain semantic candidate but cannot count`() {
        val detection = YoutubeEntryDetector().detect(ScreenSnapshot(
            listOf("@채널 채널로 이동", "이 사운드를 사용하는 동영상 더보기", "리믹스", "동영상 공유"),
            listOf("com.google.android.youtube:id/changed_player_root")))
        assertEquals(ShortformScreenState.CANDIDATE, detection.state)
        assertTrue(RecognitionSafety.canProbe(detection.state))
        assertFalse(RecognitionSafety.canTrack(detection.state, ShortformContentKind.NORMAL, true))
    }
    @Test fun `unknown ad overlay or missing identity never tracks`() {
        for (state in ShortformScreenState.values()) for (kind in ShortformContentKind.values()) {
            assertFalse(RecognitionSafety.canTrack(state, kind, false))
            assertEquals(state == ShortformScreenState.INSIDE && kind in
                setOf(ShortformContentKind.NORMAL, ShortformContentKind.LIVE, ShortformContentKind.PHOTO_POST), RecognitionSafety.canTrack(state, kind, true))
        }
    }
    @Test fun `entry loss grace expires and resets`() {
        val grace = EntryEvidenceGrace()
        assertFalse(grace.shouldRetain(ShortformScreenState.OUTSIDE, 0))
        assertTrue(grace.shouldRetain(ShortformScreenState.INSIDE, 100))
        assertTrue(grace.shouldRetain(ShortformScreenState.OUTSIDE, 2_100))
        assertFalse(grace.shouldRetain(ShortformScreenState.OUTSIDE, 2_101))
        grace.reset()
        assertFalse(grace.shouldRetain(ShortformScreenState.OUTSIDE, 2_200))
    }
    @Test fun `frequent refresh does not inflate health counters`() {
        val health = RecognitionHealth()
        health.observe(ShortformScreenState.CANDIDATE, false, 0)
        health.observe(ShortformScreenState.CANDIDATE, false, 100)
        health.observe(ShortformScreenState.INSIDE, false, 1_000)
        health.inference(null, true)
        health.inference(ShortformContentKind.UNKNOWN, false)
        val result = health.snapshot()
        assertEquals(2L, result.observations)
        assertEquals(1L, result.entryUncertain)
        assertEquals(1L, result.identityMissing)
        assertEquals(1L, result.inferenceFailures)
        assertEquals(2L, result.unknownPredictions)
    }
    @Test fun `identity disappearing does not count the same video twice`() {
        var now = 0L
        var count = 0L
        val tracker = ShortformSessionTracker(clockMs = { now }, onCountDelta = { count += it.count }, onSessionCompleted = {})
        tracker.onScreenChanged(true, com.example.dopaminecut2.domain.SupportedPlatform.YOUTUBE, "same", ShortformContentKind.NORMAL)
        now = 6_000
        tracker.tick()
        tracker.setPaused(true)
        now = 20_000
        tracker.tick()
        tracker.setPaused(false)
        tracker.onScreenChanged(true, com.example.dopaminecut2.domain.SupportedPlatform.YOUTUBE, "same", ShortformContentKind.NORMAL)
        now = 26_000
        tracker.tick()
        assertEquals(1L, count)
        assertEquals(12L, tracker.activeProgress()?.durationSec)
    }
    @Test fun `health review flag needs enough observations`() {
        val health = RecognitionHealth()
        repeat(19) { health.observe(ShortformScreenState.CANDIDATE, false, it * 1_000L) }
        assertFalse(health.snapshot().needsReview)
        health.observe(ShortformScreenState.CANDIDATE, false, 19_000L)
        assertTrue(health.snapshot().needsReview)
    }
}
