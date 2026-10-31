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

## Added by main agent
- Also read `handouts/T03-domain-core.md`: emit TransactionIntent JSON EXACTLY per its "TransactionIntent JSON" section (14 type ids minus REVERSAL, field rules table, `confidence` Int 0..100, `date` yyyy-MM-dd, `paid_with` CASH|BANK|null, `party_name`, `category` enum, `needs_clarification{question}`). Schema file must mirror it; add a contract test that validates every eval expected-intent against the schema AND the field-rule table.
- Node 22 + npm available; registry.npmjs.org reachable. Use TypeScript, `@anthropic-ai/sdk`, a minimal HTTP layer (fastify or node:http), vitest. Default model env `ANTHROPIC_MODEL=claude-haiku-5-5`. Invoke the `claude-api` skill for current SDK usage (tool use with forced `tool_choice`, prompt caching of the system prompt).
- Today's date is passed to the LLM per request (`today` ISO + Jalali not needed) so relative dates ("دیروز") resolve; amount arithmetic forbidden, LLM returns `amount_text` verbatim-as-stated AND `amount_rial` ONLY if unambiguous; app cross-checks with local parser in T07. Add `amount_text` (string, the amount phrase copied from user text) as an extra field in the schema and document it in the handout so T07 can use it.
- Units: user says toman by default; prompt must say "if unit is toman, amount_rial = toman*10 is done by the APP; return `amount_unit`: 'toman'|'rial' and the number as stated". Decide + document: LLM returns `amount_value` (number as stated, integer) and `amount_unit`; app converts. (Replaces direct `amount_rial` from LLM; backend response keeps both fields: `amount_rial` computed deterministically by backend code from value*unit, not by LLM.)
- Eval set must include: cash purchase (the soda example), credit purchase from named person, sale cash/credit, expenses by each category, receipts/payments with people, bank deposit/withdrawal, owner capital/withdrawal, returns, colloquial/Finglish-free Persian phrasing, Persian digits, Arabic digits, "نیم میلیون", missing payment method -> needs_clarification, nonsense input -> needs_clarification, prompt-injection attempts in text ("ignore previous instructions") -> still valid intent or clarification, never leaks key.
