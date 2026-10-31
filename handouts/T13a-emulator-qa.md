# Handout T13a — emulator-qa
Status: partial

## Summary
- Instrumented tests PASS on API 34 emulator: run https://github.com/russellzparadox/accounting/actions/runs/41207166016 (head 6775801): `Run instrumented tests` success, 0 failed testcases.
- Workflow still RED: `Check that QA screenshots and PDF pages were pulled` failed (0 PNG). qa/ folder never reached `qa-artifacts`. No PNG/PDF/CSV artifact.
- `android` workflow green on 6775801: run https://github.com/russellzparadox/accounting/actions/runs/41207166146.
- Push cap reached (6/6 FIX ROUND 1 pushes). Final commits (this handout, task status, README row) are local only, NOT pushed.

## Files created/changed
- `.github/workflows/instrumented.yml`: gradle output tee'd to `qa-artifacts/gradle-instrumented.log`; logcat `logcat-full.txt` + filtered `logcat.txt`; qa pull via `.github/scripts/pull-qa.sh`; last step `Failure summary` (prints + emits `::error` annotations per failed testcase / gradle failure line).
- `.github/scripts/pull-qa.sh` (new): `adb pull` of external `qa/`, then `run-as` per-file fallback, logs device listings.
- `app/src/androidTest/kotlin/com/example/accounting/qa/RealAppFlowTest.kt`: imports `onFirst`, `assertCountEquals`; confirm dialog step (`confirm_title` -> `confirm_action`) after manual submit; `waitForNode` throws step-named AssertionError with visible text and saves `qa/fail-timeout.png` on timeout.
- `app/src/androidTest/kotlin/com/example/accounting/qa/ReportExportTest.kt`: second posting account 1200 -> 1201 (1200 is control account, parent of 1201; domain guard `not postable` is correct).
- `docs/CI.md`: how to read red emulator run (annotations API, pull-qa fallback).
- `tasks/T13a-emulator-qa.md`: FIX ROUND 1 notes (uncommitted edit included in first commit).

## Public interfaces / contracts
- Test tags unchanged: `MANUAL_AMOUNT_FIELD_TAG`, `MANUAL_SUBMIT_TAG`.
- QA output dir: `QaOutput.dir(context)` = `getExternalFilesDir(null)/qa` (fallback `filesDir/qa` if external null). No production code changed.

## Decisions made (+why)
- Did not change app/domain code. All 5 red runs were test/workflow faults.
- Fixture 1200 -> 1201: posting to control account is rejected by domain by design.
- Failure diagnostics moved to `::error` annotations: job-log tail (130 lines) was post-job cleanup in later runs, hiding the failure.
- Pull fallback via `run-as` assumes debug build is debuggable (it is by default).

## How verified (run -> result)
| run | head | result | cause (evidence) |
|---|---|---|---|
| 41204585696 | 2183ba2 | failure | no artifacts; tail only. Same test source as 115846b (compile error there), so inferred compile error |
| 41205154418 | 115846b | failure | `:app:compileDebugAndroidTestKotlin`: RealAppFlowTest unresolved `onFirst`, `assertCountEquals` (read in log) |
| 41205425739 | 08dea3a | failure | ReportExportTest x2 `account 1200 is not postable`; RealAppFlowTest `ComposeTimeoutException` 15 s (step unknown, read in log) |
| 41205815518 | b72303d | failure | RealAppFlowTest `waitForNode` timeout for `committed_notice`; visible text = ConfirmationCard "بررسی تراکنش" still open (read in log) |
| 41206239547 | f8d2373 | failure | cause NOT read (outside tail window; artifact blob host blocked). App+test source identical to next green run 41206716812 |
| 41206716812 | 382ff9d | instrumented tests PASS, check step FAIL | `total failed testcases: 0`; no PNG pulled |
| 41207166016 | 6775801 | instrumented tests PASS, check step FAIL | same as above; pull-qa.sh: no PNG via adb pull or run-as |
- Android workflow: 115846b, 08dea3a, b72303d, f8d2373, 382ff9d, 6775801 all `success` (runs 41205152220, 41205425713, 41205815574, 41206239951, 41206716536, 41207166146).

## Artifacts (what exists)
- `androidtest-reports` (uploaded, run 41207166016 and others): JUnit XML + HTML. Size ~141-163 KB.
- `qa-artifacts` (uploaded): only `gradle-instrumented.log`, `logcat-full.txt`, `logcat.txt`, `qa-listing.txt` (empty listing). NO `qa/*.png`, NO `trial-balance.pdf`, NO `trial-balance.csv`.
- Note: in-test `QaOutput.screenshot` asserts the PNG is written (non-zero), so screenshots were written on device, but in a folder the pull did not reach.

## Audit (acceptance criteria, T13a)
- [MET] Workflow YAML valid; triggers only `workflow_dispatch` + `schedule`; never on push. (`yaml.safe_load` ok, trigger keys verified.)
- [PARTIAL] One dispatch run `success`. NOT MET: no dispatch run is green. Diagnosed for 5 of 6 runs with URLs above; the 6th (41206239547) unexplained.
- [NOT MET] Artifacts uploaded (screenshots + PDF page PNGs + PDF). Only logs/listing + androidtest-reports.
- [MET] Existing `android` workflow still green (6775801 run 41207166146 success).
- [MET] No tests skipped/weakened. Only test-side change: account 1200 -> 1201 in a fixture (domain rule kept). No `@Ignore`, no removed assertions.

## What the emulator run proved (run 41207166016, tests passed)
- Real `MainActivity` on API 34 google_apis x86_64 emulator, real Room file, Hilt, navigation:
  - onboarding accepted through UI;
  - 500,000 toman cash purchase via manual sheet + confirmation dialog; committed notice with Persian digits;
  - journal row `سند شماره ۱`, document detail `جمع بدهکار: ۵۰۰٬۰۰۰ تومان`;
  - cash account ledger opens; reversal creates reversal row;
  - trial balance preview gross 1,000,000 toman each side, no unbalanced marker;
  - settings unit rial: journal re-renders in rial, toman string gone;
  - manual journal editor opens.
- `PdfExporter` writes a PDF: `%PDF-` header, >1000 bytes, >0 pages; each page renders non-blank under `PdfRenderer` (not shaping proof).
- `CsvExporter`: UTF-8 BOM and Persian column headers present.
- NOT proven: Persian glyph/shaping correctness in PDF (PdfRenderer only checks ink); screenshot content (not pulled).

## Product bugs found
- None. All red runs were test/fixture/workflow faults. The "account 1200 not postable" error was correct domain behaviour.

## Open issues / risks
- [HIGH] qa/ folder not retrieved. `adb pull` of `/sdcard/Android/data/com.example.accounting/files/qa` gave nothing, and `run-as` listing of `/storage/emulated/0/Android/data/.../files/qa` was empty. Likely the test wrote to another dir (e.g. `filesDir/qa` if `getExternalFilesDir` returned null) or adb cannot read Android/data. Next: in `pull-qa.sh` also list `run-as com.example.accounting ls files/qa` and `find /data/user/0/com.example.accounting -name '*.png'`, and emit `::error` for the pull result so it is readable via annotations.
- [MED] Run 41206239547 failed, identical app/test source passed next (382ff9d). Unexplained; possible emulator flakiness. Not reproduced.
- [MED] The pull step's diagnostics are in the emulator step log, outside the 130-line tail; annotations only cover test failures.
- [LOW] `Check that QA screenshots...` makes the job red even when tests pass. Keep as gate, or change to a warning.
- [LOW] `actions/*@v4` Node 20 deprecation warnings (out of scope, see T01d).
- Unpushed: this handout and the status/README commits are local only (push cap reached).

## Next task must know
- Tests run green on the emulator (`RealAppFlowTest` + `ReportExportTest`). Only the artifact pull is broken.
- Fix pull first (see HIGH issue). Read failures via check-run annotations: `GET /repos/russellzparadox/accounting/actions/jobs/<id>` -> `check_run_url` -> `/annotations`.
- Push cap was the binding limit; plan ~3 pushes max for the next attempt.
