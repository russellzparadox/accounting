# Handout T13a — emulator-qa
Status: done

## Summary
- Instrumented run GREEN: https://github.com/russellzparadox/accounting/actions/runs/41208267404 (head db62fac). Tests pass, QA files pulled, gate passed.
- Pull annotation (job 124557715124): `pulled 13 files from files/qa`. Listing: 10 screenshots, `trial-balance-page-1.png`, `trial-balance.pdf`, `trial-balance.csv`.
- Root cause of earlier 0-file pulls: `connectedDebugAndroidTest` uninstalls app + test APK when it ends, which deleted `/data/data/com.example.accounting/files/qa` before the pull. Fixed with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` (commit db62fac). Confirmed by diag notice: `package:com.example.accounting` still installed after Gradle.
- `android` run on db62fac: https://github.com/russellzparadox/accounting/actions/runs/41208261720 = success.
- Final `android` run for the docs commit: see `git log` / runs list for this branch (pushed after this handout).

## Files created/changed
- `.github/workflows/instrumented.yml`: gradle with leaveApksInstalledAfterRun flag; logcat `logcat-full.txt` + filtered `logcat.txt`; qa pull via `.github/scripts/pull-qa.sh`; gate step (10 screenshots, PDF, page PNG, CSV); `Failure summary` step emits `::error` annotations.
- `.github/scripts/pull-qa.sh`: `::notice title=qa-diag` lines (installed packages, `run-as ls -R files/qa`); run-as tar pull with per-file cat fallback; `::notice title=qa-pull` count.
- `app/src/androidTest/kotlin/com/example/accounting/qa/RealAppFlowTest.kt`, `ReportExportTest.kt`: test-side fixes from FIX ROUND 1 (imports, confirm dialog step, step-named timeout errors, fixture account 1200 -> 1201).
- `docs/CI.md`: how to run and read the instrumented workflow.
- `tasks/T13a-emulator-qa.md`: status and fix-round notes.
- No production app code changed.

## Public interfaces / contracts
- Test tags unchanged: `MANUAL_AMOUNT_FIELD_TAG`, `MANUAL_SUBMIT_TAG`.
- QA output dir: `QaOutput.dir(context)` = `getExternalFilesDir(null)/qa`, fallback `filesDir/qa`. Pull reads `files/qa` via run-as (debug build is debuggable). The external-storage path is not what the pull reads.

## Decisions made (+why)
- No app/domain change. Red runs before FIX ROUND 3 were test, fixture, workflow or retrieval faults.
- Fixture 1200 -> 1201: posting to control account is rejected by domain by design.
- Failure diagnostics as `::error` annotations: job-log tail is often post-job cleanup.
- Pull via run-as tar, because `adb pull` cannot read the app's Android/data dir on API 30+.

## How verified (run -> result)
| run | head | result | cause (evidence) |
|---|---|---|---|
| 41204585696 | 2183ba2 | failure | no artifacts; inferred compile error |
| 41205154418 | 115846b | failure | `:app:compileDebugAndroidTestKotlin` unresolved `onFirst`, `assertCountEquals` |
| 41205425739 | 08dea3a | failure | ReportExportTest `account 1200 is not postable`; RealAppFlowTest timeout |
| 41205815518 | b72303d | failure | RealAppFlowTest `waitForNode` timeout on `committed_notice` (confirm card still open) |
| 41206239547 | f8d2373 | failure | cause not read; identical source passed next (382ff9d). Unexplained |
| 41206716812 | 382ff9d | tests PASS, pull FAIL | 0 PNG pulled |
| 41207166016 | 6775801 | tests PASS, pull FAIL | 0 PNG pulled |
| 41207774274 | 5af39e3 | tests PASS, pull FAIL | `pulled 0 files`; app uninstalled before pull (diagnosed) |
| 41208267404 | db62fac | SUCCESS | `pulled 13 files from files/qa`, gate green |

## Artifacts
- `qa-artifacts` (workflow upload, run 41208267404): `qa/` folder (10 screenshots, `trial-balance-page-1.png`, `trial-balance.pdf`, `trial-balance.csv`) + `gradle-instrumented.log`, `logcat-full.txt`, `logcat.txt`.
- `androidtest-reports`: JUnit XML + HTML under `app/build/outputs/androidTest-results/` and `reports/androidTests/`.
- Per-file sizes not recorded here (zip not downloaded). Names verified via pull listing annotation.

## Audit (acceptance criteria, T13a)
- [MET] Workflow YAML valid; triggers only `workflow_dispatch` + `schedule`; never on push. (`yaml.safe_load` ok.)
- [MET] One dispatch run `success`: 41208267404.
- [MET] Artifacts uploaded: `qa-artifacts` with 10 screenshots, PDF page PNG, PDF, CSV (evidence: pull annotation listing above).
- [MET] Existing `android` workflow green on db62fac: run 41208261720. Final docs-commit android run: see runs list.
- [MET] No tests skipped/weakened. Only test-side change is fixture account 1200 -> 1201 (domain rule kept). No `@Ignore`, no removed assertions.

## What the emulator run proved (run 41207166016 tests; 41208267404 pull)
- Real `MainActivity` on API 34 google_apis x86_64 emulator, real Room DB, Hilt, navigation:
  - onboarding accepted through UI;
  - 500,000 toman cash purchase via manual sheet + confirmation; committed notice with Persian digits;
  - journal row `سند شماره ۱`, document detail `جمع بدهکار: ۵۰۰٬۰۰۰ تومان`;
  - cash account ledger opens; reversal creates reversal row;
  - trial balance preview gross 1,000,000 toman each side, no unbalanced marker;
  - settings unit rial: journal re-renders in rial;
  - manual journal editor opens.
- `PdfExporter` writes a PDF (`%PDF-`, >0 pages); each page renders non-blank under `PdfRenderer`.
- `CsvExporter`: UTF-8 BOM and Persian headers present.
- NOT proven: Persian glyph/shaping correctness in PDF (PdfRenderer checks ink only). Screenshot and PDF page content not reviewed by a human yet; PNGs are in `qa-artifacts`.

## Product bugs found
- None. All red runs were test, fixture, workflow or retrieval faults. "account 1200 not postable" was correct domain behaviour.

## Open issues / risks
- [MED] Run 41206239547 failed for an unread cause; identical source passed next run (382ff9d). Not reproduced.
- [LOW] `Check that QA screenshots...` gate makes the job red if any QA file is missing. Intentional; keep.
- [LOW] `actions/*@v4` Node 20 deprecation warnings and setup-java@v4 deprecation annotation (out of scope, see T01d).
- [LOW] `ubuntu-latest` migrates to Ubuntu 26 (notice, Oct 2026). Watch for emulator breakage.

## Next task must know
- Instrumented workflow: dispatch via Actions tab or API; read results via check-run annotations (`/repos/.../check-runs/<job_id>/annotations`), not job-log tails.
- Pull works because the workflow passes `leaveApksInstalledAfterRun=true`. Do not drop that flag.
- Persian shaping in PDF is not verified by machine. Needs a human look at `qa-artifacts/qa/trial-balance-page-1.png`.
