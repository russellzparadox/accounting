package com.example.accounting.core.persian

import com.example.accounting.core.persian.AmountUnit.RIAL
import com.example.accounting.core.persian.AmountUnit.TOMAN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class NumberWordParserTest {

    private data class Case(val text: String, val value: Long, val unit: AmountUnit)

    private val sentences = listOf(
        Case("۴ قوطی نوشابه روی هم ۵۰۰٬۰۰۰ تومان خریدم", 500_000L, TOMAN),
        Case("۴ قوطی نوشابه روی هم ۵۰۰ هزار تومان خریدم", 500_000L, TOMAN),
        Case("پانصد هزار تومان", 500_000L, TOMAN),
        Case("نیم میلیون تومان", 500_000L, TOMAN),
        Case("دو و نیم میلیون تومان", 2_500_000L, TOMAN),
        Case("دو و نیم میلیون", 2_500_000L, TOMAN),
        Case("یک میلیون و دویست هزار تومان", 1_200_000L, TOMAN),
        Case("صد و پنجاه هزار تومان", 150_000L, TOMAN),
        Case("۵۰۰٬۰۰۰ تومان", 500_000L, TOMAN),
        Case("۲٫۵ میلیون تومان", 2_500_000L, TOMAN),
        Case("2.5 میلیون تومان", 2_500_000L, TOMAN),
        Case("یک میلیارد ریال", 1_000_000_000L, RIAL),
        Case("۱۲۳٬۴۵۶ ریال", 123_456L, RIAL),
        Case("یه میلیون تومن", 1_000_000L, TOMAN),
        Case("هزار تومان", 1_000L, TOMAN),
        Case("سی هزار تومان", 30_000L, TOMAN),
        Case("بیست و پنج هزار تومان", 25_000L, TOMAN),
        Case("دو صد هزار تومان", 200_000L, TOMAN),
        Case("یک میلیون و نیم تومان", 1_500_000L, TOMAN),
        Case("ده هزار و پانصد تومان", 10_500L, TOMAN),
        Case("نود و نه هزار تومان", 99_000L, TOMAN),
        Case("دو میلیون و دویست و پنجاه هزار تومان", 2_250_000L, TOMAN),
        Case("۲ میلیون و ۳۰۰ هزار تومان", 2_300_000L, TOMAN),
        Case("۴ و ۵۰۰ تومان", 500L, TOMAN),
        Case("۴ قوطی نوشابه ۵۰۰۰۰۰ تومان", 500_000L, TOMAN),
        Case("هزارتومان", 1_000L, TOMAN),
        Case("نیم میلیون ریال", 500_000L, RIAL),
        Case("۲۵۰٬۰۰۰٬۰۰۰ تومان", 250_000_000L, TOMAN),
        Case("سیصد و سی و سه هزار تومان", 333_000L, TOMAN),
        Case("۱۵۰ میلیون ریال", 150_000_000L, RIAL),
    )

    @TestFactory
    fun sentenceTable(): List<DynamicTest> = sentences.map { c ->
        DynamicTest.dynamicTest(c.text) {
            val p = NumberWordParser.parseAmount(c.text)
            assertEquals(c.value, p?.value, "value")
            assertEquals(c.unit, p?.unit, "unit")
            val expectedRial = if (c.unit == TOMAN) c.value * 10 else c.value
            assertEquals(expectedRial, p?.rial, "rial")
        }
    }

    @Test
    fun quantityIsNotTakenWhenAmountHasUnit() {
        val p = NumberWordParser.parseAmount("۴ قوطی نوشابه روی هم ۵۰۰ هزار تومان خریدم")
        assertEquals(500_000L, p?.value)
    }

    @Test
    fun matchedTextIsExactOriginalSubstring() {
        val p = NumberWordParser.parseAmount("۴ قوطی نوشابه روی هم ۵۰۰ هزار تومان خریدم")
        assertEquals("۵۰۰ هزار تومان", p?.matchedText)
    }

    @Test
    fun matchedTextIncludesUnitWordOnly() {
        val p = NumberWordParser.parseAmount("نیم میلیون ریال")
        assertEquals("نیم میلیون ریال", p?.matchedText)
    }

    @Test
    fun candidatesReturnAllPhrasesInOrder() {
        val c = NumberWordParser.candidates("۴ قوطی ۵۰۰ هزار تومان")
        assertEquals(listOf(4L, 500_000L), c.map { it.value })
    }

    @Test
    fun twoMarkedPhrasesAreAmbiguousAndReturnNull() {
        val text = "۴ قوطی ۵۰۰ هزار تومان و ۳ عدد ۲۰۰ هزار ریال"
        assertNull(NumberWordParser.parseAmount(text))
        assertEquals(4, NumberWordParser.candidates(text).size)
    }

    @Test
    fun twoUnmarkedPhrasesAreAmbiguousAndReturnNull() {
        assertNull(NumberWordParser.parseAmount("۴ قوطی نوشابه ۵۰۰۰۰۰"))
    }

    @Test
    fun singleUnmarkedNumberIsReturnedWithDefaultUnit() {
        val p = NumberWordParser.parseAmount("۵۰۰۰۰۰")
        assertEquals(500_000L, p?.value)
        assertEquals(TOMAN, p?.unit)
    }

    @Test
    fun defaultUnitRialIsApplied() {
        val p = NumberWordParser.parseAmount("۵۰۰ هزار", RIAL)
        assertEquals(500_000L, p?.value)
        assertEquals(RIAL, p?.unit)
        assertEquals(500_000L, p?.rial)
    }

    @Test
    fun noNumberReturnsNull() {
        assertNull(NumberWordParser.parseAmount("سلام، حال شما چطور است؟"))
    }

    @Test
    fun emptyStringReturnsNull() {
        assertNull(NumberWordParser.parseAmount(""))
    }

    @Test
    fun zeroIsNotAnAmount() {
        assertNull(NumberWordParser.parseAmount("صفر تومان"))
    }

    @Test
    fun zeroIsExcludedFromCandidates() {
        val c = NumberWordParser.candidates("صفر تومان ۵ تومان")
        assertEquals(listOf(5L), c.map { it.value })
    }

    @Test
    fun loneHalfIsNotAnAmount() {
        assertNull(NumberWordParser.parseAmount("نیم تومان"))
    }

    @Test
    fun overflowOfRialReturnsNull() {
        // 1e18 toman = 1e19 rial > Long.MAX_VALUE
        val text = PersianDigits.toPersian("1" + "0".repeat(18)) + " تومان"
        assertNull(NumberWordParser.parseAmount(text))
    }

    @Test
    fun largestSafeTomanAmountParses() {
        // 9e17 toman = 9e18 rial < Long.MAX_VALUE
        val text = PersianDigits.toPersian("9" + "0".repeat(17)) + " تومان"
        assertEquals(9_000_000_000_000_000_000L, NumberWordParser.parseAmount(text)?.rial)
    }

    @Test
    fun arabicIndicDigitsAndSeparators() {
        val p = NumberWordParser.parseAmount("٥٠٠٬٠٠٠ تومان")
        assertEquals(500_000L, p?.value)
    }

    @Test
    fun asciiCommaGrouping() {
        assertEquals(500_000L, NumberWordParser.parseAmount("500,000 تومان")?.value)
    }

    @Test
    fun arabicYehAndKafAreNormalized() {
        // يک میلیون with Arabic yeh (U+064A)
        assertEquals(1_000_000L, NumberWordParser.parseAmount("يک میلیون تومان")?.value)
    }

    @Test
    fun magnitudeWordAloneMeansOne() {
        assertEquals(1_000_000L, NumberWordParser.parseAmount("میلیون تومان")?.value)
    }

    @Test
    fun persianCommaBetweenNumbersIsNotThousandsGroup() {
        assertNull(NumberWordParser.parseAmount("۴، ۵۰۰ قوطی"))
    }

    @Test
    fun twoAmountsWithUnitsJoinedByWaAreAmbiguous() {
        assertNull(NumberWordParser.parseAmount("۵۰ تومان و ۲۰ هزار تومان"))
    }

    @Test
    fun resultRialIsAlwaysTenTimesTomanValue() {
        val p = NumberWordParser.parseAmount("۱۲ هزار تومان")
        assertTrue(p != null && p.rial == p.value * 10)
    }
}
