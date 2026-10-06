package com.example.dopaminecut2.logic.shortform

/** 종류 판단과 시청 확정을 분리한다. CV만으로 Shorts 진입을 확정하지 않는다. */
object RecognitionSafety {
    fun canTrack(state: ShortformScreenState, kind: ShortformContentKind, identityReady: Boolean): Boolean =
        state == ShortformScreenState.INSIDE && identityReady &&
            kind in setOf(ShortformContentKind.NORMAL, ShortformContentKind.LIVE, ShortformContentKind.PHOTO_POST)

    fun canProbe(state: ShortformScreenState): Boolean =
        state == ShortformScreenState.INSIDE || state == ShortformScreenState.CANDIDATE
}

/** 진입 단서가 잠깐 누락되면 측정은 멈추고 세션만 짧게 보존한다. */
class EntryEvidenceGrace(private val graceMs: Long = 2_000L) {
    private var lastInsideMs: Long? = null
    fun shouldRetain(state: ShortformScreenState, nowMs: Long): Boolean {
        if (state == ShortformScreenState.INSIDE || state == ShortformScreenState.OVERLAY) {
            lastInsideMs = nowMs
            return true
        }
        val last = lastInsideMs ?: return false
        return nowMs >= last && nowMs - last <= graceMs
    }
    fun reset() { lastInsideMs = null }
}

data class RecognitionHealthSnapshot(
    val observations: Long, val entryUncertain: Long, val identityMissing: Long,
    val inferences: Long, val inferenceFailures: Long, val unknownPredictions: Long
) {
    /** 이상 후보일 뿐 UI 변경을 증명하지 않는다. 충분한 표본이 있을 때만 표시한다. */
    val needsReview: Boolean get() =
        (observations >= 20 && (entryUncertain * 2 >= observations || identityMissing * 2 >= observations)) ||
            (inferences >= 10 && (inferenceFailures * 2 >= inferences || unknownPredictions * 2 >= inferences))
}

/** 단조 시계로 표본 빈도를 제한한 로컬 집계. 화면 원문·이미지·계정 정보는 보관하지 않는다. */
class RecognitionHealth(private val sampleIntervalMs: Long = 1_000L) {
    private var lastObservationMs: Long? = null
    private var observations = 0L
    private var entryUncertain = 0L
    private var identityMissing = 0L
    private var inferences = 0L
    private var inferenceFailures = 0L
    private var unknownPredictions = 0L
    fun observe(state: ShortformScreenState, identityReady: Boolean, nowMs: Long) {
        val previous = lastObservationMs
        if (previous != null && nowMs - previous < sampleIntervalMs) return
        lastObservationMs = nowMs
        observations++
        if (state == ShortformScreenState.CANDIDATE || state == ShortformScreenState.UNKNOWN) entryUncertain++
        if (state == ShortformScreenState.INSIDE && !identityReady) identityMissing++
    }
    fun inference(kind: ShortformContentKind?, failed: Boolean) {
        inferences++
        if (failed) inferenceFailures++
        if (kind == null || kind == ShortformContentKind.UNKNOWN) unknownPredictions++
    }
    fun snapshot() = RecognitionHealthSnapshot(observations, entryUncertain, identityMissing,
        inferences, inferenceFailures, unknownPredictions)
}
