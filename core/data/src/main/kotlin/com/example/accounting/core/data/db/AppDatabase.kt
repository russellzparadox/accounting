package com.example.accounting.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        PartyEntity::class,
        FiscalYearEntity::class,
        DocSequenceEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun partyDao(): PartyDao
    abstract fun fiscalYearDao(): FiscalYearDao
    abstract fun docSequenceDao(): DocSequenceDao
    abstract fun journalDao(): JournalDao

    companion object {
        /** Must equal the `version` in the @Database annotation above. Checked by a test and by backup restore. */
        const val SCHEMA_VERSION = 1

        /** File name under the app's databases folder. */
        const val FILE_NAME = "accounting.db"

        fun file(context: Context, name: String = FILE_NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addCallback(IntegrityCallback)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addCallback(IntegrityCallback)
                .build()
    }
}

/** Triggers that make committed journal rows immutable. Room cannot express triggers in annotations. */
internal val IMMUTABILITY_TRIGGERS = listOf(
    "CREATE TRIGGER IF NOT EXISTS journal_entry_no_update BEFORE UPDATE ON journal_entry " +
        "BEGIN SELECT RAISE(ABORT, 'committed journal entry is immutable'); END",
    "CREATE TRIGGER IF NOT EXISTS journal_entry_no_delete BEFORE DELETE ON journal_entry " +
        "BEGIN SELECT RAISE(ABORT, 'committed journal entry is immutable'); END",
    "CREATE TRIGGER IF NOT EXISTS journal_line_no_update BEFORE UPDATE ON journal_line " +
        "BEGIN SELECT RAISE(ABORT, 'committed journal line is immutable'); END",
    "CREATE TRIGGER IF NOT EXISTS journal_line_no_delete BEFORE DELETE ON journal_line " +
        "BEGIN SELECT RAISE(ABORT, 'committed journal line is immutable'); END",
)

/**
 * Runs on every open (and so after create). Triggers use IF NOT EXISTS, so repeated opens are safe.
 * Foreign keys are switched on because SQLite leaves them off by default.
 */
internal object IntegrityCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON")
        IMMUTABILITY_TRIGGERS.forEach { db.execSQL(it) }
    }
}
