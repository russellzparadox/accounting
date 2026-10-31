# T05 — persian-utils
Status: done
Depends: T01
Read first: CLAUDE.md, handouts/T01-android-scaffold.md
Skills to invoke: persian-writing:persian-writing

## Goal
- Pure Kotlin utils: Jalali date conversion and format, Persian digit normalize, number-word parser, toman/rial format. Unit tests.

## Deliverables (files)
- `core/persian/src/main/kotlin/.../JalaliDate.kt`
- `core/persian/src/main/kotlin/.../PersianDigits.kt`
- `core/persian/src/main/kotlin/.../NumberWordParser.kt`
- `core/persian/src/main/kotlin/.../MoneyFormat.kt`
- `core/persian/src/test/kotlin/.../` tests

## Acceptance criteria
- [ ] Gregorian <-> Jalali correct for >= 20 known dates incl. leap years.
- [ ] Digit normalize maps ۰-۹ and ٠-٩ to 0-9, and back.
- [ ] Parser handles at least: "پانصد هزار تومان", "نیم میلیون", "۵۰۰٬۰۰۰", "دو و نیم میلیون".
- [ ] Parser output unit (rial or toman) stated in handout.
- [ ] Money formatting uses Persian digits and `٬` separator.
- [ ] >= 60 unit test cases total, all pass.
- [ ] No Android imports.

## Out of scope
- Date pickers, UI, LLM, voice.

## Verify (commands)
- `./gradlew :core:persian:test`

## Added by main agent
- Also read `handouts/T03-domain-core.md` (kotlin("jvm") conversion pattern, libs.versions.toml entries) and `handouts/T06-backend-llm-proxy.md` (T07 will cross-check `amount_text` against this parser).
- Convert `core/persian/build.gradle.kts` to a pure Kotlin/JVM module exactly like `core/domain` (T03) so tests run here without Android SDK; verify in a scratch Gradle project under /tmp (Maven Central works, Google Maven not) and report commands/results. CI is the final check. No `android.*`, no Float/Double for money values (parser returns Long).
- API needed by later tasks (document in handout):
  - `NumberWordParser.parseAmount(text: String, defaultUnit: Unit = TOMAN): ParsedAmount?` -> `ParsedAmount(value: Long, unit: TOMAN|RIAL, rial: Long, matchedText: String)`. Extract the amount phrase from a longer sentence ("۴ قوطی نوشابه روی هم ۵۰۰ هزار تومان خریدم" -> 500000 toman; must NOT take the quantity "۴" as the amount; when multiple numbers, prefer the one followed by a unit word/magnitude word تومان/ریال/هزار/میلیون/میلیارد, else return null (ambiguous) and expose `candidates()` for T07 to ask the user). Support: هزار, میلیون, میلیارد, "نیم" (half), "دو و نیم میلیون", "یک میلیون و دویست هزار", "صد و پنجاه هزار", digits with `,`, `٬`, `،`, mixed ("۵۰۰ هزار", "2.5 میلیون" decimals via integer math, no Double), "تومن" colloquial, "ت" not required.
  - `JalaliDate` (year, month, day) <-> `java.time.LocalDate`; `toString` Persian digits formatter; month names; `JalaliDate.fiscalYear(jalaliYear): Pair<LocalDate, LocalDate>` (1 فروردین .. last day of اسفند, leap-aware) for T04.
  - `PersianDigits.toLatin/toPersian`, `MoneyFormat.format(rial: Long, unit)` with `٬` separators and Persian digits.
- Leap-year rule: use a well-tested algorithm (e.g. 2820-year/arithmetical or the standard jalaali-js algorithm); test known years (1399, 1403, 1408 leap; 1404 not) and conversions around Nowruz and 29/30 اسفند.
