package com.example.dopaminecut2.logic.ai

class GeminiCategoryClassifier {

    fun classify(text: String): String {
        return when {
            text.contains("게임") -> "GAME"
            text.contains("먹방") -> "FOOD"
            text.contains("공부") -> "STUDY"
            text.contains("뉴스") -> "NEWS"
            else -> "UNKNOWN"
        }
    }
}
