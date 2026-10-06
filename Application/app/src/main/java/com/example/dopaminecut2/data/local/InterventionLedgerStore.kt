package com.example.dopaminecut2.data.local

data class InterventionLedger(val notified: Set<String> = emptySet(), val snoozedUntil: Map<String, Long> = emptyMap())
interface InterventionLedgerStore {
    suspend fun loadInterventionLedger(uid: String, date: String): InterventionLedger
    suspend fun saveInterventionLedger(uid: String, date: String, ledger: InterventionLedger)
}
