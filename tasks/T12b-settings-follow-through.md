# T12b — Make settings real everywhere
Status: in_progress
Depends: T12
Read first: CLAUDE.md, handouts/T12-settings-onboarding.md (audit table + open issues), handouts/T10-ledger-ui.md, handouts/T10b-manual-journal-editor.md, handouts/T11-reports.md, handouts/T08-entry-ui.md, handouts/T08a-entry-viewmodel.md, handouts/T04-room-persistence.md, handouts/T01d-ci-green.md, docs/CI.md
Skills to invoke: persian-writing:persian-writing, design:ux-copy (+ design:accessibility-review at code level)

## Goal
Close the T12 gaps that make the app inconsistent. No new features.

## Deliverables / acceptance criteria
1. AMOUNT UNIT EVERYWHERE: the toman/rial setting (SettingsRepository) must drive ALL money display and input: journal list + document detail, account ledger, account list balances, entry DraftCard, quick manual sheet, manual journal editor, ledger/journal search by amount, reports (already). Provide ONE mechanism (e.g. `LocalAmountUnit` CompositionLocal or an injected formatter fed from SettingsRepository at AppShell) instead of per-screen copies; conversions only via core:persian (`MoneyFormat`, `AmountUnit`), Long math, no Float/Double. Tests: flipping the setting changes displayed amounts on at least journal, ledger, draft card and quick manual sheet; entering 500 in rial mode posts 500 rial, in toman mode 5000 rial.
2. FISCAL YEAR WIRING: the fiscal year chosen in Settings is the default selection for journal, accounts/ledger, reports and the year used by `ensureFiscalYear` at app start. Committing a document dated in a Jalali year whose fiscal year is not yet open must not fail with a confusing error: lazily `ensureFiscalYear(JalaliDate.from(date).year)` at commit time (core:data or the VM layer), with a Robolectric test (entry dated in the previous Jalali year commits and gets doc number 1 of THAT year; numbering per year stays gap-free).
3. PRIVACY TEXT ACCURACY: onboarding/settings privacy copy must not claim voice recognition is always on-device. Say (Persian, ZWNJ correct): speech recognition is performed by the phone's speech service; on some phones it may use the internet; the app only receives the recognized text; text sent to the server is only what you submit for analysis. Keep the three main points accurate: what goes to the backend/LLM, no API key in the app, manual entry works without backend.
4. SINGLE BACKEND CONFIG UI: remove the duplicate config dialog in feature:entry; when backend is NotConfigured the entry screen's action navigates to Settings (backend section). One source of truth (ConfigStore). Update/adjust tests accordingly (do not delete coverage: port it).
5. Document in the handout (no UI): where the pre-restore safety copy lives and how a user could recover it (adb/file manager path) — add a short «بازیابی از نسخه ایمنی» note in Settings help text only if trivial.

## Out of scope
New screens, new features, emulator tests (T13).

## Push discipline (binding)
- Google Maven is blocked locally; Android code compiles only in CI: review carefully before each push. ONE push per attempt; wait for THAT run to finish before pushing again (poll https://api.github.com/repos/russellzparadox/accounting/actions/runs/<id> with an `until` loop in Bash run_in_background); 'Failure summary' via mcp__github__get_job_logs tail_lines <= 130. Max 7 pushes, commit prefix `T12b:`, only branch claude/dreamy-mccarthy-mczm8c, no force push.
- Never weaken/skip tests. Do NOT end your turn until the last push's CI run completed AND `handouts/T12b-settings-follow-through.md` is written (honest audit of items 1-5 MET/PARTIAL/NOT MET with file:line, manual device checklist for the user, final run id); set Status and the README row honestly.

## FIX ROUND 1 (main agent)
- Code pushed in d8d61fa is RED: CI run 41202841940 (run 117). Previous subagent stopped without a handout.
- Step 1: read the 'Failure summary' step (mcp__github__get_job_logs on the run's job, tail_lines 130), fix root causes (compile errors / failing tests). Never weaken/skip tests; if you must change a test expectation, justify it in the handout (e.g. toman vs rial conversions).
- Step 2 (after green): audit items 1-5 of this task against the code (MET / PARTIAL / NOT MET with file:line), fix cheap PARTIALs (one push per attempt, wait for the run each time), then write `handouts/T12b-settings-follow-through.md` (audit table, safety-copy location note, manual device checklist for the user: switch to rial, enter 500, check journal/ledger/draft/reports; entry dated in previous Jalali year; privacy text; entry screen -> Settings when backend unset; final run id). Set Status done only if green and all items MET; update the T12b row in tasks/README.md. Max 6 pushes. Do NOT end your turn before the final run completes and docs are updated. Include this uncommitted task-file edit in your first commit.
