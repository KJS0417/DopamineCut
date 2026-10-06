package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.domain.SupportedPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassificationBatchQueueTest {
    @Test
    fun `five unique sessions are emitted as one batch`() {
        val queue = ClassificationBatchQueue(maxBatchSize = 5)
        repeat(4) { index -> assertNull(queue.offer(item(index, 0L))) }

        val batch = queue.offer(item(4, 0L))

        assertEquals(BatchFlushReason.FULL, batch?.reason)
        assertEquals(5, batch?.items?.size)
        assertEquals(0, queue.size())
    }

    @Test
    fun `partial batch is emitted after timeout`() {
        val queue = ClassificationBatchQueue(maxWaitMs = 15_000L)
        queue.offer(item(1, 1_000L))

        assertNull(queue.pollDue(15_999L))
        val batch = queue.pollDue(16_000L)

        assertEquals(BatchFlushReason.TIMEOUT, batch?.reason)
        assertEquals(1, batch?.items?.size)
    }

    @Test
    fun `same session replaces text instead of consuming another slot`() {
        val queue = ClassificationBatchQueue()
        queue.offer(item(1, 0L, "첫 OCR"))
        queue.offer(item(1, 1_000L, "  더 정확한   OCR  "))

        val batch = queue.flush()

        assertEquals(1, batch?.items?.size)
        assertEquals("더 정확한 OCR", batch?.items?.single()?.ocrText)
    }

    @Test
    fun `repeated OCR does not postpone the original batch deadline`() {
        val queue = ClassificationBatchQueue(maxWaitMs = 15_000L)
        queue.offer(item(1, 1_000L, "첫 OCR"))
        queue.offer(item(1, 14_000L, "개선된 OCR"))

        val batch = queue.pollDue(16_000L)

        assertEquals(BatchFlushReason.TIMEOUT, batch?.reason)
        assertEquals("개선된 OCR", batch?.items?.single()?.ocrText)
        assertEquals(1_000L, batch?.items?.single()?.enqueuedAtElapsedMs)
    }

    private fun item(index: Int, time: Long, text: String = "영상 $index") =
        PendingContentClassification(
            viewSessionId = "session-$index",
            contentKey = "content-$index",
            platform = SupportedPlatform.YOUTUBE,
            ocrText = text,
            enqueuedAtElapsedMs = time
        )
}
