package com.example.accounting.core.data

import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.repo.PartyRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.DomainException
import com.example.accounting.core.domain.ExpenseCategory
import com.example.accounting.core.domain.FiscalYear
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.PostingEngine
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.TransactionIntent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.random.Random

/**
 * Invariant fuzz through the real stack: PostingEngine, PartyRepository (in-memory Room, real party rows) and
 * LedgerRepository.commit / reverse. 1000 random valid intents over all 13 posting templates, plus about 10%
 * random reversals. After every batch of 100 intents the ledger must satisfy every invariant below.
 * Failure messages carry the seed and the operation count, so a failing run can be replayed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerFuzzTest {
    private lateinit var db: AppDatabase
    private lateinit var parties: PartyRepository
    private lateinit var ledger: LedgerRepository
    private lateinit var fiscal: FiscalYear
    private val chart = ChartOfAccounts.standard()
    private val year = 1405

    @Before
    fun setUp() = runBlocking {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        ChartSeeder(db).seed()
        parties = PartyRepository(db)
        ledger = LedgerRepository(db, chart)
        fiscal = ledger.ensureFiscalYear(year)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val intentTemplates = listOf(
        TemplateId.PURCHASE_CASH, TemplateId.PURCHASE_CREDIT,
        TemplateId.SALE_CASH, TemplateId.SALE_CREDIT,
        TemplateId.EXPENSE_PAID, TemplateId.EXPENSE_PAYABLE,
        TemplateId.RECEIPT_FROM_PERSON, TemplateId.PAYMENT_TO_PERSON,
        TemplateId.BANK_TRANSFER,
        TemplateId.OWNER_CAPITAL, TemplateId.OWNER_WITHDRAWAL,
        TemplateId.PURCHASE_RETURN, TemplateId.SALE_RETURN,
    )
    private val creditTemplates = setOf(TemplateId.PURCHASE_CREDIT, TemplateId.SALE_CREDIT, TemplateId.EXPENSE_PAYABLE)
    private val alwaysParty = creditTemplates + setOf(TemplateId.RECEIPT_FROM_PERSON, TemplateId.PAYMENT_TO_PERSON)
    private val returns = setOf(TemplateId.PURCHASE_RETURN, TemplateId.SALE_RETURN)
    private val categoryTemplates = setOf(TemplateId.EXPENSE_PAID, TemplateId.EXPENSE_PAYABLE)
    private val partyNames = (1..20).map { "طرف $it" } + listOf("علی رضایی", "علي رضایی", "شرکت  نور", "میوه‌فروشی")
    private val banks = chart.childrenOf("1200").map { it.code }

    /** A random intent the posting rules accept (template field rules of PostingEngine.expect). */
    private fun randomIntent(rnd: Random): TransactionIntent {
        val type = intentTemplates.random(rnd)
        val paid = if (rnd.nextBoolean()) PaidWith.CASH else PaidWith.BANK
        val paidWith = when (type) {
            in creditTemplates -> null
            in returns -> if (rnd.nextBoolean()) null else paid
            else -> paid
        }
        val bank = if (paidWith == PaidWith.BANK || type == TemplateId.BANK_TRANSFER) banks.random(rnd) else null
        val party = if (type in alwaysParty || (type in returns && paidWith == null)) partyNames.random(rnd) else null
        val category = if (type in categoryTemplates) ExpenseCategory.entries.random(rnd) else null
        return TransactionIntent(
            type = type,
            amountRial = rnd.nextLong(1, 10_000_000_000_000L),
            date = fiscalStart.plusDays(rnd.nextLong(0, 365)),
            paidWith = paidWith,
            bankAccount = bank,
            partyName = party,
            category = category,
            memo = null,
            confidence = rnd.nextInt(0, 101),
        )
    }

    private val fiscalStart: LocalDate get() = fiscal.start

    private fun <T> onDb(block: suspend () -> T): T = runBlocking { block() }

    @Test
    fun thousandRandomEntriesKeepEveryInvariant() {
        val seed = 20261031L
        val rnd = Random(seed)
        val engine = PostingEngine(chart, parties, fiscal) // real resolver: creates parties on first use
        val originals = mutableListOf<String>()
        val reversedIds = HashSet<String>()
        var posted = 0
        var reversals = 0

        repeat(10) { batch ->
            repeat(100) {
                val intent = randomIntent(rnd)
                val entry = engine.post(intent)
                val doc = onDb { ledger.commit(entry) }
                originals += doc.id
                posted++

                if (rnd.nextInt(100) < 10) {
                    val pick = originals.filter { it !in reversedIds }.randomOrNull(rnd)
                    if (pick != null) {
                        val date = fiscalStart.plusDays(rnd.nextLong(0, 365))
                        onDb { ledger.reverse(pick, date, "fuzz") }
                        reversedIds += pick
                        reversals++
                    }
                }
            }
            val where = "seed=$seed batch=$batch posted=$posted reversals=$reversals"
            assertInvariants(where)
        }

        assertEquals("seed=$seed", 1000, posted)
        assertTrue("seed=$seed reversals=$reversals (expected about 100)", reversals in 60..160)

        // A document can be reversed once: a second reversal of the same original is rejected.
        val twice = reversedIds.first()
        assertThrows("seed=$seed", DomainException::class.java) {
            onDb { ledger.reverse(twice, fiscalStart) }
        }
    }

    private fun assertInvariants(where: String) {
        val trial = onDb { ledger.trialBalance(year) }
        assertEquals("trial balance debit == credit [$where]", trial.debitTotal, trial.creditTotal)
        assertEquals("trial rows sum to total [$where]", trial.rows.sumOf { it.debit }, trial.debitTotal)

        // Running balance of each account at the end equals its trial balance row (debit minus credit).
        trial.rows.forEach { row ->
            val lines = onDb { ledger.ledger(row.code).first() }
            assertEquals("ledger end balance of ${row.code} == trial [$where]", row.debit - row.credit, lines.last().balance)
            lines.zipWithNext().forEach { (prev, next) ->
                assertEquals("running balance step in ${row.code} [$where]", prev.balance + next.debit - next.credit, next.balance)
            }
        }

        val docs = onDb { ledger.journal(year).first() }
        val numbers = docs.map { it.docNumber }.sorted()
        assertEquals("doc numbers gap-free and unique [$where]", (1..docs.size).toList(), numbers)
        assertEquals("doc ids unique [$where]", docs.size, docs.map { it.id }.toSet().size)
        docs.forEach { doc ->
            assertEquals("entry balanced ${doc.id} [$where]", doc.entry.debitTotal, doc.entry.creditTotal)
            assertEquals("entry has lines [$where]", true, doc.entry.lines.isNotEmpty())
        }

        // Each reversal nets its original to zero, per account.
        val byId = docs.associateBy { it.id }
        onDb { ledger.reversalLinks().first() }.forEach { link ->
            val original = byId.getValue(link.originalId)
            val reversal = byId.getValue(link.reversalId)
            assertEquals("reversal restores net effect [$where]", emptyMap<String, Long>(), netByAccount(original, reversal))
        }

        assertImmutable(where)
    }

    /** Net (debit minus credit) per account over the given documents. Zero entries are dropped. */
    private fun netByAccount(vararg docs: CommittedDoc): Map<String, Long> {
        val net = HashMap<String, Long>()
        docs.forEach { doc ->
            doc.entry.lines.forEach { line ->
                val signed = if (line.side == Side.DEBIT) line.amount.rial else -line.amount.rial
                net[line.accountCode] = (net[line.accountCode] ?: 0L) + signed
            }
        }
        return net.filterValues { it != 0L }
    }

    /** Journal rows cannot be updated or deleted: the BEFORE triggers abort the statement. */
    private fun assertImmutable(where: String) {
        val statements = listOf(
            "UPDATE journal_entry SET memo = 'x' WHERE rowid = (SELECT MIN(rowid) FROM journal_entry)",
            "DELETE FROM journal_entry WHERE rowid = (SELECT MIN(rowid) FROM journal_entry)",
            "UPDATE journal_line SET amount = amount + 1 WHERE rowid = (SELECT MIN(rowid) FROM journal_line)",
            "DELETE FROM journal_line WHERE rowid = (SELECT MIN(rowid) FROM journal_line)",
        )
        statements.forEach { sql ->
            assertThrows("rejected: $sql [$where]", SQLiteException::class.java) {
                db.openHelper.writableDatabase.execSQL(sql)
            }
        }
    }

    @Test
    fun largeRialAmountSurvivesCommitUnchanged() {
        // Smoke for the Money contract used by the fuzz: a Long rial value round-trips unchanged.
        val engine = PostingEngine(chart, parties, fiscal)
        val entry = engine.post(
            TransactionIntent(
                type = TemplateId.PURCHASE_CASH,
                amountRial = 9_999_999_999_999L,
                date = fiscalStart,
                paidWith = PaidWith.CASH,
                confidence = 100,
            ),
        )
        val doc = onDb { ledger.commit(entry) }
        assertEquals(Money(9_999_999_999_999L), doc.entry.debitTotal)
    }
}
