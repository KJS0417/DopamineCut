package com.example.dopaminecut2.logic.shortform

import java.util.UUID

/** 라이브/사진 게시물 시간 전용 체류 식별자. 작성자·제목·시청자 수를 보관하지 않는다. */
class YoutubeLiveSession(private val idFactory: () -> String = { UUID.randomUUID().toString() }) {
    var key: String? = null
        private set
    var confirmed: Boolean = false
        private set
    var kind: ShortformContentKind = ShortformContentKind.LIVE
        private set

    fun confirm(contentKind: ShortformContentKind = ShortformContentKind.LIVE): String {
        require(contentKind == ShortformContentKind.LIVE || contentKind == ShortformContentKind.PHOTO_POST)
        if (kind != contentKind) reset()
        kind = contentKind
        val prefix = if (kind == ShortformContentKind.LIVE) "youtube_live" else "youtube_photo"
        val activeKey = key ?: "${prefix}_${idFactory()}".also { key = it }
        confirmed = true
        return activeKey
    }

    fun pause() { confirmed = false }

    fun reset() {
        key = null
        confirmed = false
    }
}
