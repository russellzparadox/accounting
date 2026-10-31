# Handout T08a — entry-viewmodel
Status: done

## Summary
- Entry state layer wired: DI, ConfigStore, EntryViewModel (no Compose UI).
- CI fix round 1 done: android run 41187478229 (head b3c879d) success (build, lint, test, assembleDebug, publish).
- Root causes fixed: Compose plugin on `feature:entry` with no runtime; `RoomDatabase` not on classpath of `:app` and `feature:entry` tests.

## Files created/changed
- `feature/entry/build.gradle.kts`: removed `alias(libs.plugins.kotlin.compose)` and `buildFeatures.compose`. Module has no Compose code.
- `core/data/build.gradle.kts`: `room-runtime` now `api` (was `implementation`). `AppDatabase` extends `RoomDatabase`.
- `tasks/T08a-entry-viewmodel.md` (fix-round notes, Status done).
- `handouts/T08a-entry-viewmodel.md` (this file).
- Earlier T08a code (a96c19d): `feature/entry/src/main/kotlin/.../EntryState.kt`, `EntryViewModel.kt`, `core/data/.../config/ConfigStore.kt`, `core/data/.../nlp/NlParserProvider.kt`, `app/.../di/DataModule.kt`.

## Public interfaces / contracts
- `EntryViewModel @Inject constructor(@ApplicationContext Context, LedgerRepository, ChartOfAccounts, PartyAccountResolver, ChartSeeder, ConfigStore, NlParserProvider, java.time.Clock)`.
  - `val state: StateFlow<EntryUiState>`
  - `fun onAction(action: EntryAction)` (only UI entry point; work in viewModelScope).
- `sealed interface EntryAction`: `Submit(text)`, `AnswerClarification(answer)`, `Retry(errorId)`, `Confirm(draftId)`, `Discard(draftId)`, `UseDetectedAmount(draftId)`, `UseModelAmount(draftId)`, `Undo(docId)`, `SubmitManual(templateId, amountRial: Long, paidWith?, partyName?, category?, memo?)`.
- `data class EntryUiState(items: List<ChatItem>, busy: Boolean, pendingClarification: String?)`.
- `sealed interface ChatItem { id }`: `UserText(id, text)`, `DraftCard(id, entry: JournalEntry, intent, reasons: List<ConfirmReason>, reasonTexts, modelRial: Long, committing: Boolean, source: PipelineResult.Draft?)`, `Clarification(id, question)`, `Error(id, kind: EntryErrorKind, title, message, retryText: String?)` with `actions`, `Committed(id, doc: CommittedDoc, reversed)`, `Reversed(id, originalDocId, doc)`.
- `enum EntryErrorKind`: OFFLINE, UNAUTHORIZED, RATE_LIMITED, SERVER, BAD_RESPONSE, EMPTY_INPUT, AMOUNT_MISSING, DATE_OUTSIDE_FISCAL_YEAR, NOT_CONFIGURED, MANUAL_INVALID, LEDGER_FAILED.
- `enum ErrorAction { RETRY, CONFIGURE_SERVER, MANUAL_ENTRY }`; `EntryErrorKind.actions()` maps kind to buttons.
- `fun combineClarification(original, answer): String` = trimmed non-blank parts joined by one space.
- `data class BackendConfig(baseUrl: String, token: String)`; `ConfigStore(context)`: `save(config)`, read via `Flow<BackendConfig>`. Blank default = NotConfigured. Plain SharedPreferences (EncryptedSharedPreferences = later follow-up).
- `interface NlParserProvider { fun parserFor(config: BackendConfig): NlParser? }`; impl `NlParserHttpProvider` (rebuilds on config change).
- `app/.../di/DataModule.kt` bindings: `AppDatabase.file(context)`, `ChartOfAccounts.standard()`, `Clock.system(ZoneId.of("Asia/Tehran"))`, `LedgerRepository(db, chart)`, `ChartSeeder(db, chart)`, `PartyRepository(db)` as `PartyAccountResolver`, `ConfigStore`, `NlParserHttpProvider` as `NlParserProvider`.

## Decisions made (+why)
- Clarification: `AnswerClarification` re-runs `combineClarification(originalText, answer)`. Keeps the single pipeline input path.
- Confirm: `claimLock` + `committing` flag on DraftCard. Second confirm of same draft returns early. Commit runs on Dispatchers.IO; on error it releases claim and shows LEDGER_FAILED. Cancel releases and rethrows.
- Undo = `LedgerRepository` reversal doc (`ChatItem.Reversed`). Never delete/update. Undo of an already reversed doc is a no-op.
- Party lookup is blocking, so it runs on IO.
- Manual path (`SubmitManual`) goes straight to PostingEngine, no backend needed.
- Test drop-in: Compose not needed; removing it avoids `IncompatibleComposeRuntimeVersionException`. T08 must re-add `kotlin.compose` + `buildFeatures.compose` in `feature/entry` when it adds screens.

## How verified (commands + result)
- Run 41186975674 (e1e7141): fail in `:feature:entry:compileDebugKotlin` (Compose runtime missing).
- Run 41187270036 (8b4d603): compile passed; fail in `:app:compileDebugJavaWithJavac` (`cannot access RoomDatabase` in generated `DataModule_ProvideDatabaseFactory`) and `:feature:entry:compileDebugUnitTestKotlin` (same cause).
- Run 41187478229 (b3c879d): build job success (`Lint, test, assemble debug` success, `Upload debug APK` success), publish success. Final verdict.
- Run 41187574785 on same sha is a `dynamic` dependency-graph submission, not the android workflow. Ignored.
- Local Gradle not run (Google Maven blocked in sandbox). CI is the only verifier.

## Open issues / risks
- Known gap (not fixed, by design): party find-or-create happens at draft time inside `PostingEngine.post` (called by `NlPipeline`). An unconfirmed credit draft can create a party معین. Fix later: resolve parties lazily at confirm (needs core:domain change).
- `lifecycle-viewmodel-ktx` 2.9.4 resolved in CI (no coordinate failure seen).
- `javax.inject.Inject` and Hilt/KSP wiring in `feature:entry` compiled in CI.
- Node 20 deprecation warnings from actions (checkout/setup-java/upload-artifact/setup-gradle). Out of scope.

## Next task must know
- `feature:entry` has no Compose. Add `kotlin.compose` + `buildFeatures.compose` + compose deps together when adding screens (T08).
- Modules exposing types that extend Room classes must use `api` for room-runtime (`core:data` now does).
- UI must only use `EntryViewModel.onAction` and `state`. Never `LedgerRepository` directly.
- To debug red CI: read the `Failure summary` step (last step) and the `Lint, test, assemble debug` log tail. See `docs/CI.md`.
