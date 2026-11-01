# HANDOFF — Persian NL/Voice Accounting App (Android)

Snapshot date: 2026-11-01. Branch: `claude/dreamy-mccarthy-mczm8c` (public repo `russellzparadox/accounting`). Head at time of writing: `9c60863` + the handoff commit. Read this file first, then `CLAUDE.md`, then `tasks/README.md`.

## 1. What this is
Android app (Kotlin, Jetpack Compose, Persian-first, RTL). The user types or speaks a transaction in Persian ("۴ قوطی نوشابه روی هم ۵۰۰٬۰۰۰ تومان خریدم"); the app records a balanced double-entry journal document like an Iranian accountant (chart of accounts, سند حسابداری, تراز آزمایشی, Jalali dates, toman/rial).
Golden rule (CLAUDE.md): **the LLM only extracts structure** (a `TransactionIntent` JSON via a backend proxy). All posting and all money maths are deterministic Kotlin (`PostingEngine`). Money = `Long` rials. Committed documents are immutable (fix = reversal). Dates stored Gregorian, shown Jalali; UI digits Persian.

## 2. Current state (honest)
**Done and CI-verified:** T00–T13a, T09b, T09c (see `tasks/README.md`; every task has `tasks/Txx-*.md` + `handouts/Txx-*.md`). Features in the app: chat entry (typed), draft confirmation card with Dr/Cr, amount cross-check (LLM amount vs local Persian number parser), clarification flow, manual quick entry (no backend), free-form manual journal editor (`MANUAL`), journal/ledger/accounts tabs with reversal, reports (trial balance/journal/ledger → PDF + CSV), settings (toman/rial, fiscal year, backend URL+token, connection test, backup/restore, speech diagnostics), onboarding with privacy notice, voice input (on-device → system recognizer fallback, cloud STT via backend), Keystore-encrypted token.
**Partial:** T14 release-prep — R8 minify enabled and `assembleRelease` green in CI; the minified emulator smoke run is RED; no docs written. Details + exact diagnosis + fix hint in `handouts/T14-release-prep.md`. (Fix hint: keep `kotlinx.coroutines.**` in `app/proguard-qa-rules.pro`.)
**Latest published APK:** GitHub Releases `latest` (build #168 at the time of writing): `https://github.com/russellzparadox/accounting/releases/download/latest/accounting-debug-latest.apk` (debug-signed, minSdk 26). Every green push to the branch publishes `build-<n>` + updates `latest`.

**Never verified (needs a human or keys):**
- Real microphone behaviour on a phone (user's phone showed "recognizer unavailable" before T09b; T09b/T09c fixes untested on device). Checklist in `docs/VOICE.md` and `handouts/T09b-voice-fallback-fix.md`.
- Live LLM accuracy (no API key was available): only mock eval 58/58 (`docs/EVAL_REPORT.md`). Target gate ≥ 90% on type + amount_rial + paid_with.
- Real cloud STT provider accuracy.
- Persian text shaping/RTL in the exported PDF: emulator artifacts exist (`qa-artifacts`, run 41208267404) but nobody has looked at `trial-balance-page-1.png`.
- R8-minified app behaviour at runtime (T14 BLOCKER above).
- Accounting correctness: chart of accounts + 13 posting templates are a proposal; needs an Iranian accountant (`docs/ACCOUNTANT_REVIEW.md`).

## 3. Repo map
```
CLAUDE.md                 project rules + workflow (mandatory reading)
tasks/ handouts/          one task file + one handout per task (inter-agent communication)
docs/                     ARCHITECTURE(skeleton) ACCOUNTING_RULES DECISIONS BACKEND LLM_PROMPTS VOICE DESIGN
                          CI TESTING EVAL_REPORT ACCOUNTANT_REVIEW HANDOFF
app/                      Application, MainActivity, AppShell (nav), Hilt DI (DataModule), androidTest (emulator QA)
core/domain               PURE KOTLIN/JVM: Money, Account, JournalEntry, PostingEngine (13 intent templates + REVERSAL + MANUAL),
                          TransactionIntent, NlPipeline, ManualJournal, report builders, CsvWriter
core/persian              PURE KOTLIN/JVM: JalaliDate, PersianDigits, NumberWordParser, MoneyFormat
core/data                 Android lib: Room DB (immutability triggers), LedgerRepository, PartyRepository, ChartSeeder,
                          ConfigStore, NlParserHttp, speech/* (AndroidSpeechSource, CloudSpeechSource, diagnostics)
core/ui                   theme + Vazirmatn font
feature/entry|ledger|reports|settings   Compose screens + ViewModels (+ Robolectric/Compose tests)
backend/                  Node 22 + TypeScript proxy, vitest (211 tests). /v1/parse (app), OpenAI-compatible
                          /v1/chat/completions, /v1/models, /v1/audio/transcriptions
.github/workflows/        android.yml (build+test+release build+publish), backend.yml, instrumented.yml (emulator, manual+nightly), release.yml (v* tags)
```
Stack/versions: Gradle 8.14.3 wrapper, AGP 8.13.0, Kotlin 2.2.20, KSP, Hilt 2.57.1, Room 2.7.2, Compose BOM 2025.10.00, JDK 21 toolchain, minSdk 26, compile/target 36, applicationId `com.example.accounting` (PLACEHOLDER; must be chosen before first real release; do not rename without owner decision).

## 4. Run it locally (you have a real Android SDK; the cloud sandbox had none)
Prerequisites: JDK 21, Android SDK (platform 36 + build-tools, an emulator or device with API ≥ 26; API 34 x86_64 emulator is what CI uses), Node 22, git.
```bash
git clone https://github.com/russellzparadox/accounting.git && cd accounting
git checkout claude/dreamy-mccarthy-mczm8c
# Android
./gradlew test                      # all JVM + Robolectric tests (pure modules + feature tests)
./gradlew assembleDebug             # app/build/outputs/apk/debug/app-debug.apk
./gradlew lint test assembleDebug   # what CI runs
./gradlew assembleRelease           # minified (R8) release build
./gradlew :app:connectedQaAndroidTest   # emulator smoke + PDF/CSV export against the minified `qa` variant (currently RED, see T14)
# Backend
cd backend && npm ci && npm test && npm run build
```
Run the backend (read `docs/BACKEND.md` and `backend/src/config.ts` for the exact env names; do not guess):
- LLM: `LLM_PROVIDER=anthropic` (+ Anthropic key, `ANTHROPIC_MODEL`, default `claude-haiku-5-5`) or `openai` (+ `OPENAI_BASE_URL`, `OPENAI_API_KEY`, `LLM_MODEL`).
- STT (optional): `STT_BASE_URL`, `STT_API_KEY`, `STT_MODEL` (default `whisper-1`).
- Bearer auth token + rate limit settings: see docs/BACKEND.md. Text is never logged unless `DEBUG_LOG_TEXT=1`.
Then in the app: Settings → server URL + token → «تست اتصال». Without a backend, manual entry still works.
Emulator-based voice testing is unreliable; use a phone for mic tests.

## 5. Decisions already made (do not re-litigate; see docs/DECISIONS.md D1–D13)
Native Kotlin+Compose; LLM only behind a thin backend proxy (no key in app); on-device voice first then cloud via backend; v1 scope = small-business double entry (no VAT/payroll/per-item inventory); money = integer rial; **periodic purchases** (Dr خرید 5100); customers/suppliers auto-created as معین under 1300/2100 **at confirm time**; Jalali fiscal year (1 فروردین), lazily opened per entry date; backend OpenAI-compatible both ways (tool calling + strict JSON schema); cloud STT through `/v1/audio/transcriptions`; one subagent at a time with handouts as the only channel.

## 6. Known gaps / open issues (consolidated from handouts)
- T14 (above): minified emulator run red; docs (README fa+en, SIGNING, PRIVACY, DEPLOY+Dockerfile, RELEASING, THIRD_PARTY_NOTICES) not written; secrets grep not run.
- Bank-to-bank transfer documented but not implemented. Cheques/notes (accounts 1350/2200) have no templates. No VAT/payroll/inventory. Colloquial «پونصد» not parsed (flagged as mismatch, never posted silently).
- Cloud STT duration check is size-only (10 MB cap; app caps recording at 30 s); processing status event is not shown in the input field.
- Several T10/T12 audit sub-points are code-only (no test): see handouts/T10-ledger-ui.md, T12b.
- Pre-restore safety copy lives at `databases/accounting.db.before-restore` (no UI).
- Dependabot PRs (#3–#10 at the time) are open/noisy; AGP/Kotlin/Gradle major bumps are ignored by config.
- No project LICENSE chosen (owner decision). Vazirmatn is OFL (assets/licenses).
- The skills `impeccable`, `ui-ux-pro-max`, `caveman`, `ponytail` the owner named were never available; `design:*` + `persian-writing` skills were used instead (ponytail is unknown: ask).

## 7. Pitfalls learned (save yourself the time)
1. **CI concurrency cancels in-flight runs.** Never push while a verification run is in progress; one push per attempt; poll `https://api.github.com/repos/russellzparadox/accounting/actions/runs?branch=<branch>` (public repo, no auth).
2. Failure details: android workflow's "Failure summary" step (compile errors `e: file:line`, failed tests); emulator workflow emits annotations (`/check-runs/<job_id>/annotations`). Raw log tails are mostly post-job noise.
3. **Room schema race:** release/debug KSP shared `core/data/schemas`; fixed by `kspReleaseKotlin dependsOn kspDebugKotlin` (core/data/build.gradle.kts).
4. `connectedAndroidTest` uninstalls the app afterwards; the emulator job uses `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` and `run-as tar` to pull `files/qa`.
5. Compose tests under Robolectric need a phone-sized window (`@Config(qualifiers = "w411dp-h1200dp")`); `PdfDocument` cannot run under Robolectric (PDF layout is a pure `PdfLayout`, rendering verified only on the emulator).
6. Material3 `Typography(defaultFontFamily=…)` ctor is internal in BOM 2025.10.00: set fonts per text style.
7. `PartyRepository.resolve()` is blocking: never call on the main thread. Drafts must not write to the DB (`DraftPartyResolver`).
8. Subagents often stopped right after pushing, before CI finished and before writing a handout: always verify CI yourself and require the handout.
9. Commit trailers used throughout: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01RnRibkyKPrDTtbAEvgLVF7` (keep or drop as you prefer locally).

## 8. Suggested next steps (priority order)
1. Fix T14 minified smoke run (see handouts/T14-release-prep.md), then write T14 docs and mark T14 done.
2. Device tests by the owner: install `latest`, run the manual checklists (voice, PDF look, backup/restore, rial mode). Collect Settings → «تشخیص گفتار» report if voice fails.
3. Host the backend (Docker/Node) with real LLM + STT keys; run live eval (`backend/eval`, gate ≥ 90%); record in docs/EVAL_REPORT.md.
4. Accountant review of `docs/ACCOUNTANT_REVIEW.md`; adjust chart/templates only with their sign-off (changes must keep the intent JSON schema and backend in sync).
5. Choose applicationId, create keystore, add the 4 signing secrets (names in `.github/workflows/release.yml`), tag `v0.1.0`.
6. Then backlog: bank-to-bank transfer, cheques, VAT, perpetual inventory, more spoken-number variants.

## 9. Kickoff prompt for the next (local) agent
> You are continuing an Android accounting app. Read `docs/HANDOFF.md`, `CLAUDE.md`, `tasks/README.md`, and `handouts/T14-release-prep.md`. You have a real Android SDK/emulator locally, so run builds and tests directly (section 4) instead of the CI-only workflow the earlier cloud agents used. First run `./gradlew test` and `cd backend && npm test` to confirm a green baseline. Then finish T14: fix the minified `qa` instrumented run (keep rules, see handout), prove it with `./gradlew :app:connectedQaAndroidTest`, write the missing docs, run the secrets grep, update the handout and task status, commit with prefix `T14:`. Keep the rules: LLM never does money maths; money is Long rials; committed documents are immutable; do not weaken or skip tests; do not rename the applicationId; do not claim anything unverified (real mic, live LLM, PDF shaping, accountant sign-off) is verified. Work on branch `claude/dreamy-mccarthy-mczm8c` (or a new branch from it) and report honestly what you verified.
