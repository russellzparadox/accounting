# T13 — qa
Status: todo
Depends: T07, T08, T09, T10, T11, T12
Read first: CLAUDE.md, handouts/T07-nl-pipeline.md, handouts/T08-entry-ui.md, handouts/T09-voice-input.md, handouts/T10-ledger-ui.md, handouts/T11-reports.md, handouts/T12-settings-onboarding.md
Skills to invoke: none

## Goal
- Full test coverage pass: Compose UI tests, instrumented tests, LLM eval run, invariant fuzz, accountant-review checklist.

## Deliverables (files)
- `app/src/androidTest/` and `feature/*/src/androidTest/` Compose UI tests
- Invariant fuzz test: 1000 random entries -> balanced trial balance
- Eval run report from `backend/eval/` (mock mode always; live mode if key present)
- Accountant-review checklist: path not set in T00. Propose `docs/ACCOUNTANT_REVIEW.md`, note in handout.

## Acceptance criteria
- [ ] Compose UI tests cover entry, ledger, reports, settings screens.
- [ ] Instrumented tests pass on emulator or device. If none available, note in handout.
- [ ] Invariant fuzz: 1000 random entries, trial balance balanced. Test passes.
- [ ] Eval: all >= 50 sentences run. Results recorded. Live run only if key available; otherwise note.
- [ ] Accountant-review checklist written.
- [ ] Fixes found by QA go to handout Open issues. QA does not change domain rules.

## Out of scope
- New features, release config (T14).

## Verify (commands)
- `./gradlew test`
- `./gradlew connectedDebugAndroidTest` (if device available)
- `cd backend && npm test`
