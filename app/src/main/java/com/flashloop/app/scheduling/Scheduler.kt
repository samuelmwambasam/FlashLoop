package com.flashloop.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.flashloop.app.FlashLoopApp
import com.flashloop.app.data.AppSettings
import com.flashloop.app.data.Flashcard
import com.flashloop.app.data.FreqMode
import com.flashloop.app.data.Keys
import com.flashloop.app.data.LearningStatus
import com.flashloop.app.overlay.OverlayManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

/**
 * Decides WHEN the next interruption should happen and WHAT to show.
 *
 * Uses AlarmManager.setAndAllowWhileIdle: needs no special permission, survives Doze, but Android
 * may delay it by minutes. Times are therefore "about then", never exact.
 */
object Scheduler {
    private const val REQUEST_CODE = 4242
    private const val MINUTE = 60_000L
    const val ACTION_FIRE = "com.flashloop.app.ACTION_FIRE"
    const val ACTION_SNOOZE = "com.flashloop.app.ACTION_SNOOZE"
    const val EXTRA_MINUTES = "minutes"

    private fun app(context: Context) = context.applicationContext as FlashLoopApp

    // ---------- time rules ----------

    fun windowMinutes(s: AppSettings): Int {
        val d = s.activeEndMin - s.activeStartMin
        return when {
            d > 0 -> d
            d < 0 -> d + 1440
            else -> 1440
        }
    }

    /** Gap between interruptions. Daily spreads them across the active window. */
    fun spacingMillis(s: AppSettings): Long {
        val count = s.freqCount.coerceAtLeast(1)
        return when (s.freqMode) {
            FreqMode.DAILY -> windowMinutes(s) * MINUTE / count
            FreqMode.WEEKLY -> 7L * 1440L * MINUTE / count
            FreqMode.MONTHLY -> 30L * 1440L * MINUTE / count
        }
    }

    private fun inRange(m: Int, start: Int, end: Int): Boolean = when {
        start == end -> true
        start < end -> m in start until end
        else -> m >= start || m < end
    }

    fun isAllowed(at: Long, s: AppSettings): Boolean {
        val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        val m = t.hour * 60 + t.minute
        if (!inRange(m, s.activeStartMin, s.activeEndMin)) return false
        if (s.quietEnabled && s.quietStartMin != s.quietEndMin &&
            inRange(m, s.quietStartMin, s.quietEndMin)
        ) return false
        return true
    }

    /** Pushes a candidate time forward until it falls in active hours and outside quiet hours. */
    fun nextAllowed(candidate: Long, s: AppSettings): Long {
        var t = candidate
        var guard = 0
        while (!isAllowed(t, s) && guard < 4 * 24 * 60) {
            t += MINUTE
            guard++
        }
        return if (isAllowed(t, s)) t else candidate + 6 * 60 * MINUTE
    }

    // ---------- alarm plumbing ----------

    private fun firePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private suspend fun setAlarm(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        // Same PendingIntent every time -> the new alarm REPLACES the old one (no duplicates).
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, firePendingIntent(context))
        app(context).settings.edit { it[Keys.NEXT_ALARM_AT] = at }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context))
    }

    private suspend fun clearAlarm(context: Context) {
        cancel(context)
        app(context).settings.edit { it[Keys.NEXT_ALARM_AT] = 0L }
    }

    // ---------- main entry points ----------

    /** Recompute and set the single next alarm. Safe to call any time, as often as you like. */
    suspend fun reschedule(context: Context) {
        val a = app(context)
        val now = System.currentTimeMillis()
        val s = a.settings.get()
        val dao = a.db.flashcardDao()

        if (!s.enabled || s.pausedUntil == Long.MAX_VALUE) {
            clearAlarm(context)
            return
        }

        var hasPending = false
        if (s.pendingCardId != 0L) {
            if (dao.getById(s.pendingCardId) != null) {
                hasPending = true
            } else {
                a.settings.edit {
                    it[Keys.PENDING_CARD] = 0L
                    it[Keys.SNOOZED_UNTIL] = 0L
                }
            }
        }

        val spacing = spacingMillis(s)
        var candidate: Long
        if (hasPending) {
            candidate = if (s.snoozedUntil > now) s.snoozedUntil else max(now, s.lastShownAt + spacing)
        } else {
            val earliest = dao.earliestDue(s.deckFilterId)
            if (earliest == null) { // no cards at all -> nothing to schedule
                clearAlarm(context)
                return
            }
            candidate = max(now, max(s.lastShownAt + spacing, earliest))
        }
        if (s.pausedUntil > now) candidate = max(candidate, s.pausedUntil)
        candidate = max(candidate, now + 5_000L)

        setAlarm(context, nextAllowed(candidate, s))
    }

    /** Called when the alarm fires. */
    suspend fun onAlarm(context: Context) {
        val a = app(context)
        val now = System.currentTimeMillis()
        val s = a.settings.get()
        val dao = a.db.flashcardDao()

        val blocked = !s.enabled || s.pausedUntil > now || !isAllowed(now, s) ||
            OverlayManager.isShowing()
        if (blocked) {
            reschedule(context)
            return
        }

        // Hourly cap
        val recent = s.recentShown.filter { it > now - 60 * MINUTE }
        if (s.maxPerHour > 0 && recent.size >= s.maxPerHour) {
            val at = (recent.minOrNull() ?: now) + 60 * MINUTE + 1_000L
            setAlarm(context, nextAllowed(max(at, now + 5_000L), s))
            return
        }

        // Pending card first (it was never answered), otherwise pick a due card.
        val pendingCard: Flashcard? = if (s.pendingCardId != 0L) dao.getById(s.pendingCardId) else null
        val card: Flashcard = pendingCard
            ?: pickCard(dao.getDue(now, s.deckFilterId), s, now)
            ?: run { // nothing useful to review -> do NOT interrupt
                reschedule(context)
                return
            }

        a.settings.edit {
            it[Keys.PENDING_CARD] = card.id
            it[Keys.SNOOZED_UNTIL] = 0L
            it[Keys.LAST_SHOWN_AT] = now
            it[Keys.LAST_SHOWN_CARD] = card.id
            it[Keys.RECENT_SHOWN] = (recent + now).joinToString(",")
        }

        present(context, card, s)
        reschedule(context)
    }

    /** Snooze keeps the card pending. It never records a result. */
    suspend fun snooze(context: Context, minutes: Int) {
        val a = app(context)
        a.settings.edit { it[Keys.SNOOZED_UNTIL] = System.currentTimeMillis() + minutes * MINUTE }
        Notifier.cancel(context)
        withContext(Dispatchers.Main) { OverlayManager.hide() }
        reschedule(context)
    }

    // ---------- helpers ----------

    private fun pickCard(due: List<Flashcard>, s: AppSettings, now: Long): Flashcard? {
        if (due.isEmpty()) return null
        val candidates = due.filter { it.id != s.lastShownCardId }.ifEmpty { due }
        return candidates.maxByOrNull { c ->
            val overdueHours = ((now - c.nextReviewAt) / (60 * MINUTE)).coerceIn(0L, 72L).toDouble()
            var score = overdueHours + c.failedReviews * 2.0
            if (c.learningStatus == LearningStatus.NEW) score += 1.0
            if (c.lastReviewedAt != null && c.consecutiveSuccesses == 0) score += 3.0
            score
        }
    }

    private suspend fun present(context: Context, card: Flashcard, s: AppSettings) {
        var shown = false
        if (s.overlayEnabled && Settings.canDrawOverlays(context)) {
            shown = withContext(Dispatchers.Main) {
                runCatching { OverlayManager.show(context, card) }.getOrDefault(false)
            }
        }
        if (!shown && s.notificationsEnabled) {
            Notifier.show(context, card, s.snoozeMinutes)
        }
    }
}
