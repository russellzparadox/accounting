# Handout T00 — bootstrap
Status: done

## Summary
- Created 15 task files (T01..T14 incl. T01b) from T00 table, plus `tasks/README.md`.
- Each task file: header (Status todo, Depends, Read first, Skills), Goal, Deliverables, Acceptance criteria, Out of scope, Verify.
- No code written. No other files touched.

## Files created/changed
- `tasks/T01-android-scaffold.md`, `tasks/T01b-design-system.md`, `tasks/T02-accounting-rules.md`, `tasks/T03-domain-core.md`, `tasks/T04-room-persistence.md`, `tasks/T05-persian-utils.md`, `tasks/T06-backend-llm-proxy.md`, `tasks/T07-nl-pipeline.md`, `tasks/T08-entry-ui.md`, `tasks/T09-voice-input.md`, `tasks/T10-ledger-ui.md`, `tasks/T11-reports.md`, `tasks/T12-settings-onboarding.md`, `tasks/T13-qa.md`, `tasks/T14-release-prep.md`
- `tasks/README.md`
- `handouts/T00-bootstrap.md` (this file)
- Total new files: 17

## Public interfaces / contracts
- None. Docs only.
- Slugs: T01 android-scaffold, T01b design-system, T02 accounting-rules, T03 domain-core, T04 room-persistence, T05 persian-utils, T06 backend-llm-proxy, T07 nl-pipeline, T08 entry-ui, T09 voice-input, T10 ledger-ui, T11 reports, T12 settings-onboarding, T13 qa, T14 release-prep.

## Decisions made (+why)
- "Read first" in each task lists CLAUDE.md plus every dependency handout (per CLAUDE.md rule).
- T13 dependency "T07..T12" expanded to T07, T08, T09, T10, T11, T12.
- Paths not given in T00 are proposed in the task file and flagged there: CSV digit style (T11), accountant checklist (T13), signing and privacy docs (T14), T07 module placement.
- Module paths for `core:*` and `feature:*` assumed as `core/<name>`, `feature/<name>`.

## How verified (commands + result)
- `ls tasks | wc -l` -> 18 (`_TEMPLATE.md`, `T00-bootstrap.md`, 15 task files, `README.md`).
- `ls handouts` -> `_TEMPLATE.md`, `T00-bootstrap.md`.

## Open issues / risks
- T00 Verify line expects 17 files in `tasks/`. Actual is 18 (counts template and T00 too). Count of new task files (15) matches the table. Main agent to confirm.
- T00 table path for chart JSON is `app/core/domain/...`. T01 uses `core/domain/`. Kept as given in T02, flagged there.
- T00 task file still says `Status: in_progress`. Left unchanged (main agent sets status).
- `ui-ux-pro-max` and `impeccable` not verified as installed. T01b and T08 list fallbacks.
- Task files do not carry the "Skills to invoke" line for T03, T04, T07, T13 beyond "none". Matches T00 table.

## Next task must know
- Start with T01. Its handout is `handouts/T01-android-scaffold.md`.
- Vazirmatn font must be obtained in T01. If not, T01 records it in Open issues.
- Run order is in `tasks/README.md`.
