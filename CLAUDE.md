# CLAUDE.md — Persian NL/Voice Accounting App (Android)

## What this is
Android app (Kotlin + Jetpack Compose), Persian-first, RTL. User says/types a transaction in Persian
("۴ قوطی نوشابه روی هم ۵۰۰٬۰۰۰ تومان خریدم"); app records a balanced double-entry journal entry like an
Iranian accountant. LLM (via thin backend proxy) only extracts structure. Code does all posting + math.

## Golden rules
1. LLM never writes to ledger, never does money arithmetic. Pipeline: text/voice -> normalize -> LLM `TransactionIntent` JSON -> `PostingEngine` (deterministic) -> user confirms -> Room.
2. Money = `Long` rials internally. Toman = rial/10 (display setting). Never Float/Double for money.
3. Debits == credits, always. Committed docs immutable. Fix = reversing entry, never delete/update.
4. Dates stored Gregorian ISO; UI shows Jalali. UI digits Persian; DB digits Latin.
5. All user-visible strings Persian (`values/strings.xml` default `fa`). Use ZWNJ correctly.
6. No API keys in app. Backend holds Anthropic key.

## Stack
Kotlin, Compose M3 (RTL), Hilt, Room, Coroutines/Flow, Vazirmatn font. Backend: small Node/TS proxy in `/backend`.
Voice v1: on-device `SpeechRecognizer` (fa-IR) behind `SpeechSource` interface (cloud impl later).

## Workflow (MANDATORY)
- Every task = file `tasks/Txx-name.md` (from `tasks/_TEMPLATE.md`), written BEFORE work starts.
- Every task ends with `handouts/Txx-name.md` (from `handouts/_TEMPLATE.md`). Handout = how subagents talk to each other.
- Start of a task: read this file, the task file, handouts of every dependency. Nothing else is shared.
- ONE subagent at a time, sequential. Spawn: `model: haiku`, `effort: medium`. Main agent writes/updates task file, spawns, reads handout, verifies (build/tests), sets status, commits, then next.
- One commit per task on branch `claude/dreamy-mccarthy-mczm8c`. No PRs unless user asks.
- Subagent must not widen scope. Unknown/ambiguous -> note in handout "Open issues", don't guess silently.
- Task/handout style: terse bullets, no filler ("caveman" style).

## Skills (use if available; names given by user)
- Design/UI: `impeccable`, `ui-ux-pro-max` (NOT installed in cloud session at planning time) -> fallback: `design:design-system`, `design:design-critique`, `design:accessibility-review`, `design:ux-copy`, `design:design-handoff`.
- Persian text: `persian-writing:persian-writing` for strings, docs, prompts.
- `ponytail`: unknown; ask user.
- Subagents don't inherit skills: each task file lists which to invoke.

## Docs map
`docs/ARCHITECTURE.md` `docs/ACCOUNTING_RULES.md` `docs/LLM_PROMPTS.md` `docs/VOICE.md` `docs/BACKEND.md` `docs/DESIGN.md` `docs/DECISIONS.md`

## Commands (fill as tasks land)
- Android tests: `./gradlew test`
- Backend: `cd backend && npm test`
