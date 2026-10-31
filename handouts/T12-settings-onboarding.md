# Handout T12 — settings-onboarding
Status: partial (CI green on 1587d63 = run 41201166431; device checks open, see checklist)

## Summary
- Settings screen (unit, fiscal year, backend URL + token, «تست اتصال», backup/restore, theme note, about, privacy text).
- Onboarding gate: nothing is reachable until the Persian privacy disclosure is accepted.
- Token encrypted (Android Keystore AES-GCM); plain token from earlier builds migrated on first read.
- DB backup (SAF) and restore (SAF open, validate header/integrity/schema version/tables before touching the live DB, safety copy, atomic rename, app restart).
- D1 Jalali calendar picker (manual editor date + journal date filter). D3 unit setting (toman/rial) in manual editor, draft card, reports (A5).
- Pushes this round: 04feee2 (status), 7147b56, 2a66068, 1587d63 (+ this docs push). Runs: 41200575707 red (2 unit tests), 41200911417 red (2 unit tests), 41201166431 GREEN.

## Files (new or changed)
- core:persian `JalaliPicker.kt` (JalaliPickerState: leap/clamp, nextMonth/previousMonth, leadingBlanks, yearOptions); `AmountInput.kt` (parseRial/toInputText, toman↔rial, Long only). Tests `JalaliPickerTest`, `AmountInputTest`.
- core:domain `settings/AppSettings.kt` (AppSettings, AppSettingsCodec); `backup/BackupValidator.kt` (BackupFacts → BackupVerdict; order: NOT_SQLITE, TOO_SMALL, CORRUPT, WRONG_SCHEMA_VERSION, MISSING_TABLES). Tests `AppSettingsCodecTest`, `BackupValidatorTest`.
- core:data `config/SettingsRepository.kt` (plain prefs; `settings: StateFlow`, `current`); `config/TokenCipher.kt` (interface + `KeystoreTokenCipher`); `config/ConfigStore.kt` (token in `token_enc`, legacy `token` migrated, `BackendConfig.toString` redacts token); `db/AppDatabase.kt` (`SCHEMA_VERSION = 1`, `FILE_NAME`). Tests `SettingsRepositoryTest`, `ConfigStoreTest` (fake cipher, encryption-at-rest, migration, undecryptable token).
- core:ui `components/JalaliDatePicker.kt` (JalaliDatePickerDialog, JALALI_DAY_TAG_PREFIX), `res/values/strings.xml`.
- feature:ledger: `ManualJournalState.kt` (unit-aware rial, `buildManualUi(unit)`), `ManualJournalViewModel.kt` (4th ctor param `SettingsRepository`), `ManualJournalScreen.kt` (calendar button, unit totals/labels), `DateFilter.kt` (calendar buttons for custom range).
- feature:entry: `EntryViewModel.kt` (`settings` ctor param, `amountUnit` flow, `money()` unit), `ConfirmationCard.kt` (unit totals, line amounts, edit dialog via AmountInput), `ManualEntrySheet.kt` (unit input), `EntryScreen.kt` (settings button on entry top bar, unit threaded).
- feature:reports: `ReportsViewModel.kt` (3rd ctor param; `UNIT` constant removed; re-runs when unit changes).
- feature:settings (new code): `backup/BackupManager.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` (SettingsRoute + SettingsScreen), `BackendPing.kt`, `RestartApp.kt`, `onboarding/OnboardingScreen.kt` (+ `PrivacyPoints`), `res/values/strings.xml`, `build.gradle.kts`.
- app: `AppShell.kt` (onboarding gate, settings route, settings buttons), `MainActivity.kt` (injects SettingsRepository), `di/DataModule.kt` (provideSettings), `build.gradle.kts` (buildConfig = true), `res/values/strings.xml`.
- Tests: feature:settings `BackupRestoreTest` (5), `BackendPingTest` (6), `SettingsScreenTest` (2 incl. onboarding); feature:ledger `ManualJournalViewModelTest` (+2), `ManualJournalScreenTest` (+2); feature:entry `EntryScreenTest` (+1 rial draft), `FakeTokenCipher.kt`; feature:reports `ReportsViewModelTest` (+1 unit switch).

## Public interfaces
- `SettingsRepository(context)`: `settings: StateFlow<AppSettings>`, `current`, `setAmountUnit`, `setFiscalYear(Int?)`, `setOnboardingAccepted`.
- `ConfigStore(context, cipher = KeystoreTokenCipher())`.
- `BackupManager(context, db)`: `export(openOutput: () -> OutputStream?)`, `restore(openInput: () -> InputStream?)` → `BackupResult { Ok | Rejected(BackupReject) | Failed(BackupStage) }`.
- `BackendPing.check(config)` → `ConnectionResult`; `BackendPing.forStatus(code)` (pure).
- `JalaliDatePickerDialog(initial, onConfirm, onDismiss, title)`.
- `SettingsRoute(onClose, onRestartApp, versionName)`; `SettingsScreen(state, onAction, ...)`; `OnboardingScreen(onAccept)`.

## Decisions (+why)
- SettingsRepository lives in core:data, not feature:settings: ledger, entry and reports need the unit and cannot depend on feature:settings (sibling feature).
- Token: Android Keystore AES-GCM through our own 20-line cipher, not EncryptedSharedPreferences: avoids a new dependency whose version could not be checked offline. Robolectric has no AndroidKeyStore, so the cipher is faked in tests; the real cipher is device-only.
- Restore closes the live DB and restarts the process instead of reopening in place: other ViewModels hold flows on the closed DB.
- Backup export checkpoints the WAL (`PRAGMA wal_checkpoint(TRUNCATE)`) before copying the main file, so the file holds all committed rows.
- Restore stages the candidate in the same folder and uses `ATOMIC_MOVE`; a failed swap copies the safety copy back.
- App version/build from `BuildConfig` (buildConfig enabled in app module).
- Unit only affects amounts in the screens listed in T12. Ledger/journal list screens still show toman (see PARTIAL).

## Audit (acceptance + carried-over items)
Key: MET = proven by a CI-run test or by a grep/read I checked; PARTIAL = code present but a part is unproven or not wired; NOT MET = missing.

### Acceptance criteria
| # | Criterion | Result | Evidence |
|---|---|---|---|
| A1 | Settings persist: unit, fiscal year, backend URL, token | PARTIAL | unit: `SettingsRepositoryTest.changesArePublishedAndSurviveANewInstance`; URL+token: `ConfigStoreTest.saveTrimsAndPersistsAcrossInstances`; fiscal year stored (`SettingsRepositoryTest`) but NOT read by reports/ledger (reports still default to current Jalali year, `ReportsViewModel` init) |
| A2 | Token stored encrypted | PARTIAL | `ConfigStoreTest.tokenIsNotStoredInPlainTextAndIsNotPrintedInToString` with fake cipher only; `KeystoreTokenCipher` (TokenCipher.kt) not run under any test (no AndroidKeyStore in Robolectric) |
| A3 | Token never logged | MET (read) | No `Log`/`println` in feature/settings, ConfigStore, ManualJournal*, EntryViewModel (grep). `BackendConfig.toString` redacts (ConfigStore.kt). Backend error text not shown. `KeystoreTokenCipher.decrypt` drops exception text. |
| A4 | Onboarding Persian LLM disclosure; must accept before first entry | MET (code) | `AppShell.kt` gate: `AppShell` shows `OnboardingScreen` until `onboardingAccepted`; `SettingsScreenTest.onboardingShowsDisclosureAndAcceptCallsBack`. Text accuracy: see open issue 2. |
| A5 | Backup writes DB file; restore validates schema + rejects corrupt; tests | MET (tests) / PARTIAL (SAF) | `BackupRestoreTest` (roundtrip backup→modify→restore; foreign text; wrong tables; wrong version; truncated) all green in CI. SAF launchers in `SettingsRoute` not exercised. |
| A6 | Settings repo and restore validation tests pass | MET | CI run 41201166431 success; `SettingsRepositoryTest`, `BackupValidatorTest`, `AppSettingsCodecTest` (JVM, also local scratch run) |

### Carried over from T10b (binding)
| # | Requirement | Result | Evidence |
|---|---|---|---|
| D1 | Jalali calendar picker in manual editor date AND journal date filter; text field kept | MET (manual editor) / PARTIAL (filter) | Editor: `ManualJournalScreenTest.calendarPickerWritesTheChosenJalaliDateIntoTheField`. Filter: `DateFilter.kt` calendar buttons, no UI test. Pure logic: `JalaliPickerTest` (leap, clamp, month steps). |
| D3 | Unit setting honoured by manual editor, quick manual sheet, draft card, reports (MoneyFormat); Long math | PARTIAL | Editor: `ManualJournalScreenTest.rialUnitShowsTotalsInRial`, `ManualJournalViewModelTest.rialUnitTypesRialAndAcceptsAmountThatIsNotWholeToman`, `tomanUnitReadsTypedAmountAsTomanAndStoresRial`. Draft card: `EntryScreenTest.draftShowsTotalsInRialWhenSettingIsRial`. Reports: `ReportsViewModelTest.switchingSettingsToRialChangesReportUnitAndCsvValues`. Quick manual sheet (`ManualEntrySheet`): code only, no test. Ledger/journal list/account ledger/account picker still use `tomanText` (LedgerFormat.kt) — not in T12's list, so the setting does not reach them. |
| UI tests D1/D3 | Add UI tests for both | MET (editor, draft card, reports unit) / PARTIAL (filter, quick sheet) | see above |

### Carried over from T11 (binding)
| # | Requirement | Result | Evidence |
|---|---|---|---|
| A5(T11) | Reports use unit from SettingsRepository; CSV integer-in-unit; test that switching changes report amounts and CSV | MET | `ReportsViewModel.build(..., unit)` uses `settings.current.amountUnit`; test asserts CSV "130" toman vs "1300" rial and `table.unit` change. CI green. |

### Run discipline
| # | Requirement | Result | Evidence |
|---|---|---|---|
| R1 | Pure logic Android-free and tested in scratch project | MET | core:persian + core:domain JVM run in /tmp/t12-verify: 220 tests, 0 failures (before the Android push). |
| R2 | Settings token/URL/unit/year, toman/rial, backend URL move, «تست اتصال» (`/v1/models`), theme follows system, about/build, DB backup/restore, onboarding | PARTIAL | Config dialog NOT removed: entry screen keeps `BackendConfigDialog` (shortcut) AND settings has its own URL/token form. Theme follows system: `Theme.kt` `isSystemInDarkTheme()` (not changed). About: `BuildConfig.VERSION_NAME` (app `buildConfig=true`). «تست اتصال»: `BackendPingTest` against MockWebServer (200/401/500/unreachable, bearer header). |
| R3 | Restore: replace DB atomically; keep safety copy; reopen/restart | MET (code+test) / PARTIAL (device) | `BackupManager.swap` (safety copy, ATOMIC_MOVE, rollback); restart via `restartApp`. Restart path not tested on device. |
| R4 | Design/a11y skills (design:design-system, design:ux-copy, design:design-critique, design:accessibility-review) | NOT MET | Not invoked in this round. Only `persian-writing:persian-writing` was loaded (for strings). `impeccable` and `ui-ux-pro-max` are not in the skill list. Critique and a11y notes below are my own static read. |
| R5 | Push discipline: one push per attempt, wait per run, max 8, commit prefix `T12:` | MET | 4 pushes on branch (04feee2 status, 7147b56, 2a66068, 1587d63) plus this docs push. Each wait done on that run id. No force push. Dependabot branches untouched. |

Count: acceptance 3 MET (A3, A4, A6), 3 PARTIAL (A1, A2, A5 SAF). Carried-over D1 PARTIAL (filter), D3 PARTIAL, T11 A5 MET. Run discipline: R1 MET, R2 PARTIAL, R3 PARTIAL, R4 NOT MET, R5 MET.

## Design critique (static, my read; design skills not run)
- Settings is one long scroll of 8 sections. Acceptable for v1; group «سرور» and «پشتیبان» higher or behind a sub-page later.
- Backend section has two entry points (entry-screen dialog and settings form). Duplicate. Remove the dialog or make it open settings.
- Fiscal year row shows a number but nothing uses it yet. Looks like a control that does nothing. Wire it or hide it.
- Restore is destructive: confirm dialog exists, the copy says what happens. Restart dialog is not dismissable (intentional).
- Onboarding: one screen, six paragraphs. Dense but short per point; no skip. Users with no backend are told «ثبت دستی» in point 6.

## Accessibility notes (static)
- Headings: `heading()` on screen titles and section titles (SettingsScreen, OnboardingScreen).
- Touch targets: `heightIn(min = 48.dp)` on every button, chip, radio row (`OptionRow` uses `selectable` with Role.RadioButton).
- Live regions: connection result and backup message are `LiveRegionMode.Polite`.
- Picker day cells: `selectable(role = Role.Button)`, test tags only; weekday header has full-name contentDescription.
- Gaps: no reduced-motion handling (none needed, no animation); token field has show/hide toggle with text label (good). Picker day cells have no explicit contentDescription beyond their numeral (e.g. "۱۵") — day and month are not announced together; the title line above the grid says the full date.
- Not measured: contrast of onSurfaceVariant text in settings (same tokens as DESIGN §1.1), 200% font scale.

## Persian copy (persian-writing)
- Strings reviewed for ZWNJ (می‌شود, نمی‌شود, می‌کنید, برقرار است) and «» quotes; no em dashes; formal شما; button labels are short imperatives.
- Persian digits in UI text via `PersianDigits`; the version string stays Latin (allowed: version numbers).

## Open issues / risks
1. Fiscal year is stored but not used by any screen (A1 PARTIAL). Reports still default to the current Jalali year.
2. Privacy disclosure accuracy: point 3 says the voice is turned into text by the phone's speech service. Android `SpeechRecognizer` may use a cloud service on some phones. Needs owner review (persian-writing + legal). Point 1 says the text and today's date go to the proxy; this matches `docs/LLM_PROMPTS.md` input shape.
3. Ledger, journal list, account ledger and account picker still show toman only (D3 scope gap).
4. Backend config dialog duplicated (design critique).
5. KeystoreTokenCipher never executed by a test. A keystore failure (key invalidated) makes `decrypt` return null → the token reads blank → user re-enters. Device check.
6. Restore: the safety copy (`accounting.db.before-restore`) stays in app private storage forever. No UI to see or delete it. Could grow to one DB size. Consider a cleanup or a share option later.
7. Export/restore use the SAF `ContentResolver` streams; only the `BackupManager` logic is tested (via lambdas), not the Android document provider.
8. Restore closes the DB and relaunches via `Runtime.exit(0)`. A ViewModel collecting a Room flow in the instant between close and exit could throw. Device check.
9. Quick manual sheet (`ManualEntrySheet`) unit change: no test.

## Manual device checklist (user)
1. Install the APK from run 41201166431 (build 112, release `build-112`, unless a later push superseded it).
2. Fresh install: onboarding shows. Read the six points in Persian. Tap «پذیرفتن و ادامه». Relaunch: onboarding does not show again.
3. Settings (from «دفتر روزنامه» or «حساب‌ها», or «تنظیمات» on «ثبت»): switch to «ریال». Enter a manual journal line with 5000001 (rial). Save. Reports show rial totals; CSV integer equals the rial value.
4. Calendar: manual editor → «انتخاب از تقویم» → pick a day, confirm, date field updates. Journal filter → «مخصوص» → calendar for both dates.
5. Backend: enter URL and token → «تست اتصال» → expect «اتصال برقرار است…» on a working proxy; wrong token → unauthorized text; airplane mode → unreachable text.
6. Backup: «ذخیره پشتیبان» → save to Files/Drive. Add an entry. «بازیابی از پشتیبان» → pick the file → confirm → app restarts → the added entry is gone; ledger matches the backup.
7. Restore a non-backup file (e.g. a PDF renamed .db): expect «این فایل پشتیبان حسابداری نیست…», ledger unchanged.
8. Token: after saving, `adb shell run-as` is not needed; check the Settings token field shows empty and the saved-token hint.
9. Font scale 200%: settings sections and picker grid usable, no clipped buttons.
10. TalkBack: connection result and backup message are announced; picker day cells are tappable.

## Next task must know
- `SettingsRepository` (core:data) is the only unit/onboarding source. Read it, do not add another prefs file.
- `ReportsViewModel.UNIT` is gone; unit comes from settings.
- `AmountInput` is the only toman/rial converter for input. `MoneyFormat` is for display.
- Restore path is `BackupManager.restore` only; any new DB file operation must use the same staged-validate-swap path.
- Device checks above are required before anyone relies on backup/restore.
