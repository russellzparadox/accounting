# T05 — persian-utils
Status: in_progress
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
