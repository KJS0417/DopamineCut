package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.domain.SupportedPlatform
import com.example.dopaminecut2.logic.manager.ScreenElement
import com.example.dopaminecut2.logic.manager.ScreenSnapshot
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

object VideoIdentityNormalizer {
    const val MAX_CREATOR_CODE_POINTS = 15
    const val MAX_TITLE_CODE_POINTS = 20

    fun limit(value: String?, maximumCodePoints: Int): String? {
        if (value == null || value.codePointCount(0, value.length) <= maximumCodePoints) return value
        return value.substring(0, value.offsetByCodePoints(0, maximumCodePoints))
    }
    private val staticUiTexts = setOf(
        "좋아요", "like", "댓글", "comment", "공유", "share", "저장", "save",
        "구독", "subscribe", "리믹스", "remix", "shorts", "쇼츠", "홈", "home", "내 페이지",
        "쇼핑하기", "수수료 지급", "자동 더빙"
    )
    private val dynamicCount = Regex("""^[0-9]+(?:[.,][0-9]+)?(?:천|만|억|k|m)?$""", RegexOption.IGNORE_CASE)
    private val liveViewerCount = Regex("""^[0-9.,]+(?:천|만|억|k|m)?명\s*시청\s*중$""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")
    private val unsupportedCharacters = Regex("""[^\p{L}\p{N}@#_]+""")
    private val playbackProgress = Regex("""(?:\d+분\s*)?\d+초\s*중\s*(?:\d+분\s*)?\d+초""")
    private val creatorHandle = Regex("""^@([\p{L}\p{M}\p{N}_.-]+)""")
    private val creatorAction = Regex(
        """(?:\s+채널로\s*이동|(?:을\(를\)|를|을)?\s*구독합니다)[.!。]?\s*$"""
    )

    fun normalizeText(value: String?): String? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (playbackProgress.matches(raw) || liveViewerCount.matches(raw)) return null
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(unsupportedCharacters, " ")
            .replace(whitespace, " ")
            .trim()
        if (normalized.length < 2 || normalized in staticUiTexts || dynamicCount.matches(normalized)) {
            return null
        }
        return normalized
    }

    fun normalizeCreator(value: String?): String? {
        val raw = value?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
            ?.trim()?.replace(creatorAction, "")?.trim() ?: return null
        val handle = creatorHandle.find(raw)?.groupValues?.get(1)
        return normalizeText(handle ?: raw)?.removePrefix("@")
    }

    fun isMeaningful(value: String?): Boolean = normalizeText(value) != null
}

class VideoIdentityFactory {
    fun create(observations: List<VideoIdentityObservation>): VideoIdentity {
        require(observations.isNotEmpty())
        val platform = observations.first().platform
        require(observations.all { it.platform == platform })

        val creator = stableValue(observations.mapNotNull { VideoIdentityNormalizer.normalizeCreator(it.creator) })
        val title = stableValue(observations.mapNotNull { VideoIdentityNormalizer.normalizeText(it.title) })
        val audio = stableValue(observations.mapNotNull { VideoIdentityNormalizer.normalizeText(it.audio) })
        val visualHash = stableValue(observations.mapNotNull { it.titleVisualHash?.trim()?.lowercase() })
        val quality = qualityOf(creator, title, audio, visualHash)
        val canonical = canonicalValue(platform, creator, title, audio, visualHash, quality)
        val support = listOfNotNull(creator, title, audio).sumOf { selected ->
            observations.count { observation ->
                listOf(observation.creator, observation.title, observation.audio)
                    .mapNotNull(VideoIdentityNormalizer::normalizeText)
                    .any { similarity(it, selected) >= STABLE_SIMILARITY }
            }
        }
        val possibleSupport = max(1, observations.size * listOfNotNull(creator, title, audio).size)
        val qualityBase = when (quality) {
            IdentityQuality.HIGH -> 0.90f
            IdentityQuality.MEDIUM -> 0.75f
            IdentityQuality.LOW -> 0.55f
            IdentityQuality.NONE -> 0f
        }
        val stability = support.toFloat() / possibleSupport
        val limitedCreator = VideoIdentityNormalizer.limit(creator, VideoIdentityNormalizer.MAX_CREATOR_CODE_POINTS)
        val limitedTitle = VideoIdentityNormalizer.limit(title, VideoIdentityNormalizer.MAX_TITLE_CODE_POINTS)

        return VideoIdentity(
            platform = platform,
            contentKey = canonical?.let(::sha256),
            creator = limitedCreator,
            title = limitedTitle,
            audio = audio,
            titleVisualHash = visualHash,
            quality = quality,
            confidence = (qualityBase * (0.75f + stability * 0.25f)).coerceIn(0f, 1f),
            identityTextTruncated = limitedCreator != creator || limitedTitle != title
        )
    }

    private fun stableValue(values: List<String>): String? {
        if (values.isEmpty()) return null
        return values.maxWithOrNull(
            compareBy<String> { candidate ->
                values.count { similarity(candidate, it) >= STABLE_SIMILARITY }
            }.thenBy(String::length)
        )
    }

    private fun qualityOf(
        creator: String?,
        title: String?,
        audio: String?,
        visualHash: String?
    ): IdentityQuality = when {
        creator != null && title != null -> IdentityQuality.HIGH
        title != null && audio != null -> IdentityQuality.MEDIUM
        creator != null && visualHash != null -> IdentityQuality.MEDIUM
        title != null || (creator != null && audio != null) -> IdentityQuality.LOW
        else -> IdentityQuality.NONE
    }

    private fun canonicalValue(
        platform: SupportedPlatform,
        creator: String?,
        title: String?,
        audio: String?,
        visualHash: String?,
        quality: IdentityQuality
    ): String? = when (quality) {
        IdentityQuality.HIGH -> "${platform.storageKey}|$creator|$title"
        IdentityQuality.MEDIUM -> if (title != null) {
            "${platform.storageKey}|$title|$audio"
        } else {
            "${platform.storageKey}|$creator|$visualHash"
        }
        IdentityQuality.LOW -> "${platform.storageKey}|${title ?: creator}|${audio.orEmpty()}"
        IdentityQuality.NONE -> null
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        internal const val STABLE_SIMILARITY = 0.82

        internal fun similarity(first: String?, second: String?): Double {
            if (first == null || second == null) return 0.0
            if (first == second) return 1.0
            val left = first.replace(" ", "")
            val right = second.replace(" ", "")
            if (left.isEmpty() || right.isEmpty()) return 0.0
            val editSimilarity = 1.0 - levenshtein(left, right).toDouble() / max(left.length, right.length)
            val firstTokens = first.split(' ').filter(String::isNotEmpty).toSet()
            val secondTokens = second.split(' ').filter(String::isNotEmpty).toSet()
            val tokenSimilarity = if (firstTokens.isEmpty() || secondTokens.isEmpty()) {
                0.0
            } else {
                firstTokens.intersect(secondTokens).size.toDouble() /
                    firstTokens.union(secondTokens).size
            }
            return max(editSimilarity, tokenSimilarity)
        }

        private fun levenshtein(first: String, second: String): Int {
            var previous = IntArray(second.length + 1) { it }
            for (firstIndex in first.indices) {
                val current = IntArray(second.length + 1)
                current[0] = firstIndex + 1
                for (secondIndex in second.indices) {
                    val substitution = previous[secondIndex] +
                        if (first[firstIndex] == second[secondIndex]) 0 else 1
                    current[secondIndex + 1] = minOf(
                        current[secondIndex] + 1,
                        previous[secondIndex + 1] + 1,
                        substitution
                    )
                }
                previous = current
            }
            return previous[second.length]
        }
    }
}

class VideoIdentityMatcher(
    private val sameThreshold: Double = 0.82,
    private val differentThreshold: Double = 0.45
) {
    fun match(current: VideoIdentity, candidate: VideoIdentity): IdentityMatch {
        if (current.platform != candidate.platform) return IdentityMatch.DIFFERENT
        if (current.contentKey != null && current.contentKey == candidate.contentKey) {
            return IdentityMatch.SAME
        }
        // 잘린 접두어가 같아도 전체 원문 해시가 다르면 같은 영상으로 합치지 않는다.
        if (current.identityTextTruncated || candidate.identityTextTruncated) {
            return IdentityMatch.DIFFERENT
        }

        val creatorSimilarity = comparableSimilarity(current.creator, candidate.creator)
        if (creatorSimilarity != null && creatorSimilarity < CREATOR_SAME_THRESHOLD) {
            return IdentityMatch.DIFFERENT
        }

        val weighted = listOf(
            Triple(current.title to candidate.title, 0.55, "title"),
            Triple(current.creator to candidate.creator, 0.25, "creator"),
            Triple(current.audio to candidate.audio, 0.10, "audio"),
            Triple(current.titleVisualHash to candidate.titleVisualHash, 0.10, "visual")
        ).mapNotNull { (values, weight, type) ->
            val score = if (type == "visual") {
                visualHashSimilarity(values.first, values.second)
            } else {
                comparableSimilarity(values.first, values.second)
            }
            score?.let { it to weight }
        }

        if (weighted.isEmpty()) return IdentityMatch.INDETERMINATE
        val totalWeight = weighted.sumOf { it.second }
        val score = weighted.sumOf { it.first * it.second } / totalWeight
        return when {
            score >= sameThreshold -> IdentityMatch.SAME
            score <= differentThreshold -> IdentityMatch.DIFFERENT
            else -> IdentityMatch.INDETERMINATE
        }
    }

    private fun comparableSimilarity(first: String?, second: String?): Double? {
        if (first == null || second == null) return null
        return VideoIdentityFactory.similarity(first, second)
    }

    private fun visualHashSimilarity(first: String?, second: String?): Double? {
        if (first == null || second == null || first.length != second.length) return null
        if (first.isEmpty()) return null
        val matches = first.indices.count { first[it] == second[it] }
        return matches.toDouble() / first.length
    }

    private companion object {
        const val CREATOR_SAME_THRESHOLD = 0.92
    }
}

class YoutubeAccessibilityIdentityExtractor(
    private val factory: VideoIdentityFactory = VideoIdentityFactory()
) {
    /** OCR 결과 선별용 작성자 상단. 작성자명 자체는 분류 텍스트에 넣지 않는다. */
    fun creatorTop(snapshot: ScreenSnapshot): Float? = creatorBounds(snapshot)?.top

    fun creatorBounds(snapshot: ScreenSnapshot): com.example.dopaminecut2.logic.manager.NormalizedBounds? = snapshot.elements
        .filter { it.bounds != null }
        .sortedWith(compareBy({ it.bounds?.top }, { it.bounds?.left }))
        .firstOrNull { element -> element.visibleTexts().any { text ->
            VideoIdentityNormalizer.isMeaningful(text) && (text.trim().startsWith("@") ||
                element.viewId.orEmpty().contains("author", ignoreCase = true) ||
                element.viewId.orEmpty().contains("channel", ignoreCase = true))
        } }?.bounds

    fun extract(snapshot: ScreenSnapshot, observedAtElapsedMs: Long): VideoIdentity {
        // 작성자 위치를 먼저 찾는다. 화면 높이 58~96%라는 고정 탐색 영역에 의존하지 않는다.
        val positioned = snapshot.elements.filter { it.bounds != null }
            .flatMap { element -> element.visibleTexts().map { text -> element to text } }
            .filter { (_, text) -> VideoIdentityNormalizer.isMeaningful(text) }
            .sortedWith(compareBy({ it.first.bounds?.top ?: 1f }, { it.first.bounds?.left ?: 1f }))

        val source = if (positioned.isNotEmpty()) positioned else snapshot.texts.map { null to it }
        val creator = source.firstNotNullOfOrNull { (element, text) ->
            val id = element?.viewId.orEmpty()
            text.takeIf {
                it.trim().startsWith("@") ||
                    id.contains("author", ignoreCase = true) ||
                    id.contains("channel", ignoreCase = true)
            }
        }
        val audio = source.firstNotNullOfOrNull { (element, text) ->
            val id = element?.viewId.orEmpty()
            text.takeIf {
                id.contains("music", ignoreCase = true) ||
                    id.contains("sound", ignoreCase = true) ||
                    it.contains("음원", ignoreCase = true)
            }
        }
        val creatorTop = positioned.firstOrNull { it.second == creator }?.first?.bounds?.top
        val creatorBounds = positioned.firstOrNull { it.second == creator }?.first?.bounds
        val titleParts = source.mapNotNull { (element, text) ->
            val id = element?.viewId.orEmpty()
            val normalized = VideoIdentityNormalizer.normalizeText(text) ?: return@mapNotNull null
            val isKnownMetadata = id.contains("title", ignoreCase = true) ||
                id.contains("metadata", ignoreCase = true)
            val isCreator = text == creator || text.trim().startsWith("@") ||
                id.contains("author", ignoreCase = true) || id.contains("channel", ignoreCase = true)
            val isAudio = text == audio
            val belowCreator = creatorTop == null || (element?.bounds?.top ?: 0f) >= creatorTop
            val inCreatorColumn = creatorBounds != null && element?.bounds?.let {
                it.left >= creatorBounds.left - creatorBounds.width &&
                    it.left <= creatorBounds.left + creatorBounds.width
            } == true
            val isAction = listOf("채널로 이동", "구독합니다", "댓글 ", "동영상 공유", "이 사운드를 사용하는",
                "좋아요 표시", "위로 이동", "아래로 이동", "동영상 일시중지", "동영상 일시 중지",
                "동영상 일지중지", "동영상 재생", "탭하여 실시간으로 시청하기",
                "go to channel", "comments", "use this sound").any { text.contains(it, ignoreCase = true) }
            normalized.takeIf { creator != null && !isCreator && !isAudio &&
                !isAction && (isKnownMetadata || positioned.isNotEmpty() && belowCreator && inCreatorColumn) }
        }.distinct().take(MAX_TITLE_LINES)

        return factory.create(
            listOf(
                VideoIdentityObservation(
                    platform = SupportedPlatform.YOUTUBE,
                    creator = creator.takeIf { titleParts.isNotEmpty() },
                    title = titleParts.joinToString(" ").ifBlank { null },
                    audio = null,
                    observedAtElapsedMs = observedAtElapsedMs
                )
            )
        )
    }

    private companion object {
        const val MAX_TITLE_LINES = 3
    }
}
