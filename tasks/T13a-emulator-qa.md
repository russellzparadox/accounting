# T13a — Emulator QA job (real app on an Android emulator in CI)
Status: partial
Depends: T12b
Read first: CLAUDE.md, handouts/T11-reports.md (PDF unverified, A3), handouts/T12-settings-onboarding.md (onboarding gate, token cipher), handouts/T12b-settings-follow-through.md, handouts/T10-ledger-ui.md, handouts/T08-entry-ui.md, handouts/T01d-ci-green.md, handouts/T01e-auto-release.md, docs/CI.md, `.github/workflows/android.yml`
Skills to invoke: none

## Goal
Prove the installed app works end-to-end on a real Android runtime, and produce screenshots/PDF pages the user can inspect. This is the only place PdfDocument/PdfRenderer, the real Room DB, Hilt wiring, onboarding and navigation run together.

## Deliverables
1. `.github/workflows/instrumented.yml`: triggers `workflow_dispatch` + nightly `schedule` ONLY (never on push; must not block or cancel the `android` build/release workflow; own concurrency group). ubuntu-latest, enable KVM (udev rule step), setup-java 21, `gradle/actions/setup-gradle`, `reactivecircus/android-emulator-runner@v2` (api-level 34, arch x86_64, target google_apis, disable animations, emulator-boot-timeout generous), script runs `./gradlew :app:connectedDebugAndroidTest` then `adb pull` of the QA output folder. `if: always()` upload artifacts: `qa-artifacts/` (screenshots PNG, rendered PDF pages PNG, exported PDF/CSV) and androidTest reports. Minimal permissions (`contents: read`). Pin actions to major versions.
2. `app/src/androidTest/` instrumented tests (add deps in `app/build.gradle.kts`/catalog: androidx.test ext junit, runner, espresso-core not required, compose ui-test-junit4 + ui-test-manifest, uiautomator): 
   - Smoke flow with the REAL app (`createAndroidComposeRule<MainActivity>`): bypass/accept onboarding deterministically (documented hook, e.g. pre-seeded prefs in a `@Before` + clean app state; do NOT add test-only backdoors to production code unless guarded and documented), record a cash purchase of 500,000 toman through the quick manual entry (no backend), assert the committed bubble, open journal tab -> doc visible with Persian digits, open its ledger/account, reverse it, open reports -> trial balance preview totals equal.
   - Rial-mode check: switch unit in settings, assert amounts re-render.
   - Screenshots of each main screen (entry, journal, accounts/ledger, reports, settings, manual journal editor) via `UiDevice.takeScreenshot` or Compose `captureToImage`, saved to the app's external files `qa/` dir (adb-pullable).
   - PDF export test: build the trial-balance report from a populated DB, run the real `PdfExporter`, assert file exists and page count > 0, render each page with `PdfRenderer` to a Bitmap, assert non-blank (not all one color), save PNG of page 1..n and the PDF into `qa/`.
   - CSV export test: file bytes start with UTF-8 BOM and contain Persian headers.
3. `docs/CI.md`: how to run the instrumented workflow (Actions tab -> Run workflow), where artifacts are, what each proves.
4. `handouts/T13a-emulator-qa.md`: honest audit (MET/PARTIAL/NOT MET with evidence), exactly what the emulator run proved, and a list of anything it found broken (open issues, do not silently fix domain rules).

## Acceptance criteria
- [ ] Workflow YAML valid; triggers only dispatch + schedule; does not run on push.
- [ ] One manual dispatch run finishes with conclusion success, or failures are diagnosed in the handout with the run URL (max 6 dispatch attempts; read results with mcp__github__ tools or the public API; use tail_lines <= 130 for logs).
- [ ] Artifacts uploaded (screenshots + PDF page PNGs + PDF) — record artifact names in handout.
- [ ] Existing `android` workflow still green (run it by pushing; check it).
- [ ] No tests skipped/weakened. Product bugs found are listed in handout Open issues.

## Push / trigger discipline (binding)
- Pushes to the branch trigger the `android` workflow, which cancels in-flight runs: ONE push per attempt, wait for the android run to finish (poll `https://api.github.com/repos/russellzparadox/accounting/actions/runs?branch=claude/dreamy-mccarthy-mczm8c`) before pushing again. Trigger the instrumented workflow with `mcp__github__actions_run_trigger` (workflow_dispatch; load via ToolSearch) and poll its run via the public API. Max 8 pushes total. Commit prefix `T13a:`, only branch claude/dreamy-mccarthy-mczm8c, no force push.
- Do NOT end your turn until the last triggered run has completed AND the handout is written; set Status and README row honestly.

## FIX ROUND 1 (main agent)
- State: attempt 1 pushed (2183ba2). `android` run 41204573840 = success. Instrumented workflow run 41204585696 (workflow_dispatch) = FAILURE: emulator booted; steps "Run instrumented tests on API 34 emulator" and "Check that QA screenshots and PDF pages were pulled" failed; NO artifacts uploaded. Previous subagent stopped without diagnosing or writing the handout.
- Step 1: make failures diagnosable: the workflow must `if: always()` upload `**/build/reports/androidTests/**`, `**/build/outputs/androidTest-results/**`, any `qa/` dir, AND a logcat capture (`adb logcat -d > logcat.txt` after tests, filtered to the app package + AndroidRuntime + test runner) even when tests fail; add a short failure-summary step printing failed test names + first lines of each failure (grep the androidTest result XML), so `get_job_logs tail_lines <= 130` shows the cause.
- Step 2: read job logs (get_job_logs on run 41204585696, tail_lines 130; use `failed_only` style/targeted reads), find root causes in the tests (selectors, onboarding hook, timing/idling, permissions/storage paths for qa/ output, runner config) or in the app (a real product bug: record it in the handout Open issues, fix only if it is a clear bug outside domain accounting rules). Re-dispatch with mcp__github__actions_run_trigger (workflow_dispatch, ref claude/dreamy-mccarthy-mczm8c) after each push; poll the run via the public API (use `until` loops in Bash run_in_background with timeout up to 1500000 ms).
- Max 6 more pushes. One push per attempt; wait for the `android` run of that push to finish before pushing again (it cancels in-flight android runs; instrumented runs are separate). The final state needs: a green instrumented run with artifacts, OR a precise diagnosis of why it cannot be green (with run URLs).
- Then write `handouts/T13a-emulator-qa.md` (honest audit MET/PARTIAL/NOT MET with evidence, artifact names, exactly what the run proved, product bugs found), set Status and README row. Do NOT end your turn before that. Include this uncommitted task-file edit in your first commit.
