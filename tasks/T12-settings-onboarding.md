# T12 — settings-onboarding
Status: todo
Depends: T08
Read first: CLAUDE.md, handouts/T08-entry-ui.md
Skills to invoke: design:design-system, design:ux-copy, persian-writing:persian-writing

## Goal
- Settings screen. Onboarding with LLM privacy disclosure. DB backup and restore.

## Deliverables (files)
- `feature/settings/src/main/kotlin/.../SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsRepository.kt`
- `feature/settings/src/main/kotlin/.../onboarding/OnboardingScreen.kt`
- `feature/settings/src/main/kotlin/.../backup/BackupManager.kt`
- Tests in `feature/settings/src/test/`

## Acceptance criteria
- [ ] Settings persist: unit (toman/rial), fiscal year start, backend URL, backend token.
- [ ] Backend token stored encrypted. It is the proxy token, not an Anthropic key.
- [ ] Token never logged.
- [ ] Onboarding shows Persian LLM privacy disclosure. User must accept before first entry.
- [ ] Backup writes DB file. Restore validates schema version and rejects corrupt files. Tests for both.
- [ ] Settings repo and restore validation tests pass.

## Out of scope
- Backend changes, cloud sync, account chart editing.

## Verify (commands)
- `./gradlew test assembleDebug`

## Carried over from T10b (main agent, binding)
- D1: Jalali calendar picker dialog (year/month/day, `JalaliDate`, leap-aware, Persian month names) used by the manual journal editor date field AND by the journal date filter; keep the text field as an alternative input.
- D3: amount input/display unit setting (toman default / rial) stored in `SettingsRepository`, honoured by the manual journal editor, quick manual entry sheet, entry draft card and reports (`MoneyFormat`); conversion only via T05 utilities, Long math.
- Add UI tests for both. See `handouts/T10b-manual-journal-editor.md` rows D1, D3.

## Carried over from T11 (main agent, binding)
- A5: reports must use the amount unit from `SettingsRepository` (T11 `ReportsViewModel` currently fixes `UNIT = TOMAN`, see handouts/T11-reports.md); wire it (and keep CSV integer-in-unit behaviour). Add a test that switching the setting changes report amounts and CSV values.

## Run discipline (all of T12, binding)
- Read also `handouts/T10-ledger-ui.md`, `handouts/T10b-manual-journal-editor.md`, `handouts/T11-reports.md`, `handouts/T08a-entry-viewmodel.md`, `handouts/T01d-ci-green.md`, `docs/CI.md`, `docs/DESIGN.md`.
- Settings scope: amount unit (toman/rial), fiscal-year start/current year, backend URL + token (move the config dialog here; keep entry-screen shortcut when NotConfigured), a «تست اتصال» button (calls `/v1/models` or a lightweight authenticated check; Persian result text), theme follows system, about/version (build number from BuildConfig), DB backup (export file via SAF/`ACTION_CREATE_DOCUMENT`, includes DB + schema version) and restore (SAF open, validate SQLite header + schema version + integrity check BEFORE replacing; keep a safety copy; restart/reopen DB), onboarding (first-run only, LLM privacy disclosure in Persian: what text is sent to the backend/LLM, that no API key is in the app, how to use without backend via manual entry). Token storage: use EncryptedSharedPreferences or Android Keystore-backed storage if it compiles in CI, else plain prefs with the limitation recorded in the handout.
- Backup/restore correctness is critical (ledger immutability!): restore must replace the whole DB atomically; add Robolectric tests for backup->modify->restore roundtrip and for rejecting a corrupt/foreign file.
- Push discipline: ONE push per attempt; wait for THAT run to finish before pushing again (poll https://api.github.com/repos/russellzparadox/accounting/actions/runs/<id> with an `until` loop in Bash run_in_background); 'Failure summary' via mcp__github__get_job_logs tail_lines <= 130; max 8 pushes; commit prefix `T12:`. Do NOT end your turn until the last push's run completed AND `handouts/T12-settings-onboarding.md` is written (honest audit of every acceptance + carried-over item MET/PARTIAL/NOT MET with file:line, design critique + a11y notes, manual device checklist, final run id); set Status and README row honestly.
