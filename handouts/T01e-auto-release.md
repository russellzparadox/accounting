# Handout T01e — auto-release
Status: done (handout written by main agent; subagent stopped before verifying)

## Summary
- `android.yml` has a `publish` job: after green `build`, on push to `claude/dreamy-mccarthy-mczm8c`/`main`/`master` only, publishes APK to GitHub Releases.
- Per build: prerelease `build-<run_number>` with `accounting-debug-<n>-<sha7>.apk`. Rolling prerelease `latest` with `accounting-debug-latest.apk`.
- Verified live: run 41185192835 green -> `build-55` + `latest` exist; asset 11,270,985 bytes; digest sha256:7714e42f...a5cd equals body SHA-256.

## Files changed
- `.github/workflows/android.yml` (publish job; concurrency moved to build job; publish has own non-cancelling group `publish-<ref>`; `contents: write` only on publish)
- `app/build.gradle.kts` (`-PbuildNumber` -> versionCode, versionName `0.1.<n>`; local default unchanged)
- `docs/CI.md` ("Testing a build on your phone")

## Contracts
- Stable download: `https://github.com/russellzparadox/accounting/releases/download/latest/accounting-debug-latest.apk`
- Release page: `https://github.com/russellzparadox/accounting/releases/tag/latest`
- Retention: keep newest 15 `build-*`; never matches `latest` or `v*` (dry-run on fake tags only, not exercised live).
- Min Android 8.0 (API 26). Debug-key signed. Not for distribution.

## Decisions
- Draft release first then undraft, so a half-uploaded release is never public.
- Branch-only triggers; tags/releases created by workflow cannot retrigger `android`.

## How verified
- `actions_get` run 41185192835 -> success; `list_releases` -> `latest`, `build-55`; `get_release_by_tag latest` -> asset present.

## Open issues
- Retention (delete >15) not exercised live until build #70+.
- Repo may be private: download needs a logged-in GitHub session in the phone browser (or make repo public / use GitHub mobile app).
- First APK contains only the scaffold placeholder screen; real UI arrives T08.
- Rapid pushes cancel `build` (concurrency) -> no release for cancelled commits (intended).

## Next task must know
- Every green push now produces a release; avoid noisy WIP pushes to keep releases meaningful.
