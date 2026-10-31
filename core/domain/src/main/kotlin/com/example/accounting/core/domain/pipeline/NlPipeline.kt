package com.example.accounting.core.domain.pipeline

import com.example.accounting.core.domain.FiscalYear
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.PostingEngine
import com.example.accounting.core.domain.TransactionIntent
import com.example.accounting.core.persian.NumberWordParser

/** Reasons a [PipelineResult.Draft] needs the user to confirm. Never post silently on any of these. */
sealed interface ConfirmReason {
    /** Backend amount_rial differs from the locally parsed amount. Both are carried. */
    data class AmountMismatch(val llmRial: Long, val localRial: Long) : ConfirmReason

    /** Local parser returned null (no or ambiguous amount). [localCandidatesRial] lists the phrases it found. */
    data class AmountUnverified(val llmRial: Long, val localCandidatesRial: List<Long>) : ConfirmReason

    /** amount_text is absent, blank, or not a substring of the normalized input. */
    data class AmountTextUnverified(val amountText: String?) : ConfirmReason

    /** Backend confidence below the threshold (default 70). */
    data class LowConfidence(val confidence: Int) : ConfirmReason
}

sealed interface PipelineResult {
    /**
     * Balanced, unposted entry. Not committed: the caller stores it only after the user confirms.
     * [needsConfirm] is true iff [reasons] is non-empty.
     */
    class Draft internal constructor(
        val entry: JournalEntry,
        val intent: TransactionIntent,
        val needsConfirm: Boolean,
        val reasons: List<ConfirmReason>,
        private val engine: PostingEngine,
    ) : PipelineResult {
        /**
         * User picked an amount (typically the locally parsed one). Re-posts through [PostingEngine].
         * Amount reasons are dropped because the user chose the amount. [ConfirmReason.LowConfidence] stays.
         */
        fun withAmount(rial: Long): Draft {
            val newIntent = intent.copy(amountRial = rial)
            val kept = reasons.filter { it is ConfirmReason.LowConfidence }
            return Draft(engine.post(newIntent), newIntent, kept.isNotEmpty(), kept, engine)
        }
    }

    data class NeedsClarification(val question: String) : PipelineResult

    data class Failure(val error: PipelineError) : PipelineResult
}

/**
 * normalize -> [NlParser] -> cross-check against the local amount parser -> [PostingEngine] -> [PipelineResult].
 * Pure: no Room, no clock, no UI. The caller runs [process] and [PostingEngine.post] (party lookup blocks)
 * off the main thread.
 */
class NlPipeline(
    private val parser: NlParser,
    private val engine: PostingEngine,
    private val fiscalYear: FiscalYear,
    private val minConfidence: Int = DEFAULT_MIN_CONFIDENCE,
) {
    suspend fun process(rawText: String): PipelineResult {
        val text = InputNormalizer.normalize(rawText)
        if (text.isEmpty()) return PipelineResult.Failure(PipelineError.EmptyInput)

        val parsed = when (val r = parser.parse(text)) {
            is ParserResult.Error -> return PipelineResult.Failure(r.error)
            is ParserResult.Clarification -> return PipelineResult.NeedsClarification(r.question)
            is ParserResult.Intent -> r.parsed
        }
        val intent = parsed.intent
        if (intent.date !in fiscalYear) return PipelineResult.Failure(PipelineError.DateOutsideFiscalYear(intent.date))

        val local = NumberWordParser.parseAmount(text)
        val amountText = parsed.amountText?.trim()?.takeIf { it.isNotEmpty() }
        if (local == null && amountText == null) return PipelineResult.Failure(PipelineError.AmountMissing)

        val reasons = buildList {
            if (local == null) {
                add(ConfirmReason.AmountUnverified(intent.amountRial, NumberWordParser.candidates(text).map { it.rial }))
            } else if (local.rial != intent.amountRial) {
                add(ConfirmReason.AmountMismatch(intent.amountRial, local.rial))
            }
            if (amountText == null || !text.contains(amountText)) add(ConfirmReason.AmountTextUnverified(amountText))
            if (intent.confidence < minConfidence) add(ConfirmReason.LowConfidence(intent.confidence))
        }

        val entry = try {
            engine.post(intent)
        } catch (e: IllegalArgumentException) {
            // DomainException (template field rules, bank/party/category) and JournalEntry checks.
            return PipelineResult.Failure(PipelineError.BadResponse(e.message ?: "domain rule"))
        }
        return PipelineResult.Draft(entry, intent, reasons.isNotEmpty(), reasons, engine)
    }

    companion object {
        const val DEFAULT_MIN_CONFIDENCE = 70
    }
}
