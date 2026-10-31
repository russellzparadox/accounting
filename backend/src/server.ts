import { createServer as createHttpServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { authenticate } from "./auth.js";
import type { LlmClient } from "./llm.js";
import { LlmTransportError } from "./llm.js";
import type { Logger } from "./log.js";
import { LlmOutputError, parseText } from "./parse.js";
import type { FixedWindowLimiter } from "./ratelimit.js";

export const MAX_BODY_BYTES = 4096;
export const MAX_TEXT_CHARS = 500;

export interface AppDeps {
  llm: LlmClient;
  tokens: string[];
  limiter: FixedWindowLimiter;
  logger: Logger;
  now?: () => Date;
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

async function readBody(req: IncomingMessage): Promise<string | null> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of req) {
    const b = chunk as Buffer;
    total += b.length;
    if (total > MAX_BODY_BYTES) return null;
    chunks.push(b);
  }
  return Buffer.concat(chunks).toString("utf8");
}

/** Accepts exactly {"text": "<non-empty string, <= MAX_TEXT_CHARS code points>"}. Returns trimmed text or null. */
export function validateBody(x: unknown): string | null {
  if (typeof x !== "object" || x === null || Array.isArray(x)) return null;
  const keys = Object.keys(x);
  if (keys.length !== 1 || keys[0] !== "text") return null;
  const t = (x as { text: unknown }).text;
  if (typeof t !== "string") return null;
  const trimmed = t.trim();
  const n = [...trimmed].length;
  return n >= 1 && n <= MAX_TEXT_CHARS ? trimmed : null;
}

export function createApp(deps: AppDeps): Server {
  const { logger } = deps;

  async function route(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const started = Date.now();
    const path = new URL(req.url ?? "/", "http://localhost").pathname;

    if (path === "/healthz") {
      if (req.method !== "GET") return send(res, 405, { error: "method_not_allowed" }, { allow: "GET" });
      return send(res, 200, { status: "ok" });
    }
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

    const raw = await readBody(req);
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
      if (!res.headersSent) send(res, 500, { error: "internal" });
      else res.end();
    });
  });
}
