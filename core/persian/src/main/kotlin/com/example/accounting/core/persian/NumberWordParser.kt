package com.example.accounting.core.persian

/** Unit of an amount. Named AmountUnit (not Unit) so it does not shadow kotlin.Unit. */
enum class AmountUnit { TOMAN, RIAL }

/**
 * [value] = integer amount in [unit] as stated (after magnitude words, e.g. "۵۰۰ هزار تومان" -> 500000).
 * [rial] = always rials (toman * 10). [matchedText] = exact substring of the input.
 */
data class ParsedAmount(
    val value: Long,
    val unit: AmountUnit,
    val rial: Long,
    val matchedText: String,
)

/**
 * Extracts amounts from Persian text. Integer math only (no Float/Double).
 *
 * Ambiguity rule for [parseAmount]: one number phrase in the text -> that phrase.
 * Several phrases -> the single phrase that has a unit word (تومان/ریال) or a magnitude word
 * (هزار/میلیون/میلیارد); none or several such -> null. Use [candidates] to ask the user.
 */
object NumberWordParser {

    fun parseAmount(text: String, defaultUnit: AmountUnit = AmountUnit.TOMAN): ParsedAmount? {
        val found = scan(text, defaultUnit)
        if (found.size == 1) return found[0].amount
        return found.filter { it.marked }.singleOrNull()?.amount
    }

    /** Every positive number phrase in [text], in order of appearance. */
    fun candidates(text: String, defaultUnit: AmountUnit = AmountUnit.TOMAN): List<ParsedAmount> =
        scan(text, defaultUnit).map { it.amount }

    // ---- internals -------------------------------------------------------------------------

    private const val ZWNJ: Char = '‌'
    private const val NEEM_WORD = "نیم"
    private const val AND_WORD = "و"

    private val VALUES: Map<String, Long> = mapOf(
        "صفر" to 0L, "یک" to 1L, "یه" to 1L, "دو" to 2L, "سه" to 3L, "چهار" to 4L,
        "پنج" to 5L, "شش" to 6L, "هفت" to 7L, "هشت" to 8L, "نه" to 9L, "ده" to 10L,
        "یازده" to 11L, "دوازده" to 12L, "سیزده" to 13L, "چهارده" to 14L, "پانزده" to 15L,
        "شانزده" to 16L, "هفده" to 17L, "هجده" to 18L, "نوزده" to 19L, "بیست" to 20L,
        "سی" to 30L, "چهل" to 40L, "پنجاه" to 50L, "شصت" to 60L, "هفتاد" to 70L,
        "هشتاد" to 80L, "نود" to 90L, "صد" to 100L, "دویست" to 200L, "سیصد" to 300L,
        "چهارصد" to 400L, "پانصد" to 500L, "ششصد" to 600L, "هفتصد" to 700L,
        "هشتصد" to 800L, "نهصد" to 900L,
    )

    private val MAGNITUDES: Map<String, Long> = mapOf(
        "هزار" to 1_000L,
        "میلیون" to 1_000_000L, "ملیون" to 1_000_000L,
        "میلیارد" to 1_000_000_000L, "ملیارد" to 1_000_000_000L,
    )

    private val UNITS: Map<String, AmountUnit> = mapOf(
        "تومان" to AmountUnit.TOMAN, "تومن" to AmountUnit.TOMAN, "ریال" to AmountUnit.RIAL,
    )

    private val UNIT_WORDS: List<String> = listOf("تومان", "تومن", "ریال")

    /** digits (ASCII after normalize), optional ',' '٬' '،' thousands groups, optional '.' '٫' decimal; or a word. */
    private val TOKEN_RE = Regex("""\d{1,3}(?:[,٬،]\d{3})+(?:[.٫]\d+)?|\d+(?:[.٫]\d+)?|[\p{L}\p{M}‌]+""")

    private enum class Kind { NUM, VAL, MAG, NEEM, AND, UNIT, OTHER }

    private val STARTERS = setOf(Kind.NUM, Kind.VAL, Kind.MAG, Kind.NEEM)

    private class Tok(
        val kind: Kind,
        val start: Int,
        val end: Int,
        val rat: Rat? = null,
        val long: Long = 0L,
        val unit: AmountUnit? = null,
    )

    /** Exact rational with Long parts; every op is overflow-checked (ArithmeticException). */
    private class Rat private constructor(val n: Long, val d: Long) {
        operator fun plus(o: Rat): Rat = of(
            Math.addExact(Math.multiplyExact(n, o.d), Math.multiplyExact(o.n, d)),
            Math.multiplyExact(d, o.d),
        )

        fun times(m: Long): Rat = of(Math.multiplyExact(n, m), d)
        fun isInteger(): Boolean = d == 1L
        fun isSmallDigit(): Boolean = d == 1L && n in 1L..9L

        companion object {
            val ZERO = Rat(0L, 1L)
            val ONE = Rat(1L, 1L)
            val HALF = Rat(1L, 2L)

            fun of(n: Long, d: Long): Rat {
                val g = gcd(Math.abs(n), d)
                return Rat(n / g, d / g)
            }

            private fun gcd(a: Long, b: Long): Long {
                var x = a
                var y = b
                while (y != 0L) {
                    val t = x % y
                    x = y
                    y = t
                }
                return x
            }
        }
    }

    private class Found(val amount: ParsedAmount, val marked: Boolean)

    private class Phrase(val amount: ParsedAmount?, val marked: Boolean, val next: Int)

    private fun scan(text: String, defaultUnit: AmountUnit): List<Found> {
        val toks = tokenize(PersianDigits.toLatin(normalizeLetters(text)))
        val out = ArrayList<Found>()
        var i = 0
        while (i < toks.size) {
            if (toks[i].kind !in STARTERS) {
                i++
                continue
            }
            val p = try {
                scanPhrase(toks, i, text, defaultUnit)
            } catch (e: ArithmeticException) {
                i++
                continue
            }
            val amount = p.amount
            if (amount != null) out += Found(amount, p.marked)
            i = p.next
        }
        return out
    }

    /** Arabic yeh/kaf -> Persian yeh/kaf. 1:1 char mapping, so indices stay valid. */
    private fun normalizeLetters(s: String): String = buildString(s.length) {
        for (c in s) {
            append(
                when (c) {
                    'ي' -> 'ی'
                    'ك' -> 'ک'
                    else -> c
                }
            )
        }
    }

    private fun tokenize(norm: String): List<Tok> {
        val out = ArrayList<Tok>()
        for (m in TOKEN_RE.findAll(norm)) {
            val s = m.value
            val start = m.range.first
            val end = m.range.last + 1
            if (s[0] in '0'..'9') {
                val r = parseNumber(s)
                out += if (r != null) Tok(Kind.NUM, start, end, rat = r) else Tok(Kind.OTHER, start, end)
                continue
            }
            val cut = splitCut(s)
            if (cut > 0) {
                out += classify(s.substring(0, cut), start, start + cut)
                out += classify(s.substring(cut), start + cut, end)
            } else {
                out += classify(s.replace(ZWNJ.toString(), ""), start, end)
            }
        }
        return out
    }

    /** "هزارتومان" -> cut before "تومان" when the prefix is a value or magnitude word. -1 if none. */
    private fun splitCut(s: String): Int {
        if (s.contains(ZWNJ)) return -1
        for (u in UNIT_WORDS) {
            if (s.length > u.length && s.endsWith(u)) {
                val cut = s.length - u.length
                val pre = s.substring(0, cut)
                if (VALUES.containsKey(pre) || MAGNITUDES.containsKey(pre)) return cut
            }
        }
        return -1
    }

    private fun classify(key: String, start: Int, end: Int): Tok = when {
        VALUES.containsKey(key) -> Tok(Kind.VAL, start, end, long = VALUES.getValue(key))
        MAGNITUDES.containsKey(key) -> Tok(Kind.MAG, start, end, long = MAGNITUDES.getValue(key))
        UNITS.containsKey(key) -> Tok(Kind.UNIT, start, end, unit = UNITS.getValue(key))
        key == NEEM_WORD -> Tok(Kind.NEEM, start, end)
        key == AND_WORD -> Tok(Kind.AND, start, end)
        else -> Tok(Kind.OTHER, start, end)
    }

    private fun parseNumber(raw: String): Rat? {
        val clean = raw.filter { it != ',' && it != '٬' && it != '،' }
        val dot = clean.indexOfFirst { it == '.' || it == '٫' }
        return try {
            if (dot < 0) {
                Rat.of(clean.toLong(), 1L)
            } else {
                val intPart = clean.substring(0, dot).toLong()
                val frac = clean.substring(dot + 1)
                var den = 1L
                repeat(frac.length) { den = Math.multiplyExact(den, 10L) }
                Rat.of(Math.addExact(Math.multiplyExact(intPart, den), frac.toLong()), den)
            }
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Whether an "و" (and) joins the previous token to the next one inside one amount phrase. */
    private fun andAllowed(last: Kind?, nxt: Kind): Boolean = when {
        nxt == Kind.NEEM -> last == Kind.NUM || last == Kind.VAL || last == Kind.MAG
        last == Kind.MAG -> nxt == Kind.NUM || nxt == Kind.VAL
        last == Kind.VAL -> nxt == Kind.VAL
        else -> false
    }

    /** Greedy scan of one amount phrase starting at [from]. */
    private fun scanPhrase(toks: List<Tok>, from: Int, text: String, defaultUnit: AmountUnit): Phrase {
        var total = Rat.ZERO
        var chunk: Rat? = null
        var last: Kind? = null
        var lastMag = 0L
        var andSeen = false
        var hasMag = false
        var j = from

        while (j < toks.size) {
            val t = toks[j]
            if (t.kind == Kind.AND) {
                val nxt = toks.getOrNull(j + 1)?.kind ?: break
                if (!andAllowed(last, nxt)) break
                andSeen = true
                j++
                continue
            }
            val c = chunk
            val ok = when (t.kind) {
                Kind.NUM -> {
                    val allowed = c == null && (last == null || (last == Kind.MAG && andSeen))
                    if (allowed) chunk = t.rat
                    allowed
                }
                Kind.VAL -> {
                    if (last == Kind.NUM) {
                        false
                    } else {
                        chunk = if (t.long == 100L && c != null && c.isSmallDigit()) {
                            c.times(100L) // "دو صد" = 200
                        } else {
                            (c ?: Rat.ZERO) + Rat.of(t.long, 1L)
                        }
                        true
                    }
                }
                Kind.MAG -> {
                    total = total + (c ?: Rat.ONE).times(t.long)
                    chunk = null
                    lastMag = t.long
                    hasMag = true
                    true
                }
                Kind.NEEM -> {
                    when {
                        c != null -> chunk = c + Rat.HALF
                        last == Kind.MAG -> total = total + Rat.of(lastMag, 2L) // "یک میلیون و نیم"
                        else -> chunk = Rat.HALF // "نیم میلیون"
                    }
                    true
                }
                Kind.AND, Kind.UNIT, Kind.OTHER -> false
            }
            if (!ok) break
            last = t.kind
            andSeen = false
            j++
        }

        if (j == from) return Phrase(null, false, from + 1)

        var endChar = toks[j - 1].end
        var explicit: AmountUnit? = null
        if (j < toks.size && toks[j].kind == Kind.UNIT) {
            explicit = toks[j].unit
            endChar = toks[j].end
            j++
        }
        val value = total + (chunk ?: Rat.ZERO)
        val unit = explicit ?: defaultUnit
        val amount = toAmount(value, unit, text.substring(toks[from].start, endChar))
        return Phrase(amount, hasMag || explicit != null, j)
    }

    private fun toAmount(value: Rat, unit: AmountUnit, matched: String): ParsedAmount? {
        if (!value.isInteger() || value.n <= 0L) return null
        return try {
            val rial = if (unit == AmountUnit.TOMAN) Math.multiplyExact(value.n, 10L) else value.n
            ParsedAmount(value.n, unit, rial, matched)
        } catch (e: ArithmeticException) {
            null
        }
    }
}
