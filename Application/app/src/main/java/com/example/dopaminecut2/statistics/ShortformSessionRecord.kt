package com.example.dopaminecut2.statistics

import com.example.dopaminecut2.domain.ContentCategory
import com.example.dopaminecut2.domain.SupportedPlatform

/** Classification metadata only: OCR, video titles and content fingerprints are never persisted. */
data class UsageClassification(
    val category: ContentCategory,
    val status: String,
    val source: String,
    val reason: String? = null,
    val modelVersion: String? = null,
    val taxonomyVersion: Int = 1,
    val confidence: Double? = null,
    val deductedScore: Long = 0L
) {
    init {
        require(status == "CLASSIFIED" || status == "UNRESOLVED")
        require((status == "UNRESOLVED") == (category == ContentCategory.UNKNOWN))
        require(source.isNotBlank() && source.length <= 64)
        require(reason == null || reason.length <= 128)
        require(modelVersion == null || modelVersion.length <= 128)
        require(taxonomyVersion > 0)
        require(confidence == null || confidence.isFinite() && confidence in 0.0..1.0)
        require(deductedScore in 0L..50_000L)
        require(category != ContentCategory.UNKNOWN || deductedScore == 0L)
    }
}

data class ShortformSessionCheckpoint(
    val userId: String,
    val date: String,
    val viewSessionId: String,
    val platform: SupportedPlatform,
    val durationSec: Long,
    val count: Long,
    val occurredAtEpochMs: Long,
    val initialCategory: ContentCategory = ContentCategory.UNKNOWN
) {
    init {
        require(userId.isNotBlank())
        require(date.matches(Regex("\\d{8}")))
        require(viewSessionId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        require(durationSec >= 5L)
        require(count >= 0L)
        require(occurredAtEpochMs >= 0L)
    }
}

/** Local idempotency ledger, owned by the surrounding user's daily snapshot. */
data class ShortformSessionRecord(
    val platform: SupportedPlatform,
    val durationSec: Long,
    val count: Long,
    val occurredAtEpochMs: Long,
    val category: ContentCategory = ContentCategory.UNKNOWN,
    val classification: UsageClassification? = null
) {
    val isPending: Boolean get() = classification == null
}
