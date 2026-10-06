package com.example.dopaminecut2.ui.common


object UiFormatters {
    fun duration(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val hours = safeSeconds / 3_600
        val minutes = (safeSeconds % 3_600) / 60
        return when {
            hours > 0 -> "${hours}시간 ${minutes}분"
            minutes > 0 -> "${minutes}분"
            else -> "${safeSeconds}초"
        }
    }

}
