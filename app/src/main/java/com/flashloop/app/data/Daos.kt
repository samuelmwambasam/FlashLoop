package com.flashloop.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Insert
    suspend fun insert(card: Flashcard): Long

    @Update
    suspend fun update(card: Flashcard)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getById(id: Long): Flashcard?

    @Query("SELECT * FROM flashcards WHERE isActive = 1 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Flashcard>>

    @Query("SELECT MIN(nextReviewAt) FROM flashcards WHERE isActive = 1 AND (:deckId = 0 OR deckId = :deckId)")
    suspend fun earliestDue(deckId: Long): Long?

    @Query("SELECT * FROM flashcards WHERE isActive = 1 AND nextReviewAt <= :now AND (:deckId = 0 OR deckId = :deckId)")
    suspend fun getDue(now: Long, deckId: Long): List<Flashcard>
}

@Dao
interface DeckDao {
    @Insert
    suspend fun insert(deck: Deck): Long

    @Query("SELECT * FROM decks ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Deck>>

    @Query("SELECT * FROM decks WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Deck?

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLog)
}
