# Handout T06b — openai-compat
Status: done (offline). Live OpenAI-compatible behaviour unverified until T13.

## Summary
- Inbound: `POST /v1/chat/completions` (tool call or JSON content, SSE) and `GET /v1/models`. Same bearer auth and rate limit. OpenAI error shape. Last user message is the sentence. Client system prompts ignored.
- Upstream: `LLM_PROVIDER=openai` (any OpenAI-compatible base URL). Fetch, no SDK. Tool call, then json_schema, then json_object fallback. Anthropic path and `/v1/parse` unchanged.
- Tests: 175 pass, no network, no keys (baseline 109). Mock eval 58/58 on three paths. No new runtime dependency. `openai` added as devDependency only.

## Files created/changed
- `backend/src/openai_compat.ts` (inbound helpers), `backend/src/llm_openai.ts` (upstream), `backend/src/text.ts` (shared text rule), `backend/src/server.ts` (routes, /v1/parse branch untouched), `backend/src/config.ts` (provider env), `backend/src/index.ts` (provider select)
- `backend/eval/harness.ts` (`runEvalWith`, `viaOpenAi`), `backend/test/eval.test.ts`
- `backend/test/openai_compat.test.ts` (33), `backend/test/llm_openai.test.ts` (21), `backend/test/openai_sdk.test.ts` (6), `backend/test/config.test.ts` (4)
- `backend/.env.example`, `backend/package.json`, `backend/package-lock.json` (+ `openai` ^5.23.2 devDependency)
- `docs/BACKEND.md` (rewritten), `docs/LLM_PROMPTS.md` (notes), `tasks/T06b-openai-compat.md` (Status: done)

## Public interfaces / contracts
### Inbound
- `POST /v1/chat/completions`. Body: `messages[]` (roles system|developer|user|assistant|tool), `model?`, `tools?`, `tool_choice?`, `stream?`. Others ignored. Body <= 32 KB.
- Tools present (and tool_choice != none): `message.tool_calls[0]` = `record_transaction` (intent JSON in `arguments`) or `ask_clarification` (`{"question"}`). `content` null, `finish_reason` `tool_calls`.
- No tools: `message.content` = intent JSON or `{"needs_clarification":{...}}`. `finish_reason` `stop`.
- Response: `{id:"chatcmpl-<24hex>", object:"chat.completion", created, model, choices:[{index:0,message,finish_reason}], usage:{0,0,0}}`.
- Stream: `data: <delta chunk>` (whole payload), `data: <final chunk, finish_reason>`, `data: [DONE]`.
- Errors: `{"error":{"message","type","param","code"}}`. 400 invalid_request_error (`invalid_json` | null, `param`), 401 `invalid_api_key` (+WWW-Authenticate), 405 `method_not_allowed`, 413 `request_too_large`, 429 `rate_limit_exceeded` (+Retry-After, type `requests`), 502 server_error (`invalid_llm_output` | `llm_unavailable`), 500 `internal`.
- `GET /v1/models` -> `{object:"list", data:[{id:<configured model>, object:"model", created, owned_by:"accounting-proxy"}]}`. Auth required, not rate limited.
### Upstream
- `OpenAiLlmClient({baseUrl, model, apiKey?, timeoutMs, fetchImpl?})` implements `LlmClient.extract`. `POST {baseUrl}/chat/completions`.
- Errors (LlmTransportError codes, all -> 502 llm_unavailable at the edge): `llm_auth_failed` (401/403), `llm_rate_limited` (429), `llm_upstream_error` (5xx), `llm_bad_request` (other 4xx), `llm_timeout`, `llm_request_failed`. `status` = upstream HTTP status.

## Env table
| var | default | provider |
|---|---|---|
| `LLM_PROVIDER` | `anthropic` | both (anything else refuses to start) |
| `ANTHROPIC_API_KEY` | required for anthropic | anthropic |
| `ANTHROPIC_MODEL` | `claude-haiku-5-5` | anthropic |
| `LLM_MODEL` | required for openai | openai |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | openai |
| `OPENAI_API_KEY` | none (optional) | openai |
| `LLM_TIMEOUT_MS` | `20000` | openai |
| `PROXY_TOKENS`, `RATE_LIMIT_PER_MIN`, `DEBUG_LOG_TEXT`, `PORT` | as before | both |

## Decisions made (+why)
- Upstream fallbacks: tool call -> json_schema -> json_object. Next step only on 400 (refused) or no tool call / invalid output. Why: providers differ on tools vs response_format. 401/429/5xx do not fall back. Why: fallbacks would hide auth or quota errors.
- Errors reuse the existing 502 codes (`invalid_llm_output`, `llm_unavailable`) instead of a new `BadUpstream` code. Why: the Android client (T07) branches on those already; the spec's `BadUpstream` maps onto them.
- Inbound body limit 32 KB (vs 4 KB for /v1/parse). Why: OpenAI SDK requests carry tool schemas and system text.
- `usage` returned as zeros. Why: tokens not metered in v1; the field is needed for SDK compatibility.
- Tool names fixed (`record_transaction`, `ask_clarification`) regardless of client tool names. Why: the proxy owns the schema.
- Client system prompts ignored. Why: prompt injection and the same pipeline rule.
- `/v1/models` skips rate limit. Why: cheap, used by SDK discovery.
- Official `openai` package used only as devDependency test. Why: prove inbound compatibility. Installed fine (5.23.2).

## How verified (commands + result)
- `cd backend && npm ci && npm test && npm run build` (Node 22.23.4 locally). Test Files 9 passed, Tests 175 passed. `EVAL mock: 58/58`, `EVAL openai-tools: 58/58`, `EVAL openai-content: 58/58`. `tsc` build OK.
- Per-file: contract 70, unit 12, llm 4, server 20 (= /v1/parse baseline 109 unchanged), eval 5, openai_compat 33, llm_openai 21, openai_sdk 6, config 4.
- Not run: Node 20 (CI) locally; CI backend workflow is the check. Live API: forbidden for this task.
- CI: `backend` workflow for the WIP checkpoint `d7da05a` = success (run 41189164327). For the final commit, see `https://github.com/russellzparadox/accounting/actions?query=branch%3Aclaude%2Fdreamy-mccarthy-mczm8c+workflow%3Abackend`.

## Open issues / risks
1. Live providers unverified: OpenAI strict schema accepts `pattern`/`minLength`? Unknown per provider. Fallbacks cover a 400, not a silent wrong answer. T13 must confirm.
2. Tool-call JSON from providers may differ in escaping. Parsed with JSON.parse, validated by schema.
3. `usage` zeros. Cost tracking needs metering later.
4. Rate limiter in-process, single instance (unchanged).
5. WIP checkpoint commit `d7da05a` (orchestrator) is in history below the final commit. Not force-pushed.
6. Persian prompt text unchanged. JSON-mode adds an English schema line to the system text only on that fallback.

## Next task must know
- Client (T07) unchanged: keep `POST /v1/parse`, `{"text":...}`, Bearer. Error codes unchanged.
- T13 (live eval): run against anthropic and against one openai-compatible provider. Track which fallback step was used (add a log field or count per step). Check `usage` for Anthropic cache reads.
- Provider setup examples: `docs/BACKEND.md` (OpenRouter, Ollama).
