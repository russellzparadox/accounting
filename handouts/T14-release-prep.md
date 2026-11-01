# Handout T14 — release-prep
Status: PARTIAL (stopped by the user mid-task). Written by the main agent from repo + CI state; the T14 subagent never wrote its own handout.

## Summary
- DONE: R8 minify + resource shrink enabled for `release`; CI builds `assembleRelease` on every push and uploads the R8 mapping (`release-mapping` artifact). `android` workflow is GREEN on head `9c60863` (run 41221241216, build #168).
- DONE: a minified, debuggable `qa` build type (`testBuildType = "qa"`) + rule files so instrumented tests can run against minified code; failure-summary step in `instrumented.yml` now emits gradle cause, XML suite and logcat FATAL as annotations.
- BROKEN: the emulator workflow against the minified `qa` variant is RED (runs 41220818087 and 41221522537).
- NOT STARTED: all docs (README fa+en, SIGNING, PRIVACY, DEPLOY, RELEASING, THIRD_PARTY_NOTICES), backend Dockerfile/.dockerignore + docker build step, secrets grep, accounting this handout's audit.

## Files created/changed (T14 commits df7d40c, 0b40194, a7e29fd, 179d9d5, 73a795d, 9c60863)
- `app/build.gradle.kts`: release `isMinifyEnabled=true`, `isShrinkResources=true`; build type `qa` (minified, debuggable); `testBuildType = "qa"`.
- `app/proguard-rules.pro` (release), `app/proguard-qa-rules.pro` (qa app), `app/proguard-test-rules.pro` (test APK).
- `.github/workflows/android.yml`: release build step + `release-mapping` artifact. `.github/workflows/instrumented.yml`: runs `:app:connectedQaAndroidTest`, richer failure annotations.

## Diagnosis of the RED emulator run (run 41221522537, job annotations)
- `RealAppFlowTest` (3/3 failed): `java.lang.NoSuchMethodError: No interface method complete()Z in class Lkotlinx/coroutines/CompletableJob;` thrown from `androidx.compose.ui.test.IdlingResourceRegistry.<init>`. Cause: R8 in the APP (qa) strips/renames `kotlinx.coroutines` members; the (separately compiled) test APK shares the app's classloader and calls `CompletableJob.complete()`.
- `ReportExportTest` failures (`lateinit property db has not been initialized`, ReportExportTest.kt:55) are a SECONDARY effect (setup failed, teardown `closeDatabase` runs on uninitialized lateinit). Also make `closeDatabase` tolerate that (`if (::db.isInitialized)`).
- Harmless noise: `SliceDataConverter ClassNotFoundException` logcat lines come from the system Settings app, not ours; "Unable to strip libandroidx.graphics.path.so" is a warning.
- Suggested fix (not applied): in `app/proguard-qa-rules.pro` add `-keep class kotlinx.coroutines.** { *; }` (and `-dontwarn kotlinx.coroutines.**`); if more NoSuchMethod/ClassNotFound errors follow, keep `androidx.compose.ui.test.**`, `androidx.test.**`, `kotlin.**` for the qa variant ONLY. Do NOT weaken the real `release` rules to make tests pass: the goal is to prove the shipping config works. If the qa and release rules differ, document exactly how.

## Acceptance audit (original T14 + updated scope)
| Item | Status | Evidence |
|---|---|---|
| R8 enabled for release + rules file | MET | app/build.gradle.kts (release block), app/proguard-rules.pro |
| `assembleRelease` exits 0 in CI | MET | android run 41221241216 success |
| R8 mapping uploaded | MET | android.yml step `release-mapping` |
| Minified build proven by emulator smoke tests | NOT MET | instrumented runs 41220818087, 41221522537 failed (above) |
| docs/SIGNING.md (fa+en) | NOT MET | not written |
| docs/PRIVACY.md (fa+en) | NOT MET | not written |
| README.md fa + en | NOT MET | not written |
| docs/DEPLOY.md + backend/Dockerfile + docker CI step | NOT MET | not written |
| docs/RELEASING.md | NOT MET | not written |
| THIRD_PARTY_NOTICES.md | NOT MET | not written |
| secrets grep clean | NOT RUN | run `grep -rniE "password|keystore|api_key" --exclude-dir=.git --exclude-dir=node_modules .` and justify hits |
| No keystore/passwords in repo | LIKELY MET | none added by T14; verify with the grep |

## Next steps for whoever resumes (in order)
1. Fix the qa-variant R8 rules (above); re-run `instrumented.yml` (locally you can run `./gradlew :app:connectedQaAndroidTest` on an emulator). Expect the 13 QA files (10 screenshots, trial-balance PDF + page PNG, CSV) like run 41208267404.
2. Write the docs listed in tasks/T14-release-prep.md "Updated scope" (use persian-writing conventions for Persian text).
3. Secrets grep, then finish this handout's audit table and set `tasks/T14-release-prep.md` + `tasks/README.md` to done.
4. Do NOT rename `applicationId` (`com.example.accounting`, placeholder) without the owner's decision; document where to change it.

## Open issues / risks
- R8 on the real release variant is only proven to BUILD, not to RUN. Until the minified smoke tests pass, treat release APKs as untested.
- Room/Hilt/kotlinx.serialization/Keystore code paths are the likely R8 breakages if any appear.
