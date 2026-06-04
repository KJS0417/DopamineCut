package com.example.dopaminecut2.logic

import kotlinx.coroutines.*
import android.util.Log

/** * 숏폼 영상을 사용자가 실제로 유효하게 시청했는지(설정된 시간 이상 머물렀는지) 추적하고 판별하는 타이머 클래스
 * @param viewThresholdMs 유효 시청으로 인정할 기준 대기 시간 (예: 5000ms = 5초)
 * @param onValidViewCounted 목표 시청 시간을 무사히 달성했을 때 카운트를 증가시키기 위해 실행되는 콜백 함수
 */
class ViewTracker(
    private val viewThresholdMs: Long = 5000L,
    private val onValidViewCounted: (platform: String, videoId: String?) -> Unit
) {

    private var trackingJob: Job? = null
    private var currentVideoId: String? = null
    private val viewedSet = mutableSetOf<String>()

    fun onScreenChanged(
        isShortform: Boolean,
        videoId: String?,
        isAd: Boolean,
        platform: String
    ) {

        // 1. 숏폼 아니거나 videoId 없으면 종료
        if (!isShortform || videoId == null) {
            stopTracking()
            return
        }

        // 2. 광고면 무시
        if (isAd) {
            stopTracking()
            return
        }

        // 3. 이미 본 영상이면 무시
        if (viewedSet.contains(videoId)) {
            stopTracking()
            return
        }

        // 4. 같은 영상이면 유지
        if (videoId == currentVideoId) return

        // 5. 새로운 영상 → 타이머 시작
        currentVideoId = videoId
        startTracking(videoId, platform)
    }

    private fun startTracking(videoId: String, platform: String) {
        stopTracking()

        trackingJob = CoroutineScope(Dispatchers.Default).launch {
            delay(viewThresholdMs)

            viewedSet.add(videoId)

            withContext(Dispatchers.Main) {
                onValidViewCounted(platform, videoId)
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        currentVideoId = null
    }
}
