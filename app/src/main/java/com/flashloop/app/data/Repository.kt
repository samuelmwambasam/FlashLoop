package com.flashloop.app.data

import android.content.Context
import androidx.room.withTransaction
import com.flashloop.app.overlay.OverlayManager
import com.flashloop.app.scheduling.Notifier
import com.flashloop.app.scheduling.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepo
) {
    private val cardDao = db.flashcardDao()
    private val deckDao = db.deckDao()
    private val logDao = db.reviewLogDao()

    val cards: Flow<List<Flashcard>> = cardDao.observeAll()
    val decks: Flow<List<Deck>> = deckDao.observeAll()

    suspend fun addCard(question: String, answer: String, deckId: Long?): Long {
        val now = System.currentTimeMillis()
        val id = cardDao.insert(
            Flashcard(
                question = question, answer = answer, deckId = deckId,
                createdAt = now, updatedAt = now, nextReviewAt = Srs.initialDueAt(now)
            )
        )
        Scheduler.reschedule(context)
        return id
    }

    suspend fun updateCard(id: Long, question: String, answer: String, deckId: Long?) {
        val old = cardDao.getById(id) ?: return
        cardDao.update(
            old.copy(question = question, answer = answer, deckId = deckId, updatedAt = System.currentTimeMillis())
        )
        Scheduler.reschedule(context)
    }

    suspend fun deleteCard(id: Long) {
        cardDao.deleteById(id)
        // If the deleted card was pending, clear it so it can never be shown.
        val s = settings.get()
        if (s.pendingCardId == id) {
            settings.edit {
                it[Keys.PENDING_CARD] = 0L
                it[Keys.SNOOZED_UNTIL] = 0L
            }
            Notifier.cancel(context)
            withContext(Dispatchers.Main) { OverlayManager.hide() }
        }
        Scheduler.reschedule(context)
    }

    suspend fun getOrCreateDeck(name: String): Long {
        val existing = deckDao.findByName(name)
        return existing?.id ?: deckDao.insert(Deck(name = name))
    }

    suspend fun deleteDeck(id: Long) {
        deckDao.deleteById(id) // cards in it keep existing: deckId becomes null
        if (settings.get().deckFilterId == id) settings.edit { it[Keys.DECK_FILTER] = 0L }
        Scheduler.reschedule(context)
    }

    /**
     * The ONLY place a learning result is recorded. Both manual study and the overlay/notification
     * flow call this, so they share one learning history.
     */
    suspend fun review(cardId: Long, result: ReviewResult, mode: ReviewMode): Flashcard? {
        val card = cardDao.getById(cardId) ?: return null
        val now = System.currentTimeMillis()
        val updated = Srs.apply(card, result, now)
        db.withTransaction {
            cardDao.update(updated)
            logDao.insert(
                ReviewLog(
                    flashcardId = cardId, reviewedAt = now, result = result, reviewMode = mode,
                    previousInterval = card.intervalMinutes, newInterval = updated.intervalMinutes
                )
            )
        }
        if (settings.get().pendingCardId == cardId) {
            settings.edit {
                it[Keys.PENDING_CARD] = 0L
                it[Keys.SNOOZED_UNTIL] = 0L
            }
            Notifier.cancel(context)
            withContext(Dispatchers.Main) { OverlayManager.hide() }
        }
        Scheduler.reschedule(context)
        return updated
    }
}
