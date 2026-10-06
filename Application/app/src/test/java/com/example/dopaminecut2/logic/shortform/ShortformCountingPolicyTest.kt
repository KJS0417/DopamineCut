package com.example.dopaminecut2.logic.shortform

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortformCountingPolicyTest {
    private val policy = ShortformCountingPolicy()

    @Test
    fun `normal content becomes one view after five seconds`() {
        assertEquals(ShortformCountResult(0L, 0L), policy.result(ShortformContentKind.NORMAL, 4L))
        assertEquals(ShortformCountResult(1L, 5L), policy.result(ShortformContentKind.NORMAL, 5L))
        assertEquals(ShortformCountResult(1L, 120L), policy.result(ShortformContentKind.NORMAL, 120L))
    }

    @Test
    fun `ads and unknown screens are excluded`() {
        assertEquals(ShortformCountResult(0L, 0L), policy.result(ShortformContentKind.AD, 100L))
        assertEquals(ShortformCountResult(0L, 0L), policy.result(ShortformContentKind.UNKNOWN, 100L))
    }

    @Test
    fun `live content tracks time but never adds views`() {
        assertEquals(ShortformCountResult(0L, 0L), policy.result(ShortformContentKind.LIVE, 4L))
        assertEquals(ShortformCountResult(0L, 5L), policy.result(ShortformContentKind.LIVE, 5L))
        assertEquals(ShortformCountResult(0L, 39L), policy.result(ShortformContentKind.LIVE, 39L))
        assertEquals(ShortformCountResult(0L, 40L), policy.result(ShortformContentKind.LIVE, 40L))
        assertEquals(ShortformCountResult(0L, 89L), policy.result(ShortformContentKind.LIVE, 89L))
        assertEquals(ShortformCountResult(0L, 86_400L), policy.result(ShortformContentKind.LIVE, 86_400L))
        assertEquals(0L, policy.newCountSince(ShortformContentKind.LIVE, 39L, 40L))
    }
}
