package com.flashloop.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class LearningStatus { NEW, LEARNING, REVIEWING, WELL_REMEMBERED }
enum class ReviewResult { REMEMBERED, FORGOTTEN }
enum class ReviewMode { MANUAL, AUTOMATIC }

@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("deckId"), Index("nextReviewAt")]
)
data class Flashcard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val answer: String,
    val deckId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long,
    val intervalMinutes: Long = 0,
    val successfulReviews: Int = 0,
    val failedReviews: Int = 0,
    val consecutiveSuccesses: Int = 0,
    val learningStatus: LearningStatus = LearningStatus.NEW,
    val isActive: Boolean = true
)

@Entity(
    tableName = "review_history",
    foreignKeys = [
        ForeignKey(
            entity = Flashcard::class,
            parentColumns = ["id"],
            childColumns = ["flashcardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("flashcardId")]
)
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flashcardId: Long,
    val reviewedAt: Long,
    val result: ReviewResult,
    val reviewMode: ReviewMode,
    val previousInterval: Long,
    val newInterval: Long
)
