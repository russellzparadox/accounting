package com.example.accounting.core.domain.pipeline

import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.DraftPartyResolver
import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.PostingEngine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.intent
import com.example.accounting.core.domain.testFiscalYear
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.NumberWordParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Spoken amounts (T13 item 2) through the same path a voice transcript takes: the transcript fills the field,
 * then NlPipeline normalizes it, cross-checks the backend amount with the local parser and posts a draft.
 * The backend answer is scripted; the local parser runs for real.
 */
class SpokenAmountPipelineTest {
    private class Case(
        val spoken: String,
        /** The part of [spoken] the backend says the amount came from (must be a substring after normalizing). */
        val amountText: String,
        /** Backend amount_rial the test scripts. Must equal what a correct reading of [spoken] gives. */
        val rial: Long,
    )

    private val cases = listOf(
        // «نیم میلیون»: half a million toman.
        Case("۴ قوطی نوشابه روی هم نیم میلیون تومان خریدم", "نیم میلیون تومان", 5_000_000L),
        // Colloquial «تومن» as the unit.
        Case("نیم میلیون تومن نقد خریدم", "نیم میلیون تومن", 5_000_000L),
        // «دو و نیم میلیون»: two and a half million toman.
        Case("دو و نیم میلیون تومان خرید نقد", "دو و نیم میلیون تومان", 25_000_000L),
        // Mixed digits and words.
        Case("۲ و نیم میلیون تومان نقد خریدم", "2 و نیم میلیون تومان", 25_000_000L),
        // «سیصد و پنجاه هزار»: 350,000 toman.
        Case("سیصد و پنجاه هزار تومان نقد خریدم", "سیصد و پنجاه هزار تومان", 3_500_000L),
        // «یک میلیون و نیم»: one and a half million, with the unit given explicitly.
        Case("یک میلیون و نیم تومان نقد خریدم", "یک میلیون و نیم تومان", 15_000_000L),
        // Explicit rial unit: 2,000,000 rial (not toman), so the rial value is the amount itself.
        Case("دو میلیون ریال نقد خریدم", "دو میلیون ریال", 2_000_000L),
    )

    private fun parsedFor(case: Case) = ParsedIntent(
        intent = intent(TemplateId.PURCHASE_CASH, case.rial, paidWith = PaidWith.CASH),
        amountText = case.amountText,
        amountValue = null,
        amountUnit = null,
    )

    private fun process(text: String, parsed: ParsedIntent): PipelineResult = runBlocking {
        NlPipeline(
            FakeNlParser(ParserResult.Intent(parsed)),
            PostingEngine(ChartOfAccounts.standard(), DraftPartyResolver { _, _ -> null }, testFiscalYear),
            testFiscalYear,
        ).process(text)
    }

    @Test
    fun spokenAmountsAgreeWithTheBackendAndPostBalancedDrafts() {
        cases.forEach { case ->
            val result = process(case.spoken, parsedFor(case))
            val draft = assertIs<PipelineResult.Draft>(result, case.spoken)
            assertEquals(emptyList(), draft.reasons, "no confirm reason for: ${case.spoken}")
            assertEquals(Money(case.rial), draft.entry.debitTotal, case.spoken)
            assertEquals(draft.entry.debitTotal, draft.entry.creditTotal, case.spoken)
            assertEquals(
                Side.DEBIT,
                draft.entry.lines.first { it.accountCode == "5100" }.side,
                case.spoken,
            )
        }
    }

    @Test
    fun localParserReadsEachSpokenAmountAsTheExpectedRial() {
        cases.forEach { case ->
            val normalized = InputNormalizer.normalize(case.spoken)
            val local = NumberWordParser.parseAmount(normalized)
            assertEquals(case.rial, local?.rial, "local reading of: ${case.spoken}")
        }
    }

    @Test
    fun colloquialWordTheParserDoesNotKnowIsFlaggedNotPostedSilently() {
        // «پونصد» (colloquial «پانصد») is not in the value table. The local reading is only «هزار تومن» (1,000 toman),
        // so the backend amount (500,000 toman) must be flagged for confirmation. It must not post silently.
        val spoken = "پونصد هزار تومن نقد خریدم"
        val parsed = ParsedIntent(
            intent = intent(TemplateId.PURCHASE_CASH, 5_000_000L, paidWith = PaidWith.CASH),
            amountText = "پونصد هزار تومن",
            amountValue = null,
            amountUnit = AmountUnit.TOMAN,
        )
        val draft = assertIs<PipelineResult.Draft>(process(spoken, parsed))
        assertTrue(draft.needsConfirm)
        val mismatch = draft.reasons.filterIsInstance<ConfirmReason.AmountMismatch>().single()
        assertEquals(5_000_000L, mismatch.llmRial)
        assertEquals(10_000L, mismatch.localRial)
    }
}
