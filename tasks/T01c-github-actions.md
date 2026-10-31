# T01c — GitHub Actions CI + auto build
Status: in_progress
Depends: T01
Read first: CLAUDE.md, handouts/T01-android-scaffold.md
Skills to invoke: none

## Goal
- Auto build + test on every push/PR; publish debug APK as artifact; release APK on version tag.

## Deliverables (files)
- `.github/workflows/android.yml`: triggers push (all branches) + pull_request. JDK 17, `gradle/actions/setup-gradle` (cache), `./gradlew lint test assembleDebug`, upload `app/build/outputs/apk/debug/*.apk` (artifact `app-debug`), upload test + lint reports on failure. Concurrency group cancels superseded runs.
- `.github/workflows/backend.yml`: path filter `backend/**`; Node 20, `npm ci`, `npm test`, `npm run build` (skip gracefully if `backend/package.json` missing).
- `.github/workflows/release.yml`: trigger tag `v*`; `assembleRelease`; sign ONLY if secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` set, else upload unsigned; create GitHub Release with APK.
- `.github/dependabot.yml`: gradle, npm, github-actions weekly.
- `docs/CI.md`: workflows, required secrets, how to download APK.

## Acceptance criteria
- [ ] All YAML valid (`python3 -c "import yaml,sys;[yaml.safe_load(open(f)) for f in sys.argv[1:]]" .github/workflows/*.yml`)
- [ ] Commands in workflows match real Gradle tasks from T01 handout (no invented tasks)
- [ ] No secrets hardcoded; permissions block minimal (`contents: read`; release job `contents: write`)
- [ ] Actions pinned to major versions (checkout@v4, setup-java@v4, upload-artifact@v4)
- [ ] `docs/CI.md` + handout written

## Out of scope
- Play Store deploy. Running the workflows (no GH access to run here beyond push).

## Verify (commands)
- yaml parse command above
