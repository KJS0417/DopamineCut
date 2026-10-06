package com.example.dopaminecut2.logic.shortform

/** 접근성 식별자만 비교한다. 정보 누락은 유지하고 모호한 변경은 연속 관측 후 전환한다. */
class VideoIdentitySessionResolver(
    private val matcher: VideoIdentityMatcher = VideoIdentityMatcher(),
    private val lowQualitySwitchObservations: Int = 2
) {
    private var current: VideoIdentity? = null
    private var pending: VideoIdentity? = null
    private var pendingObservations = 0

    init {
        require(lowQualitySwitchObservations > 0)
    }

    fun observe(candidate: VideoIdentity?): VideoIdentity? {
        if (candidate?.contentKey == null) return current
        val active = current ?: return switchTo(candidate)
        return when (matcher.match(active, candidate)) {
            IdentityMatch.SAME -> {
                clearPending()
                active
            }
            IdentityMatch.DIFFERENT -> {
                if (candidate.quality == IdentityQuality.HIGH) switchTo(candidate)
                else confirmPending(candidate, active)
            }
            IdentityMatch.INDETERMINATE -> confirmPending(candidate, active)
        }
    }

    fun reset() {
        current = null
        clearPending()
    }

    private fun confirmPending(candidate: VideoIdentity, active: VideoIdentity): VideoIdentity {
        val previous = pending
        if (previous != null && matcher.match(previous, candidate) == IdentityMatch.SAME) {
            pendingObservations++
        } else {
            pending = candidate
            pendingObservations = 1
        }
        return if (pendingObservations >= lowQualitySwitchObservations) switchTo(candidate) else active
    }

    private fun switchTo(identity: VideoIdentity): VideoIdentity {
        current = identity
        clearPending()
        return identity
    }

    private fun clearPending() {
        pending = null
        pendingObservations = 0
    }
}
