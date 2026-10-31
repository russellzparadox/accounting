# T06 — backend-llm-proxy
Status: in_progress
Depends: T02
Read first: CLAUDE.md, handouts/T02-accounting-rules.md
Skills to invoke: persian-writing:persian-writing

## Goal
- Node/TS proxy `POST /v1/parse`. Calls Claude Haiku with tool-use + JSON schema. Returns TransactionIntent or needs_clarification. Auth, rate limit, no text logging. Eval harness runs offline.

## Deliverables (files)
- `backend/package.json`, `backend/tsconfig.json`
- `backend/src/` server, `parse.ts`, `auth.ts`, `ratelimit.ts`, `llm.ts` (client behind interface), schema JSON
- `backend/test/` unit + contract tests
- `backend/eval/sentences.fa.json` (>= 50 Persian sentences, expected intents)
- `docs/LLM_PROMPTS.md`, `docs/BACKEND.md`

## Acceptance criteria
- [ ] `POST /v1/parse` body `{text}` returns TransactionIntent or `needs_clarification`. Response validated against schema.
- [ ] LLM called via tool-use with the schema. Model ID from env var.
- [ ] Anthropic key read from backend env only. Never returned or logged.
- [ ] No token -> 401. Wrong token -> 401. Tests cover both.
- [ ] Rate limit -> 429 after N requests per minute per token. Tested.
- [ ] Request text not logged by default. Only logged when `DEBUG_LOG_TEXT=1`. Tested.
- [ ] LLM client is mockable. `npm test` passes with no API key.
- [ ] Eval harness runs all >= 50 sentences in mock mode and reports pass/fail.
- [ ] Prompt forbids arithmetic. LLM returns the amount as stated in text; app cross-checks in T07.
- [ ] `docs/LLM_PROMPTS.md` and `docs/BACKEND.md` written.

## Out of scope
- App client code, live eval run (T13), deployment.

## Verify (commands)
- `cd backend && npm test`
