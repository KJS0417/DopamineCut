package com.example.dopaminecut2.domain

enum class SupportedPlatform(
    val packageName: String,
    val storageKey: String,
    val displayName: String
) {
    YOUTUBE("com.google.android.youtube", "youtube", "YouTube"),
    INSTAGRAM("com.instagram.android", "instagram", "Instagram"),
    KAKAOTALK("com.kakao.talk", "kakaotalk", "KakaoTalk"),
    TIKTOK("com.zhiliaoapp.musically", "tiktok", "TikTok");

    companion object {
        fun fromStorageKey(value: String): SupportedPlatform? =
            entries.firstOrNull { it.storageKey.equals(value, ignoreCase = true) }
    }
}
