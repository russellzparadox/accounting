# T03 — domain-core
Status: todo
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
