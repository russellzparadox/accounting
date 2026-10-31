# BACKEND

Status: T06 done, T06b done (offline; live provider behaviour unverified until T13), T09c backend done (STT offline; live provider unverified). Node 20+ (CI: Node 20). TypeScript, node:http, no framework. Only runtime dependencies: `@anthropic-ai/sdk`, `ajv`. Dev-only: `openai` (contract test). No new dependency for STT (multipart via built-in `Request.formData()`).

## Run
- `cd backend && npm ci && npm test && npm run build`
- `npm run eval` (mock eval, verbose: /v1/parse, openai-tools, openai-content)
- `npm start` after build. Config from env (see `backend/.env.example`). Refuses to start without `PROXY_TOKENS`, and without `ANTHROPIC_API_KEY` when `LLM_PROVIDER=anthropic`.

## Env
| var | default | note |
|---|---|---|
| `PROXY_TOKENS` | none (required) | Comma list of bearer tokens. Empty = reject all. |
| `LLM_PROVIDER` | `anthropic` | `anthropic` or `openai`. Anything else = refuse to start. |
| `ANTHROPIC_API_KEY` | none (required if anthropic) | Read only here. Never returned, never logged. |
| `ANTHROPIC_MODEL` | `claude-haiku-5-5` | anthropic only. |
| `LLM_MODEL` | none (required if openai) | openai only. Model id sent upstream; listed by `/v1/models`. |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | openai only. Trailing slashes stripped. |
| `OPENAI_API_KEY` | none (optional) | openai only. Sent as Bearer when set. Never returned, never logged. |
| `LLM_TIMEOUT_MS` | `20000` | openai only. Per upstream request. |
| `RATE_LIMIT_PER_MIN` | `30` | Fixed window per token. |
| `DEBUG_LOG_TEXT` | off | Only `1` logs request text. |
| `STT_BASE_URL` | none (speech disabled) | OpenAI-compatible base URL for `/v1/audio/transcriptions`. Unset = 501 `not_configured`. Trailing slashes stripped. |
| `STT_API_KEY` | none (optional) | Sent as Bearer when set. Never returned, never logged. |
| `STT_MODEL` | `whisper-1` | Sent upstream as `model`. The client's `model` field is ignored. |
| `STT_TIMEOUT_MS` | `30000` | Per upstream transcription request. |
| `PORT` | `8787` | |

## Endpoints
| method path | auth | body/notes |
|---|---|---|
| `POST /v1/parse` | Bearer | App endpoint (T07). Body `{"text": "..."}`. Unchanged by T06b. |
| `POST /v1/chat/completions` | Bearer | OpenAI chat shape (inbound). See below. |
| `POST /v1/audio/transcriptions` | Bearer | Speech-to-text, OpenAI shape, multipart. See below. |
| `GET /v1/models` | Bearer | Lists the configured model id. |
| `GET /healthz` | none | `{"status":"ok"}`. |

### /v1/parse (app)
- Body: exactly one key `text` (string), trimmed, 1 to 500 code points, body <= 4 KB.
- 200 intent, or 200 `{"needs_clarification":{"question":"..."}}`.
- Errors: `{"error":"bad_json|bad_request|unauthorized|not_found|method_not_allowed|too_large|rate_limited|invalid_llm_output|llm_unavailable|internal"}`. Shapes in handout T06.

### /v1/chat/completions (OpenAI-compatible inbound)
- The proxy is an accounting parser, not a chat model. It takes the LAST user message as the sentence. System, developer, assistant and tool messages are ignored. Client system prompts have no effect.
- Content: string, or array of `{"type":"text","text":...}` parts (joined with newlines). Any other part type = 400.
- Same text rules as /v1/parse (1 to 500 code points after trim). Body <= 32 KB (client tool schemas are large).
- Sampling fields (`temperature`, `max_tokens`, `top_p`, `user`, ...) are accepted and ignored. Unknown fields are ignored.
- `model`: echoed when sent, else the configured model. Any string is accepted.
- `tools` present (and `tool_choice` not `none`): reply is a tool call. `record_transaction` carries the intent JSON. `ask_clarification` carries `{"question":...}`. `content` null, `finish_reason` `tool_calls`.
- No tools: `message.content` is the intent JSON, or `{"needs_clarification":{...}}`. `finish_reason` `stop`.
- `stream: true`: SSE. One delta chunk (whole payload), one final chunk with `finish_reason`, then `data: [DONE]`. Errors before streaming are plain JSON errors.
- `usage` is present with zeros (tokens are not metered in v1).
- `id` = `chatcmpl-<24 hex>`. `created` = unix seconds.

Errors (OpenAI shape `{"error":{"message","type","param","code"}}`):
| status | type | code | when |
|---|---|---|---|
| 400 | invalid_request_error | `invalid_json` / null | bad JSON, bad body, bad messages, bad text. `param` names the field. |
| 401 | invalid_request_error | `invalid_api_key` | missing or wrong token. `WWW-Authenticate: Bearer`. |
| 405 | invalid_request_error | `method_not_allowed` | wrong method. `Allow` header. |
| 413 | invalid_request_error | `request_too_large` | body > 32 KB. |
| 429 | requests | `rate_limit_exceeded` | over N/min for token. `Retry-After` (s). |
| 502 | server_error | `invalid_llm_output` / `llm_unavailable` | upstream failed or returned invalid output. |
| 500 | server_error | `internal` | unexpected. |

`/v1/models` and `/v1/chat/completions` share auth. `/v1/models` is not rate limited.

### /v1/audio/transcriptions (OpenAI-compatible speech-to-text)
- Multipart/form-data. Fields: `file` (required), `language` (default `fa`, ISO 639-1 base code), `prompt` (default: Persian accounting vocabulary; empty string = no hint; max 1000 chars), `response_format` (`json` default | `text` | `verbose_json`), `model` and `temperature` (accepted, ignored: `STT_MODEL` is always used).
- Audio types: `audio/mp4` (m4a), `audio/aac`, `audio/ogg`/opus, `audio/webm`, `audio/wav`, `audio/mpeg` (mp3). If the part has no usable MIME, the filename extension is used. Anything else = 415. Upstream sees `audio.<ext>` (providers pick the decoder from the extension; OpenAI does not accept `aac` or `opus`, so the app sends m4a).
- Size: file <= 10 MB (`413 file_too_large`), body <= 10 MB + 64 KB (`413 request_too_large`). Empty file = 400. Duration is not measured (no decoding in the proxy). The app caps recordings at 30 s.
- Response: `200 {"text": "..."}`. `text` format: `text/plain`. `verbose_json`: upstream fields plus `text`.
- Errors (OpenAI shape): 400 `invalid_request_error` `invalid_multipart` / `missing_file` / `empty_file` / `invalid_language` / `invalid_response_format` / `prompt_too_long` / `invalid_audio` (upstream rejected the audio); 401 `invalid_api_key`; 405; 413 `file_too_large` / `request_too_large`; 415 `unsupported_file_type`; 429 `rate_limit_exceeded` (own limit, or upstream rate limit); 501 `not_configured` (`STT_BASE_URL` unset); 502 `server_error` `stt_unavailable` (upstream auth, timeout, network, 5xx, or invalid output).
- Audio bytes are held in memory for the request only. They are not written to disk and never logged. The transcript is logged only with `DEBUG_LOG_TEXT=1`.
- Shares bearer auth and the per-token rate limit with the other endpoints.

## Upstream providers
- `anthropic` (default): `@anthropic-ai/sdk`, forced tool `record_transaction`, `strict: true`, thinking disabled, effort low, prompt caching on the system block. Unchanged by T06b.
- `openai`: `backend/src/llm_openai.ts`, Node `fetch`, no SDK. `POST {OPENAI_BASE_URL}/chat/completions`. Attempts in order, each validated with the LLM schema:
  1. Forced tool call `record_transaction` (`strict: true`, schema as `parameters`).
  2. Only if (1) was refused (HTTP 400) or ignored (no tool call): `response_format: json_schema` (strict).
  3. Only if (2) was refused or invalid: `response_format: json_object`, schema text added to the system message, first JSON object taken from `content`.
  Output still goes through the same validators and field rules as the anthropic path. The backend computes `amount_rial`. The LLM never does arithmetic.
- Upstream errors are mapped to log codes, and the upstream body is never returned: `llm_auth_failed` (401/403), `llm_rate_limited` (429), `llm_upstream_error` (5xx), `llm_bad_request` (other 4xx), `llm_timeout`, `llm_request_failed` (network). All surface to the client as 502 `llm_unavailable`. `upstream_status` is logged.

### Speech-to-text upstream (STT_*)
Any OpenAI-compatible `POST {STT_BASE_URL}/audio/transcriptions`. Set `STT_BASE_URL` to enable.
```
# OpenAI
STT_BASE_URL=https://api.openai.com/v1
STT_API_KEY=sk-REPLACE_ME
STT_MODEL=whisper-1

# Groq (hosted Whisper)
STT_BASE_URL=https://api.groq.com/openai/v1
STT_API_KEY=gsk_REPLACE_ME
STT_MODEL=whisper-large-v3

# Local server (whisper.cpp / faster-whisper servers that expose /v1/audio/transcriptions; check the server first)
STT_BASE_URL=http://127.0.0.1:8000/v1
STT_MODEL=large-v3
```
Live accuracy for Persian with any of these is NOT verified in this repo. Test with real Persian speech before relying on it.

### Provider setup examples
OpenRouter (hosted, OpenAI-compatible):
```
LLM_PROVIDER=openai
OPENAI_BASE_URL=https://openrouter.ai/api/v1
OPENAI_API_KEY=sk-or-REPLACE_ME
LLM_MODEL=anthropic/claude-haiku-4.5
```
Ollama (local, no key; check the model supports tools):
```
LLM_PROVIDER=openai
OPENAI_BASE_URL=http://127.0.0.1:11434/v1
LLM_MODEL=qwen2.5:7b
```
Also: LM Studio (`http://127.0.0.1:1234/v1`), vLLM, OpenAI itself (default base URL). Live behaviour of these providers is NOT verified. Check with the T13 live eval before use.

## Curl examples
```
# /v1/parse (app)
curl -sS http://127.0.0.1:8787/v1/parse -H "authorization: Bearer $PROXY_TOKEN" -H "content-type: application/json" \
  -d '{"text":"۵۰۰٬۰۰۰ تومان نقد خریدم"}'

# chat completions, tools mode
curl -sS http://127.0.0.1:8787/v1/chat/completions -H "authorization: Bearer $PROXY_TOKEN" -H "content-type: application/json" \
  -d '{"model":"accounting","messages":[{"role":"user","content":"۵۰۰٬۰۰۰ تومان نقد خریدم"}],
       "tools":[{"type":"function","function":{"name":"record_transaction","parameters":{"type":"object"}}}]}'

# chat completions, stream
curl -sS -N http://127.0.0.1:8787/v1/chat/completions -H "authorization: Bearer $PROXY_TOKEN" -H "content-type: application/json" \
  -d '{"messages":[{"role":"user","content":"۵۰۰٬۰۰۰ تومان نقد خریدم"}],"stream":true}'

# models
curl -sS http://127.0.0.1:8787/v1/models -H "authorization: Bearer $PROXY_TOKEN"

# speech-to-text (needs STT_BASE_URL on the server; 501 otherwise)
curl -sS http://127.0.0.1:8787/v1/audio/transcriptions -H "authorization: Bearer $PROXY_TOKEN" \
  -F "file=@clip.m4a;type=audio/mp4" -F "language=fa"
```

## Pipeline (`src/parse.ts`, shared by both endpoints)
1. LLM tool input -> `llm_output.schema.json` (strict).
2. If `needs_clarification` set -> clarification body.
3. Require type, amount_text, amount_value, amount_unit, date. Check date calendar validity.
4. `amount_rial = amount_value * (toman ? 10 : 1)` in `src/amount.ts`. Safe integer, >= 1.
5. Field-rule table (`src/rules.ts`, from T03/ACCOUNTING_RULES section 8).
6. Response -> `transaction_intent.schema.json`.

## Security and logging
- Auth: constant-time compare of bearer token against `PROXY_TOKENS`. Rate-limit key = 16-hex hash of token.
- Logs: JSON lines to stdout. Fields: event, status, outcome, key (hash), ms, reason, error class name, upstream status. `text` dropped unless `DEBUG_LOG_TEXT=1`. Never logs SDK messages, upstream bodies, headers or keys. Compat logs (`chat_parse*`) never include text, even with DEBUG_LOG_TEXT.
- Keys: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY` and `STT_API_KEY` are read in `config.ts` and used only for the upstream call.
- STT logs (`stt`, `stt_failed`): status, outcome, format, byte count, key hash, ms, upstream status, error code. Never audio, never the filename, never upstream bodies. `text` only with `DEBUG_LOG_TEXT=1`.

## Files
- `src/` server.ts (routes), parse.ts (pipeline), openai_compat.ts (inbound shapes), llm.ts (anthropic client + LlmClient seam), llm_openai.ts (openai upstream), stt.ts (transcription fields, multipart, STT upstream client), config.ts, index.ts, text.ts, prompt.ts, rules.ts, amount.ts, validate.ts, auth.ts, ratelimit.ts, log.ts
- `schema/` llm_output.schema.json (LLM tool input), transaction_intent.schema.json (response)
- `test/` contract, unit, llm (anthropic fake transport), server (/v1/parse HTTP), openai_compat (inbound contract), llm_openai (upstream vs local stub), openai_sdk (official `openai` package vs local server), stt (transcription route, stub STT upstream, no-log), stt_sdk (official `openai` audio.transcriptions vs local server), config, eval
- `eval/` sentences.fa.json, harness.ts (direct, openai-tools, openai-content)
- `.env.example`

## Known limits
- Rate limiter is in-process (one instance only). Map never pruned (fine for v1 single instance).
- No live API calls in tests. Upstream OpenAI-compatible providers are tested only against the local stub.
- OpenAI `strict` schema support for `pattern`/`minLength` varies by provider. Providers that reject the schema fall back (see Upstream) or fail as 502.
- STT: no duration check (the proxy does not decode audio). Upload size cap is the only bound. Upstream STT is tested only against a local stub; no live provider was called.
