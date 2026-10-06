package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.domain.SupportedPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoIdentitySessionResolverTest {
    private val factory = VideoIdentityFactory()

    @Test
    fun `minor OCR changes retain the active content key`() {
        val resolver = VideoIdentitySessionResolver()
        val first = identity("@channel", "고양이가 처음 눈을 본 날")
        val noisy = identity("channel", "고양이가 처음 눈을 본날")

        assertEquals(first.contentKey, resolver.observe(first)?.contentKey)
        assertEquals(first.contentKey, resolver.observe(noisy)?.contentKey)
    }

    @Test
    fun `high quality different creator switches immediately`() {
        val resolver = VideoIdentitySessionResolver()
        val first = identity("creator_a", "같은 제목")
        val second = identity("creator_b", "같은 제목")

        resolver.observe(first)

        assertEquals(second.contentKey, resolver.observe(second)?.contentKey)
    }

    @Test
    fun `low quality candidate requires consecutive observations`() {
        val resolver = VideoIdentitySessionResolver(lowQualitySwitchObservations = 2)
        val first = lowIdentity("first")
        val second = lowIdentity("second")

        resolver.observe(first)
        assertEquals(first.contentKey, resolver.observe(second)?.contentKey)
        assertEquals(second.contentKey, resolver.observe(second)?.contentKey)
    }

    private fun identity(creator: String, title: String): VideoIdentity =
        factory.create(
            listOf(
                VideoIdentityObservation(
                    platform = SupportedPlatform.YOUTUBE,
                    creator = creator,
                    title = title,
                    observedAtElapsedMs = 0L
                )
            )
        )

    @Test
    fun `missing metadata preserves current key without creating another identity`() {
        val resolver = VideoIdentitySessionResolver()
        val first = identity("channel", "고정 제목")
        resolver.observe(first)
        assertEquals(first.contentKey, resolver.observe(null)?.contentKey)
        assertEquals(first.contentKey, resolver.observe(first)?.contentKey)
    }

    @Test
    fun `reset removes previous session identity`() {
        val resolver = VideoIdentitySessionResolver()
        resolver.observe(identity("channel", "첫 제목"))
        resolver.reset()
        assertEquals(null, resolver.observe(null))
        val next = identity("next_channel", "새 제목")
        assertEquals(next.contentKey, resolver.observe(next)?.contentKey)
    }

    private fun lowIdentity(key: String) = VideoIdentity(
        platform = SupportedPlatform.YOUTUBE,
        contentKey = key,
        creator = null,
        title = key,
        audio = null,
        titleVisualHash = null,
        quality = IdentityQuality.LOW,
        confidence = 0.55f
    )
}
