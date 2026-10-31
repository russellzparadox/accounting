# Testing

What each test layer proves, how to run it, and what it does not prove.

## Layers

| Layer | Where | Proves | Runs |
|---|---|---|---|
| Pure JVM | `core/persian`, `core/domain` tests | Money math, Persian number and date rules, posting templates, pipeline rules (draft, cross-check, confirm reasons), draft-time party rule, spoken amounts through the local parser | `./gradlew :core:persian:test :core:domain:test`, CI `android` job |
| Robolectric, in-memory Room | `core/data`, `feature/*` unit tests | Room schema and triggers, repositories, ledger invariants (`LedgerFuzzTest`), ViewModels against a real in-memory DB, party rows created only on confirm, process-death restore of unsent text | `./gradlew test`, CI `android` job |
| Robolectric Compose | `feature/*/src/test` screen tests | Screens render and dispatch the right actions (entry, ledger, reports, settings) | `./gradlew test`, CI `android` job |
| Emulator, real app | `app/src/androidTest` (`RealAppFlowTest`, `ReportExportTest`) | Real `MainActivity`, Hilt, navigation, real Room file; PDF and CSV written and rendered | `.github/workflows/instrumented.yml`, manual or nightly |
| Backend unit and contract | `backend/test` | Config, auth, rate limit, LLM transport (fake), OpenAI-compatible inbound and outbound, schema validation | `cd backend && npm test`, CI `backend` job |
| Backend mock eval | `backend/eval`, `backend/test/eval.test.ts` | 58 sentences through the proxy plumbing with a fixture LLM | `cd backend && npm test` |

## How to run

- Pure JVM quickly, without Android: copy `core/persian` and `core/domain` into a scratch Gradle project with the same version catalog and run `:core:domain:test :core:persian:test` (used in T13 when Google Maven was blocked).
- Android unit tests: `./gradlew test` (needs Google Maven).
- Emulator: see `docs/CI.md`, section "Emulator QA".
- Backend: `cd backend && npm test`. Live LLM: see `docs/EVAL_REPORT.md`.

## Invariants checked by `LedgerFuzzTest`
- 1000 random valid intents over all 13 posting templates, plus about 10% random reversals, with a fixed seed. Checked after every batch of 100:
  - trial balance debit total equals credit total;
  - the running balance at the end of each account equals its trial balance row, and each running-balance step is consistent;
  - document numbers in the fiscal year are unique and gap-free;
  - each reversal nets its original to zero per account;
  - `UPDATE` and `DELETE` on `journal_entry` and `journal_line` are rejected by the triggers.
- A second reversal of the same document is rejected.
- Failure messages include the seed and the batch, so a failing run can be replayed.

## Known gaps
- Real microphone and speech recognition are not tested. The recognizer is faked (`FakeSpeechSource`). The user must run the manual device checklist in `docs/VOICE.md`.
- Live LLM accuracy is not measured. The mock eval checks only plumbing. No live run was done (no key).
- Persian glyph shaping in the exported PDF is not checked by machine. `PdfRenderer` checks only that each page has ink. A person must look at `qa-artifacts/qa/trial-balance-page-1.png` from the emulator run.
- Keystore token encryption (`KeystoreTokenCipher`) is not run under any test. Robolectric has no AndroidKeyStore, so the tests use a fake cipher.
- The Android document provider (SAF) used by backup and restore is not exercised. The backup logic is tested through lambdas.
- Compose tests run on Robolectric only. Layout at 200% font scale and TalkBack behaviour are not tested.
- Spoken colloquial numbers that the parser does not know (for example «پونصد») are not understood. The pipeline flags them as an amount mismatch, so they are not posted silently. They are not corrected.
