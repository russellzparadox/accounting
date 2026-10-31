# Handout T01c — github-actions
Status: partial

## Summary
- 4 workflows + dependabot: `android.yml` (lint, test, assembleDebug), `backend.yml` (skips, no `backend/` yet), `release.yml` (tag `v*`, assembleRelease, optional signing).
- Only Gradle tasks: `lint test assembleDebug assembleRelease`. Not run here (no Google Maven). First CI run is the T01 verification.
- YAML parses (PyYAML). `docs/CI.md` documents secrets, downloads, and first-run triage.

## Files created/changed
- `.github/workflows/android.yml`, `backend.yml`, `release.yml`
- `.github/dependabot.yml`
- `docs/CI.md`
- `handouts/T01c-github-actions.md`
- `tasks/T01c-github-actions.md` (Status line only)
- `gradlew` mode unchanged (already `-rwxr-xr-x`); workflow also runs `chmod +x gradlew`.

## Public interfaces / contracts
- Artifacts: `app-debug` (debug APK), `android-reports` (failure only), `app-release` (release APK, signed or `app-release-unsigned.apk`).
- Release: GitHub Release on tag `v*` with `dist/*.apk`.
- Secrets read: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (all four required to sign).
- Concurrency group: `android-<pr-or-ref>`, `backend-<pr-or-ref>`.

## Decisions made (+why)
- JDK 21 (Temurin), not 17 as T01c says: `jvmToolchain(21)` in every module.
- Signing done with `zipalign` + `apksigner` from runner `ANDROID_HOME` build-tools: `app/build.gradle.kts` has no `signingConfig` and this task may not touch it.
- `gh release create` instead of a third-party release action: fewer third-party actions, `GITHUB_TOKEN` with `contents: write` only in release job.
- Diagnostics step first (catalog, wrapper, settings) so a red first run shows versions in the log.
- Backend steps gated with `hashFiles('backend/package.json')`; a skip-message step runs when it is missing. Step `working-directory` overridden to `.` for that step so it works without `backend/`.
- Actions pinned to majors: checkout@v4, setup-java@v4, setup-node@v4, upload-artifact@v4, gradle/actions/setup-gradle@v4.

## How verified (commands + result)
- `python3 -c "import yaml ...; yaml.safe_load" .github/workflows/*.yml .github/dependabot.yml` -> ok.
- Gradle tasks checked against `app/build.gradle.kts` (AGP app module, `jvmToolchain(21)`, debug/release buildTypes): `assembleDebug`/`assembleRelease` exist. `lint` and `test` are standard AGP/Gradle lifecycle tasks; not executed.
- Not run: any workflow (no GH run from here), any Gradle build (blocked).

## Open issues / risks
- First `android.yml` run is the real T01 check. Likely failures: `ksp 2.2.20-2.0.4`, `hilt 2.57.1`, `composeBom`, `agp 8.13.0` versions in `gradle/libs.versions.toml` (unverified). Triage steps in `docs/CI.md`.
- `lint` may fail on the scaffold (not yet verified). Lint report in `android-reports`.
- `npm run build` requires a `build` script in `backend/package.json` once backend exists.
- Dependabot npm entry for `/backend` may log an error until `backend/package.json` exists.
- Release signing path is untested (no keystore). Unsigned path is the default.
- Package name `com.example.accounting` still placeholder (T14).

## Next task must know
- Workflows only use `./gradlew lint test assembleDebug` (CI) and `assembleRelease` (tag). Add new tasks only if they exist.
- Debug APK: `app/build/outputs/apk/debug/*.apk`. Fix CI failures by editing `gradle/libs.versions.toml`, not workflows.
- Backend CI activates when `backend/package.json` lands; it needs `test` and `build` scripts.
