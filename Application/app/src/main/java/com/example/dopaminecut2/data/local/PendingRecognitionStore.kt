package com.example.dopaminecut2.data.local

import com.example.dopaminecut2.domain.SupportedPlatform

/** Local-only uncertainty ledger: never included in daily totals or cloud payloads. */
data class PendingRecognition(
    val userId: String,
    val date: String,
    val sessionId: String,
    val platform: SupportedPlatform,
    val durationSec: Long,
    val ended: Boolean,
    val updatedAtEpochMs: Long
) {
    init {
        require(userId.isNotBlank() && date.matches(Regex("[0-9]{8}")))
        require(sessionId.matches(Regex("[A-Za-z0-9_-]{1,128}")) && durationSec >= 5L)
        require(updatedAtEpochMs >= 0L)
    }
}

interface PendingRecognitionStore {
    suspend fun upsertPendingRecognition(record: PendingRecognition)
    suspend fun removePendingRecognition(userId: String, sessionId: String)
    suspend fun pendingRecognitions(userId: String): List<PendingRecognition>
}
