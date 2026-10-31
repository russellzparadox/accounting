# T03 — domain-core
Status: in_progress
Depends: T02, T01
Read first: CLAUDE.md, handouts/T02-accounting-rules.md, handouts/T01-android-scaffold.md
Skills to invoke: none

## Goal
- Pure Kotlin domain: Money, Account, JournalEntry/Line, TransactionIntent, PostingEngine (from T02 templates), balance validation, reversal. JVM tests incl. fuzz.

## Deliverables (files)
- `core/domain/src/main/kotlin/.../Money.kt`, `Account.kt`, `JournalEntry.kt`, `JournalLine.kt`, `TransactionIntent.kt`, `PostingEngine.kt`
- `core/domain/src/test/kotlin/.../` unit + fuzz tests

## Acceptance criteria
- [ ] `Money` wraps `Long` rials. No `Float`/`Double` in `core/domain` (grep).
- [ ] `JournalEntry` construction fails when sum(debit) != sum(credit).
- [ ] `PostingEngine` implements every template in `docs/ACCOUNTING_RULES.md`.
- [ ] Reversal produces a new entry. No mutating API on committed entries.
- [ ] `TransactionIntent` JSON shape documented in handout (T06 and T07 depend on it).
- [ ] Fuzz test: >= 1000 random valid intents, Dr == Cr for each posted entry.
- [ ] No `android.*` imports in `core/domain`.

## Out of scope
- Persistence, UI, LLM calls, Jalali/number parsing.

## Verify (commands)
- `./gradlew :core:domain:test`
- `grep -rE "Float|Double" core/domain/src/main` (expect no matches)

## Added by main agent (decisions + constraints)
- Decisions D7-D9, D11 in `docs/DECISIONS.md` are binding: periodic purchases (5100); `Party` معین find-or-create by normalized name under 1300 (customers) / 2100 (suppliers) via a `PartyAccountResolver` interface (persistence impl is T04); fiscal year Jalali default (stored as Gregorian start/end dates, passed in; no Jalali logic here, T05 owns it).
- Convert `core/domain/build.gradle.kts` to a pure Kotlin/JVM module (`kotlin("jvm")`, JUnit5 or kotlin.test, jvmToolchain 21), update `gradle/libs.versions.toml` + any module referencing it. No `android.*`. Loading `chart_of_accounts.json`: use kotlinx-serialization-json (add to catalog) or a tiny hand parser, document the choice.
- Implement the 14 template ids EXACTLY as in `docs/ACCOUNTING_RULES.md` section 8 (copy id strings). `PaidWith {CASH, BANK}` variants, category -> 6100..6800 for expenses, معین required when parent کل has children.
- Gradle may not resolve in this sandbox (Google Maven blocked). Try to verify the pure-JVM module in a scratch dir under /tmp (Maven Central / kotlinc) and report honestly; CI (T01c) is the fallback.
- TransactionIntent JSON shape (types = template ids, amount_rial Long, paid_with, party_name, category, date, memo, confidence, needs_clarification{question}) must be documented in the handout - T06 and T07 depend on it.
