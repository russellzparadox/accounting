package com.example.accounting.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Enum columns store the Kotlin enum name (e.g. DEBIT, GENERAL). Dates store ISO yyyy-MM-dd TEXT. Money stores rials INTEGER.

@Entity(tableName = "account", indices = [Index("parent")])
data class AccountEntity(
    @PrimaryKey val code: String,
    @ColumnInfo(name = "name_fa") val nameFa: String,
    val level: String,
    val parent: String?,
    val type: String,
    @ColumnInfo(name = "normal_balance") val normalBalance: String,
)

@Entity(
    tableName = "party",
    primaryKeys = ["kind", "party_key"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["code"],
            childColumns = ["account_code"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["account_code"], unique = true)],
)
data class PartyEntity(
    val kind: String,
    @ColumnInfo(name = "party_key") val partyKey: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "account_code") val accountCode: String,
)

@Entity(tableName = "fiscal_year")
data class FiscalYearEntity(
    @PrimaryKey @ColumnInfo(name = "jalali_year") val jalaliYear: Int,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
)

@Entity(
    tableName = "doc_sequence",
    foreignKeys = [
        ForeignKey(
            entity = FiscalYearEntity::class,
            parentColumns = ["jalali_year"],
            childColumns = ["fiscal_year"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class DocSequenceEntity(
    @PrimaryKey @ColumnInfo(name = "fiscal_year") val fiscalYear: Int,
    @ColumnInfo(name = "last_number") val lastNumber: Int,
)

@Entity(
    tableName = "journal_entry",
    foreignKeys = [
        ForeignKey(
            entity = FiscalYearEntity::class,
            parentColumns = ["jalali_year"],
            childColumns = ["fiscal_year"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["fiscal_year", "doc_number"], unique = true),
        Index(value = ["reversal_of_id"], unique = true),
        Index("entry_date"),
    ],
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entry_date") val entryDate: String,
    @ColumnInfo(name = "fiscal_year") val fiscalYear: Int,
    @ColumnInfo(name = "doc_number") val docNumber: Int,
    @ColumnInfo(name = "template_id") val templateId: String,
    val memo: String?,
    @ColumnInfo(name = "reversal_of_id") val reversalOfId: String?,
)

@Entity(
    tableName = "journal_line",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["code"],
            childColumns = ["account_code"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("entry_id"), Index("account_code")],
)
data class JournalLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "account_code") val accountCode: String,
    val side: String,
    val amount: Long,
)
