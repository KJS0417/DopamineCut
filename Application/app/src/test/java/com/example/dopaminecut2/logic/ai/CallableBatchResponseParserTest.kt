package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.domain.ContentCategory
import com.example.dopaminecut2.domain.SupportedPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CallableBatchResponseParserTest {
    @Test
    fun `partial success retains AI categories and unresolved reason`() {
        val batch = listOf(item("a"), item("b"))
        val parsed = CallableBatchResponseParser.parse(response(
            unresolvedResult("b", "QUOTA_EXCEEDED"),
            result("a", "EDUCATION", "GEMINI")
        ), batch, "request-1")

        assertEquals(ContentCategory.EDUCATION, parsed[0].category)
        assertEquals(ClassificationSource.GEMINI, parsed[0].source)
        assertEquals(ClassificationStatus.CLASSIFIED, parsed[0].status)
        assertEquals(ContentCategory.UNKNOWN, parsed[1].category)
        assertEquals(ClassificationStatus.UNRESOLVED, parsed[1].status)
        assertEquals("QUOTA_EXCEEDED", parsed[1].reason)
    }

    @Test
    fun `response preserves provenance and reorders by session id`() {
        val parsed = CallableBatchResponseParser.parse(response(
            result("b", "SPORTS", "JEV") + mapOf("confidence" to 0.91, "modelVersion" to "jev-test"),
            result("a", "OTHER", "GEMINI")
        ), listOf(item("a"), item("b")), "request-1")

        assertEquals("a", parsed[0].viewSessionId)
        assertEquals(ContentCategory.OTHER, parsed[0].category)
        assertEquals(ClassificationStatus.CLASSIFIED, parsed[0].status)
        assertEquals(0.91, parsed[1].confidence!!, 0.0)
        assertEquals("jev-test", parsed[1].modelVersion)
    }

    @Test
    fun `processing response preserves retry-after for bounded retry`() {
        val error = assertThrows(ClassificationProcessingException::class.java) {
            CallableBatchResponseParser.parse(
                response() + mapOf("requestStatus" to "PROCESSING", "retryAfterMs" to 1500),
                listOf(item("a")), "request-1"
            )
        }
        assertEquals(1500L, error.retryAfterMs)
    }

    @Test
    fun `wrong request id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CallableBatchResponseParser.parse(response(result("a", "GAME", "JEV")),
                listOf(item("a")), "different-request")
        }
    }

    @Test
    fun `duplicate missing and unexpected session ids are rejected`() {
        val batch = listOf(item("a"), item("b"))
        listOf(
            response(result("a", "GAME", "JEV"), result("a", "GAME", "JEV")),
            response(result("a", "GAME", "JEV")),
            response(result("a", "GAME", "JEV"), result("other", "GAME", "JEV"))
        ).forEach { response ->
            assertThrows(IllegalArgumentException::class.java) {
                CallableBatchResponseParser.parse(response, batch, "request-1")
            }
        }
    }

    @Test
    fun `invalid category unknown and live cannot pretend to be AI classifications`() {
        listOf("IGNORE_PREVIOUS_INSTRUCTIONS", "UNKNOWN", "LIVE").forEach { category ->
            assertThrows(IllegalArgumentException::class.java) {
                CallableBatchResponseParser.parse(response(result("a", category, "JEV")),
                    listOf(item("a")), "request-1")
            }
        }
    }

    @Test
    fun `invalid confidence or nonintegral taxonomy is rejected`() {
        val base = result("a", "GAME", "JEV")
        listOf(
            response(base + ("confidence" to Double.NaN)),
            response(base + ("confidence" to 1.1)),
            response(base) + ("taxonomyVersion" to 1.5),
            response(unresolvedResult("a", "TIMEOUT") + ("category" to "OTHER"))
        ).forEach { response ->
            assertThrows(IllegalArgumentException::class.java) {
                CallableBatchResponseParser.parse(response, listOf(item("a")), "request-1")
            }
        }
    }

    @Test
    fun `unresolved item requires a known reason and empty provider metadata`() {
        listOf(
            unresolvedResult("a", "NOT_A_REASON"),
            unresolvedResult("a", "PROVIDER_TIMEOUT") + ("confidence" to 0.5),
            unresolvedResult("a", "PROVIDER_TIMEOUT") + ("modelVersion" to "unexpected")
        ).forEach { rawResult ->
            assertThrows(IllegalArgumentException::class.java) {
                CallableBatchResponseParser.parse(response(rawResult), listOf(item("a")), "request-1")
            }
        }
    }

    @Test
    fun `classified provider metadata must match its provider`() {
        listOf(
            result("a", "GAME", "JEV") + ("confidence" to null),
            result("a", "GAME", "GEMINI") + ("confidence" to 0.8),
            result("a", "GAME", "JEV") + ("modelVersion" to null),
            result("a", "GAME", "JEV") + ("reason" to "PROVIDER_ERROR")
        ).forEach { rawResult ->
            assertThrows(IllegalArgumentException::class.java) {
                CallableBatchResponseParser.parse(response(rawResult), listOf(item("a")), "request-1")
            }
        }
    }

    private fun response(vararg results: Map<String, Any?>): Map<String, Any?> = mapOf(
        "apiVersion" to 2,
        "taxonomyVersion" to 1,
        "requestId" to "request-1",
        "requestStatus" to "COMPLETED",
        "results" to results.toList()
    )

    private fun result(sessionId: String, category: String, provider: String) = mapOf(
        "viewSessionId" to sessionId,
        "category" to category,
        "confidence" to if (provider == "JEV") 0.95 else null,
        "provider" to provider,
        "modelVersion" to "test-model",
        "status" to "CLASSIFIED",
        "reason" to null
    )

    private fun unresolvedResult(sessionId: String, reason: String) = mapOf(
        "viewSessionId" to sessionId,
        "category" to null,
        "confidence" to null,
        "provider" to "UNRESOLVED",
        "modelVersion" to null,
        "status" to "UNRESOLVED",
        "reason" to reason
    )

    private fun item(id: String) = PendingContentClassification(
        viewSessionId = id,
        contentKey = id,
        platform = SupportedPlatform.YOUTUBE,
        ocrText = "테스트",
        enqueuedAtElapsedMs = 0L,
        ownerUserId = "user-a"
    )
}
