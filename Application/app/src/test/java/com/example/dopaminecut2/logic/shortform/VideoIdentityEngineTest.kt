package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.domain.SupportedPlatform
import com.example.dopaminecut2.logic.manager.NormalizedBounds
import com.example.dopaminecut2.logic.manager.ScreenElement
import com.example.dopaminecut2.logic.manager.ScreenSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoIdentityEngineTest {
    private val factory = VideoIdentityFactory()

    @Test fun `korean creator action suffix is removed before hashing`() {
        for (creator in listOf("@우용암", "@우용암 채널로 이동", "@우용암을(를) 구독합니다.")) {
            assertEquals("우용암", VideoIdentityNormalizer.normalizeCreator(creator))
        }
        val clean = factory.create(listOf(observation("@우용암", "영상 제목", 0L)))
        val action = factory.create(listOf(observation("@우용암 채널로 이동", "영상 제목", 1L)))
        assertEquals(clean.contentKey, action.contentKey)
    }

    @Test fun `korean creator retains unicode digits and latin handle characters`() {
        assertEquals("한국_creator123", VideoIdentityNormalizer.normalizeCreator("@한국_creator123 채널로 이동"))
        assertEquals("흑백리뷰", VideoIdentityNormalizer.normalizeCreator("@흑백리뷰를 구독합니다."))
        assertEquals("만원요리최씨남매", VideoIdentityNormalizer.normalizeCreator("@만원요리최씨남매을(를) 구독합니다."))
    }

    @Test fun `long korean creator is cleaned before display length limit`() {
        val creator = "한".repeat(18)
        val clean = factory.create(listOf(observation("@$creator", "영상 제목", 0L)))
        val described = factory.create(listOf(observation("@$creator 채널로 이동", "영상 제목", 1L)))
        assertEquals("한".repeat(15), described.creator)
        assertEquals(clean.contentKey, described.contentKey)
    }

    @Test fun `commerce and dubbing control labels cannot contaminate title`() {
        listOf("쇼핑하기", "수수료 지급", "자동 더빙").forEach {
            assertEquals(null, VideoIdentityNormalizer.normalizeText(it))
        }
    }

    @Test
    fun `my page navigation is excluded before hashing and cannot replace a missing title`() {
        val extractor = YoutubeAccessibilityIdentityExtractor()
        val author = element("@channel", 0.05f, 0.72f, 0.70f, 0.76f)
        val title = element("실제 영상 제목", 0.05f, 0.80f, 0.70f, 0.84f)
        val navigation = element("내 페이지", 0.70f, 0.91f, 0.88f, 0.95f)
        fun snapshot(items: List<ScreenElement>) =
            ScreenSnapshot(items.mapNotNull { it.text }, emptyList(), items)
        val clean = extractor.extract(snapshot(listOf(author, title)), 0L)
        val withNavigation = extractor.extract(snapshot(listOf(author, title, navigation)), 1L)
        val withoutTitle = extractor.extract(snapshot(listOf(author, navigation)), 2L)
        assertEquals("실제 영상 제목", withNavigation.title)
        assertEquals(clean.contentKey, withNavigation.contentKey)
        assertEquals(null, withoutTitle.contentKey)
        assertEquals(IdentityQuality.NONE, withoutTitle.quality)
    }

    @Test
    fun `creator and title are bounded without changing full content hash`() {
        val creator = "channel".repeat(20)
        val prefix = "제목".repeat(100)
        val first = factory.create(listOf(observation(creator, prefix + "첫 영상", 0L)))
        val repeated = factory.create(listOf(observation(creator, prefix + "첫 영상", 1L)))
        val different = factory.create(listOf(observation(creator, prefix + "다른 영상", 2L)))
        assertEquals(15, first.creator!!.codePointCount(0, first.creator.length))
        assertEquals(20, first.title!!.codePointCount(0, first.title.length))
        assertTrue(first.identityTextTruncated)
        assertEquals(first.title, different.title)
        assertEquals(IdentityMatch.SAME, VideoIdentityMatcher().match(first, repeated))
        assertEquals(IdentityMatch.DIFFERENT, VideoIdentityMatcher().match(first, different))
        assertFalse(first.contentKey == different.contentKey)
    }

    @Test
    fun `length limit does not split supplementary Unicode characters`() {
        val text = "𠀀".repeat(200)
        val limited = VideoIdentityNormalizer.limit(text, 20)!!
        assertEquals(20, limited.codePointCount(0, limited.length))
        assertEquals("𠀀".repeat(20), limited)
    }

    @Test
    fun `minor OCR variation produces the same stable identity`() {
        val first = factory.create(
            listOf(
                observation("@channel", "고양이가 처음 눈을 본 날", 0L),
                observation("@channel", "고양이가 처음 눈을 본날", 1_000L)
            )
        )
        val second = factory.create(
            listOf(observation("channel", "고양이가 처음 눈을 본날", 2_000L))
        )

        assertEquals(IdentityQuality.HIGH, first.quality)
        assertNotNull(first.contentKey)
        assertEquals(IdentityMatch.SAME, VideoIdentityMatcher().match(first, second))
    }

    @Test
    fun `same title from a different creator is different content`() {
        val first = factory.create(listOf(observation("creator-a", "오늘의 뉴스 요약", 0L)))
        val second = factory.create(listOf(observation("creator-b", "오늘의 뉴스 요약", 0L)))

        assertEquals(IdentityMatch.DIFFERENT, VideoIdentityMatcher().match(first, second))
    }

    @Test
    fun `youtube extractor ignores right side text outside creator column`() {
        val extractor = YoutubeAccessibilityIdentityExtractor()
        val snapshot = ScreenSnapshot(
            texts = listOf("@cat_channel", "귀여운 고양이 영상", "댓글의 아주 긴 문장입니다"),
            viewIds = emptyList(),
            elements = listOf(
                element("@cat_channel", 0.05f, 0.65f, 0.45f, 0.70f),
                element("귀여운 고양이 영상", 0.05f, 0.72f, 0.70f, 0.80f),
                element("댓글의 아주 긴 문장입니다", 0.90f, 0.55f, 1.0f, 0.75f)
            )
        )

        val identity = extractor.extract(snapshot, 0L)

        assertEquals("cat_channel", identity.creator)
        assertTrue(identity.title.orEmpty().contains("귀여운 고양이 영상"))
        assertFalse(identity.title.orEmpty().contains("댓글"))
        assertNotNull(identity.contentKey)
    }

    private fun observation(creator: String, title: String, time: Long) =
        VideoIdentityObservation(
            platform = SupportedPlatform.YOUTUBE,
            creator = creator,
            title = title,
            observedAtElapsedMs = time
        )

    @Test fun `creator and title moving outside old percentage area retains identity`() {
        val extractor = YoutubeAccessibilityIdentityExtractor()
        fun extract(top: Float) = extractor.extract(ScreenSnapshot(emptyList(), emptyList(), listOf(
            element("@channel 채널로 이동", .05f, top, .13f, top + .04f),
            element("고정 영상 제목", .05f, top + .05f, .70f, top + .09f),
            element("댓글 311개 보기", .85f, top, 1f, top + .05f)
        )), 0L)
        val before = extract(.70f)
        val moved = extract(.30f)
        assertEquals(IdentityQuality.HIGH, moved.quality)
        assertEquals(before.contentKey, moved.contentKey)
        assertEquals("고정 영상 제목", moved.title)
    }

    @Test
    fun `creator accessibility action and video progress are not identity text`() {
        assertEquals("zyro_journey", VideoIdentityNormalizer.normalizeCreator("@Zyro_Journey을(를) 구독합니다."))
        assertEquals(null, VideoIdentityNormalizer.normalizeText("0분 4초 중 0분 2초"))
    }

    @Test
    fun `accessibility ignores subtitle above creator and playback progress below title`() {
        val elements = listOf(
            element("바뀌는 영상 자막", 0.05f, 0.60f, 0.70f, 0.64f),
            element("@channel을(를) 구독합니다.", 0.05f, 0.72f, 0.70f, 0.76f),
            element("고정 영상 제목", 0.05f, 0.80f, 0.70f, 0.84f),
            element("0분 4초 중 0분 2초", 0.05f, 0.91f, 0.70f, 0.95f)
        )
        val identity = YoutubeAccessibilityIdentityExtractor().extract(
            ScreenSnapshot(elements.mapNotNull { it.text }, emptyList(), elements), 0L
        )
        assertEquals("channel", identity.creator)
        assertEquals("고정 영상 제목", identity.title)
    }

    @Test
    fun `changing subtitle visibility does not change accessibility identity`() {
        val metadata = listOf(
            element("@channel", 0.05f, 0.72f, 0.70f, 0.76f),
            element("고정 영상 제목", 0.05f, 0.80f, 0.70f, 0.84f)
        )
        val extractor = YoutubeAccessibilityIdentityExtractor()
        fun snapshot(items: List<ScreenElement>) =
            ScreenSnapshot(items.mapNotNull { it.text }, emptyList(), items)
        val withSubtitle = extractor.extract(snapshot(metadata +
            element("3초에 보이는 자막", 0.05f, 0.60f, 0.70f, 0.64f)), 3_000L)
        val withoutSubtitle = extractor.extract(snapshot(metadata), 6_000L)
        assertEquals(withSubtitle.contentKey, withoutSubtitle.contentKey)
    }

    @Test
    fun `missing author or title does not create an alternative youtube identity`() {
        val extractor = YoutubeAccessibilityIdentityExtractor()
        for (items in listOf(
            listOf(element("@channel", 0.05f, 0.72f, 0.70f, 0.76f)),
            listOf(element("자막만 보이는 화면", 0.05f, 0.80f, 0.70f, 0.84f)),
            emptyList()
        )) {
            val identity = extractor.extract(
                ScreenSnapshot(items.mapNotNull { it.text }, emptyList(), items), 0L
            )
            assertEquals(IdentityQuality.NONE, identity.quality)
            assertEquals(null, identity.contentKey)
        }
    }

    private fun element(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) = ScreenElement(
        text = text,
        contentDescription = null,
        viewId = null,
        className = null,
        bounds = NormalizedBounds(left, top, right, bottom),
        isSelected = false
    )
}
