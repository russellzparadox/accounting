package com.example.accounting.core.data.seed

import androidx.room.withTransaction
import com.example.accounting.core.data.db.AccountEntity
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.domain.ChartOfAccounts

/** Loads the standard chart (`chart_of_accounts.json`, from core:domain resources) into the account table. */
class ChartSeeder(
    private val db: AppDatabase,
    private val chart: ChartOfAccounts = ChartOfAccounts.standard(),
) {
    /** Inserts accounts whose code is not yet present. Returns the number inserted (0 on repeat runs). */
    suspend fun seed(): Int = db.withTransaction {
        val before = db.accountDao().count()
        val rows = chart.codes.sorted().map { code ->
            val account = checkNotNull(chart.account(code))
            AccountEntity(
                code = account.code,
                nameFa = account.nameFa,
                level = account.level.name,
                parent = account.parent,
                type = account.type.name,
                normalBalance = account.normalBalance.name,
            )
        }
        db.accountDao().insertIgnore(rows)
        db.accountDao().count() - before
    }
}
