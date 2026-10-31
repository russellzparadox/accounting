package com.example.accounting.feature.reports

import com.example.accounting.feature.reports.export.PdfLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure PDF geometry. Replaces the Robolectric PdfExporter render tests, which cannot run here (PdfDocument is
 * closed under Robolectric). Rendering itself is verified only on a device (handout checklist).
 */
class PdfLayoutTest {
    @Test
    fun columnWidthsSumToUsableWidthAndFollowWeights() {
        val w = PdfLayout.columnWidths(listOf(1f, 3f), 400f)
        assertEquals(100f, w[0], 0.01f)
        assertEquals(300f, w[1], 0.01f)
    }

    @Test
    fun rtlColumnZeroSitsAtRightMarginAndLaterColumnsExtendLeft() {
        val widths = listOf(100f, 200f)
        val right = PdfLayout.PAGE_W - PdfLayout.MARGIN
        assertEquals(right - 100f, PdfLayout.columnLeft(widths, 0), 0.01f)
        assertEquals(right - 300f, PdfLayout.columnLeft(widths, 1), 0.01f)
    }

    @Test
    fun shortTableIsOnePage() {
        assertEquals(listOf(0), PdfLayout.pageStarts(List(3) { 20f }, firstTop = 200f, headerHeight = 20f))
    }

    @Test
    fun longTableBreaksAndEveryPageFitsTheBody() {
        val heights = List(120) { 20f }
        val firstTop = 200f
        val header = 20f
        val starts = PdfLayout.pageStarts(heights, firstTop, header)
        assertTrue("expected several pages, got ${starts.size}", starts.size > 1)
        assertEquals(0, starts.first())
        assertTrue(starts.zipWithNext().all { (a, b) -> a < b })
        val limit = PdfLayout.PAGE_H - PdfLayout.MARGIN
        starts.forEachIndexed { p, from ->
            val to = starts.getOrNull(p + 1) ?: heights.size
            val top = if (p == 0) firstTop else PdfLayout.MARGIN + header
            val bottom = top + heights.subList(from, to).sum()
            assertTrue("page $p overflows: bottom=$bottom limit=$limit", bottom <= limit)
        }
        // every row appears exactly once
        assertEquals(heights.size, starts.indices.sumOf { p ->
            (starts.getOrNull(p + 1) ?: heights.size) - starts[p]
        })
    }

    @Test
    fun emptyTableHasOnePage() {
        assertEquals(listOf(0), PdfLayout.pageStarts(emptyList(), firstTop = 200f, headerHeight = 20f))
    }
}
