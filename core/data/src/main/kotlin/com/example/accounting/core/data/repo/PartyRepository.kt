package com.example.accounting.core.data.repo

import androidx.room.withTransaction
import com.example.accounting.core.data.db.AccountEntity
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.db.PartyEntity
import com.example.accounting.core.domain.AccountLevel
import com.example.accounting.core.domain.DomainException
import com.example.accounting.core.domain.PartyAccountResolver
import com.example.accounting.core.domain.PartyKind
import kotlinx.coroutines.runBlocking

/**
 * Find-or-create of معین accounts for customers and suppliers (domain port [PartyAccountResolver]).
 * The port is synchronous, so [resolve] blocks the calling thread. Never call it on the main thread.
 * New codes are max(existing code in range) + 1, starting at the first code of the range.
 */
class PartyRepository(private val db: AppDatabase) : PartyAccountResolver {
    private val lock = Any()

    override fun resolve(kind: PartyKind, key: String, displayName: String): String = synchronized(lock) {
        runBlocking { db.withTransaction { resolveInTx(kind, key, displayName) } }
    }

    /** Read only. Blocks like [resolve], so the same threads apply (never the main thread). */
    override fun find(kind: PartyKind, key: String): String? = runBlocking {
        db.partyDao().find(kind.name, key)?.accountCode
    }

    private suspend fun resolveInTx(kind: PartyKind, key: String, displayName: String): String {
        db.partyDao().find(kind.name, key)?.let { return it.accountCode }

        val control = checkNotNull(db.accountDao().find(kind.controlCode)) {
            "control account ${kind.controlCode} missing; seed the chart first"
        }
        val highest = db.accountDao().maxCodeInRange(kind.codes.first.toString(), kind.codes.last.toString())
        val next = if (highest == null) kind.codes.first else highest.toInt() + 1
        if (next > kind.codes.last) {
            throw DomainException("party accounts for ${kind.name} exhausted (${kind.codes.first}..${kind.codes.last})")
        }
        val code = next.toString()

        db.accountDao().insert(
            AccountEntity(
                code = code,
                nameFa = displayName,
                level = AccountLevel.DETAIL.name,
                parent = control.code,
                type = control.type,
                normalBalance = control.normalBalance,
            ),
        )
        db.partyDao().insert(PartyEntity(kind.name, key, displayName, code))
        return code
    }
}
