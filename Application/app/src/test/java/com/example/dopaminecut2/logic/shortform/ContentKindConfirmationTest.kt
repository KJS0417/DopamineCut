package com.example.dopaminecut2.logic.shortform

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentKindConfirmationTest {
    @Test
    fun `two agreeing separated frames are required`() {
        val confirmation = ContentKindConfirmation()
        assertEquals(ShortformContentKind.UNKNOWN, confirmation.observe(ShortformContentKind.AD, 0))
        assertEquals(ShortformContentKind.UNKNOWN, confirmation.observe(ShortformContentKind.AD, 100))
        assertEquals(ShortformContentKind.AD, confirmation.observe(ShortformContentKind.AD, 1000))
    }

    @Test
    fun `unknown and conflicting frames restart confirmation`() {
        val confirmation = ContentKindConfirmation()
        confirmation.observe(ShortformContentKind.NORMAL, 0)
        confirmation.observe(ShortformContentKind.UNKNOWN, 1000)
        assertEquals(ShortformContentKind.UNKNOWN, confirmation.observe(ShortformContentKind.NORMAL, 2000))
        assertEquals(ShortformContentKind.UNKNOWN, confirmation.observe(ShortformContentKind.LIVE, 3000))
        assertEquals(ShortformContentKind.LIVE, confirmation.observe(ShortformContentKind.LIVE, 4000))
    }

    @Test
    fun `next video and overlay reset cannot inherit previous confirmation`() {
        val confirmation = ContentKindConfirmation()
        confirmation.observe(ShortformContentKind.NORMAL, 0)
        confirmation.reset()
        assertEquals(ShortformContentKind.UNKNOWN, confirmation.observe(ShortformContentKind.NORMAL, 1000))
    }
}
