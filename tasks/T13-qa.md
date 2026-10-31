# T13 — qa
Status: in_progress
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

## Carried over (main agent, binding)
- From T09: add >= 5 spoken-amount sentences through the voice->pipeline test path (`نیم میلیون`, `دو و نیم میلیون`, mixed digits/words, colloquial `تومن`).
- From T11 (A3): PDF Persian RTL rendering is unverified by tests (PdfDocument cannot run under Robolectric). Add a CI job using an Android emulator (e.g. reactivecircus/android-emulator-runner, API 34, x86_64, KVM enabled on ubuntu-latest) that runs an instrumented test: export a trial-balance PDF, render pages with `PdfRenderer` to Bitmaps, assert page count > 0 and non-blank pixels, and upload the PNGs + PDF as workflow artifacts for the user to inspect. Same emulator job should run basic UI smoke tests (launch app, type manual entry, commit, see in journal). Keep this job separate from the main `android` build so it never blocks releases (allow failure / separate workflow `instrumented.yml`, manual + nightly trigger).
- From T10: process death restore for unsent entry text (SavedStateHandle) if not done.

## Updated scope after T13a (main agent, binding)
- DONE elsewhere (do not redo): emulator workflow, instrumented smoke tests, screenshots, PDF/CSV export on emulator (T13a, handouts/T13a-emulator-qa.md). Read it, plus handouts T07, T08a, T09, T10, T11, T12, T12b, T04, T06, T06b and docs/ACCOUNTING_RULES.md.
- Remaining deliverables:
  1. FUZZ THROUGH THE REAL STACK: Robolectric test with in-memory Room: 1000 random valid intents of all 13 posting templates (PostingEngine + PartyRepository + LedgerRepository.commit), random reversals of ~10%, assert after each batch: trial balance debit total == credit total, per-account ledger running balances equal trial balance rows, doc numbers per fiscal year gap-free and unique, no document editable/deletable (UPDATE/DELETE on journal tables rejected by the triggers), reversal restores net effect (original+reversal nets to zero per account). Deterministic seed printed on failure.
  2. VOICE: >= 5 spoken-amount sentences through the voice->pipeline path (`نیم میلیون`, `دو و نیم میلیون`, mixed digits/words, colloquial `تومن`, `پونصد` if the parser supports it; if not, record as an Open issue, do not hack the domain).
  3. DRAFT-TIME PARTY CREATION BUG (T07 open issue 1, T08a handout): an unconfirmed credit draft currently find-or-creates a party معین inside PostingEngine.post. Fix: building a Draft must NOT write to the DB. Use a non-persisting preview resolver for drafts (placeholder account code/name shown as «طرف حساب جدید: <name>») and resolve/create the real party only at confirm time (re-post with the real resolver inside the same commit flow). Tests: discard a credit draft -> no party/account row created; confirm -> created once; double confirm -> one doc. This is a bug fix, not a rule change; keep the 14 templates and intent JSON unchanged.
  4. COVERAGE GAPS from audits: test account-picker balance unit (AccountPicker.kt, T12b), reports default fiscal year from settings (ReportsViewModel.kt), SavedStateHandle/process-death restore of unsent entry text if cheap.
  5. LLM EVAL REPORT: run `cd backend && npm test` and the eval harness in mock mode; write `docs/EVAL_REPORT.md` (counts per category, failures, how to run live with `ANTHROPIC_API_KEY` or an OpenAI-compatible `OPENAI_BASE_URL`/`OPENAI_API_KEY`/`LLM_MODEL`, expected live accuracy gate >= 90% on type+amount_rial+paid_with). No live run (no key here): say so.
  6. `docs/ACCOUNTANT_REVIEW.md`: checklist for the user's accountant: chart of accounts (docs/ACCOUNTING_RULES.md, chart_of_accounts.json), the 14 templates with Dr/Cr table and sample Persian sentences, periodic purchase decision (D7), party accounts decision (D8), fiscal year/doc numbering, rounding toman/rial, immutability + reversal policy, open questions from handouts/T02 (list them).
  7. `docs/TESTING.md`: what each test layer proves (JVM pure, Robolectric, emulator, backend mock eval), how to run each, known gaps (real mic, live LLM, PDF Persian shaping needs human look at the artifact).
- QA finds problems -> record in handout Open issues. Do not change accounting rules.
- Verification: pure JVM parts locally in a scratch Gradle project (/tmp/domain-verify pattern); Android parts in CI. ONE push per attempt; wait for THAT android run to finish before pushing again (poll https://api.github.com/repos/russellzparadox/accounting/actions/runs?branch=claude/dreamy-mccarthy-mczm8c with `until` loops in Bash run_in_background); read failures from check-run annotations (GET /repos/russellzparadox/accounting/check-runs/<job_id>/annotations; job id from /actions/runs/<run>/jobs) and the 'Failure summary' step via mcp__github__get_job_logs (tail_lines <= 130 only if annotations are insufficient). Max 8 pushes, commit prefix `T13:`, only branch claude/dreamy-mccarthy-mczm8c, no force push. Do NOT end your turn until the last push's run completed AND `handouts/T13-qa.md` is written (honest audit of items 1-7 MET/PARTIAL/NOT MET with file:line, final run id); set Status and README row honestly.
