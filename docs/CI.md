# CI

## Workflows (`.github/workflows/`)
- `android.yml`: every push (all branches) + PR. JDK 21, `./gradlew --stacktrace -PbuildNumber=<run_number> lint test assembleDebug` (output tee'd to `gradle-build.log`). Uploads `app-debug` APK. On failure uploads `android-reports` (lint, test, build reports) and prints a short error summary as the LAST step of the job.
  - `publish` job (needs `build`, only if build succeeded, only for push to `claude/dreamy-mccarthy-mczm8c`, `main` or `master`): publishes the APK as GitHub Release `build-<run_number>` and moves the rolling prerelease `latest`. Keeps the newest 15 `build-*` releases. Details under "Testing a build on your phone".
- `backend.yml`: push/PR touching `backend/**`. Node 20, `npm ci`, `npm test`, `npm run build`. Skips with a log line if `backend/package.json` is missing. `npm run build` needs a `build` script in `backend/package.json`.
- `release.yml`: tag `v*`. `assembleRelease`, then signs with `apksigner` if signing secrets exist, else uploads unsigned. Creates a GitHub Release with the APK.
- `dependabot.yml`: weekly updates for gradle, npm (`/backend`, inert until it exists), github-actions.
- `instrumented.yml`: emulator QA. `workflow_dispatch` (Actions tab, Run workflow) + nightly schedule (default branch only). Never on push. Own concurrency group. Not required for releases.

## Emulator QA (`instrumented.yml`)
- How to run: Actions tab, `instrumented`, Run workflow, pick the branch. Takes about 15-25 min (emulator boot, Gradle, tests).
- Emulator: API 34, x86_64, google_apis, pixel_6, KVM enabled by a udev rule, animations disabled.
- Runs `./gradlew :app:connectedDebugAndroidTest`, then pulls the app's internal `files/qa/` folder (`run-as` tar stream, see `.github/scripts/pull-qa.sh`). The external `Android/data` folder is not used: `adb pull` cannot read it on API 30+.
- Artifacts (on the run page, Artifacts):
  - `qa-artifacts`: `qa/*.png` = screenshots `01-entry-committed` ... `10-manual-editor`. `qa/trial-balance.pdf` (the real PdfExporter output). `qa/trial-balance-page-N.png` (each PDF page rendered by PdfRenderer). `qa/trial-balance.csv` (BOM + headers).
  - `androidtest-reports`: JUnit XML and HTML from `connectedDebugAndroidTest`.
- What each test proves:
  - `RealAppFlowTest` (one method, real MainActivity, real Room file, Hilt, navigation): onboarding accepted through the UI, a 500,000 toman cash purchase through the quick manual sheet, the journal row with Persian digits, the document detail totals, the cash account ledger, reversal, trial balance preview gross totals (both sides 1,000,000 toman), unit switch to rial re-renders journal amounts, manual journal editor opens.
  - `ReportExportTest`: the PDF is written by the real `PdfExporter`, has a `%PDF-` header and at least one page, and each page renders non-blank under `PdfRenderer` (not a proof of Persian shaping; see the audit in the T13a handout). The CSV starts with a UTF-8 BOM and has the Persian column headers.
- Constraints:
  - `RealAppFlowTest` must stay a single test method. The app's Room singleton lives for the whole process, so a second activity test would see the first one's data. `CleanAppStateRule` deletes the Room file, the `app_settings` prefs and the export cache before the Activity starts.
  - `ReportExportTest` uses an in-memory database and never touches the app's file.
  - Test tags on the quick manual sheet: `MANUAL_AMOUNT_FIELD_TAG`, `MANUAL_SUBMIT_TAG` (feature/entry `ManualEntrySheet.kt`). They attach no behaviour.
- Local runs: `./gradlew :app:connectedDebugAndroidTest` needs a device or emulator. Google Maven is blocked in the cloud session, so the CI run is the only place these tests have compiled and run.
- Reading a red emulator run: the `Check that QA screenshots...` step tells you whether any screenshots were pulled. The `Run instrumented tests` step ends with Gradle's status.
  - The job log tail is often only post-job cleanup, so the `Failure summary` step also emits each failed androidTest case and each Gradle failure line as a `::error` annotation. Read them with the public API: `GET /repos/russellzparadox/accounting/actions/jobs/<job_id>` gives `check_run_url`; then `GET <check_run_url>/annotations`.
  - `.github/scripts/pull-qa.sh` pulls `files/qa/` with `adb exec-out run-as com.example.accounting tar c -C files qa` into `qa-artifacts/qa/`. When no PNG arrives it falls back to a per-file `run-as cat`. It logs the `run-as ls` listing for diagnosis. The gate step `Check that QA screenshots...` fails unless all 10 screenshots, the PDF, at least one PDF page PNG and the CSV are present.
  - Failures are in `androidtest-reports` (`app/build/outputs/androidTest-results`), and `qa-artifacts/gradle-instrumented.log` and `logcat.txt` (filtered) / `logcat-full.txt` hold the Gradle and device output.

## Secrets (optional, for signed release)
- `KEYSTORE_BASE64` (base64 of the `.jks`/`.keystore`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Set under repo Settings, Secrets and variables, Actions. If any is missing the release is unsigned (`app-release-unsigned.apk`).
- Signing happens after assembly because `app/build.gradle.kts` has no `signingConfig`.

## Testing a build on your phone
- Stable link (always the newest build from the dev branch): `https://github.com/russellzparadox/accounting/releases/download/latest/accounting-debug-latest.apk`
- Per build: Releases page `https://github.com/russellzparadox/accounting/releases`, pick `build-<n>`, asset `accounting-debug-<n>-<shortsha>.apk`. Direct URL: `https://github.com/russellzparadox/accounting/releases/download/build-<n>/accounting-debug-<n>-<shortsha>.apk`.
- Each release body has: commit, CI run link, SHA-256, min Android (8.0 / API 26), commits since the previous build.
- Install: open the link in the phone browser, download, open the file. Allow the browser to install unknown apps when Android asks (Settings, Apps, Special access, Install unknown apps). Android may also show Play Protect warnings for the debug build; this is expected.
- Updates install over the previous debug build (same debug key). If Android says the package conflicts with an installed app, uninstall the old one first.
- Version: Settings, Apps, accounting, shows `0.1.<n>` where `<n>` is the CI run number (versionCode = `<n>`). Local builds show `0.1.0`.
- Debug build, signed with the debug key, for testing only.
- Publishing rules: only a successful `android` build of a push to `claude/dreamy-mccarthy-mczm8c`, `main` or `master` publishes. PRs, forks, dependabot and tag pushes never publish. A superseded run is cancelled in its build job and then publishes nothing. A publish that started is not cancelled by a newer push.
- Retention: the newest 15 `build-*` releases and tags are kept. `latest` and `v*` releases are never deleted.

## Download an APK
- Branch/PR builds: Actions tab, pick the run, download `app-debug` under Artifacts.
- Releases: Releases page, download `app-release.apk` (or `app-release-unsigned.apk`).

## First run = real T01 verification
- T01 never built locally (Google Maven blocked). The first `android.yml` run is the first real `assembleDebug`/`test`.
- Start of the log prints the version catalog and wrapper version.
- If dependency resolution fails ("Plugin ... was not found", "Could not resolve"): fix versions in `gradle/libs.versions.toml`. Most likely suspects: `ksp = 2.2.20-2.0.4` (must match Kotlin 2.2.20), `hilt = 2.57.1`, `composeBom`, `agp = 8.13.0`. Check the artifact `android-reports` and the log for the failing coordinate.
- If `jvmToolchain(21)` fails: JDK mismatch. The workflow uses Temurin 21, so check the module `kotlin { jvmToolchain }` lines.
- If lint fails: lint report is in `android-reports`, `**/build/reports/lint-results-*.html`.

## How to read a red run (cheap)
- Last step of the `build` job, `Failure summary (short, read this first)`: lists `e:` compile errors, `What went wrong` blocks and `FAILURE` lines (max 80 lines). Usually the root cause is here.
- Full output: the `Lint, test, assemble debug` step (also in `gradle-build.log` inside the runner, not uploaded).
- Kotlin `e: ...` line = source error. `Execution failed for task ':x:y'` = which module/task failed.
- Lint/unit-test reports: `android-reports` artifact (failure only).
- Tool tip: read logs with a small tail (`tail_lines` <= 80); full job logs are large.
