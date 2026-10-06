package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.domain.ContentCategory

internal object CallableBatchResponseParser {
    fun parse(
        raw: Any?,
        batch: List<PendingContentClassification>,
        expectedRequestId: String
    ): List<ClassifiedContent> {
        val root = raw as? Map<*, *> ?: throw IllegalArgumentException("분류 응답이 객체가 아닙니다.")
        require((root["apiVersion"] as? Number)?.toDouble() == 2.0) { "지원하지 않는 API 버전입니다." }
        require((root["taxonomyVersion"] as? Number)?.toDouble() == 1.0) { "지원하지 않는 분류 체계입니다." }
        require(root["requestId"] == expectedRequestId) { "분류 응답의 요청 ID가 다릅니다." }
        val rawResults = root["results"] as? List<*> ?: throw IllegalArgumentException("분류 결과 배열이 없습니다.")
        if (root["requestStatus"] == "PROCESSING") {
            requireOnlyKeys(root, PROCESSING_RESPONSE_KEYS)
            require(rawResults.isEmpty()) { "처리 중인 응답에 확정 결과가 포함되었습니다." }
            val retryAfter = root["retryAfterMs"] as? Number
                ?: throw IllegalArgumentException("재조회 대기시간이 없습니다.")
            val value = retryAfter.toDouble()
            require(value.isFinite() && value >= 0 && value <= 45_000 && value % 1.0 == 0.0)
            throw ClassificationProcessingException(retryAfter.toLong())
        }
        require(root["requestStatus"] == "COMPLETED") { "알 수 없는 요청 상태입니다." }
        requireOnlyKeys(root, COMPLETED_RESPONSE_KEYS)
        require(rawResults.size == batch.size) { "분류 결과 개수가 요청과 다릅니다." }
        val expectedIds = batch.map(PendingContentClassification::viewSessionId).toSet()
        require(expectedIds.size == batch.size) { "분류 요청 세션 ID가 중복되었습니다." }
        val bySession = mutableMapOf<String, ClassifiedContent>()
        rawResults.forEach { rawResult ->
            val result = rawResult as? Map<*, *> ?: throw IllegalArgumentException("분류 결과 항목이 객체가 아닙니다.")
            requireOnlyKeys(result, RESULT_KEYS)
            val sessionId = result["viewSessionId"] as? String ?: throw IllegalArgumentException("분류 결과에 세션 ID가 없습니다.")
            require(sessionId in expectedIds && sessionId !in bySession) { "분류 결과 세션 ID가 잘못되었습니다." }
            val reason = optionalString(result, "reason", 128)
            val model = optionalString(result, "modelVersion", 200)
            val confidence = result["confidence"]?.let { value ->
                (value as? Number)?.toDouble()?.also {
                    require(it.isFinite() && it in 0.0..1.0) { "잘못된 분류 신뢰도입니다." }
                } ?: throw IllegalArgumentException("잘못된 분류 신뢰도입니다.")
            }
            bySession[sessionId] = when (result["status"]) {
                "UNRESOLVED" -> {
                    require(result["category"] == null && result["provider"] == "UNRESOLVED" &&
                        confidence == null && model == null)
                    require(reason in REMOTE_UNRESOLVED_REASONS) { "알 수 없는 미확인 사유입니다." }
                    ClassifiedContent.unresolved(sessionId, requireNotNull(reason))
                }
                "CLASSIFIED" -> {
                    val source = when (result["provider"]) {
                        "JEV" -> ClassificationSource.JEV
                        "GEMINI" -> ClassificationSource.GEMINI
                        else -> throw IllegalArgumentException("알 수 없는 분류 공급자입니다.")
                    }
                    val category = ContentCategory.entries.firstOrNull {
                        it != ContentCategory.LIVE && it != ContentCategory.UNKNOWN && it.id == result["category"]
                    } ?: throw IllegalArgumentException("허용되지 않은 카테고리입니다.")
                    require(reason == null && !model.isNullOrBlank()) { "분류 출처 정보가 잘못되었습니다." }
                    when (source) {
                        ClassificationSource.JEV -> require(confidence != null) { "Jev 신뢰도가 없습니다." }
                        ClassificationSource.GEMINI -> require(confidence == null) { "Gemini 응답 형식이 잘못되었습니다." }
                        else -> error("원격 분류 공급자가 아닙니다.")
                    }
                    ClassifiedContent(
                        sessionId, category, source = source, modelVersion = model,
                        confidence = confidence
                    )
                }
                else -> throw IllegalArgumentException("알 수 없는 분류 상태입니다.")
            }
        }
        return batch.map { requireNotNull(bySession[it.viewSessionId]) }
    }

    private fun optionalString(result: Map<*, *>, key: String, maxLength: Int): String? {
        val raw = result[key] ?: return null
        require(raw is String && raw.isNotBlank() && raw.length <= maxLength) { "잘못된 $key 값입니다." }
        return raw
    }

    private fun requireOnlyKeys(value: Map<*, *>, expected: Set<String>) {
        require(value.keys == expected) { "분류 응답 필드가 계약과 다릅니다." }
    }
}

private val COMPLETED_RESPONSE_KEYS = setOf(
    "apiVersion", "taxonomyVersion", "requestId", "requestStatus", "results"
)
private val PROCESSING_RESPONSE_KEYS = COMPLETED_RESPONSE_KEYS + "retryAfterMs"
private val RESULT_KEYS = setOf(
    "viewSessionId", "category", "confidence", "provider", "modelVersion", "status", "reason"
)
private val REMOTE_UNRESOLVED_REASONS = setOf(
    "QUOTA_EXCEEDED", "INSUFFICIENT_TEXT", "PROVIDER_TIMEOUT", "PROVIDER_ERROR",
    "INVALID_PROVIDER_RESPONSE", "REQUEST_EXPIRED", "PROCESSING"
)
