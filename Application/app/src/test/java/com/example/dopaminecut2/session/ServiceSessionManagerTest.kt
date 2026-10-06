package com.example.dopaminecut2.session

import com.example.dopaminecut2.auth.AuthRepository
import com.example.dopaminecut2.time.DateIdProvider
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceSessionManagerTest {
    @Test
    fun `detects date account and logout transitions`() {
        val auth = FakeAuth("a")
        var date = "20260812"
        val manager = ServiceSessionManager(auth, DateIdProvider { date })

        val first = manager.pendingTransition()!!
        assertEquals(ServiceSession("a", "20260812"), first.current)
        manager.activate(first.current)
        assertNull(manager.pendingTransition())

        date = "20260813"
        val midnight = manager.pendingTransition()!!
        assertEquals("20260812", midnight.previous?.date)
        assertEquals("20260813", midnight.current?.date)
        manager.activate(midnight.current)

        auth.userId = "b"
        val account = manager.pendingTransition()!!
        assertEquals("a", account.previous?.userId)
        assertEquals("b", account.current?.userId)
        manager.activate(account.current)

        auth.userId = null
        assertNull(manager.pendingTransition()!!.current)
    }

    private class FakeAuth(var userId: String?) : AuthRepository {
        override fun currentUserId(): String? = userId
        override suspend fun login(email: String, password: String) = Result.success(Unit)
        override suspend fun signup(email: String, password: String, nickname: String) = Result.success(Unit)
        override fun logout() = Result.success(Unit)
    }
}
