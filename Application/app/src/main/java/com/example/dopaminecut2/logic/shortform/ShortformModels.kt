package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.domain.SupportedPlatform

enum class ShortformScreenState {
    OUTSIDE,
    CANDIDATE,
    INSIDE,
    OVERLAY,
    UNKNOWN
}

data class ShortformScreenDetection(
    val state: ShortformScreenState,
    val confidence: Float,
    val evidence: Set<String> = emptySet()
) {
    init {
        require(confidence in 0f..1f)
    }

    val isConfirmedShortform: Boolean
        get() = state == ShortformScreenState.INSIDE || state == ShortformScreenState.OVERLAY
}

enum class ShortformContentKind {
    NORMAL,
    AD,
    LIVE,
    PHOTO_POST,
    UNKNOWN
}

enum class IdentityQuality {
    HIGH,
    MEDIUM,
    LOW,
    NONE
}

data class VideoIdentityObservation(
    val platform: SupportedPlatform,
    val creator: String? = null,
    val title: String? = null,
    val audio: String? = null,
    val titleVisualHash: String? = null,
    val observedAtElapsedMs: Long
)

data class VideoIdentity(
    val platform: SupportedPlatform,
    val contentKey: String?,
    val creator: String?,
    val title: String?,
    val audio: String?,
    val titleVisualHash: String?,
    val quality: IdentityQuality,
    val confidence: Float,
    val identityTextTruncated: Boolean = false
) {
    init {
        require(confidence in 0f..1f)
        require(quality == IdentityQuality.NONE || contentKey != null)
    }
}

enum class IdentityMatch {
    SAME,
    DIFFERENT,
    INDETERMINATE
}

data class ShortformViewSession(
    val viewSessionId: String,
    val platform: SupportedPlatform,
    val contentKey: String?,
    val contentKind: ShortformContentKind,
    val startedAtElapsedMs: Long,
    val qualifiedAtElapsedMs: Long? = null,
    val counted: Boolean = false
)
