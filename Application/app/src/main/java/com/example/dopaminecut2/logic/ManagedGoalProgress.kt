package com.example.dopaminecut2.logic

import com.example.dopaminecut2.data.model.AppUsage
import com.example.dopaminecut2.domain.SupportedPlatform
import com.example.dopaminecut2.logic.shortform.ShortformContentKind
import com.example.dopaminecut2.logic.shortform.ShortformSessionCheckpoint

/** 저장된 일일 집계에 아직 반영되지 않은 현재 세션의 진행만 더한다. 원본은 변경하지 않는다. */
object ManagedGoalProgress {
    fun effectiveUsage(
        persisted: Map<String, AppUsage>,
        foreground: SupportedPlatform,
        activeAppSec: Long,
        active: ShortformSessionCheckpoint?,
        checkpoint: ShortformSessionCheckpoint?
    ): Map<String, AppUsage> {
        val result = persisted.toMutableMap()
        val app = result[foreground.storageKey] ?: AppUsage()
        result[foreground.storageKey] = app.copy(runTimeSec = app.runTimeSec + activeAppSec.coerceAtLeast(0L))
        if (active == null || active.platform != foreground ||
            active.contentKind !in setOf(ShortformContentKind.NORMAL, ShortformContentKind.LIVE, ShortformContentKind.PHOTO_POST)) return result

        val saved = checkpoint?.takeIf {
            it.viewSessionId == active.viewSessionId && it.platform == active.platform && it.contentKind == active.contentKind
        }
        val value = result[active.platform.storageKey] ?: AppUsage()
        result[active.platform.storageKey] = value.copy(
            shortformTimeSec = value.shortformTimeSec + (active.durationSec - (saved?.durationSec ?: 0L)).coerceAtLeast(0L),
            shortformCount = value.shortformCount + if (active.contentKind == ShortformContentKind.NORMAL) {
                (active.count - (saved?.count ?: 0L)).coerceAtLeast(0L)
            } else 0L
        )
        return result
    }

}
