package com.example.dopaminecut2.logic.shortform

/** 서로 다른 시점의 2개 프레임이 동의해야 일반/광고/라이브를 확정한다. */
class ContentKindConfirmation(private val minimumIntervalMs: Long = 300L) {
    private var candidate = ShortformContentKind.UNKNOWN
    private var observedAtMs = 0L

    fun observe(kind: ShortformContentKind, nowMs: Long): ShortformContentKind {
        if (kind == ShortformContentKind.UNKNOWN) {
            reset()
            return ShortformContentKind.UNKNOWN
        }
        if (kind != candidate) {
            candidate = kind
            observedAtMs = nowMs
            return ShortformContentKind.UNKNOWN
        }
        return if (nowMs - observedAtMs >= minimumIntervalMs) kind else ShortformContentKind.UNKNOWN
    }

    fun reset() {
        candidate = ShortformContentKind.UNKNOWN
        observedAtMs = 0L
    }
}
