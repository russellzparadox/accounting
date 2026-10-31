# T04 — room-persistence
Status: todo
Depends: T03
Read first: CLAUDE.md, handouts/T03-domain-core.md
Skills to invoke: none

## Goal
- Room entities, DAOs, repositories. Seed chart of accounts. Fiscal year and sequential doc numbers. Enforce immutability of committed entries.

## Deliverables (files)
- `core/data/src/main/kotlin/.../db/` entities, DAOs, `AppDatabase.kt`, migrations
- `core/data/src/main/kotlin/.../repo/` repositories
- `core/data/src/main/kotlin/.../seed/ChartSeeder.kt` (reads `chart_of_accounts.json`)
- `core/data/src/test/` or `androidTest/` DAO tests

## Acceptance criteria
- [ ] Entities: Account, JournalEntry, JournalLine, FiscalYear, DocSequence.
- [ ] Money columns are `INTEGER` (rials). No `REAL` columns (grep).
- [ ] Dates stored as ISO Gregorian `TEXT`.
- [ ] Seeder is idempotent. Second run adds no rows (test).
- [ ] Doc numbers are sequential per fiscal year, allocated in a transaction, no gaps (test).
- [ ] Committed journal rows cannot be UPDATE or DELETE. Enforced by DAO API and by DB triggers (tests).
- [ ] Test runner (Robolectric or instrumented) and reason recorded in handout.

## Out of scope
- UI, LLM, voice, reports.

## Verify (commands)
- `./gradlew :core:data:test`
- `grep -rE "REAL|Float|Double" core/data/src/main` (expect no money matches)
