package com.app.mindunload.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN snoozedUntil INTEGER")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN doneAt INTEGER")
        db.execSQL("ALTER TABLE items ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE items ADD COLUMN researchSuggested INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN recurrence TEXT")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE captures ADD COLUMN mode TEXT NOT NULL DEFAULT 'CAPTURE'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS api_usage (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "feature TEXT NOT NULL, model TEXT NOT NULL, " +
                    "inputTokens INTEGER NOT NULL, cacheWriteTokens INTEGER NOT NULL, " +
                    "cacheReadTokens INTEGER NOT NULL, outputTokens INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL)",
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE captures ADD COLUMN attachmentPath TEXT")
        db.execSQL("ALTER TABLE captures ADD COLUMN attachmentKind TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE captures ADD COLUMN attachmentExtracted INTEGER NOT NULL DEFAULT 0")
        // The recipes chat function is gone; its history entries would otherwise
        // deserialize into a mode that no longer exists.
        db.execSQL("UPDATE captures SET mode = 'ASK' WHERE mode = 'RECIPES'")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The review chat mode is gone — ask covers it. Its history entries would
        // otherwise deserialize into a mode that no longer exists.
        db.execSQL("UPDATE captures SET mode = 'ASK' WHERE mode = 'REVIEW'")
    }
}

@Database(
    entities = [
        PlannerItem::class,
        ItemLink::class,
        ResearchNote::class,
        CaptureRequest::class,
        ChatMessage::class,
        ApiUsage::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): PlannerItemDao
    abstract fun linkDao(): ItemLinkDao
    abstract fun researchDao(): ResearchNoteDao
    abstract fun captureDao(): CaptureDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun apiUsageDao(): ApiUsageDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mindunload.db",
                )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                    )
                    // Deliberately NO fallbackToDestructiveMigration: entries are only ever
                    // marked done or archived, never dropped, and a destructive fallback would
                    // silently wipe the whole backlog on the first schema change that forgets
                    // its migration. Room then fails loudly at open time instead — the missing
                    // migration is a bug to fix, not data to throw away.
                    .build().also { instance = it }
            }
    }
}
