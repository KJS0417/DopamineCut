package com.example.dopaminecut2.data.local

import kotlinx.coroutines.flow.Flow

enum class NotificationOption(val storageId: String) {
    MORNING("morning"), LUNCH("lunch"), EVENING("evening"),
    GOAL_PROGRESS("goal_progress"), MEASUREMENT_INTERRUPTION("measurement_interruption")
}

data class NotificationSettings(
    val enabledOptions: Set<NotificationOption> = NotificationOption.entries.toSet()
) {
    fun isEnabled(option: NotificationOption): Boolean = option in enabledOptions

    fun withEnabled(option: NotificationOption, enabled: Boolean): NotificationSettings = copy(
        enabledOptions = if (enabled) enabledOptions + option else enabledOptions - option
    )

    fun storageIds(): Set<String> = enabledOptions.map { it.storageId }.toSet()

    companion object {
        fun fromStorageIds(ids: Set<String>?): NotificationSettings =
            if (ids == null) NotificationSettings() else NotificationSettings(
                NotificationOption.entries.filter { it.storageId in ids }.toSet()
            )
    }
}

interface NotificationSettingsStore {
    fun observeNotificationSettings(userId: String): Flow<NotificationSettings>
    suspend fun saveNotificationSettings(userId: String, settings: NotificationSettings)
}
