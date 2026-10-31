# T01d — Make Android CI green
Status: done
Depends: T01, T01c, T03
Read first: CLAUDE.md, handouts/T01-android-scaffold.md, handouts/T01c-github-actions.md, handouts/T03-domain-core.md
Skills to invoke: none

## Goal
- `android` workflow on branch `claude/dreamy-mccarthy-mczm8c` passes: `./gradlew --stacktrace lint test assembleDebug`.
- Known: CI dependency resolution works (AGP/Hilt/KSP resolved). Run 41181837841 failed with Kotlin `CompilationErrorException`. Real `e:` lines not seen yet.

## Deliverables
- `.github/workflows/android.yml`: tee gradle output to a file; on failure add a step printing a SHORT summary: `grep -E '^e: |^w: .*error|What went wrong|FAILURE' -A4` (limit ~80 lines) so job log tail shows the root cause.
- Fixes to scaffold/build files (T01 scope + core/domain wiring) as needed. Versions in `gradle/libs.versions.toml` may be adjusted if wrong (verify they exist: Maven Central reachable here, Google Maven not; CI is the Google Maven check).
- `.github/dependabot.yml`: noise control: `open-pull-requests-limit: 2`; group all gradle updates into one group; ignore semver-major for `com.android.*`, `org.jetbrains.kotlin.*`, `gradle-wrapper`, `com.google.devtools.ksp` (major bumps break the toolchain).
- `handouts/T01d-ci-green.md`, update `docs/CI.md` (how to read failures).

## Acceptance criteria
- [ ] Latest `android` run for the branch head: conclusion success (check with github MCP `actions_list list_workflow_runs`, then `list_workflow_jobs`; get logs via `get_job_logs` with SMALL `tail_lines` e.g. 60 — huge logs overflow context).
- [ ] `./gradlew :core:domain:test` ran in CI (JUnit results visible in log) and passed
- [ ] Max 6 push-fix-check iterations; if still red, stop and write findings in handout (status partial)
- [ ] Do not skip/disable tests or lint to get green; do not weaken checks. If a lint rule must be configured, justify in handout.
- [ ] Do not touch app logic beyond what the failure needs.

## Allowed git
- This task may `git add/commit/push` to `claude/dreamy-mccarthy-mczm8c` only (needed to trigger CI). Commit msg prefix `T01d:`. Never force-push. Never touch other branches or dependabot PRs.

## Out of scope
- New features, T04+ work.
