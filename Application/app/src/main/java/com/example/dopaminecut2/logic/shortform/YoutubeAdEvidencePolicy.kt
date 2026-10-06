package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.logic.manager.ScreenSnapshot
import java.util.Locale

data class YoutubeAdEvidence(val explicitAd: Boolean = false, val shoppingLink: Boolean = false)

/** 제목 속 단어가 아닌 독립된 플랫폼 라벨만 광고 단서로 사용한다. 실제 UI에서 추가 검증 필요. */
object YoutubeAdEvidenceDetector {
    fun detect(snapshot: ScreenSnapshot): YoutubeAdEvidence {
        val labels = snapshot.elements.asSequence()
            .filterNot { element ->
                val id = element.viewId.orEmpty().lowercase(Locale.ROOT)
                listOf("title", "metadata", "author", "channel", "comment", "sound", "music").any(id::contains)
            }
            .flatMap { it.visibleTexts().asSequence() }
            .map { it.trim().lowercase(Locale.ROOT) }.toSet()
        // 실제 광고의 '광고주\n광고' 접근성 설명을 보조 근거로 지원한다.
        // 임의 제목의 개행 광고 문구를 막기 위해 광고 컨테이너 구조가 함께 있을 때만 사용한다.
        val hasAdContainer = snapshot.hasResourceId("com.google.android.youtube", "reel_player_delegated_overlay") ||
            snapshot.hasResourceId("com.google.android.youtube", "non_video_page_element_container")
        val combinedAdLabel = hasAdContainer && snapshot.elements.any { element ->
            val id = element.viewId.orEmpty().lowercase(Locale.ROOT)
            val lines = element.contentDescription.orEmpty().lines().map(String::trim).filter(String::isNotEmpty)
            !listOf("title", "metadata", "comment", "sound", "music").any(id::contains) &&
                lines.size == 2 && lines.first().length <= 100 &&
                lines.last().lowercase(Locale.ROOT) in setOf("광고", "스폰서", "ad", "sponsored")
        }
        return YoutubeAdEvidence(
            explicitAd = combinedAdLabel || labels.any { it in setOf("광고", "스폰서", "스폰서 광고", "ad", "sponsored", "advertisement") },
            shoppingLink = labels.any { it in setOf("쇼핑하기", "수수료 지급", "shop", "shop now") }
        )
    }
}

data class YoutubeContentDecision(val kind: ShortformContentKind, val reason: String)

object YoutubeAdEvidencePolicy {
    fun resolve(prediction: ShortformContentPrediction?, evidence: YoutubeAdEvidence): YoutubeContentDecision {
        if (evidence.explicitAd) return YoutubeContentDecision(ShortformContentKind.AD, "EXPLICIT_AD_LABEL")
        val kind = prediction?.kind ?: ShortformContentKind.UNKNOWN
        if (evidence.shoppingLink && kind == ShortformContentKind.AD) {
            return YoutubeContentDecision(ShortformContentKind.NORMAL, "SHOPPING_NOT_PLATFORM_AD")
        }
        if (kind == ShortformContentKind.AD) {
            return YoutubeContentDecision(ShortformContentKind.UNKNOWN, "CV_AD_WITHOUT_EXPLICIT_LABEL")
        }
        return YoutubeContentDecision(kind, "CV_${kind.name}")
    }
}
