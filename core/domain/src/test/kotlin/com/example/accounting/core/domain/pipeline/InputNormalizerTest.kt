package com.example.accounting.core.domain.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals

class InputNormalizerTest {
    @Test
    fun persianDigitsBecomeLatinAndGroupingIsKept() {
        assertEquals("4 قوطی نوشابه 500٬000 تومان", InputNormalizer.normalize("۴ قوطی نوشابه ۵۰۰٬۰۰۰ تومان"))
    }

    @Test
    fun arabicYehAndKafFoldToPersian() {
        assertEquals("کتاب ی", InputNormalizer.normalize("كتاب ي"))
    }

    @Test
    fun whitespaceRunsAndNbspCollapseAndTrim() {
        assertEquals("a b c", InputNormalizer.normalize("  a \t b \n c  "))
    }

    @Test
    fun zwnjIsKept() {
        assertEquals("می‌خرم", InputNormalizer.normalize("می‌خرم"))
    }

    @Test
    fun blankBecomesEmpty() {
        assertEquals("", InputNormalizer.normalize(" \t\n  "))
    }
}
