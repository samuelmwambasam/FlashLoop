package com.flashloop.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class FreqMode { DAILY, WEEKLY, MONTHLY }

data class AppSettings(
    val enabled: Boolean = true,
    val freqMode: FreqMode = FreqMode.DAILY,
    val freqCount: Int = 3,
    val activeStartMin: Int = 8 * 60,
    val activeEndMin: Int = 22 * 60,
    val quietEnabled: Boolean = false,
    val quietStartMin: Int = 12 * 60,
    val quietEndMin: Int = 14 * 60,
    val maxPerHour: Int = 2,            // 0 = no limit
    val overlayEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val deckFilterId: Long = 0,         // 0 = all decks
    val pausedUntil: Long = 0,          // Long.MAX_VALUE = paused until resumed
    // Internal scheduler state (persisted so it survives process death / reboot)
    val pendingCardId: Long = 0,
    val snoozedUntil: Long = 0,
    val lastShownAt: Long = 0,
    val lastShownCardId: Long = 0,
    val recentShown: List<Long> = emptyList(),
    val nextAlarmAt: Long = 0           // when the next interruption is planned (0 = none)
)

fun fmtTime(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

fun AppSettings.summary(): String {
    val per = when (freqMode) {
        FreqMode.DAILY -> "day"
        FreqMode.WEEKLY -> "week"
        FreqMode.MONTHLY -> "month"
    }
    val how = if (overlayEnabled) "floating card" else "notification"
    return when {
        !enabled -> "Automatic interruptions are off"
        pausedUntil > System.currentTimeMillis() -> "Automatic interruptions are paused"
        else -> "$freqCount per $per · ${fmtTime(activeStartMin)}–${fmtTime(activeEndMin)} · $how"
    }
}

object Keys {
    val ENABLED = booleanPreferencesKey("enabled")
    val FREQ_MODE = stringPreferencesKey("freq_mode")
    val FREQ_COUNT = intPreferencesKey("freq_count")
    val ACTIVE_START = intPreferencesKey("active_start")
    val ACTIVE_END = intPreferencesKey("active_end")
    val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
    val QUIET_START = intPreferencesKey("quiet_start")
    val QUIET_END = intPreferencesKey("quiet_end")
    val MAX_PER_HOUR = intPreferencesKey("max_per_hour")
    val OVERLAY = booleanPreferencesKey("overlay_enabled")
    val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    val SNOOZE_MIN = intPreferencesKey("snooze_minutes")
    val DECK_FILTER = longPreferencesKey("deck_filter")
    val PAUSED_UNTIL = longPreferencesKey("paused_until")
    val PENDING_CARD = longPreferencesKey("pending_card")
    val SNOOZED_UNTIL = longPreferencesKey("snoozed_until")
    val LAST_SHOWN_AT = longPreferencesKey("last_shown_at")
    val LAST_SHOWN_CARD = longPreferencesKey("last_shown_card")
    val RECENT_SHOWN = stringPreferencesKey("recent_shown")
    val NEXT_ALARM_AT = longPreferencesKey("next_alarm_at")
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepo(private val context: Context) {

    val flow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun get(): AppSettings = flow.first()

    suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            enabled = this[Keys.ENABLED] ?: d.enabled,
            freqMode = this[Keys.FREQ_MODE]?.let { runCatching { FreqMode.valueOf(it) }.getOrNull() } ?: d.freqMode,
            freqCount = this[Keys.FREQ_COUNT] ?: d.freqCount,
            activeStartMin = this[Keys.ACTIVE_START] ?: d.activeStartMin,
            activeEndMin = this[Keys.ACTIVE_END] ?: d.activeEndMin,
            quietEnabled = this[Keys.QUIET_ENABLED] ?: d.quietEnabled,
            quietStartMin = this[Keys.QUIET_START] ?: d.quietStartMin,
            quietEndMin = this[Keys.QUIET_END] ?: d.quietEndMin,
            maxPerHour = this[Keys.MAX_PER_HOUR] ?: d.maxPerHour,
            overlayEnabled = this[Keys.OVERLAY] ?: d.overlayEnabled,
            notificationsEnabled = this[Keys.NOTIFICATIONS] ?: d.notificationsEnabled,
            snoozeMinutes = this[Keys.SNOOZE_MIN] ?: d.snoozeMinutes,
            deckFilterId = this[Keys.DECK_FILTER] ?: d.deckFilterId,
            pausedUntil = this[Keys.PAUSED_UNTIL] ?: d.pausedUntil,
            pendingCardId = this[Keys.PENDING_CARD] ?: d.pendingCardId,
            snoozedUntil = this[Keys.SNOOZED_UNTIL] ?: d.snoozedUntil,
            lastShownAt = this[Keys.LAST_SHOWN_AT] ?: d.lastShownAt,
            lastShownCardId = this[Keys.LAST_SHOWN_CARD] ?: d.lastShownCardId,
            recentShown = (this[Keys.RECENT_SHOWN] ?: "").split(",").mapNotNull { it.toLongOrNull() },
            nextAlarmAt = this[Keys.NEXT_ALARM_AT] ?: d.nextAlarmAt
        )
    }
}
