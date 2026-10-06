package com.example.dopaminecut2.session

import com.example.dopaminecut2.auth.AuthRepository
import com.example.dopaminecut2.time.DateIdProvider

data class ServiceSession(val userId: String, val date: String)

data class SessionTransition(
    val previous: ServiceSession?,
    val current: ServiceSession?
)

class ServiceSessionManager(
    private val authRepository: AuthRepository,
    private val dateIdProvider: DateIdProvider
) {
    private var activeSession: ServiceSession? = null

    fun active(): ServiceSession? = activeSession

    fun pendingTransition(force: Boolean = false): SessionTransition? {
        val next = authRepository.currentUserId()?.let { userId ->
            ServiceSession(userId, dateIdProvider.currentDateId())
        }
        if (!force && next == activeSession) return null
        return SessionTransition(activeSession, next)
    }

    fun activate(session: ServiceSession?) {
        activeSession = session
    }

    fun clear(): ServiceSession? = activeSession.also { activeSession = null }
}
