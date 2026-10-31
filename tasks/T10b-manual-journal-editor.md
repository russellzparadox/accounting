# T10b — Manual journal editor (free-form سند حسابداری, no LLM, no backend)
Status: in_progress
Depends: T08, T10
Read first: CLAUDE.md, handouts/T03-domain-core.md, handouts/T04-room-persistence.md, handouts/T08a-entry-viewmodel.md, handouts/T08-entry-ui.md, handouts/T10-ledger-ui.md, docs/DESIGN.md, docs/ACCOUNTING_RULES.md
Skills to invoke: design:design-critique, design:accessibility-review, design:ux-copy, persian-writing:persian-writing

## Goal
- Let the user record ANY balanced journal document by hand: pick accounts, enter Dr/Cr amounts, memo, Jalali date. Works fully offline. Complements the template-based quick manual form (T08) and the LLM path.

## Deliverables
- `feature/ledger` (or new `feature/journal`) `ManualJournalViewModel` + screen: date (Jalali picker from `JalaliDate`), memo, 2..N lines (account picker over postable accounts incl. party معین via search, side Dr/Cr, amount toman/rial per setting with Persian digits), live totals + difference indicator, "افزودن سطر", delete row, save disabled until Dr == Cr > 0 and each line valid.
- Validation: reuse `JournalEntry` init rules (balanced, >= 2 lines, lines > 0), `ChartOfAccounts.requirePostable` (no گروه/کل-with-children accounts; معین required when کل has children), date inside open fiscal year; Persian error messages.
- Commit via `LedgerRepository.commit` (template id `MANUAL` — add to domain `TemplateId` only if it does not break the 14 templates or T06 schema; otherwise store as nullable/`MANUAL` kind in the entry and document). Undo = reversal doc (existing `reverse`).
- Entry points: FAB/menu on journal screen ("سند دستی"); from a committed/draft card "ویرایش به صورت سند" (prefill lines from a Draft before confirm).
- Tests: VM unit tests (balanced/unbalanced, postable rules, fiscal-year check, toman<->rial conversion, double submit once), Compose UI test (add rows, difference indicator, save), trial balance stays balanced after commit.

## Acceptance criteria
- [ ] Works with backend unset and airplane mode.
- [ ] Unbalanced or invalid documents can never be committed (VM + repo level).
- [ ] No Float/Double for money; UI uses Persian digits; touch targets >= 48dp; RTL.
- [ ] CI green (android workflow), no tests skipped; max 6 pushes; commit prefix `T10b:`.

## Out of scope
- Editing/deleting committed documents (immutable; reversal only), attachments, multi-currency.

## Added by main agent (binding)
- Read also `handouts/T10-ledger-ui.md` (navigation shell, account picker, DocumentDetail, search normalization), `handouts/T09-voice-input.md` (skim), `handouts/T01d-ci-green.md`, `docs/CI.md`.
- Decision: drafts' «ویرایش مبلغ» stays; committed documents have no edit/delete (golden rule 3). The manual editor creates NEW documents only.
- Known CI gotcha: Room schema folder race fixed in `core/data/build.gradle.kts` (kspRelease depends on kspDebug). If a KSP schema JSON parse error reappears, report it in the handout; do not paper over it.
- Reuse: `AccountPicker` (feature:ledger), `JalaliDate` + `MoneyFormat` (core:persian), `LedgerRepository.commit` and domain `JournalEntry` validation, `ChartOfAccounts.requirePostable`. Template id for free-form docs: check `TemplateId` in core:domain; if a `MANUAL` id is needed, add it WITHOUT touching the 14 template ids/serializer names used by the backend schema (T06) and the LLM intent (reject `MANUAL` as an LLM intent type). Add tests that `PostingEngine` still rejects/ignores MANUAL as an intent.
- Entry points: a «سند دستی» action on the journal tab (FAB/top action), and «ویرایش به صورت سند» on an unconfirmed entry DraftCard (prefill lines). Persist nothing until the user taps save.
- Party accounts: line account picker must include party معین (customers/suppliers) and allow creating a new party معین by name via `PartyRepository` (find-or-create), off the main thread.
- Verification is CI-only: ONE push per attempt; wait for THAT run to finish before the next push (poll https://api.github.com/repos/russellzparadox/accounting/actions/runs/<id> with an `until` loop in Bash run_in_background); read the 'Failure summary' step via mcp__github__get_job_logs tail_lines <= 130. Max 7 pushes. Do NOT end your turn until the CI run for your last push has completed AND `handouts/T10b-manual-journal-editor.md` is written (screens, VM API, validation rules, audit of every acceptance criterion with file:line, design critique + a11y pass at code level, manual device checklist, final run id). Set Status done only on green + all criteria MET, update the T10b row in tasks/README.md. Each green push publishes an APK release.
