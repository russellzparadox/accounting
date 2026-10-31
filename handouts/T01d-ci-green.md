# Handout T01d — ci-green
Status: done

## Summary
- `android` workflow green on branch head `ded996b`: run https://github.com/russellzparadox/accounting/actions/runs/41182099701 (`lint test assembleDebug` success, APK uploaded).
- Two fixes needed. Failing run 41181837841 (T03 head) was Kotlin compile error in `core/ui`; run 41182022927 showed it in the summary.
- Workflow now tees gradle output and prints `e:`/`What went wrong`/`FAILURE` summary as last job step.

## Files created/changed
- `.github/workflows/android.yml`: tee to `gradle-build.log`, `pipefail`, failure summary step (last).
- `core/ui/src/main/java/com/example/accounting/core/ui/theme/Type.kt`: explicit 15 public text styles with Vazirmatn.
- `.github/dependabot.yml`: `open-pull-requests-limit: 2`, gradle group, semver-major ignores for AGP/Kotlin/KSP/gradle-wrapper.
- `docs/CI.md`: how to read a red run.
- `tasks/T01d-ci-green.md` (Status: done), `handouts/T01d-ci-green.md`.

## Public interfaces / contracts
- `AppTypography: Typography` unchanged name/type. Same fonts, now set per style.

## Decisions made (+why)
- Type.kt: `Typography(defaultFontFamily = ...)` resolves to the Material3 constructor that is `internal` in BOM 2025.10.00 (material3 1.4.x). Fix uses the public 15-style constructor, Vazirmatn set via `copy(fontFamily=)`. No BOM bump (BOM bump unverifiable locally, would widen scope).
- Summary step uses `if: failure()` and is the last step, so the job-log tail shows it. Uses `head -80`.
- dependabot ignores only what the task listed. Did not add `ignore` for `com.google.dagger` etc.

## How verified (commands + result)
- Run 41182022927 (3135ebb): `Lint, test, assemble debug` failed at `:core:ui:compileDebugKotlin`. Summary printed `e: .../core/ui/.../theme/Type.kt:16:21 Cannot access 'constructor(...)': it is internal in 'androidx.compose.material3.Typography'`.
- Run 41182099701 (ded996b): all steps success; `Lint, test, assemble debug` 07:49:31 to 07:51:53 (~2.5 min); `Upload debug APK` success; run conclusion `success`.
- `./gradlew :core:domain:test` is not run as a separate command in CI. It is covered by the `test` task, which includes `:core:domain` (in `settings.gradle.kts`). Its per-test JUnit lines were not checked in the log (log tail limit 80 lines). Not separately confirmed.
- `dependabot.yml` parses (PyYAML).

## Open issues / risks
- Test count/JUnit lines for `core:domain` not extracted from CI log. Check `Lint, test, assemble debug` step output if needed.
- Lint not configured to fail on warnings; no lint rules were changed or disabled.
- Node 20 deprecation warning from actions (checkout/setup-java/upload-artifact/setup-gradle). Not in scope; bump to v5 later.
- Android local build still impossible (Google Maven blocked). CI is the only verifier.
- Lint step is slow to report (~2.5 min) on cold cache.

## Next task must know
- Android build is green on CI. Add modules/code only with `./gradlew lint test assembleDebug` passing in CI.
- Compose BOM 2025.10.00 => Material3 `Typography` must be built with the 15 public named params (not `defaultFontFamily`).
- To debug red CI: read the `Failure summary` step (last step) first, see `docs/CI.md`.
