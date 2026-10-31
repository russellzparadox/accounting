# T14 — release-prep
Status: todo
Depends: T13
Read first: CLAUDE.md, handouts/T13-qa.md
Skills to invoke: persian-writing:persian-writing

## Goal
- Release build: Proguard/R8, signing doc, privacy note, README (fa + en).

## Deliverables (files)
- `app/proguard-rules.pro`, R8 enabled for release in `app/build.gradle.kts`
- Signing doc: path not set in T00. Propose `docs/SIGNING.md`.
- Privacy note: path not set in T00. Propose `docs/PRIVACY.md`.
- `README.md` with fa and en sections

## Acceptance criteria
- [ ] R8 enabled for release. Rules file present.
- [ ] `./gradlew assembleRelease` exits 0. Signing config read from env or gradle properties. No keystore or passwords in repo.
- [ ] Privacy note: states what goes to the LLM backend and what stays on device.
- [ ] README has fa section and en section.
- [ ] `grep -rniE "password|keystore|api_key" --exclude-dir=.git .` shows no secrets.

## Out of scope
- Store listing, marketing copy, new features.

## Verify (commands)
- `./gradlew assembleRelease`
- `./gradlew test`
