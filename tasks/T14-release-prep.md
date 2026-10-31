# T14 — release-prep
Status: in_progress
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

## Updated scope (main agent, binding)
- Read also: handouts T01e (release workflow), T09b, T09c, T12, T12b, T13, T13a, docs/CI.md, docs/BACKEND.md, docs/VOICE.md, docs/TESTING.md, docs/ACCOUNTANT_REVIEW.md, docs/EVAL_REPORT.md, `.github/workflows/*.yml`, `app/build.gradle.kts`, `backend/**` (config/env), `docs/DECISIONS.md`.
- R8/minify SAFETY (the real risk): enabling R8 can break Room, Hilt/Dagger, kotlinx.serialization, Compose, reflection-free JSON in NlParserHttp, Keystore cipher, SpeechRecognizer APIs. Deliver `app/proguard-rules.pro` with only justified keep rules (comment each), `isMinifyEnabled = true` + `isShrinkResources = true` for release, and PROVE it works: (a) `./gradlew assembleRelease` in the `android` workflow (add a CI step that builds release unsigned/debug-signed and uploads the mapping file as an artifact; do not block debug release publishing if release build fails? NO: a red release build must fail the workflow, fix it); (b) run the emulator smoke tests (T13a `RealAppFlowTest` + `ReportExportTest`) against the MINIFIED release variant via `testBuildType = "release"` (or a dedicated `benchmark`-like debuggable minified variant with the same rules) in `instrumented.yml` with its pulled QA artifacts; document how. If the app cannot be tested against release for a reason, say so precisely.
- Signing: `release.yml` already signs from secrets (T01c). Write `docs/SIGNING.md` (fa+en): create a keystore with keytool, base64 it, which 4 secrets to add (names as used by release.yml), how to tag `v0.1.0` and where the signed APK appears, how to verify with `apksigner verify`, warning to back up the keystore (losing it = cannot update the app), debug-signed vs release-signed install conflict (uninstall first). NO keystore/passwords in repo. Package name `com.example.accounting` is a placeholder: add a clearly visible section in docs/SIGNING.md + README explaining that the applicationId must be chosen BEFORE the first real release (cannot change later), and list the exact files/places to change; do NOT rename it yourself without an explicit instruction.
- `docs/PRIVACY.md` (fa + en, accurate to the code): data flow table: typed/pasted text -> your backend -> configured LLM provider (Anthropic or OpenAI-compatible); audio -> your backend -> STT provider ONLY when cloud voice engine is used (otherwise stays with the phone's speech service, which may itself use Google servers/internet); ledger DB stays on device; backups are files the user chooses; token stored encrypted via Keystore; no analytics/ads/crash reporting; what the backend does/doesn't log (`DEBUG_LOG_TEXT`); provider retention depends on the provider you configure; user responsibilities. Match the in-app privacy text; if they disagree, fix the in-app text via a small resource change.
- `README.md`: fa section first, then en. Contents: what it is, features, screenshots mention (QA artifacts), install from Releases (`latest`) incl. unknown-sources steps, first-run, how to use (examples sentences: the soda example, credit purchase, expense, receipt), manual entry, voice engines, reports/backup, architecture overview (modules diagram in text), accounting rules summary with link to docs/ACCOUNTING_RULES.md and ACCOUNTANT_REVIEW.md, backend setup (env table, run, test connection), build/test commands, CI/workflows overview (android, backend, instrumented, release), known limitations (real-mic/live-LLM/PDF shaping unverified; bank-to-bank transfer not implemented; no VAT/payroll/inventory), docs index. Keep claims honest: only what is implemented.
- `docs/DEPLOY.md` + `backend/Dockerfile` + `backend/.dockerignore`: how to run the proxy (Node 22; env table incl. LLM_PROVIDER, ANTHROPIC_*, OPENAI_*, STT_*, AUTH token(s), rate limit, DEBUG_LOG_TEXT), systemd example, Docker run example, TLS reverse proxy (Caddy/nginx snippet), health check, hosting notes for Iran (reachability of upstream providers from Iran is the operator's concern; configurable base URLs; mention an OpenAI-compatible provider reachable from the server's region). Add a CI step in `backend.yml` that builds the Docker image (no push) if docker is available on the runner.
- `THIRD_PARTY_NOTICES.md`: Vazirmatn (OFL, from core/ui assets), main dependency licenses (Compose/AndroidX/Room/Hilt/Kotlin/OkHttp: Apache-2.0; backend deps from package.json) — generate honestly from the catalogs, do not invent. Mention no project LICENSE chosen yet (owner decision) in README + handout.
- Version: confirm `versionName 0.1.<build>`/`versionCode` scheme from T01e; add `docs/RELEASING.md` (how to cut v0.1.0: tag, workflow, release notes template, checklist incl. run the emulator workflow, accountant review, device test of voice).
- Secrets grep acceptance: `grep -rniE "password|keystore|api_key" --exclude-dir=.git --exclude-dir=node_modules .` must show only placeholders/names/docs (list them in the handout with justification).
- Push discipline: ONE push per attempt; wait for THAT android run (and backend run when backend files change) to finish before pushing again (poll https://api.github.com/repos/russellzparadox/accounting/actions/runs?branch=claude/dreamy-mccarthy-mczm8c with `until` loops in Bash run_in_background; instrumented workflow via mcp__github__actions_run_trigger, read results from check-run annotations: /actions/runs/<run>/jobs -> job id -> /check-runs/<job_id>/annotations); failures via 'Failure summary' step (get_job_logs tail_lines 160). Max 10 pushes, commit prefix `T14:`, only branch claude/dreamy-mccarthy-mczm8c, no force push, never weaken tests. Use persian-writing:persian-writing for ALL Persian prose. Do NOT end your turn until the last runs completed AND `handouts/T14-release-prep.md` (honest audit of every item MET/PARTIAL/NOT MET with file:line, run ids, what a human must still do before a real release) is written and pushed; update Status + README row honestly.
