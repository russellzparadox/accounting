# Handout T06 — backend-llm-proxy
Status: done

## Summary
- `POST /v1/parse` (Node 20+, TS, node:http). Forced tool-use `record_transaction` on `claude-haiku-5-5` (env `ANTHROPIC_MODEL`). Output validated twice (LLM schema, response schema) plus T03 field rules.
- Backend computes `amount_rial` (toman x10). LLM never does money math and never emits rial.
- Offline only: 109 tests pass with no API key. Mock eval 58/58 (plumbing only, not model quality). Live eval = T13.

## Files created/changed
- `backend/package.json`, `backend/package-lock.json`, `backend/tsconfig.json`, `backend/.gitignore`
- `backend/src/`: index, server, parse, llm, prompt (Persian), rules, amount, validate, auth, ratelimit, log, config
- `backend/schema/`: `llm_output.schema.json` (tool input, strict), `transaction_intent.schema.json` (response)
- `backend/test/`: contract, unit, llm, server, eval
- `backend/eval/`: `sentences.fa.json` (58), `harness.ts`
- `docs/LLM_PROMPTS.md`, `docs/BACKEND.md`, `handouts/T06-backend-llm-proxy.md`, Status line of `tasks/T06-backend-llm-proxy.md`

## Public interfaces / contracts
### Request
```
POST /v1/parse
Authorization: Bearer <one of PROXY_TOKENS>
Content-Type: application/json

{"text":"۴ قوطی نوشابه روی هم ۵۰۰٬۰۰۰ تومان نقد خریدم"}
```
Body: exactly one key `text`, string, trimmed, 1..500 code points, <= 4096 bytes. `today` is server-side (Asia/Tehran), not sent by client.

### Response 200, intent (T03 TransactionIntent + 3 extras)
```json
{"type":"PURCHASE_CASH","amount_text":"۵۰۰٬۰۰۰ تومان","amount_value":500000,"amount_unit":"toman",
 "amount_rial":5000000,"date":"2026-10-31","paid_with":"CASH","bank_account":null,
 "party_name":null,"category":null,"memo":null,"confidence":90,"needs_clarification":null}
```
- `type`: 13 ids (REVERSAL never). `amount_text`: verbatim substring of request text (T07 cross-check). `amount_value`: int as stated. `amount_unit`: `toman|rial`. `amount_rial`: int >= 1, backend-computed. `date`: yyyy-MM-dd Gregorian. `paid_with`: `CASH|BANK|null` (null = نسیه). `bank_account`: 4-digit string | null. `category`: `RENT|FREIGHT|PHONE|SALARY|UTILITIES|REPAIR|MISC|BANK_FEE|null` (only expense types). `confidence`: int 0..100.

### Response 200, clarification
```json
{"needs_clarification":{"question":"..."}}
```
No `type` key. T07: branch on `needs_clarification` BEFORE decoding TransactionIntent.

### Errors (JSON `{"error": code}`)
400 `bad_json|bad_request`, 401 `unauthorized` (+`WWW-Authenticate`), 404, 405, 413 `too_large`, 429 `rate_limited` (+`Retry-After`), 502 `invalid_llm_output|llm_unavailable`, 500 `internal`. Never echoes text, SDK messages or keys.

### LLM tool input (internal, `record_transaction`)
Same as intent but: `amount_text`, `amount_value`, `amount_unit` nullable; no `amount_rial`; `needs_clarification` `{question}|null`; `type` nullable (null only with clarification). Schema: `backend/schema/llm_output.schema.json`.

### TS seams
- `interface LlmClient { extract({text, today}): Promise<unknown> }` (`src/llm.ts`)
- `parseText({text,today}, llm): Promise<{kind:"intent"|"clarification", body}>` (`src/parse.ts`)
- `createApp({llm, tokens, limiter, logger, now?}): http.Server` (`src/server.ts`)

## Decisions made (+why)
- Units: LLM returns `amount_value` + `amount_unit` (default toman when unstated, per T06). Backend computes rial. Why: no LLM arithmetic (CLAUDE.md rule 2).
- Intent body = T03 shape plus `amount_text/amount_value/amount_unit`. Why: T06 says keep both; T03 decoder ignores extra keys.
- Clarification is a separate body without `type`. Why: T03 `type` must be one of 14 ids, nonsense input has no type.
- Field rules enforced on output (T03 table). Plus `category` must be null for non-expense types (T03 says "-", stricter). Plus `bank_account` only with BANK or BANK_TRANSFER.
- Incomplete LLM output with no clarification -> 502, not guessed. Why: "never guess".
- `thinking: disabled`, `effort: low`. Why: extraction, forced tool choice. Live behaviour unverified.
- `node:http` instead of fastify. Why: fewer deps. Vitest pinned 3.2.x (CI Node 20; vitest 4+ needs Node 22).
- Eval mock = fixture expectations replayed. `test/eval.test.ts` also self-checks that wrong outputs fail.
- Soda cash-purchase eval sentence includes «نقد». The original sentence without a payment method is an explicit clarification case.

## How verified (commands + result)
- `cd backend && npm ci` → `added 68 packages` (Node v22.23.4, npm 10.9.9; lockfile committed).
- `npm test` → exit 0. `Test Files 5 passed (5)`, `Tests 109 passed (109)`. `EVAL mock: 58/58 passed`.
- `npm run build` → exit 0 (`tsc`), emits 12 files to `dist/`.
- Startup smoke: no `ANTHROPIC_API_KEY` → `ANTHROPIC_API_KEY is not set. Refusing to start.` exit 1. With placeholder key + `PROXY_TOKENS`: `GET /healthz` → `{"status":"ok"}`; `POST /v1/parse` without token → 401. No Anthropic call made.
- Not run: Node 20 (CI version). Not run: live API (forbidden for this task).

## Open issues / risks
1. Live Haiku 5.5 unverified: forced `tool_choice` + `strict` + `thinking:disabled` + `effort:low` in one request. T13 must confirm 200s. If 400, drop `thinking` field first.
2. `cache_control` on the system prompt may be below Haiku's minimum cacheable length and silently not cache. Check `usage.cache_read_input_tokens` in T13.
3. Mock eval validates the pipeline only. Model accuracy on the 58 sentences is unmeasured.
4. Bank-to-bank transfer (T03 open issue 1) is not representable. Model will likely clarify or pick BANK_TRANSFER with `bank_account`.
5. Default unit toman when unstated (T06 rule). Risky for small amounts without unit. T07 cross-check should flag.
6. Persian lint (`fa_lint --check`) flags bidi-start on prompt field lines (`- amount_text:`). Intentional: the prompt is model input and never rendered.
7. Rate limiter in-process only. Map not pruned. Single instance assumed.
8. Node 20 (CI) not run locally. Engines `>=20`. Vitest 3.2 supports 20.

## Next task must know
- T07 (NL pipeline): POST body `{"text": ...}` with `Authorization: Bearer`. Branch on `needs_clarification` first (`{question}` body has no `type`). Otherwise decode TransactionIntent (ignore `amount_text/amount_value/amount_unit`, or use them to cross-check local parser). Use `amount_rial` for posting, not your own conversion. Check `amount_text` is a substring of input. Treat 502 as retry-or-manual-entry. Never log text.
- T13 (live eval): run `npm run eval` with a live client (add a switch; mock is default in tests). Track `cache_read_input_tokens`.
- Docs: `docs/BACKEND.md` (env, error table), `docs/LLM_PROMPTS.md` (prompt rules, eval scope).
