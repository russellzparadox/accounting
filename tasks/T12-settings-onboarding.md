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
