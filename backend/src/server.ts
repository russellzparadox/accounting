import { createServer as createHttpServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { authenticate } from "./auth.js";
import type { LlmClient } from "./llm.js";
import { LlmTransportError } from "./llm.js";
import type { Logger } from "./log.js";
import { LlmOutputError, parseText } from "./parse.js";
import type { FixedWindowLimiter } from "./ratelimit.js";
import {
  completionBody, completionId, modelsBody, OPENAI_MAX_BODY_BYTES, oaError, type OpenAiError,
  parseChatRequest, streamBody,
} from "./openai_compat.js";
import {
  parseTranscription, readMultipart, SttTransportError, STT_MAX_BODY_BYTES, type SttClient,
} from "./stt.js";
import { validateText } from "./text.js";

export { MAX_TEXT_CHARS } from "./text.js";
export const MAX_BODY_BYTES = 4096;

export interface AppDeps {
  llm: LlmClient;
  tokens: string[];
  limiter: FixedWindowLimiter;
  logger: Logger;
  now?: () => Date;
  /** Model id echoed by /v1/chat/completions when the request names none, and listed by /v1/models. */
  model?: string;
  /** Speech-to-text upstream. Undefined = /v1/audio/transcriptions answers 501 not_configured. */
  stt?: SttClient;
}

/** Calendar date in Asia/Tehran, yyyy-MM-dd. Relative Persian dates resolve against this. */
export function todayTehran(d: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Tehran", year: "numeric", month: "2-digit", day: "2-digit",
  }).format(d);
}

function send(res: ServerResponse, status: number, body: unknown, headers: Record<string, string> = {}): void {
  res.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers });
  res.end(JSON.stringify(body));
}

function sendOpenAi(res: ServerResponse, e: OpenAiError): void {
  send(res, e.status, e.body, e.headers);
}

async function readBody(req: IncomingMessage, limit: number): Promise<string | null> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of req) {
    const b = chunk as Buffer;
    total += b.length;
    if (total > limit) return null;
    chunks.push(b);
  }
  return Buffer.concat(chunks).toString("utf8");
}

/** Raw body bytes, or null when the body exceeds `limit`. */
async function readBytes(req: IncomingMessage, limit: number): Promise<Buffer | null> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of req) {
    const b = chunk as Buffer;
    total += b.length;
    if (total > limit) return null;
    chunks.push(b);
  }
  return Buffer.concat(chunks);
}

/** Accepts exactly {"text": "<non-empty string, <= MAX_TEXT_CHARS code points>"}. Returns trimmed text or null. */
export function validateBody(x: unknown): string | null {
  if (typeof x !== "object" || x === null || Array.isArray(x)) return null;
  const keys = Object.keys(x);
  if (keys.length !== 1 || keys[0] !== "text") return null;
  const t = (x as { text: unknown }).text;
  if (typeof t !== "string") return null;
  return validateText(t);
}

export function createApp(deps: AppDeps): Server {
  const { logger } = deps;
  const modelId = deps.model ?? "claude-haiku-5-5";
  const nowSec = () => Math.floor((deps.now ? deps.now() : new Date()).getTime() / 1000);

  /** Shared bearer auth for the OpenAI-shaped routes. Returns the rate-limit key or null after replying. */
  function openAiAuth(req: IncomingMessage, res: ServerResponse): string | null {
    const auth = authenticate(req.headers.authorization, deps.tokens);
    if (!auth.ok) {
      logger.info("auth_rejected", { status: 401 });
      sendOpenAi(res, oaError(401, "invalid_request_error", "invalid_api_key",
        "Incorrect API key provided.", null, { "www-authenticate": "Bearer" }));
      return null;
    }
    return auth.key;
  }

  async function chatCompletions(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const started = Date.now();
    if (req.method !== "POST") {
      return sendOpenAi(res, oaError(405, "invalid_request_error", "method_not_allowed", "Use POST.", null, { allow: "POST" }));
    }
    const key = openAiAuth(req, res);
    if (key === null) return;

    const rl = deps.limiter.check(key);
    if (!rl.allowed) {
      logger.info("rate_limited", { status: 429, key });
      return sendOpenAi(res, oaError(429, "requests", "rate_limit_exceeded",
        "Rate limit reached for requests.", null, { "retry-after": String(rl.retryAfterSec) }));
    }

    const raw = await readBody(req, OPENAI_MAX_BODY_BYTES);
    if (raw === null) {
      return sendOpenAi(res, oaError(413, "invalid_request_error", "request_too_large", "Request body is too large."));
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(raw);
    } catch {
      return sendOpenAi(res, oaError(400, "invalid_request_error", "invalid_json", "Request body is not valid JSON."));
    }
    const chat = parseChatRequest(parsed);
    if (!chat.ok) return sendOpenAi(res, chat.err);

    const { req: c } = chat;
    const id = completionId();
    const created = nowSec();
    const model = c.model ?? modelId;
    const today = todayTehran(deps.now ? deps.now() : new Date());
    try {
      const result = await parseText({ text: c.text, today }, deps.llm);
      // Never log the text here. DEBUG_LOG_TEXT is honoured only by the /v1/parse log line.
      logger.info("chat_parse", {
        status: 200, outcome: result.kind, stream: c.stream, tools: c.hasTools, key, ms: Date.now() - started,
      });
      if (c.stream) {
        res.writeHead(200, { "content-type": "text/event-stream; charset=utf-8", "cache-control": "no-store" });
        return void res.end(streamBody({ id, created, model, result, hasTools: c.hasTools }));
      }
      return send(res, 200, completionBody({ id, created, model, result, hasTools: c.hasTools }));
    } catch (e) {
      const reason = e instanceof LlmOutputError || e instanceof LlmTransportError ? e.code : "unexpected";
      const upstream = e instanceof LlmTransportError && e.status !== undefined ? e.status : null;
      logger.info("chat_parse_failed", {
        status: 502, reason, error: e instanceof Error ? e.name : "unknown", upstream_status: upstream,
        key, ms: Date.now() - started,
      });
      const code = e instanceof LlmOutputError ? "invalid_llm_output" : "llm_unavailable";
      return sendOpenAi(res, oaError(502, "server_error", code, "The upstream model failed to return a valid result."));
    }
  }

  /** POST /v1/audio/transcriptions. Audio and transcript are never logged (text only with DEBUG_LOG_TEXT=1). */
  async function transcriptions(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const started = Date.now();
    if (req.method !== "POST") {
      return sendOpenAi(res, oaError(405, "invalid_request_error", "method_not_allowed", "Use POST.", null, { allow: "POST" }));
    }
    const key = openAiAuth(req, res);
    if (key === null) return;
    if (!deps.stt) {
      return sendOpenAi(res, oaError(501, "server_error", "not_configured",
        "Speech-to-text is not configured on this server. Set STT_BASE_URL."));
    }

    const rl = deps.limiter.check(key);
    if (!rl.allowed) {
      logger.info("rate_limited", { status: 429, key });
      return sendOpenAi(res, oaError(429, "requests", "rate_limit_exceeded",
        "Rate limit reached for requests.", null, { "retry-after": String(rl.retryAfterSec) }));
    }

    const raw = await readBytes(req, STT_MAX_BODY_BYTES);
    if (raw === null) {
      return sendOpenAi(res, oaError(413, "invalid_request_error", "request_too_large", "Request body is too large."));
    }
    const form = await readMultipart(raw, req.headers["content-type"]);
    if (form === null) {
      return sendOpenAi(res, oaError(400, "invalid_request_error", "invalid_multipart",
        "Expected a multipart/form-data body."));
    }
    const parsed = await parseTranscription(form);
    if (!parsed.ok) return sendOpenAi(res, parsed.err);

    const { value, bytes } = parsed;
    try {
      const out = await deps.stt.transcribe(value);
      logger.info("stt", {
        status: 200, outcome: "ok", format: value.format, bytes, key, ms: Date.now() - started, text: out.text,
      });
      if (value.format === "text") {
        res.writeHead(200, { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" });
        return void res.end(out.text);
      }
      return send(res, 200, value.format === "verbose_json" ? { ...out.verbose, text: out.text } : { text: out.text });
    } catch (e) {
      const reason = e instanceof SttTransportError ? e.code : "unexpected";
      const upstream = e instanceof SttTransportError && e.status !== undefined ? e.status : null;
      logger.info("stt_failed", {
        status: e instanceof SttTransportError && e.code === "stt_rate_limited" ? 429
          : e instanceof SttTransportError && e.code === "stt_bad_audio" ? 400 : 502,
        reason, error: e instanceof Error ? e.name : "unknown", upstream_status: upstream, key,
        bytes, ms: Date.now() - started,
      });
      if (e instanceof SttTransportError && e.code === "stt_rate_limited") {
        return sendOpenAi(res, oaError(429, "requests", "rate_limit_exceeded",
          "The speech provider is rate limited. Try again shortly."));
      }
      if (e instanceof SttTransportError && e.code === "stt_bad_audio") {
        return sendOpenAi(res, oaError(400, "invalid_request_error", "invalid_audio",
          "The speech provider could not decode this audio."));
      }
      return sendOpenAi(res, oaError(502, "server_error", "stt_unavailable",
        "The speech provider failed to return a transcript."));
    }
  }

  async function models(req: IncomingMessage, res: ServerResponse): Promise<void> {
    if (req.method !== "GET") {
      return sendOpenAi(res, oaError(405, "invalid_request_error", "method_not_allowed", "Use GET.", null, { allow: "GET" }));
    }
    if (openAiAuth(req, res) === null) return;
    return send(res, 200, modelsBody(modelId, nowSec()));
  }

  async function route(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const started = Date.now();
    const path = new URL(req.url ?? "/", "http://localhost").pathname;

    if (path === "/healthz") {
      if (req.method !== "GET") return send(res, 405, { error: "method_not_allowed" }, { allow: "GET" });
      return send(res, 200, { status: "ok" });
    }
    if (path === "/v1/chat/completions") return chatCompletions(req, res);
    if (path === "/v1/models") return models(req, res);
    if (path === "/v1/audio/transcriptions") return transcriptions(req, res);
    if (path !== "/v1/parse") return send(res, 404, { error: "not_found" });
    if (req.method !== "POST") return send(res, 405, { error: "method_not_allowed" }, { allow: "POST" });

    const auth = authenticate(req.headers.authorization, deps.tokens);
    if (!auth.ok) {
      logger.info("auth_rejected", { status: 401 });
      return send(res, 401, { error: "unauthorized" }, { "www-authenticate": "Bearer" });
    }

    const rl = deps.limiter.check(auth.key);
    if (!rl.allowed) {
      logger.info("rate_limited", { status: 429, key: auth.key });
      return send(res, 429, { error: "rate_limited" }, { "retry-after": String(rl.retryAfterSec) });
    }

    const raw = await readBody(req, MAX_BODY_BYTES);
    if (raw === null) return send(res, 413, { error: "too_large" });

    let parsed: unknown;
    try {
      parsed = JSON.parse(raw);
    } catch {
      return send(res, 400, { error: "bad_json" });
    }
    const text = validateBody(parsed);
    if (text === null) return send(res, 400, { error: "bad_request" });

    const today = todayTehran(deps.now ? deps.now() : new Date());
    try {
      const result = await parseText({ text, today }, deps.llm);
      logger.info("parse", {
        status: 200, outcome: result.kind, key: auth.key, ms: Date.now() - started, text,
      });
      return send(res, 200, result.body);
    } catch (e) {
      // Log class and code only. Never the message, never the text unless DEBUG_LOG_TEXT=1.
      const reason = e instanceof LlmOutputError || e instanceof LlmTransportError
        ? e.code
        : "unexpected";
      const upstream = e instanceof LlmTransportError && e.status !== undefined ? e.status : null;
      logger.info("parse_failed", {
        status: 502, reason, error: e instanceof Error ? e.name : "unknown",
        upstream_status: upstream, key: auth.key, ms: Date.now() - started, text,
      });
      const error = e instanceof LlmOutputError ? "invalid_llm_output" : "llm_unavailable";
      return send(res, 502, { error });
    }
  }

  return createHttpServer((req, res) => {
    route(req, res).catch(() => {
      logger.info("internal_error", { status: 500 });
      if (!res.headersSent) {
        const p = new URL(req.url ?? "/", "http://localhost").pathname;
        if (p.startsWith("/v1/chat/") || p === "/v1/models" || p === "/v1/audio/transcriptions") {
          sendOpenAi(res, oaError(500, "server_error", "internal", "Internal error."));
        } else {
          send(res, 500, { error: "internal" });
        }
      } else res.end();
    });
  });
}
