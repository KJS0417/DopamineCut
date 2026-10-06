package com.example.dopaminecut2.logic.shortform

data class ShortformCountResult(
    val count: Long,
    val trackedDurationSec: Long
)

/** 콘텐츠 종류에 따라 시청 횟수와 저장할 시청 시간을 일관되게 계산한다. */
class ShortformCountingPolicy(
    private val normalQualificationSec: Long = 5L
) {
    init {
        require(normalQualificationSec > 0L)
    }

    fun result(kind: ShortformContentKind, durationSec: Long): ShortformCountResult {
        val safeDuration = durationSec.coerceAtLeast(0L)
        return when (kind) {
            ShortformContentKind.NORMAL -> if (safeDuration >= normalQualificationSec) {
                ShortformCountResult(count = 1L, trackedDurationSec = safeDuration)
            } else {
                ShortformCountResult(count = 0L, trackedDurationSec = 0L)
            }

            ShortformContentKind.LIVE,
            ShortformContentKind.PHOTO_POST -> if (safeDuration >= normalQualificationSec) {
                ShortformCountResult(
                    count = 0L,
                    trackedDurationSec = safeDuration
                )
            } else {
                ShortformCountResult(count = 0L, trackedDurationSec = 0L)
            }

            ShortformContentKind.AD,
            ShortformContentKind.UNKNOWN -> ShortformCountResult(0L, 0L)
        }
    }

    fun newCountSince(
        kind: ShortformContentKind,
        previousDurationSec: Long,
        currentDurationSec: Long
    ): Long = (result(kind, currentDurationSec).count - result(kind, previousDurationSec).count)
        .coerceAtLeast(0L)
}
