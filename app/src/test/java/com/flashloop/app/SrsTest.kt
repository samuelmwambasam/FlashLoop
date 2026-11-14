package com.flashloop.app

import com.flashloop.app.data.Flashcard
import com.flashloop.app.data.LearningStatus
import com.flashloop.app.data.ReviewResult
import com.flashloop.app.data.Srs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsTest {
    private val day = 24 * 60L
    private fun newCard(now: Long) = Flashcard(
        question = "Q", answer = "A", createdAt = now, updatedAt = now, nextReviewAt = Srs.initialDueAt(now)
    )

    @Test
    fun successfulRecallsFollowTheSchedule() {
        var card = newCard(0)
        val expectedDays = listOf(1L, 3L, 7L, 14L, 30L, 60L, 120L)
        expectedDays.forEach { d ->
            card = Srs.apply(card, ReviewResult.REMEMBERED, 0)
            assertEquals(d * day, card.intervalMinutes)
        }
        assertEquals(LearningStatus.WELL_REMEMBERED, card.learningStatus)
    }

    @Test
    fun intervalIsCappedAtOneYear() {
        var card = newCard(0)
        repeat(20) { card = Srs.apply(card, ReviewResult.REMEMBERED, 0) }
        assertEquals(365 * day, card.intervalMinutes)
    }

    @Test
    fun forgettingResetsTheCard() {
        var card = newCard(0)
        repeat(4) { card = Srs.apply(card, ReviewResult.REMEMBERED, 0) }
        card = Srs.apply(card, ReviewResult.FORGOTTEN, 1_000)
        assertEquals(0, card.consecutiveSuccesses)
        assertEquals(1, card.failedReviews)
        assertEquals(10L, card.intervalMinutes)
        assertEquals(LearningStatus.LEARNING, card.learningStatus)
        assertTrue(card.nextReviewAt < 60 * 60_000L)
    }

    @Test
    fun newCardIsDueAfterTenMinutes() {
        assertEquals(10 * 60_000L, newCard(0).nextReviewAt)
    }
}
