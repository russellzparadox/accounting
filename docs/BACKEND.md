# BACKEND

Status: T06 done. Node 20+ (CI: Node 20). TypeScript, node:http, no framework.

## Run
- `cd backend && npm ci && npm test && npm run build`
- `npm run eval` (mock eval, verbose)
- `npm start` after build. Needs env below. Refuses to start without `ANTHROPIC_API_KEY` or `PROXY_TOKENS`.

## Env
| var | default | note |
|---|---|---|
| `ANTHROPIC_API_KEY` | none (required) | Read only here. Never returned, never logged. |
| `ANTHROPIC_MODEL` | `claude-haiku-5-5` | |
| `PROXY_TOKENS` | none (required) | Comma list of bearer tokens. Empty = reject all. |
| `RATE_LIMIT_PER_MIN` | `30` | Fixed window per token. |
| `DEBUG_LOG_TEXT` | off | Only `1` logs request text. |
| `PORT` | `8787` | |

## Endpoints
- `POST /v1/parse`: body `{"text": "..."}`. Auth required.
- `GET /healthz`: `{"status":"ok"}`. No auth.

## Request rules
- Body must be JSON with exactly one key `text` (string). Trimmed, 1 to 500 code points. Body <= 4096 bytes.
- `today` (Gregorian yyyy-MM-dd) is computed server side in Asia/Tehran.

## Responses
| status | body | when |
|---|---|---|
| 200 | intent (see handout T06 contract) | valid transaction |
| 200 | `{"needs_clarification":{"question":"..."}}` | LLM asks a question |
| 400 | `{"error":"bad_json"}` / `{"error":"bad_request"}` | invalid body |
| 401 | `{"error":"unauthorized"}` + `WWW-Authenticate: Bearer` | no/wrong token |
| 404 | `{"error":"not_found"}` | other path |
| 405 | `{"error":"method_not_allowed"}` | wrong method |
| 413 | `{"error":"too_large"}` | body > 4 KB |
| 429 | `{"error":"rate_limited"}` + `Retry-After` (s) | over N/min for token |
| 502 | `{"error":"invalid_llm_output"}` | LLM output fails schema or field rules |
| 502 | `{"error":"llm_unavailable"}` | SDK/transport error, no tool_use, max_tokens/refusal |
| 500 | `{"error":"internal"}` | unexpected |

## Pipeline (`src/parse.ts`)
1. LLM tool input -> `llm_output.schema.json` (strict).
2. If `needs_clarification` set -> clarification body.
3. Require type, amount_text, amount_value, amount_unit, date. Check date calendar validity.
4. `amount_rial = amount_value * (toman ? 10 : 1)` in `src/amount.ts`. Safe integer, >= 1.
5. Field-rule table (`src/rules.ts`, from T03/ACCOUNTING_RULES section 8).
6. Response -> `transaction_intent.schema.json`.

## Security and logging
- Auth: constant-time compare of bearer token against `PROXY_TOKENS`. Rate-limit key = 16-hex hash of token.
- Logs: JSON lines to stdout. Fields: event, status, outcome, key (hash), ms, reason, error class name, upstream status. `text` dropped unless `DEBUG_LOG_TEXT=1`. Never logs SDK messages, headers or keys.

## Files
- `src/` server.ts, parse.ts, auth.ts, ratelimit.ts, llm.ts, prompt.ts, rules.ts, amount.ts, validate.ts, log.ts, config.ts, index.ts
- `schema/` llm_output.schema.json (LLM tool input), transaction_intent.schema.json (response)
- `test/` contract, unit, llm (fake transport), server (HTTP), eval
- `eval/` sentences.fa.json, harness.ts

## Known limits
- Rate limiter is in-process (one instance only). Map never pruned (fine for v1 single instance).
- No live API calls in tests.
