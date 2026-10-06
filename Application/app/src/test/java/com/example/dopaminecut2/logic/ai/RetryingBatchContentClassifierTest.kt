package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.domain.ContentCategory
import com.example.dopaminecut2.domain.SupportedPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryingBatchContentClassifierTest {
    @Test
    fun `retry reuses request id and original payload at most twice`() = runBlocking {
        val batches = mutableListOf<List<PendingContentClassification>>()
        val delegate = BatchContentClassifier { batch ->
            batches += batch
            if (batches.size == 1) Result.failure(ClassificationCallTimeoutException())
            else successful(batch)
        }
        val classifier = RetryingBatchContentClassifier(
            delegate, retryDelaysMs = listOf(0L), delayBlock = {},
            currentUserId = { "user-a" }, requestIdFactory = { "stable-id" }
        )
        val result = classifier.classify(listOf(item())).getOrThrow()

        assertEquals(2, batches.size)
        assertEquals(batches[0], batches[1])
        assertEquals("stable-id", batches[0].single().requestId)
        assertEquals(ContentCategory.SPORTS, result.single().category)
    }

    @Test
    fun `permanent failure becomes unknown without retry or guessed category`() = runBlocking {
        var calls = 0
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier {
                calls++
                Result.failure(IllegalArgumentException("invalid response"))
            },
            currentUserId = { "user-a" }
        )
        val value = classifier.classify(listOf(item())).getOrThrow().single()

        assertEquals(1, calls)
        assertEquals(ContentCategory.UNKNOWN, value.category)
        assertEquals("INVALID_RESPONSE", value.reason)
    }

    @Test
    fun `quota is terminal even when generic policy would retry`() = runBlocking {
        var calls = 0
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier {
                calls++
                Result.failure(ClassificationRemoteException("QUOTA_EXCEEDED", retryable = false))
            },
            currentUserId = { "user-a" },
            shouldRetry = { true }, delayBlock = {}
        )
        val value = classifier.classify(listOf(item())).getOrThrow().single()

        assertEquals(1, calls)
        assertEquals("QUOTA_EXCEEDED", value.reason)
        assertEquals(ContentCategory.UNKNOWN, value.category)
    }

    @Test
    fun `resolved items survive a quota-failed sibling with no extra call`() = runBlocking {
        var calls = 0
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier {
                calls++
                Result.success(listOf(
                    ClassifiedContent("a", ContentCategory.SPORTS, source = ClassificationSource.JEV),
                    ClassifiedContent.unresolved("b", "QUOTA_EXCEEDED")
                ))
            }, currentUserId = { "user-a" }
        )
        val values = classifier.classify(listOf(item("a"), item("b"))).getOrThrow()

        assertEquals(1, calls)
        assertEquals(ContentCategory.SPORTS, values[0].category)
        assertEquals(ContentCategory.UNKNOWN, values[1].category)
    }

    @Test
    fun `owner mismatch never invokes provider`() = runBlocking {
        var calls = 0
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier { calls++; successful(it) },
            currentUserId = { "user-b" }
        )
        assertEquals("ACCOUNT_CHANGED", classifier.classify(listOf(item())).getOrThrow().single().reason)
        assertEquals(0, calls)
    }

    @Test
    fun `account changed during backoff prevents second request`() = runBlocking {
        var uid = "user-a"
        var calls = 0
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier {
                calls++
                Result.failure(ClassificationCallTimeoutException())
            },
            currentUserId = { uid }, delayBlock = { uid = "user-b" }
        )
        assertEquals("ACCOUNT_CHANGED", classifier.classify(listOf(item())).getOrThrow().single().reason)
        assertEquals(1, calls)
    }

    @Test
    fun `transient remote failure retries once and preserves request identity`() = runBlocking {
        val requestIds = mutableListOf<String?>()
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier { batch ->
                requestIds += batch.single().requestId
                if (requestIds.size == 1) {
                    Result.failure(ClassificationRemoteException("UNAVAILABLE", retryable = true))
                } else {
                    successful(batch)
                }
            },
            currentUserId = { "user-a" }, requestIdFactory = { "remote-retry" },
            retryDelaysMs = listOf(0L), delayBlock = {}
        )

        assertEquals(ContentCategory.SPORTS, classifier.classify(listOf(item())).getOrThrow().single().category)
        assertEquals(listOf("remote-retry", "remote-retry"), requestIds)
    }

    @Test
    fun `invalid generated request id is rejected before provider call`() = runBlocking {
        var calls = 0
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier { calls++; successful(it) },
            currentUserId = { "user-a" }, requestIdFactory = { "invalid request id" }
        )

        assertEquals("INVALID_REQUEST", classifier.classify(listOf(item())).getOrThrow().single().reason)
        assertEquals(0, calls)
    }

    @Test
    fun `invalid or unsupported item is rejected before provider call`() = runBlocking {
        var calls = 0
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier { calls++; successful(it) },
            currentUserId = { "user-a" }
        )
        val unsupported = item().copy(platform = SupportedPlatform.TIKTOK)

        assertEquals(
            "INVALID_REQUEST",
            classifier.classify(listOf(unsupported)).getOrThrow().single().reason
        )
        assertEquals(0, calls)
    }

    @Test
    fun `processing respects server delay and has a finite attempt budget`() = runBlocking {
        var calls = 0
        val delays = mutableListOf<Long>()
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier {
                calls++
                Result.failure(ClassificationProcessingException(2_000L))
            },
            currentUserId = { "user-a" }, jitterMs = { 0L }, delayBlock = { delays += it }
        )
        assertEquals("PROCESSING", classifier.classify(listOf(item())).getOrThrow().single().reason)
        assertEquals(2, calls)
        assertEquals(listOf(2_000L), delays)
    }

    @Test
    fun `backoff releases mutex so another batch can complete`() = runBlocking {
        withTimeout(2_000L) {
            val backoffEntered = CompletableDeferred<Unit>()
            val releaseBackoff = CompletableDeferred<Unit>()
            var aCalls = 0
            val classifier = RetryingBatchContentClassifier(
                delegate = BatchContentClassifier { batch ->
                    if (batch.single().viewSessionId == "a" && ++aCalls == 1) {
                        Result.failure(ClassificationCallTimeoutException())
                    } else successful(batch)
                },
                currentUserId = { "user-a" },
                delayBlock = { backoffEntered.complete(Unit); releaseBackoff.await() }
            )
            val a = async { classifier.classify(listOf(item("a"))) }
            backoffEntered.await()
            val b = classifier.classify(listOf(item("b"))).getOrThrow()
            assertEquals(ContentCategory.SPORTS, b.single().category)
            releaseBackoff.complete(Unit)
            assertEquals(ContentCategory.SPORTS, a.await().getOrThrow().single().category)
        }
    }

    @Test
    fun `queue capacity includes active request and overflow never calls provider`() = runBlocking {
        withTimeout(2_000L) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val classifier = RetryingBatchContentClassifier(
                delegate = BatchContentClassifier { batch ->
                    calls++; entered.complete(Unit); release.await(); successful(batch)
                },
                maxInMemoryRequests = 1, currentUserId = { "user-a" }
            )
            val first = async { classifier.classify(listOf(item("a"))) }
            entered.await()
            assertEquals("QUEUE_FULL", classifier.classify(listOf(item("b"))).getOrThrow().single().reason)
            assertEquals(1, calls)
            release.complete(Unit)
            assertEquals(
                ContentCategory.SPORTS,
                first.await().getOrThrow().single().category
            )
        }
    }

    @Test
    fun `total deadline terminates queued calls without indefinite waiting`() = runBlocking {
        withTimeout(2_000L) {
            val entered = CompletableDeferred<Unit>()
            val classifier = RetryingBatchContentClassifier(
                delegate = BatchContentClassifier { entered.complete(Unit); awaitCancellation() },
                currentUserId = { "user-a" }, totalTimeoutMs = 80L, callTimeoutMs = 500L
            )
            val first = async { classifier.classify(listOf(item("a"))) }
            entered.await()
            val second = async { classifier.classify(listOf(item("b"))) }
            assertEquals("DEADLINE_EXCEEDED", first.await().getOrThrow().single().reason)
            assertEquals("DEADLINE_EXCEEDED", second.await().getOrThrow().single().reason)
        }
    }

    @Test
    fun `call timeout is bounded and retries once`() = runBlocking {
        var calls = 0
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier { calls++; awaitCancellation() },
            currentUserId = { "user-a" }, callTimeoutMs = 20L, totalTimeoutMs = 1_000L,
            retryDelaysMs = listOf(0L), delayBlock = {}
        )
        assertEquals("DEADLINE_EXCEEDED", classifier.classify(listOf(item())).getOrThrow().single().reason)
        assertEquals(2, calls)
    }

    @Test
    fun `external cancellation propagates and frees capacity`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var calls = 0
        val classifier = RetryingBatchContentClassifier(
            delegate = BatchContentClassifier { batch ->
                calls++
                if (calls == 1) { entered.complete(Unit); awaitCancellation() } else successful(batch)
            },
            currentUserId = { "user-a" }, maxInMemoryRequests = 1
        )
        val first = async { classifier.classify(listOf(item("a"))) }
        entered.await()
        first.cancelAndJoin()
        assertTrue(first.isCancelled)
        try {
            first.await()
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Expected: cancellation is not converted to a classification result.
        }
        assertEquals(ContentCategory.SPORTS, classifier.classify(listOf(item("b"))).getOrThrow().single().category)
    }

    private fun successful(batch: List<PendingContentClassification>) = Result.success(
        batch.map { ClassifiedContent(it.viewSessionId, ContentCategory.SPORTS, source = ClassificationSource.JEV) }
    )

    private fun item(id: String = "session-1") = PendingContentClassification(
        viewSessionId = id, contentKey = "content-1", platform = SupportedPlatform.YOUTUBE,
        ocrText = "축구 경기", enqueuedAtElapsedMs = 0L, ownerUserId = "user-a"
    )
}
