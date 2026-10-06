package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.domain.ContentCategory
import com.example.dopaminecut2.domain.SupportedPlatform

data class PendingContentClassification(
    val viewSessionId: String,
    val contentKey: String,
    val platform: SupportedPlatform,
    val ocrText: String,
    val enqueuedAtElapsedMs: Long,
    val ownerUserId: String = "",
    val requestId: String? = null
)

enum class BatchFlushReason { FULL, TIMEOUT, MANUAL }

data class ContentClassificationBatch(
    val items: List<PendingContentClassification>,
    val reason: BatchFlushReason
)

enum class ClassificationStatus { CLASSIFIED, UNRESOLVED }

enum class ClassificationSource { JEV, GEMINI, UNRESOLVED }

data class ClassifiedContent(
    val viewSessionId: String,
    val category: ContentCategory,
    val status: ClassificationStatus = if (category == ContentCategory.UNKNOWN) {
        ClassificationStatus.UNRESOLVED
    } else {
        ClassificationStatus.CLASSIFIED
    },
    val source: ClassificationSource = ClassificationSource.UNRESOLVED,
    val reason: String? = null,
    val modelVersion: String? = null,
    val confidence: Double? = null,
    val taxonomyVersion: Int = 1
) {
    init {
        require(viewSessionId.isNotBlank())
        require(taxonomyVersion > 0)
        require(confidence == null || confidence.isFinite() && confidence in 0.0..1.0)
        when (status) {
            ClassificationStatus.CLASSIFIED -> {
                require(category != ContentCategory.UNKNOWN && category != ContentCategory.LIVE)
                require(source != ClassificationSource.UNRESOLVED)
            }
            ClassificationStatus.UNRESOLVED -> {
                require(category == ContentCategory.UNKNOWN)
                require(source == ClassificationSource.UNRESOLVED)
                require(!reason.isNullOrBlank())
            }
        }
    }

    companion object {
        fun unresolved(viewSessionId: String, reason: String): ClassifiedContent = ClassifiedContent(
            viewSessionId = viewSessionId,
            category = ContentCategory.UNKNOWN,
            reason = reason
        )
    }
}

fun interface BatchContentClassifier {
    suspend fun classify(batch: List<PendingContentClassification>): Result<List<ClassifiedContent>>
}
