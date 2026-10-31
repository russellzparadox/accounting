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
