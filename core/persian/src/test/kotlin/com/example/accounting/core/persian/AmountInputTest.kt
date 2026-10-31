package com.example.accounting.core.persian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountInputTest {
    @Test
    fun tomanInputIsConvertedToRial() {
        assertEquals(5_000_000L, AmountInput.parseRial("۵۰۰٬۰۰۰", AmountUnit.TOMAN))
        assertEquals(5_000_000L, AmountInput.parseRial("500,000", AmountUnit.TOMAN))
    }

    @Test
    fun rialInputIsTakenAsTypedWithoutConversion() {
        assertEquals(5_000_000L, AmountInput.parseRial("۵٬۰۰۰٬۰۰۰", AmountUnit.RIAL))
        assertEquals(5_000_001L, AmountInput.parseRial("5000001", AmountUnit.RIAL))
    }

    @Test
    fun emptyZeroNonDigitAndNonIntegerAreRejectedInBothUnits() {
        for (unit in AmountUnit.entries) {
            assertNull(AmountInput.parseRial("", unit), unit.name)
            assertNull(AmountInput.parseRial("0", unit), unit.name)
            assertNull(AmountInput.parseRial("12.5", unit), unit.name)
            assertNull(AmountInput.parseRial("abc", unit), unit.name)
            assertNull(AmountInput.parseRial("-5", unit), unit.name)
        }
    }

    @Test
    fun upperBoundIsRialMaximumInBothUnits() {
        assertEquals(AmountInput.MAX_RIAL, AmountInput.parseRial("1000000000000000", AmountUnit.RIAL))
        assertNull(AmountInput.parseRial("1000000000000001", AmountUnit.RIAL))
        assertEquals(999_999_999_999_990L, AmountInput.parseRial("99999999999999", AmountUnit.TOMAN))
        // 15 toman digits is over the toman parser limit (14 digits).
        assertNull(AmountInput.parseRial("100000000000000", AmountUnit.TOMAN))
    }

    @Test
    fun tomanEditTextIsNullForRialThatIsNotWholeToman() {
        assertEquals("500000", AmountInput.toInputText(5_000_000L, AmountUnit.TOMAN))
        assertNull(AmountInput.toInputText(5_000_001L, AmountUnit.TOMAN))
        assertEquals("5000001", AmountInput.toInputText(5_000_001L, AmountUnit.RIAL))
        assertNull(AmountInput.toInputText(0L, AmountUnit.RIAL))
    }

    @Test
    fun roundTripKeepsValueInEachUnit() {
        // MAX_RIAL is not in the list: its toman form has 15 digits, which TomanInput does not accept.
        for (rial in listOf(10L, 5_000_000L, 999_999_999_999_990L)) {
            val toman = AmountInput.toInputText(rial, AmountUnit.TOMAN)
            if (toman != null) assertEquals(rial, AmountInput.parseRial(toman, AmountUnit.TOMAN))
            assertEquals(rial, AmountInput.parseRial(AmountInput.toInputText(rial, AmountUnit.RIAL)!!, AmountUnit.RIAL))
        }
    }
}
