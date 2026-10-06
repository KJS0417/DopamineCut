package com.example.dopaminecut2.domain

enum class ContentCategory(val id: String, val label: String) {
    GAME("GAME", "게임"),
    HUMOR("HUMOR", "유머"),
    EDUCATION("EDUCATION", "교육"),
    SOCIETY("SOCIETY", "사회"),
    FOOD("FOOD", "먹방"),
    SPORTS("SPORTS", "스포츠"),
    MUSIC("MUSIC", "음악"),
    ENTERTAINMENT("ENTERTAINMENT", "연예"),
    LIVE("LIVE", "라이브"),
    OTHER("OTHER", "기타"),
    UNKNOWN("UNKNOWN", "미확인");

    companion object {
        fun fromStored(value: String?): ContentCategory {
            return entries.firstOrNull { category ->
                category.id.equals(value, ignoreCase = true) || category.label == value
            } ?: OTHER
        }
    }
}
