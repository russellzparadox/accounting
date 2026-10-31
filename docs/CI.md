# CI

## Workflows (`.github/workflows/`)
- `android.yml`: every push (all branches) + PR. JDK 21, `./gradlew --stacktrace -PbuildNumber=<run_number> lint test assembleDebug` (output tee'd to `gradle-build.log`). Uploads `app-debug` APK. On failure uploads `android-reports` (lint, test, build reports) and prints a short error summary as the LAST step of the job.
  - `publish` job (needs `build`, only if build succeeded, only for push to `claude/dreamy-mccarthy-mczm8c`, `main` or `master`): publishes the APK as GitHub Release `build-<run_number>` and moves the rolling prerelease `latest`. Keeps the newest 15 `build-*` releases. Details under "Testing a build on your phone".
- `backend.yml`: push/PR touching `backend/**`. Node 20, `npm ci`, `npm test`, `npm run build`. Skips with a log line if `backend/package.json` is missing. `npm run build` needs a `build` script in `backend/package.json`.
- `release.yml`: tag `v*`. `assembleRelease`, then signs with `apksigner` if signing secrets exist, else uploads unsigned. Creates a GitHub Release with the APK.
- `dependabot.yml`: weekly updates for gradle, npm (`/backend`, inert until it exists), github-actions.

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
