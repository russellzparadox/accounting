# Handout T05 — persian-utils
Status: done

## Summary
- `core:persian` = pure Kotlin/JVM (`kotlin("jvm")`, jvmToolchain 21, kotlin-test-junit5). No `android.*`. No Float/Double. Money = Long rial/toman.
- Gregorian <-> Jalali, digit normalize, money format, number-word/digit amount parser.
- 105 JVM tests pass (scratch project, see How verified). Table: 30 Persian amount sentences (T05 needs 25+).

## Files created/changed
- `core/persian/build.gradle.kts` (Android → `kotlin("jvm")`, same shape as `core/domain`).
- `core/persian/src/main/AndroidManifest.xml` deleted.
- `core/persian/src/main/kotlin/com/example/accounting/core/persian/`: `PersianDigits.kt`, `JalaliDate.kt`, `MoneyFormat.kt`, `NumberWordParser.kt`.
- `core/persian/src/test/kotlin/com/example/accounting/core/persian/`: `PersianDigitsTest`, `JalaliDateTest`, `MoneyFormatTest`, `NumberWordParserTest`.
- `tasks/T05-persian-utils.md` Status line only.
- `gradle/libs.versions.toml`: NOT changed (no new deps needed).

## Public API (package `com.example.accounting.core.persian`)
```kotlin
enum class AmountUnit { TOMAN, RIAL }

data class ParsedAmount(val value: Long, val unit: AmountUnit, val rial: Long, val matchedText: String)

object NumberWordParser {
    fun parseAmount(text: String, defaultUnit: AmountUnit = AmountUnit.TOMAN): ParsedAmount?
    fun candidates(text: String, defaultUnit: AmountUnit = AmountUnit.TOMAN): List<ParsedAmount>
}

object PersianDigits {
    fun toLatin(text: String): String      // ۰-۹ and ٠-٩ -> 0-9, length-preserving
    fun toPersian(text: String): String    // 0-9 -> ۰-۹
}

object MoneyFormat {
    fun format(rial: Long, unit: AmountUnit): String
    // TOMAN: "۵۰۰٬۰۰۰ تومان" (rial/10; "٫" + digit if rial not multiple of 10). RIAL: "۱۲۳٬۴۵۶٬۷۸۹ ریال".
}

data class JalaliDate(val year: Int, val month: Int, val day: Int) {  // validated in init (IllegalArgumentException)
    fun toGregorian(): LocalDate
    fun monthName(): String
    override fun toString(): String        // "۱۴۰۴/۰۱/۰۱"
    companion object {
        val MONTH_NAMES: List<String>
        fun isLeapYear(year: Int): Boolean
        fun daysInMonth(year: Int, month: Int): Int
        fun from(date: LocalDate): JalaliDate
        fun fiscalYear(jalaliYear: Int): Pair<LocalDate, LocalDate>   // 1 فروردین .. last day اسفند, inclusive
    }
}
```

## Parser contract (for T07 / T06 cross-check)
- `ParsedAmount.value` = integer amount in `unit` AS STATED, after magnitude words. `"۵۰۰ هزار تومان"` → value 500000, unit TOMAN, rial 5000000.
- `ParsedAmount.rial` = rials. TOMAN → value×10. Overflow → candidate dropped (null).
- `matchedText` = exact substring of input, includes the unit word if present. T06 `amount_text` should equal or contain this.
- Supported: value words (یک…نود, صد, دویست…نهصد, یه colloquial), magnitudes (هزار, میلیون/ملیون, میلیارد/ملیارد), نیم, "و" joins ("دو و نیم میلیون", "یک میلیون و دویست هزار", "صد و پنجاه هزار", "دو صد هزار"), digits with `,` `٬` `،` grouping, decimals `.` or `٫` (integer math via exact rational, no Double), units تومان/تومن/ریال, glued forms ("هزارتومان"), Arabic yeh/kaf.
- Ambiguity: 1 number phrase → it. Several → the single phrase carrying a unit word or magnitude word; 0 or >1 such → null. `candidates()` returns all positive phrases in order (for T07 to ask the user).
- Non-representable (→ null): fractional stated value ("نیم تومان", "۱٫۵ تومان"), zero, overflow.

## Decisions made (+why)
- Enum named `AmountUnit`, not `Unit`: spec said `Unit`; a type named `Unit` in this package shadows `kotlin.Unit`. T07 must import `AmountUnit`.
- `ParsedAmount.value` is in stated unit (not always rial) so it matches T06 `amount_value`.
- Jalali leap rule = arithmetic 33-year cycle `(25y+11) mod 33 < 8`, not the astronomical Iranian rule. Checked against anchors (Nowruz 1398–1405, Esfand 29/30 boundaries, 1399/1403/1408 leap, 1404 not) and daily round-trip 1990–2060. Why: no Jalali library in JVM std; simple and exact in the window.
- Money math via `Rat(Long,Long)` with overflow-checked ops. Why: "no Float/Double for money"; decimals and "نیم" need fractions.
- `fiscalYear` anchored at 1 فروردین 1404 = 2025-03-21 (internal anchor only).

## How verified (commands + result)
- Scratch project `/tmp/persian-verify` (copied `settings.gradle.kts` with `include(":core:persian")`, root build and `gradle/libs.versions.toml` from `/tmp/domain-verify`, `core/persian` copied from repo).
- `cd /tmp/persian-verify && /opt/gradle/bin/gradle :core:persian:cleanTest :core:persian:test --no-daemon --console=plain` → BUILD SUCCESSFUL.
- JUnit XML: JalaliDateTest 33, MoneyFormatTest 12, NumberWordParserTest 52 (incl. 30-row table), PersianDigitsTest 8. Total 105, 0 failures, 0 errors.
- `grep -rnE "Float|Double|android" core/persian/src` → only a doc comment.
- Repo `./gradlew :core:persian:test` NOT run here (Google Maven blocked). CI is the final check.

## Open issues / risks
1. Single unmarked number is returned as-is: "یه قوطی نوشابه خریدم" → 1 toman. Quantity risk. T07 must cross-check `matchedText`/unit with the LLM output, or ask.
2. Common words that are number words: نه (9), سه (3), ده (10), یه (1), سی (30). They only create candidates; they matter only when no other phrase exists.
3. Jalali arithmetic rule may diverge from the official astronomical calendar outside the tested window (~1380–1430 tested). Outside it, don't trust conversions without review.
4. "X و Y" with two magnitudes is summed ("۱۰۰ هزار و ۲۰۰ هزار" → 300000), by Persian grammar. Two independent amounts joined by و will merge.
5. Fractional stated values are null. "۱٫۵ تومان" is not parsed.
6. `NumberWordParser` ignores sign: "-۵۰۰ تومان" → 500.
7. Repo CI not yet run for this module.

## Next task must know
- T07 (NL pipeline): `NumberWordParser.parseAmount(text)` → if null, ask user (use `candidates()` to show options). Use `ParsedAmount.rial` for `TransactionIntent.amount_rial`; don't convert yourself. Import `AmountUnit`.
- T04/UI: `JalaliDate.from(localDate)`, `toString()` for display, `JalaliDate.fiscalYear(y)` for FY bounds, `MoneyFormat.format(rial, unit)` for display. Persian digits only in UI; DB digits stay Latin (ISO dates).
- Don't add Float/Double to `core/persian`.
