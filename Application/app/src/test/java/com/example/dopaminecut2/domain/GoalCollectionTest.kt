package com.example.dopaminecut2.domain

import com.example.dopaminecut2.data.local.GoalStorageCodec
import org.junit.Assert.*
import org.junit.Test

class GoalCollectionTest {
    @Test fun `remote change invalidates stale editors even when revisions collided`() {
        val old = goal().copy(revision = 2)
        val imported = GoalCollection.importRemote(listOf(old), listOf(old.copy(target = 10)))
        assertEquals(3L, imported.single().revision)
        assertThrows(IllegalArgumentException::class.java) { GoalCollection.save(imported, listOf(old)) }
        assertEquals(imported, GoalCollection.importRemote(imported, listOf(old.copy(target = 10))))
    }
    private fun goal(metric: GoalMetric = GoalMetric.DAILY_TIME, target: Int = 30) =
        ManagedGoal(metric, target, setOf(SupportedPlatform.YOUTUBE), InterventionMode.RECORD_ONLY)

    @Test fun `three goal kinds are saved together`() {
        val goals = GoalCollection.save(emptyList(), GoalMetric.entries.map { goal(it) })
        assertEquals(3, goals.size)
        assertTrue(goals.all { it.revision == 1L && it.status == GoalStatus.ACTIVE })
    }
    @Test fun `edit leaves other targets untouched`() {
        val current = GoalCollection.save(emptyList(), GoalMetric.entries.map { goal(it) })
        val edited = GoalCollection.save(current, listOf(current[0].copy(target = 15)))
        assertEquals(15, edited[0].target)
        assertEquals(current.drop(1), edited.drop(1))
    }
    @Test fun `pause and restore preserve value platforms and intervention`() {
        val current = GoalCollection.save(emptyList(), listOf(goal().copy(intervention = InterventionMode.RESTRICT)))
        val paused = GoalCollection.setStatus(current, GoalMetric.DAILY_TIME, 1, GoalStatus.PAUSED)
        val resumed = GoalCollection.setStatus(paused, GoalMetric.DAILY_TIME, 2, GoalStatus.ACTIVE)
        assertEquals(current[0].copy(revision = 3), resumed[0])
    }
    @Test fun `stale edit cannot undo pause`() {
        val current = GoalCollection.save(emptyList(), listOf(goal()))
        val paused = GoalCollection.setStatus(current, GoalMetric.DAILY_TIME, 1, GoalStatus.PAUSED)
        assertThrows(IllegalArgumentException::class.java) { GoalCollection.save(paused, listOf(current[0].copy(target = 10))) }
    }
    @Test fun `editing paused goal does not start it`() {
        val current = GoalCollection.save(emptyList(), listOf(goal().copy(status = GoalStatus.PAUSED)))
        assertEquals(GoalStatus.PAUSED, GoalCollection.save(current, listOf(current[0].copy(target = 10)))[0].status)
    }
    @Test fun `invalid goal rejects entire batch`() {
        val current = GoalCollection.save(emptyList(), listOf(goal()))
        assertThrows(IllegalArgumentException::class.java) { GoalCollection.save(current, listOf(current[0].copy(target = 10), goal(GoalMetric.DAILY_COUNT, 1000))) }
        assertEquals(30, current[0].target)
    }
    @Test fun `duplicate goal kind is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { GoalCollection.save(emptyList(), listOf(goal(), goal(target = 10))) }
    }
    @Test fun `zero is an active abstinence target not a paused goal`() {
        val goals = GoalCollection.save(emptyList(), listOf(goal(target = 0)))
        assertEquals(0, goals[0].target)
        assertEquals(GoalStatus.ACTIVE, goals[0].status)
    }
    @Test fun `time and count upper bounds differ`() {
        goal(target = 1440).validate()
        goal(GoalMetric.DAILY_COUNT, 999).validate()
        assertThrows(IllegalArgumentException::class.java) { goal(target = 1441).validate() }
    }
    @Test fun `empty platforms rejected`() {
        assertThrows(IllegalArgumentException::class.java) { goal().copy(platforms = emptySet()).validate() }
    }
    @Test fun `unsupported platforms rejected`() {
        assertThrows(IllegalArgumentException::class.java) { goal().copy(platforms = setOf(SupportedPlatform.TIKTOK)).validate() }
    }
    @Test fun `codec restores all persisted states and revisions`() {
        val goals = GoalMetric.entries.map { goal(it).copy(revision = 12, status = GoalStatus.PAUSED, platforms = ManagedGoal.SUPPORTED) }
        assertEquals(goals, GoalStorageCodec.decode(GoalStorageCodec.encode(goals)))
        assertEquals(emptyList<ManagedGoal>(), GoalStorageCodec.decode(null))
    }
    @Test fun `corrupt storage is reported not silently cleared`() {
        assertThrows(IllegalArgumentException::class.java) { GoalStorageCodec.decode("v1\nDAILY_TIME|bad") }
    }

}
