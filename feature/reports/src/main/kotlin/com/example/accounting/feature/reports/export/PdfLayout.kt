package com.example.accounting.feature.reports.export

/**
 * Pure PDF geometry (no android.* imports, so it runs on the plain JVM): RTL column placement and page breaks.
 * Units are PDF points on an A4 portrait page. Column 0 is the rightmost column.
 */
internal object PdfLayout {
    const val PAGE_W = 595f
    const val PAGE_H = 842f
    const val MARGIN = 36f

    /** Column widths from relative weights, summing to [usable]. */
    fun columnWidths(weights: List<Float>, usable: Float): List<Float> {
        require(weights.isNotEmpty() && weights.all { it > 0f }) { "weights must be positive" }
        val total = weights.sum()
        return weights.map { usable * it / total }
    }

    /** Left edge of column [i] when column 0 sits at [right] and columns extend leftwards. */
    fun columnLeft(widths: List<Float>, i: Int, right: Float = PAGE_W - MARGIN): Float =
        right - widths.take(i + 1).sum()

    /**
     * Indices of the rows that start a new page (always starts with 0 when there are rows).
     * [firstTop] is the absolute y where rows start on page 1 (after the title block and header row).
     * Later pages start rows at MARGIN + [headerHeight] (header repeated). Body ends at PAGE_H - MARGIN.
     */
    fun pageStarts(rowHeights: List<Float>, firstTop: Float, headerHeight: Float): List<Int> {
        if (rowHeights.isEmpty()) return listOf(0)
        val limit = PAGE_H - MARGIN
        val starts = mutableListOf(0)
        var y = firstTop
        rowHeights.forEachIndexed { i, h ->
            require(h > 0f) { "row height must be positive" }
            if (i > 0 && y + h > limit) {
                starts += i
                y = MARGIN + headerHeight
            }
            y += h
        }
        return starts
    }
}
