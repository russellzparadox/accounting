# T06b — OpenAI-compatible backend (both directions)
Status: todo (queued; run right after T08a)
Depends: T06
Read first: CLAUDE.md, handouts/T06-backend-llm-proxy.md, handouts/T03-domain-core.md (intent shape), docs/BACKEND.md, docs/LLM_PROMPTS.md, `backend/src/**`, `.github/workflows/backend.yml`
Skills to invoke: claude-api (Anthropic path stays), persian-writing:persian-writing (if prompt text changes)

## Goal (user decision: "both sides", structured output via tool calling + strict JSON schema)
1. INBOUND: the proxy exposes an OpenAI-compatible API so any OpenAI client/SDK can talk to it: `POST /v1/chat/completions` and `GET /v1/models`. The existing `POST /v1/parse` (used by the Android app, T07) stays unchanged and keeps working.
2. UPSTREAM: the proxy can use any OpenAI-compatible provider (OpenRouter, vLLM/Ollama/LM Studio, OpenAI, etc.) in addition to Anthropic. Selected by env `LLM_PROVIDER=anthropic|openai` (default anthropic). The app still talks ONLY to the proxy; no LLM key in the app.

## Inbound spec (`/v1/chat/completions`)
- Auth: same Bearer token + rate limit as `/v1/parse`. Errors in OpenAI error shape `{"error":{"message","type","param","code"}}` with matching HTTP codes (401 invalid_api_key, 429 rate_limit_exceeded + `Retry-After`, 400 invalid_request_error, 502/500 server_error).
- Request: standard fields `model`, `messages[]` (roles system/user/assistant; text content as string or array of `{type:"text"}` parts), `tools`, `tool_choice`, `response_format`, `stream`, `temperature`/`max_tokens` accepted & ignored, unknown fields ignored. The proxy is an accounting parser, NOT a general chat: it takes the LAST user message as the sentence, ignores client system prompts (documented), and runs the same parse pipeline as `/v1/parse` (same prompt, same schema, same validation, same `amount_rial` computation).
- Response (non-stream): `{id:"chatcmpl-...", object:"chat.completion", created, model, choices:[{index:0, message:{role:"assistant", content, tool_calls?}, finish_reason}], usage:{prompt_tokens, completion_tokens, total_tokens}}`.
  - If request has `tools`: reply with `tool_calls:[{id, type:"function", function:{name:"record_transaction" (or "ask_clarification"), arguments:"<JSON string>"}}]`, `content:null`, `finish_reason:"tool_calls"`.
  - Else: `content` = JSON string of the intent/clarification, `finish_reason:"stop"`.
  - `model` echoes the request's model string or the configured one. `GET /v1/models` lists the configured model id(s).
- `stream:true`: support minimal SSE (single content/tool_calls delta chunk, then final chunk with finish_reason, then `data: [DONE]`), `Content-Type: text/event-stream`.
- Never log message text unless `DEBUG_LOG_TEXT=1` (existing rule). Never return upstream keys/URLs in errors.

## Upstream OpenAI-compatible client (`backend/src/llm_openai.ts`, behind the existing LLM client interface)
- Env: `OPENAI_BASE_URL` (default https://api.openai.com/v1), `OPENAI_API_KEY` (optional for local servers), `LLM_MODEL` (or reuse `ANTHROPIC_MODEL` for anthropic), `LLM_TIMEOUT_MS`.
- Use Node 22 built-in `fetch` (no heavy SDK dependency). Call `POST {base}/chat/completions` with `tools:[{type:"function", function:{name, description, parameters: <same JSON schema>, strict:true}}]` and forced `tool_choice:{type:"function", function:{name}}`.
- Fallbacks in order when the provider ignores tools: (a) `response_format:{type:"json_schema", json_schema:{name, schema, strict:true}}`; (b) JSON mode + extract the first JSON object from `content`. Always validate with the existing schema validator; invalid -> 502 `BadUpstream`. Document which providers were assumed to work; mark live behaviour unverified until T13.
- Same prompt caching n/a; same prompt text; amounts still returned as stated (no arithmetic by the LLM).

## Deliverables
- `backend/src/openai_compat.ts` (inbound routes + (de)serialisation), `backend/src/llm_openai.ts`, config changes, `.env.example`, route registration; keep `/v1/parse`.
- Tests (no network, no keys): inbound contract tests for every response shape above incl. error shapes and SSE; upstream client tests against a local stub HTTP server covering tools path, json_schema fallback, JSON-mode fallback, timeout, 401/429/5xx mapping; a compat test using the official `openai` npm package as devDependency pointed at the local server (chat.completions.create with tools, and stream) if it installs; existing 109 tests still pass; extend eval harness to run through the OpenAI inbound path in mock mode.
- Docs: `docs/BACKEND.md` (new endpoints, env table, curl examples for inbound + provider setup examples for OpenRouter/Ollama), `docs/LLM_PROMPTS.md` note, `handouts/T06b-openai-compat.md`.

## Acceptance criteria
- [ ] `cd backend && npm ci && npm test && npm run build` pass locally; counts in handout (>= 109 existing + new).
- [ ] `/v1/parse` response unchanged (existing contract tests green).
- [ ] Inbound shapes validated against the OpenAI chat-completions shape (and the official `openai` client in a test).
- [ ] Both providers selectable by env; default unchanged (anthropic).
- [ ] No secrets in logs/errors; no new runtime dependency unless justified in handout.
- [ ] Push once at the end (max 3 pushes); `backend` GitHub workflow run is green (check via mcp__github__ tools, tail_lines <= 90). Commit prefix `T06b:`, branch `claude/dreamy-mccarthy-mczm8c` only.
- [ ] Android code untouched.

## Out of scope
- Android client changes (app keeps `/v1/parse`), live provider eval (T13), deployment.
