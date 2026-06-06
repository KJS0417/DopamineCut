package com.example.dopaminecut2.logic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewTracker(
    private val viewThresholdMs: Long = 5000L,
    private val onValidViewCounted: (platform: String, videoId: String?) -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var trackingJob: Job? = null
    private var currentVideoId: String? = null
    private val viewedSet = mutableSetOf<String>()

    fun onScreenChanged(
        isShortform: Boolean,
        videoId: String?,
        isAd: Boolean,
        platform: String
    ) {
        val normalizedVideoId = videoId?.trim()?.takeIf { it.isNotEmpty() }

        if (!isShortform || normalizedVideoId == null || isAd) {
            stopTracking()
            return
        }

        if (viewedSet.contains(normalizedVideoId)) {
            stopTracking()
            return
        }

        if (normalizedVideoId == currentVideoId) {
            return
        }

        currentVideoId = normalizedVideoId
        startTracking(normalizedVideoId, platform)
    }

    private fun startTracking(videoId: String, platform: String) {
        stopTracking(clearCurrentVideo = false)

        trackingJob = scope.launch {
            delay(viewThresholdMs)

            if (videoId != currentVideoId) return@launch
            if (viewedSet.contains(videoId)) return@launch

            viewedSet.add(videoId)

            withContext(Dispatchers.Main) {
                onValidViewCounted(platform, videoId)
            }
        }
    }

    private fun stopTracking(clearCurrentVideo: Boolean = true) {
        trackingJob?.cancel()
        trackingJob = null

        if (clearCurrentVideo) {
            currentVideoId = null
        }
    }

    fun clearViewedHistory() {
        viewedSet.clear()
    }

    fun release() {
        stopTracking()
        scope.cancel()
    }
}
