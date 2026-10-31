package com.example.accounting.feature.entry

import com.example.accounting.core.domain.pipeline.PipelineError
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the state layer. No Android, no Room. */
class EntryStateTest {

    @Test
    fun everyPipelineErrorMapsToAKind() {
        assertEquals(EntryErrorKind.OFFLINE, PipelineError.Offline.toErrorKind())
        assertEquals(EntryErrorKind.UNAUTHORIZED, PipelineError.Unauthorized.toErrorKind())
        assertEquals(EntryErrorKind.RATE_LIMITED, PipelineError.RateLimited(30L).toErrorKind())
        assertEquals(EntryErrorKind.SERVER, PipelineError.ServerError(503).toErrorKind())
        assertEquals(EntryErrorKind.BAD_RESPONSE, PipelineError.BadResponse("x").toErrorKind())
        assertEquals(EntryErrorKind.EMPTY_INPUT, PipelineError.EmptyInput.toErrorKind())
        assertEquals(EntryErrorKind.AMOUNT_MISSING, PipelineError.AmountMissing.toErrorKind())
        assertEquals(
            EntryErrorKind.DATE_OUTSIDE_FISCAL_YEAR,
            PipelineError.DateOutsideFiscalYear(LocalDate.of(2030, 1, 1)).toErrorKind(),
        )
    }

    @Test
    fun networkErrorsOfferRetryAndManualEntry() {
        val actions = EntryErrorKind.OFFLINE.actions()
        assertTrue(ErrorAction.RETRY in actions)
        assertTrue(ErrorAction.MANUAL_ENTRY in actions)
        assertTrue(ErrorAction.RETRY in EntryErrorKind.RATE_LIMITED.actions())
        assertTrue(ErrorAction.RETRY in EntryErrorKind.SERVER.actions())
    }

    @Test
    fun configurationErrorsOfferServerSetupAndManualEntry() {
        assertEquals(
            listOf(ErrorAction.CONFIGURE_SERVER, ErrorAction.MANUAL_ENTRY),
            EntryErrorKind.NOT_CONFIGURED.actions(),
        )
        assertTrue(ErrorAction.CONFIGURE_SERVER in EntryErrorKind.UNAUTHORIZED.actions())
    }

    @Test
    fun inputAndLedgerErrorsDoNotRetry() {
        assertFalse(ErrorAction.RETRY in EntryErrorKind.AMOUNT_MISSING.actions())
        assertTrue(EntryErrorKind.EMPTY_INPUT.actions().isEmpty())
        assertTrue(EntryErrorKind.MANUAL_INVALID.actions().isEmpty())
        assertTrue(EntryErrorKind.LEDGER_FAILED.actions().isEmpty())
    }

    @Test
    fun clarificationAnswerIsOriginalThenAnswer() {
        assertEquals("قوطی نوشابه خریدم ۵۰۰ تومان", combineClarification("  قوطی نوشابه خریدم ", " ۵۰۰ تومان  "))
    }

    @Test
    fun clarificationDropsBlankParts() {
        assertEquals("قوطی نوشابه", combineClarification("قوطی نوشابه", "   "))
        assertEquals("۵۰۰ تومان", combineClarification("", "۵۰۰ تومان"))
    }

    @Test
    fun listHelpersAppendReplaceAndRemoveById() {
        val error = ChatItem.Error("e1", EntryErrorKind.OFFLINE, "t", "m", retryText = "متن")
        val user = ChatItem.UserText("u1", "متن")
        val state = EntryUiState().append(user).append(error)

        assertEquals(error, state.items[1])
        assertNull(state.draft("e1"))

        val replaced = state.replace("e1", ChatItem.Clarification("e1", "سؤال؟"))
        assertTrue(replaced.items[1] is ChatItem.Clarification)

        val removed = replaced.remove("u1")
        assertEquals(listOf<ChatItem>(replaced.items[1]), removed.items)
    }

    @Test
    fun actionsForErrorItemComeFromKind() {
        val item = ChatItem.Error("e1", EntryErrorKind.NOT_CONFIGURED, "t", "m", retryText = null)
        assertEquals(EntryErrorKind.NOT_CONFIGURED.actions(), item.actions)
    }
}
