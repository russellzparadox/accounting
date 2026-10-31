# Handout T13 — qa
Status: done. Final android run 41210807374 on db523bf (contains all T13 commits): success. Live LLM eval NOT run (no key). Accountant review pending (user).

## Summary
- Fuzz through the real stack (in-memory Room): 1000 random intents, 13 templates, ~10% reversals, invariants per batch.
- Draft-time party bug fixed: a draft no longer creates a معین. Confirm re-posts with the real resolver and creates it once.
- Spoken amounts: 7 sentences in the pure pipeline and 6 through the EntryViewModel voice path. One colloquial word unsupported (open issue).
- Coverage gaps: picker balance unit, reports default fiscal year, process-death restore of unsent text, reports Compose test.
- Docs: `docs/EVAL_REPORT.md`, `docs/ACCOUNTANT_REVIEW.md`, `docs/TESTING.md`.
- Backend mock eval 58/58 on three paths; `npm test` 175/175.
- Runs: android 41209690178 (8815733) success; android 41210267830 (de7c888) success; android 41210613571 (ff332b5, handout) cancelled because a newer push superseded it; android 41210807374 (db523bf) success. db523bf is the remote head: it is another session's commit (T09b/T09c queue, D13, docs only) on top of T13 and is kept as is.

## Files created/changed
- `core/domain/.../core/domain/PostingEngine.kt`: `PartyAccountResolver.find` (read-only); `UnsavedParty`; `DraftPartyResolver` (never writes; unknown party → placeholder code = last code of the kind's range, recorded in `unsaved`).
- `core/data/.../repo/PartyRepository.kt`: `find` implemented (read-only).
- `core/data/.../repo/LedgerRepository.kt`: `NoPartyResolver.find` stub.
- `feature/entry/.../EntryViewModel.kt`: drafts (pipeline and manual) use `DraftPartyResolver`; `confirm` re-posts `card.intent` with `PartyRepository` and `idGenerator = { card.entry.id }` (committed id = draft id); `input` backed by `SavedStateHandle` (key `entry_input`); `lineLabels`.
- `feature/entry/.../EntryState.kt`: `DraftCard.lineLabels` (default empty).
- `feature/entry/.../ConfirmationCard.kt`: line label from `lineLabels` before chart name.
- `feature/entry/src/main/res/values/strings.xml`: `draft_new_party_account` = «طرف حساب جدید: %1$s».
- `feature/entry/build.gradle.kts` + `gradle/libs.versions.toml`: `lifecycle-viewmodel-savedstate`.
- `feature/reports/build.gradle.kts`: compose ui test deps.
- Tests (new): `core/data/src/test/.../LedgerFuzzTest.kt`; `core/domain/src/test/.../pipeline/DraftPartyTest.kt`; `core/domain/src/test/.../pipeline/SpokenAmountPipelineTest.kt`; `feature/reports/src/test/.../ReportsScreenTest.kt`.
- Tests (changed, additions only): `feature/entry/.../EntryViewModelTest.kt` (+ discard, confirm once/double confirm, 6 spoken sentences via voice path, process death; `viewModel()` takes `saved`); `feature/entry/.../EntryScreenTest.kt` (`savedState` arg); `feature/ledger/.../LedgerScreenTest.kt` (+2 picker balance unit); `feature/reports/.../ReportsViewModelTest.kt` (+1 default fiscal year); `core/domain/.../PostingEngineTest.kt`, `TestFixtures.kt` (`find` in fakes); `feature/reports/.../TrialBalanceFixtureDbTest.kt` (`find` stub).
- Docs (new): `docs/EVAL_REPORT.md`, `docs/ACCOUNTANT_REVIEW.md`, `docs/TESTING.md`.
- No change to `chart_of_accounts.json`, `ACCOUNTING_RULES`, the 13 intent templates, or the intent JSON.

## Public interfaces / contracts
- `interface PartyAccountResolver { resolve(kind, key, displayName): String /* may write */; find(kind, key): String? /* read-only */ }`.
- `class DraftPartyResolver(existing: (PartyKind, String) -> String?)`: `resolve` → existing code, else `placeholderCode(kind)` and record; `unsaved: List<UnsavedParty>`; `companion placeholderCode(kind) = kind.codes.last.toString()`.
- Draft contract: `PipelineResult.Draft.intent` is the only thing needed to commit. To commit: `PostingEngine(chart, parties /*real*/, fiscal, idGenerator).post(intent)` then `LedgerRepository.commit`.
- `EntryViewModel` ctor adds `savedState: SavedStateHandle` (Hilt-injected; tests pass `SavedStateHandle()`).

## Decisions made (+why)
- Placeholder code = last code of the range. One party per template, so only one line can carry it; the UI replaces its name via `lineLabels`. The placeholder is never posted: confirm re-posts.
- Party lookup for drafts reads the table (`find`), so an existing supplier still shows its real code and label.
- Confirm keeps the draft's entry id, so the committed document is the one the user saw and the existing `sodaExampleConfirmsIntoBalancedLedger` assertion stays unchanged.
- `SavedStateHandle` for input (item 4) instead of `rememberSaveable`, because the input lives in the Activity-scoped VM.
- Fuzz uses a fixed seed (20261031) and fails with seed and batch in the message.
- Docs: accountant checklist in Persian, EVAL and TESTING in English (same as CI.md).

## How verified (commands and results)
- Pure JVM (scratch project `/tmp/t13-verify`, copy of `core/domain` + `core/persian`, `/opt/gradle/bin/gradle --no-daemon`): `core:domain` 107 tests, `core:persian` 123 tests, 0 failures. New: DraftPartyTest 6, SpokenAmountPipelineTest 3.
- Backend: `cd backend && npm test`: 9 files, 175 tests passed. Eval mock: "EVAL mock: 58/58", "openai-tools: 58/58", "openai-content: 58/58".
- CI (Google Maven blocked locally, so all Android tests ran only in CI):
  - push 8815733 → android run 41209690178 → success (job 124561876577 "Lint, test, assemble debug" success; publish success).
  - push de7c888 → android run 41210267830 → success.
  - No failed run in this task: the first push was green.
- Instrumented emulator (`instrumented.yml`) was NOT re-run on the T13 head. Last green: run 41208267404 on db62fac (T13a).

## Audit (acceptance criteria, carried items, deliverables 1–7)
Key: MET = proven by a CI-run test, a local scratch test or a file I checked; PARTIAL = present but a part is unproven; NOT MET = missing.

### Acceptance criteria (T13)
| # | Criterion | Result | Evidence |
|---|---|---|---|
| A1 | Compose UI tests cover entry, ledger, reports, settings | MET | `EntryScreenTest`, `LedgerScreenTest`, `ReportsScreenTest` (new, `feature/reports/.../ReportsScreenTest.kt:trialReportTotalsFollowTheUnitChosenInSettings`), `SettingsScreenTest`; run 41210267830 |
| A2 | Instrumented tests pass on emulator | MET (T13a run only) / PARTIAL on this head | run 41208267404 green (T13a). Not re-run on T13 code. |
| A3 | Invariant fuzz: 1000 random entries balanced | MET | `core/data/.../LedgerFuzzTest.kt:thousandRandomEntriesKeepEveryInvariant`; run 41210267830 |
| A4 | Eval: ≥50 sentences run; live only if key | MET (mock), live NOT RUN (no key) | `docs/EVAL_REPORT.md`; backend `npm test` 58/58 ×3 |
| A5 | Accountant-review checklist written | MET (file) / accountant sign-off pending | `docs/ACCOUNTANT_REVIEW.md` |
| A6 | QA fixes → Open issues; QA does not change domain rules | MET | Only the item-3 draft-time fix touches domain (`DraftPartyResolver`, `find`); no rule, template or intent JSON change |

### Deliverables 1–7 (updated scope after T13a)
| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | Fuzz through real stack (PostingEngine + PartyRepository + commit; reversals ~10%; invariants; seed) | MET | `LedgerFuzzTest.kt` (trial balance; ledger end balance = trial row; doc numbers gap-free and unique; no UPDATE/DELETE via triggers; reversal nets to zero; double reversal rejected). Seed in messages. |
| 2 | Voice: ≥5 spoken-amount sentences through voice → pipeline | MET | Pure: `SpokenAmountPipelineTest.kt` 7 sentences (`نیم میلیون`, `دو و نیم میلیون`, `۲ و نیم`, `تومن`, `سیصد و پنجاه هزار`, `یک میلیون و نیم`, `ریال`). VM voice path: `EntryViewModelTest.spokenAmountSentencesReachBalancedDraftsThroughTheVoicePath` (6). Unsupported `پونصد`: `colloquialWordTheParserDoesNotKnowIsFlaggedNotPostedSilently` (flagged, not posted). |
| 3 | Draft-time party bug: discard → no party; confirm → created once; double confirm → one doc; 14 templates and intent JSON unchanged | MET for the credit pipeline path; manual credit path changed but not separately tested (see Open issues) | Pure: `DraftPartyTest.kt` (6). VM: `EntryViewModelTest.discardedCreditDraftCreatesNoParty`, `confirmedCreditDraftCreatesThePartyOnceAndDoubleConfirmCommitsOnce`. Templates/JSON unchanged. |
| 4a | Coverage: account-picker balance unit | MET | `LedgerScreenTest.accountPickerBalanceShowsInRialWhenRialIsChosen`, `...InTomanWhenTomanIsChosen` |
| 4b | Coverage: reports default fiscal year from settings | MET | `ReportsViewModelTest.reportsDefaultToTheFiscalYearChosenInSettings` (1404 chosen, then 1405 when cleared) |
| 4c | Coverage: process-death restore of unsent entry text | MET (unit) / PARTIAL (device) | `EntryViewModelTest.unsentTextSurvivesProcessDeath` copies the saved state into a new VM. Real system process death not exercised. |
| 5 | Eval report written; mock run; live noted | MET | `docs/EVAL_REPORT.md` |
| 6 | `docs/ACCOUNTANT_REVIEW.md` (chart, 14 templates with Dr/Cr and sample sentences, D7, D8, FY/numbering, rounding, immutability/reversal, open questions) | MET | file; D7/D8 are recorded user decisions and are cited as such; the open list links to `ACCOUNTING_RULES.md §10` |
| 7 | `docs/TESTING.md` (layers, how to run, known gaps) | MET | file |

### Carried over (binding)
| Source | Requirement | Result | Evidence |
|---|---|---|---|
| T09 | ≥5 spoken-amount sentences through voice → pipeline | MET | item 2 |
| T11 A3 | Emulator job, PDF render, artifacts | MET (T13a) / human look pending | `instrumented.yml`; run 41208267404; Persian shaping not machine-verified |
| T10 | Process-death restore for unsent entry text | MET (unit) | item 4c |

### Run discipline
| Requirement | Result |
|---|---|
| ONE push per attempt; wait for that android run | MET: pushes 8815733, de7c888 (each awaited, both success) and ff332b5 (run cancelled by the superseding push db523bf from another session, not by a failure). The final head db523bf is awaited: run 41210807374 success. |
| Max 8 pushes | MET: 3 pushes by this task (8815733, de7c888, ff332b5) plus this docs note; the other session pushed db523bf. |
| Commit prefix `T13:` and trailers | MET |
| No force-push, only `claude/dreamy-mccarthy-mczm8c`, dependabot untouched | MET |
| Never weaken/skip tests | MET: no `@Ignore`/removed assertion. One assertion kept by preserving the draft id instead of changing it. |

Counts: acceptance 5 MET + 1 MET/PARTIAL (A2); deliverables 1, 2, 4a, 4b, 5, 6, 7 MET; 3 MET (pipeline) with a manual-path note; 4c MET (unit)/PARTIAL (device). No NOT MET.

## Open issues / risks
1. [MED] Live LLM eval NOT run (no key). No live gate script exists; the ≥90% gate on type/amount_rial/paid_with is documented but not computed. `docs/EVAL_REPORT.md`.
2. [MED] Bank-to-bank transfer (`docs/ACCOUNTING_RULES.md` §8, «بانک به بانک») is documented but not implemented in `PostingEngine` (only cash↔bank). Accountant decision.
3. [MED] PDF Persian shaping is not machine-verified. A person must look at `qa-artifacts/qa/trial-balance-page-1.png` (T13a).
4. [LOW] «پونصد» (colloquial پانصد) is not in `NumberWordParser`. The pipeline flags it as AmountMismatch (not a silent post). Not hacked into the domain.
5. [LOW] Manual quick-sheet credit draft (`EntryAction.SubmitManual`, PURCHASE_CREDIT/SALE_CREDIT with a new party) now uses `DraftPartyResolver`, and its confirm re-posts. No dedicated test for that path.
6. [LOW] Instrumented emulator run not repeated on the T13 head (last green on db62fac). The changes do not touch the androidTest files, but this is unverified.
7. [LOW] Task text says "14 templates". The code has 13 intent templates, plus `REVERSAL` and `MANUAL` (15 ids). Documented in `docs/ACCOUNTANT_REVIEW.md` §3. No change made.
8. [LOW] Real process death and real microphone are not tested on a device. `docs/TESTING.md` known gaps.
9. [LOW] `KeystoreTokenCipher` and the SAF backup path are still not run under any test (T12 open issues).
10. [INFO] Placeholder code for an unsaved party is the last code of the kind's range. One party per template means no collision inside one draft; a real party on code 1349/2199 would only be shown with the same label if it were on the same draft line, which cannot happen.

## Next task must know
- Drafts are read-only for parties. Anything that commits a draft must re-post the intent with `PartyRepository` (see `EntryViewModel.confirm`).
- Committed document id = draft id (`idGenerator = { card.entry.id }`). Keep it, the existing VM test depends on it.
- Fuzz seed is `20261031`. Failure messages carry seed and batch.
- Live eval: see `docs/EVAL_REPORT.md`. Do not read the mock 58/58 as model accuracy.
- Accountant review: `docs/ACCOUNTANT_REVIEW.md` §4 (open decisions) and `docs/ACCOUNTING_RULES.md` §10. Bank-to-bank is still unimplemented.
- Next: T14 (release prep). Before release, a person must look at the PDF page PNG, and run the device checklists in `docs/VOICE.md` and `docs/CI.md`.
