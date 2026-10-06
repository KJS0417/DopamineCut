package com.example.dopaminecut2.logic.shortform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ShortformContentDecisionPolicyTest {
    private val policy = ShortformContentDecisionPolicy()

    @Test
    fun `confident logits map to model class order`() {
        assertEquals(
            ShortformContentKind.NORMAL,
            policy.fromLogits(floatArrayOf(5f, 0f, 0f, 0f)).kind
        )
        assertEquals(
            ShortformContentKind.AD,
            policy.fromLogits(floatArrayOf(0f, 5f, 0f, 0f)).kind
        )
        assertEquals(
            ShortformContentKind.LIVE,
            policy.fromLogits(floatArrayOf(0f, 0f, 5f, 0f)).kind
        )
    }

    @Test
    fun `ambiguous output remains unknown`() {
        val result = policy.fromLogits(floatArrayOf(1f, 1f, 1f, 1f))

        assertEquals(ShortformContentKind.UNKNOWN, result.kind)
        assertTrue(result.confidence in 0.249f..0.251f)
    }

    @Test
    fun `corrupt non finite output is not accepted`() {
        assertThrows(IllegalArgumentException::class.java) {
            policy.fromLogits(floatArrayOf(Float.NaN, 0f, 0f, 0f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.fromLogits(floatArrayOf(0f, Float.POSITIVE_INFINITY, 0f, 0f))
        }
    }
}
