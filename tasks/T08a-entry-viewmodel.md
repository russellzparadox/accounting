# T08a — Entry state layer: DI, config, ViewModel
Status: in_progress
Depends: T04, T07
Read first: CLAUDE.md, handouts/T03-domain-core.md, handouts/T04-room-persistence.md, handouts/T05-persian-utils.md, handouts/T07-nl-pipeline.md, handouts/T01e-auto-release.md, docs/DESIGN.md (skim: copy strings), docs/CI.md
Skills to invoke: persian-writing:persian-writing (all user-visible strings)

## Goal
- Wire the app so a typed Persian sentence becomes a confirmed ledger document. No Compose UI yet (T08 does screens). Everything below is testable with fakes.

## Deliverables (files)
- `app/src/main/java/.../di/` Hilt modules: `AppDatabase` (file DB `accounting.db`), `LedgerRepository`, `PartyRepository` (as `PartyAccountResolver`), `ChartOfAccounts.standard()`, `Clock`, `NlParser` (`NlParserHttp` built from `ConfigStore`; rebuild when config changes), `ConfigStore`.
- `core/data/.../config/ConfigStore.kt`: `BackendConfig(baseUrl: String, token: String)`; SharedPreferences-backed (plain prefs ok for v1, note EncryptedSharedPreferences as T12 follow-up in handout); `Flow<BackendConfig>`; blank default => `NotConfigured`.
- `feature/entry/.../EntryViewModel.kt` (+ `EntryUiState`, `EntryAction`, `ChatItem`): 
  - `submit(text)`: runs `NlPipeline` with current fiscal year (`ensureFiscalYear(jalaliYear of today)`); results -> `ChatItem.DraftCard(draft, needsConfirm reasons)`, `ChatItem.Clarification(question)`, `ChatItem.Error(PipelineError -> Persian message + retry/manual action)`.
  - `answerClarification(text)`: re-submit original + answer combined (document rule in handout).
  - `confirm(draftId)` -> party resolution happens off main thread (`Dispatchers.IO`; `PartyRepository.resolve` is BLOCKING) then `LedgerRepository.commit`; emits `ChatItem.Committed(doc)`; `undo(docId)` -> `LedgerRepository.reverse` (creates reversal doc, never deletes).
  - `useDetectedAmount(draftId)` / `useModelAmount(draftId)` for mismatch (via `Draft.withAmount`).
  - `discard(draftId)`.
  - Manual fallback `submitManual(templateId, amountRial, paidWith, partyName?, category?, memo?)` building `TransactionIntent` -> PostingEngine directly (works with no backend/offline).
  - Not configured backend => friendly Persian state with action "تنظیم سرور" (opens config dialog in T08) + manual entry still works.
- First-run: `ChartSeeder.seed()` + `ensureFiscalYear` before first use (idempotent).
- Persian strings in `app/src/main/res/values/strings.xml` (or `feature/entry/src/main/res/values/strings.xml`): error messages for each `PipelineError`, confirm reasons, button labels. ZWNJ correct.
- Tests (JVM/Robolectric run by CI): ViewModel with `FakeNlParser` + in-memory `AppDatabase` (Robolectric): soda example end-to-end -> committed doc in ledger -> trial balance balanced; mismatch flow; clarification flow; each error mapped; undo creates reversal doc and trial balance stays balanced; manual path; not-configured path; double confirm of the same draft commits once.

## Acceptance criteria
- [ ] `./gradlew test` and `assembleDebug` green in CI (android workflow), no tests skipped/weakened.
- [ ] No Compose screens, no network in tests.
- [ ] UI never touches `LedgerRepository` directly: only via ViewModel actions.
- [ ] Handout lists exact state/action types and the resolution rules.
- [ ] Max 6 pushes (every green push publishes a release). Prefix commits `T08a:`; only push to `claude/dreamy-mccarthy-mczm8c`.

## Out of scope
- Compose UI (T08), voice (T09), settings UI (T12), journal/ledger screens (T10).

## FIX ROUND 1 (main agent)
- CI run 41186975674 (head e1e7141, includes T08a code a96c19d) FAILED. Runs 65/66 were cancelled by pushes, so this is the first real verdict. Read the "Failure summary" step first (tail_lines <= 90), fix root causes only, no test weakening. Known unverified bits: lifecycle-viewmodel-ktx 2.9.4 coordinates, `javax.inject.Inject` availability in `feature:entry`/`core:data`, Hilt/KSP wiring in `feature:entry`, Robolectric config.
- Also finish the paperwork: `handouts/T08a-entry-viewmodel.md` (exact public types: EntryAction, ChatItem, EntryUiState, EntryViewModel API, ConfigStore/BackendConfig, NlParserProvider, DataModule bindings, rules: clarification = original + answer combined, confirm claim flag/commit once, undo = reversal, manual path) and Status done in this file.
- Known gap to document (do not fix here): party find-or-create occurs at draft time (inside `PostingEngine.post` called by `NlPipeline`), so an unconfirmed credit draft can create a party معین. Proposed fix for a later task: resolve parties lazily at confirm (needs a core:domain change).
- IMPORTANT: never push while your previous push's run is still in progress (the workflow cancels in-flight runs). Wait for the run to complete (poll the public API `https://api.github.com/repos/russellzparadox/accounting/actions/runs/<id>` with `until` loops in Bash run_in_background, or short sleeps <= 50s). Max 5 pushes.
