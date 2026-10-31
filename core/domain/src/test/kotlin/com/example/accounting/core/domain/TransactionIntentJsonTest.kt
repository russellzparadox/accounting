package com.example.accounting.core.domain

import kotlinx.serialization.SerializationException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TransactionIntentJsonTest {
    private val example = """{"type":"PURCHASE_CASH","amount_rial":5000000,"date":"2026-10-31",
        "paid_with":"CASH","bank_account":null,"party_name":null,"category":null,
        "memo":"۴ قوطی نوشابه روی هم ۵۰۰٬۰۰۰ تومان خریدم","confidence":92,"needs_clarification":null}"""

    @Test
    fun decodesDocumentedShape() {
        val i = TransactionIntent.fromJson(example)
        assertEquals(TemplateId.PURCHASE_CASH, i.type)
        assertEquals(5_000_000L, i.amountRial)
        assertEquals(LocalDate.of(2026, 10, 31), i.date)
        assertEquals(PaidWith.CASH, i.paidWith)
        assertEquals(92, i.confidence)
        assertNull(i.needsClarification)
    }

    @Test
    fun encodesSnakeCaseAndRoundTrips() {
        val i = TransactionIntent.fromJson(example)
        val text = domainJson.encodeToString(TransactionIntent.serializer(), i)
        assertEquals(true, text.contains("\"amount_rial\":5000000"))
        assertEquals(true, text.contains("\"paid_with\":\"CASH\""))
        assertEquals(i, TransactionIntent.fromJson(text))
    }

    @Test
    fun ignoresUnknownKeysAndDecodesClarification() {
        val i = TransactionIntent.fromJson(
            """{"type":"SALE_CREDIT","amount_rial":1,"date":"2026-10-31","party_name":"x","confidence":50,
               "needs_clarification":{"question":"کدام مشتری؟"},"extra":1}""",
        )
        assertEquals("کدام مشتری؟", i.needsClarification?.question)
        assertEquals("x", i.partyName)
    }

    @Test
    fun rejectsUnknownTemplateAndBadValues() {
        assertFailsWith<SerializationException> {
            TransactionIntent.fromJson(example.replace("PURCHASE_CASH", "FOO"))
        }
        assertFailsWith<IllegalArgumentException> {
            TransactionIntent.fromJson(example.replace("5000000", "0"))
        }
        assertFailsWith<IllegalArgumentException> {
            TransactionIntent.fromJson(example.replace("\"confidence\":92", "\"confidence\":101"))
        }
    }
}
