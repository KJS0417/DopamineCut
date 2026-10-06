package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.logic.manager.NormalizedBounds
import com.example.dopaminecut2.logic.manager.ScreenSnapshot

/** 분류에 불필요한 계정 정보와 공통 UI를 제거하고 영상 콘텐츠 영역의 텍스트만 남긴다. */
class OcrContentTextSanitizer(
    private val maxLines: Int = 24,
    private val maxCharacters: Int = 1_500
) {
    fun sanitizeYoutube(frame: OcrFrame, snapshot: ScreenSnapshot): String {
        val region = YoutubeContentRegionResolver().resolve(frame, snapshot) ?: return ""
        val excluded = snapshot.elements.filter { element ->
            val id = element.viewId.orEmpty().substringAfterLast('/').lowercase()
            // 넓은 전체 overlay 컨테이너가 아니라 실제 버튼/패널만 제외한다.
            id.contains("button") || id.contains("comments_panel") || id.contains("engagement_panel")
        }.mapNotNull { element -> element.bounds?.let {
            snapshot.window?.toCapture(it, frame.width, frame.height)
        } }
        return sanitizeTexts(frame.lines.filter { line ->
            line.bounds.left >= region.left && line.bounds.right <= region.right &&
                line.bounds.top >= region.top && line.bounds.bottom <= region.bottom &&
                excluded.none { box -> line.bounds.left < box.right && line.bounds.right > box.left &&
                    line.bounds.top < box.bottom && line.bounds.bottom > box.top }
        }.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })).map(OcrTextLine::text))
    }

    fun sanitize(frame: OcrFrame, contentBottom: Float = 0.90f): String = sanitizeTexts(
        frame.lines
            .filter { line -> contentBottom.isFinite() && contentBottom > 0.10f &&
                line.bounds.centerIsInside(NormalizedBounds(0.03f, 0.10f, 0.88f, contentBottom.coerceAtMost(0.90f))) &&
                line.bounds.bottom <= contentBottom }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            .map(OcrTextLine::text)
    )

    fun sanitizeFallback(texts: List<String>): String = sanitizeTexts(texts)

    private fun sanitizeTexts(texts: List<String>): String = texts.asSequence()
        .map { it.replace(WHITESPACE, " ").trim() }
        .filter { it.length >= MIN_TEXT_LENGTH }
        .filterNot(::containsPersonalIdentifier)
        .filterNot(::isStaticUiText)
        .distinct()
        .take(maxLines)
        .joinToString(" ")
        .take(maxCharacters)
        .trim()

    private fun containsPersonalIdentifier(text: String): Boolean =
        text.startsWith("@") ||
            EMAIL.containsMatchIn(text) ||
            URL.containsMatchIn(text) ||
            PHONE.containsMatchIn(text)

    private fun isStaticUiText(text: String): Boolean {
        val normalized = text.lowercase().trim()
        return normalized in STATIC_UI || COUNT_ONLY.matches(normalized)
    }

    private companion object {
        val WHITESPACE = Regex("""\s+""")
        val EMAIL = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
        val URL = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
        val PHONE = Regex("""(?:\+?\d[\d\s-]{7,}\d)""")
        val COUNT_ONLY = Regex("""^[0-9]+(?:[.,][0-9]+)?(?:천|만|억|k|m)?$""", RegexOption.IGNORE_CASE)
        const val MIN_TEXT_LENGTH = 2
        val STATIC_UI = setOf(
            "좋아요", "like", "댓글", "comment", "공유", "share", "저장", "save",
            "구독", "subscribe", "리믹스", "remix", "홈", "home", "shorts", "쇼츠"
        )
    }
}
