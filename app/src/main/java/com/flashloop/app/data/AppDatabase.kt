package com.flashloop.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Add a Migration here whenever you bump `version` below, e.g.
 *   val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { ... } }
 * and put it in this array. We never use destructive migration, so user cards are never wiped.
 */
val MIGRATIONS: Array<Migration> = arrayOf()

@Database(
    entities = [Deck::class, Flashcard::class, ReviewLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flashcardDao(): FlashcardDao
    abstract fun deckDao(): DeckDao
    abstract fun reviewLogDao(): ReviewLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flashloop.db"
                ).addMigrations(*MIGRATIONS).build().also { instance = it }
            }
    }
}
