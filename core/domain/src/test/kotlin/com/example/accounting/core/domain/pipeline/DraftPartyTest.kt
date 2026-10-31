package com.example.accounting.core.domain.pipeline

import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.DraftPartyResolver
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.PartyAccountResolver
import com.example.accounting.core.domain.PartyKind
import com.example.accounting.core.domain.PostingEngine
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.UnsavedParty
import com.example.accounting.core.domain.intent
import com.example.accounting.core.domain.partyKey
import com.example.accounting.core.domain.testFiscalYear
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Draft-time party bug (T13 item 3): building a draft must not create a معین. The party is created only when the
 * draft is posted again with the real resolver at confirm time.
 */
class DraftPartyTest {
    /** Stands in for the party table. [creates] counts real creations (writes). */
    private class Store : PartyAccountResolver {
        val rows = linkedMapOf<Pair<PartyKind, String>, String>()
        var creates = 0

        override fun resolve(kind: PartyKind, key: String, displayName: String): String =
            rows.getOrPut(kind to key) {
                creates++
                (kind.codes.first + rows.keys.count { it.first == kind }).toString()
            }

        override fun find(kind: PartyKind, key: String): String? = rows[kind to key]
    }

    private val supplier = "ابرام"
    private val supplierKey = partyKey(supplier)
    private val chart = ChartOfAccounts.standard()
    private val input = "5000000 ریال از ابرام نسیه خریدم"

    private fun process(draftParties: DraftPartyResolver, parsed: ParsedIntent): PipelineResult = runBlocking {
        NlPipeline(
            FakeNlParser(ParserResult.Intent(parsed)),
            PostingEngine(chart, draftParties, testFiscalYear),
            testFiscalYear,
        ).process(input)
    }

    private fun creditPurchase() = ParsedIntent(
        intent = intent(TemplateId.PURCHASE_CREDIT, 5_000_000L, party = supplier),
        amountText = "5000000 ریال",
        amountValue = null,
        amountUnit = null,
    )

    /** The party line of a credit purchase (the only Cr line that is not the cash account). */
    private fun partyCode(entry: JournalEntry): String = entry.lines.single { it.side == Side.CREDIT }.accountCode

    @Test
    fun unknownPartyDraftWritesNothingAndUsesPlaceholder() {
        val store = Store()
        val draftParties = DraftPartyResolver(store::find)
        val draft = assertIs<PipelineResult.Draft>(process(draftParties, creditPurchase()))

        assertEquals(0, store.creates, "a draft must not create a party")
        assertTrue(store.rows.isEmpty(), "a draft must not write a party row")
        assertEquals(DraftPartyResolver.placeholderCode(PartyKind.SUPPLIER), partyCode(draft.entry))
        assertEquals(listOf(UnsavedParty(PartyKind.SUPPLIER, supplierKey, supplier)), draftParties.unsaved)
    }

    @Test
    fun discardedDraftLeavesNoPartyRow() {
        val store = Store()
        process(DraftPartyResolver(store::find), creditPurchase())
        // The draft is dropped (no confirm). Nothing was written.
        assertEquals(null, store.find(PartyKind.SUPPLIER, supplierKey))
        assertEquals(0, store.creates)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun confirmCreatesThePartyOnceAndAFurtherPostReusesIt() {
        val store = Store()
        val draft = assertIs<PipelineResult.Draft>(process(DraftPartyResolver(store::find), creditPurchase()))

        // Confirm: the real resolver posts the same intent once.
        val committed = PostingEngine(chart, store, testFiscalYear).post(draft.intent)
        assertEquals(1, store.creates)
        assertEquals(store.find(PartyKind.SUPPLIER, supplierKey), partyCode(committed))

        // A later purchase from the same supplier posts again and reuses the row.
        val again = PostingEngine(chart, store, testFiscalYear).post(draft.intent)
        assertEquals(1, store.creates, "the same party must be created once")
        assertEquals(1, store.rows.size)
        assertEquals(partyCode(committed), partyCode(again))
    }

    @Test
    fun knownPartyDraftUsesRealCodeAndRecordsNothing() {
        val store = Store()
        val realCode = store.resolve(PartyKind.SUPPLIER, supplierKey, supplier)
        val draftParties = DraftPartyResolver(store::find)
        val draft = assertIs<PipelineResult.Draft>(process(draftParties, creditPurchase()))

        assertEquals(realCode, partyCode(draft.entry))
        assertTrue(draftParties.unsaved.isEmpty())
        assertEquals(1, store.rows.size)
        assertEquals(1, store.creates, "only the setup creation happened")
    }

    @Test
    fun amountChangeOnDraftKeepsPlaceholderAndWritesNothing() {
        val store = Store()
        val draftParties = DraftPartyResolver(store::find)
        val draft = assertIs<PipelineResult.Draft>(process(draftParties, creditPurchase()))

        val changed = draft.withAmount(7_000_000L)
        assertEquals(0, store.creates)
        assertEquals(DraftPartyResolver.placeholderCode(PartyKind.SUPPLIER), partyCode(changed.entry))
        assertEquals(listOf(UnsavedParty(PartyKind.SUPPLIER, supplierKey, supplier)), draftParties.unsaved)
    }

    @Test
    fun cashDraftHasNoPartyAndWritesNothing() {
        val store = Store()
        val draftParties = DraftPartyResolver(store::find)
        process(
            draftParties,
            ParsedIntent(
                intent = intent(TemplateId.PURCHASE_CASH, 5_000_000L, paidWith = PaidWith.CASH),
                amountText = "5000000 ریال",
                amountValue = null,
                amountUnit = null,
            ),
        )
        assertTrue(draftParties.unsaved.isEmpty())
        assertEquals(0, store.creates)
    }
}
