# T01e — Auto-publish APK to GitHub Releases on every green build
Status: done
Depends: T01d
Read first: CLAUDE.md, handouts/T01c-github-actions.md, handouts/T01d-ci-green.md, docs/CI.md
Skills to invoke: none

## Goal
- Every successful `android` build on a push to the dev branch (and `main`/`master` if present) publishes the debug APK as a GitHub Release so the user can download and install it on a phone. No manual step.

## Design (follow unless a CI failure forces change)
- Add a second job `publish` in `.github/workflows/android.yml` (or a `workflow_run` workflow `publish.yml` triggered on `android` completed+success; pick the simpler/safer one and justify in handout). Runs only: event `push`, branch = `claude/dreamy-mccarthy-mczm8c` or `main`/`master`; never for pull_request, forks, dependabot. `permissions: contents: write` on that job only (workflow default stays `contents: read`).
- Per build: create prerelease tag `build-<run_number>` (target = built commit sha), title `Build #<run_number> (<short sha>)`, body = commit subject + list of commits since previous build + CI run URL + sha256 of APK + "debug build, signed with debug key, for testing" + min Android version (8.0 / API 26). Asset name: `accounting-debug-<run_number>-<shortsha>.apk`.
- ALSO maintain a rolling prerelease tag `latest` (delete+recreate or update assets) with the newest APK under the stable name `accounting-debug-latest.apk`, so one bookmarked link always works.
- Retention: after publishing, delete `build-*` releases + tags older than the newest 15 (never delete `latest`, `v*` tags, or non-prerelease releases).
- Use `gh` CLI preinstalled on ubuntu runners with `GH_TOKEN: ${{ github.token }}` (or `softprops/action-gh-release@v2`, pinned major). Avoid third-party actions that need extra secrets.
- Do not run the publish job when the build fails (needs: build, `if: success()`). Artifact handoff via upload/download-artifact@v4 in the same workflow if using a second job.
- Tag pushes `v*` still handled by existing `release.yml`; make sure the new job does not trigger on tags and that `build-*` tags created by the workflow do NOT re-trigger `android` (guard with `on.push.tags-ignore` or `branches` filter) — infinite loop check is an acceptance criterion.
- Docs: `docs/CI.md` section "Testing a build on your phone": where to find Releases, how to enable "install unknown apps", how to install, how to check the version (build number shown in app later: optional `BuildConfig` versionName `0.1.<run_number>` via `-PbuildNumber`; add to app/build.gradle.kts if simple, versionCode = run number).

## Deliverables
- Workflow changes, `docs/CI.md`, `handouts/T01e-auto-release.md`.

## Acceptance criteria
- [ ] After pushing the change, the `android` run on the branch is green AND a release `build-<n>` exists with the APK asset, plus `latest` updated (verify with mcp__github__ tools: `list_releases`, `get_latest_release` is not enough since prereleases; use `list_releases`).
- [ ] APK asset downloadable URL recorded in handout; file name matches pattern; sha256 in release body.
- [ ] A failing build publishes nothing (reason documented; test by reading workflow conditions, no need to break the build on purpose).
- [ ] No workflow loop (the publish step creating a tag does not start another android run). Verify run list shows exactly one android run per push.
- [ ] Retention step proven safe (dry-run echo in handout, never deletes `latest`/`v*`).
- [ ] Max 6 push/check iterations. Allowed git: commit/push to `claude/dreamy-mccarthy-mczm8c` only, prefix `T01e:`.

## Out of scope
- Signed release APK / Play Store (T14). App features.
