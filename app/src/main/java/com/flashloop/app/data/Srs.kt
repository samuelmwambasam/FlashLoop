package com.flashloop.app.data

import kotlin.math.min

/** All tunable numbers for the learning algorithm live here. */
object SrsConfig {
    const val MINUTE = 60_000L
    const val NEW_CARD_DELAY_MIN = 10L          // first review shortly after creation
    const val FAILED_DELAY_MIN = 10L            // forgotten card comes back soon
    val SUCCESS_STEPS_DAYS = listOf(1, 3, 7, 14, 30)  // 1st..5th consecutive success
    const val GROWTH_FACTOR = 2.0               // after the steps run out, interval doubles
    const val MAX_INTERVAL_DAYS = 365
}

/** Simple Leitner-style scheduler. Pure functions, easy to unit test. */
object Srs {
    fun initialDueAt(createdAt: Long): Long = createdAt + SrsConfig.NEW_CARD_DELAY_MIN * SrsConfig.MINUTE

    fun successIntervalMinutes(consecutive: Int): Long {
        val steps = SrsConfig.SUCCESS_STEPS_DAYS
        val days: Double = if (consecutive <= steps.size) {
            steps[consecutive - 1].toDouble()
        } else {
            var d = steps.last().toDouble()
            repeat(consecutive - steps.size) { d *= SrsConfig.GROWTH_FACTOR }
            d
        }
        return (min(days, SrsConfig.MAX_INTERVAL_DAYS.toDouble()) * 24 * 60).toLong()
    }

    fun statusFor(consecutive: Int, everReviewed: Boolean): LearningStatus = when {
        !everReviewed -> LearningStatus.NEW
        consecutive >= 5 -> LearningStatus.WELL_REMEMBERED
        consecutive >= 3 -> LearningStatus.REVIEWING
        else -> LearningStatus.LEARNING
    }

    /** Returns the updated card after an explicit Remembered / Didn't remember answer. */
    fun apply(card: Flashcard, result: ReviewResult, now: Long): Flashcard = when (result) {
        ReviewResult.REMEMBERED -> {
            val consecutive = card.consecutiveSuccesses + 1
            val interval = successIntervalMinutes(consecutive)
            card.copy(
                lastReviewedAt = now,
                nextReviewAt = now + interval * SrsConfig.MINUTE,
                intervalMinutes = interval,
                successfulReviews = card.successfulReviews + 1,
                consecutiveSuccesses = consecutive,
                learningStatus = statusFor(consecutive, true)
            )
        }
        ReviewResult.FORGOTTEN -> {
            val interval = SrsConfig.FAILED_DELAY_MIN
            card.copy(
                lastReviewedAt = now,
                nextReviewAt = now + interval * SrsConfig.MINUTE,
                intervalMinutes = interval,
                failedReviews = card.failedReviews + 1,
                consecutiveSuccesses = 0,
                learningStatus = statusFor(0, true)
            )
        }
    }
}
