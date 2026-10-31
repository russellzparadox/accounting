// /v1/audio/transcriptions: auth, limits, validation, upstream mapping, no-log guarantee.
// No network and no keys: the proxy and a stub OpenAI-compatible STT upstream run on 127.0.0.1.
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import type { AddressInfo } from "node:net";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { LlmClient } from "../src/llm.js";
import { createLogger } from "../src/log.js";
import { FixedWindowLimiter } from "../src/ratelimit.js";
import { createApp } from "../src/server.js";
import {
  audioExtension, OpenAiSttClient, readMultipart, STT_DEFAULT_PROMPT, STT_MAX_AUDIO_BYTES,
} from "../src/stt.js";

const TOKEN = "tok-stt-123";
const STT_KEY = "stt-key-SECRET-987";
const SECRET_TRANSCRIPT = "متن-صوتی-محرمانه-۱۲۳";
const AUDIO_MARKER = "AUDIO-MARKER-BYTES";
const llm: LlmClient = { extract: async () => { throw new Error("unused"); } };

type Handler = (req: IncomingMessage, body: Buffer, res: ServerResponse) => void;
interface Seen { url: string; auth: string | undefined; ct: string | undefined; body: Buffer }

let up: Server;
let upBase: string;
let upHandler: Handler;
let upSeen: Seen[];

let server: Server | undefined;
let base: string;
let logs: string[];
let logText = false;
let upstreamTimeoutMs = 2000;
let sttEnabled = true;

const json = (res: ServerResponse, status: number, body: unknown): void => {
  res.writeHead(status, { "content-type": "application/json" });
  res.end(JSON.stringify(body));
};

beforeEach(async () => {
  upSeen = [];
  upHandler = (_req, _b, res) => json(res, 200, { text: SECRET_TRANSCRIPT });
  up = createServer((req, res) => {
    const chunks: Buffer[] = [];
    req.on("data", (c: Buffer) => chunks.push(c));
    req.on("end", () => {
      const body = Buffer.concat(chunks);
      upSeen.push({ url: req.url ?? "", auth: req.headers.authorization, ct: req.headers["content-type"], body });
      if (req.url !== "/v1/audio/transcriptions") return json(res, 404, { error: "nope" });
      upHandler(req, body, res);
    });
  });
  await new Promise<void>((r) => up.listen(0, "127.0.0.1", () => r()));
  upBase = `http://127.0.0.1:${(up.address() as AddressInfo).port}/v1`;
  logText = false;
  upstreamTimeoutMs = 2000;
  sttEnabled = true;
});

afterEach(async () => {
  if (server) {
    server.closeAllConnections();
    await new Promise((r) => server!.close(r));
    server = undefined;
  }
  up.closeAllConnections();
  await new Promise((r) => up.close(r));
});

async function start(): Promise<void> {
  logs = [];
  server = createApp({
    llm,
    tokens: [TOKEN],
    limiter: new FixedWindowLimiter(100, 60_000),
    logger: createLogger({ sink: (l) => logs.push(l), logText }),
    model: "configured-model",
    stt: sttEnabled
      ? new OpenAiSttClient({ baseUrl: upBase, model: "whisper-1", apiKey: STT_KEY, timeoutMs: upstreamTimeoutMs })
      : undefined,
  });
  await new Promise<void>((r) => server!.listen(0, "127.0.0.1", () => r()));
  base = `http://127.0.0.1:${(server!.address() as AddressInfo).port}`;
}

const audio = (name = "clip.m4a", type = "audio/mp4", data: Buffer | string = Buffer.from(`${AUDIO_MARKER}-data`)) =>
  ({ name, type, data: typeof data === "string" ? Buffer.from(data) : data });

function form(file: ReturnType<typeof audio> | null, fields: Record<string, string> = {}): FormData {
  const fd = new FormData();
  if (file) fd.append("file", new Blob([file.data], { type: file.type }), file.name);
  for (const [k, v] of Object.entries(fields)) fd.append(k, v);
  return fd;
}

function transcribe(body: BodyInit | null, auth: string | null = `Bearer ${TOKEN}`, contentType?: string): Promise<Response> {
  const headers: Record<string, string> = {};
  if (auth !== null) headers.authorization = auth;
  if (contentType) headers["content-type"] = contentType;
  return fetch(`${base}/v1/audio/transcriptions`, { method: "POST", headers, body });
}

const ok = (fields: Record<string, string> = {}) => transcribe(form(audio(), fields));

describe("auth, method, configuration", () => {
  it("no token -> 401 invalid_api_key, upstream never called", async () => {
    await start();
    const r = await transcribe(form(audio()), null);
    expect(r.status).toBe(401);
    expect(r.headers.get("www-authenticate")).toBe("Bearer");
    expect(((await r.json()) as { error: { code: string } }).error.code).toBe("invalid_api_key");
    expect(upSeen.length).toBe(0);
  });

  it("wrong token -> 401", async () => {
    await start();
    expect((await transcribe(form(audio()), "Bearer nope")).status).toBe(401);
  });

  it("GET -> 405 with Allow: POST", async () => {
    await start();
    const r = await fetch(`${base}/v1/audio/transcriptions`, { headers: { authorization: `Bearer ${TOKEN}` } });
    expect(r.status).toBe(405);
    expect(r.headers.get("allow")).toBe("POST");
  });

  it("STT not configured -> 501 not_configured; authenticated callers only", async () => {
    sttEnabled = false;
    await start();
    const r = await transcribe(form(audio()));
    expect(r.status).toBe(501);
    const body = (await r.json()) as { error: { code: string; message: string } };
    expect(body.error.code).toBe("not_configured");
    expect(body.error.message).toContain("STT_BASE_URL");
    expect((await transcribe(form(audio()), null)).status).toBe(401);
  });

  it("rate limit applies to the transcription route (429 + Retry-After)", async () => {
    await start();
    server!.close();
    server = createApp({
      llm,
      tokens: [TOKEN],
      limiter: new FixedWindowLimiter(1, 60_000),
      logger: createLogger({ sink: () => {}, logText: false }),
      stt: new OpenAiSttClient({ baseUrl: upBase, model: "whisper-1", timeoutMs: 2000 }),
    });
    await new Promise<void>((r) => server!.listen(0, "127.0.0.1", () => r()));
    base = `http://127.0.0.1:${(server!.address() as AddressInfo).port}`;
    expect((await ok()).status).toBe(200);
    const r = await ok();
    expect(r.status).toBe(429);
    expect(Number(r.headers.get("retry-after"))).toBeGreaterThan(0);
    expect(((await r.json()) as { error: { type: string } }).error.type).toBe("requests");
  });
});

describe("request shape and validation", () => {
  it("non-multipart body -> 400 invalid_multipart", async () => {
    await start();
    const r = await transcribe("{}", `Bearer ${TOKEN}`, "application/json");
    expect(r.status).toBe(400);
    expect(((await r.json()) as { error: { code: string } }).error.code).toBe("invalid_multipart");
  });

  it("malformed multipart -> 400 invalid_multipart", async () => {
    await start();
    const r = await transcribe("--nope\r\nbroken", `Bearer ${TOKEN}`, "multipart/form-data; boundary=zzz");
    expect(r.status).toBe(400);
    expect(((await r.json()) as { error: { code: string } }).error.code).toBe("invalid_multipart");
  });

  it("missing file -> 400 missing_file", async () => {
    await start();
    const r = await transcribe(form(null, { model: "whisper-1" }));
    expect(r.status).toBe(400);
    expect(((await r.json()) as { error: { param: string; code: string } }).error).toMatchObject({
      code: "missing_file", param: "file",
    });
  });

  it("empty file -> 400 empty_file", async () => {
    await start();
    const r = await transcribe(form(audio("a.m4a", "audio/mp4", "")));
    expect(r.status).toBe(400);
    expect(((await r.json()) as { error: { code: string } }).error.code).toBe("empty_file");
  });

  it("file over 10 MB -> 413 file_too_large (rejected before any upstream call)", async () => {
    await start();
    const big = Buffer.alloc(STT_MAX_AUDIO_BYTES + 1, 1);
    const r = await transcribe(form(audio("big.m4a", "audio/mp4", big)));
    expect(r.status).toBe(413);
    expect(((await r.json()) as { error: { code: string } }).error.code).toBe("file_too_large");
    expect(upSeen.length).toBe(0);
  });

  it("body far over the limit -> 413 request_too_large", async () => {
    await start();
    const huge = Buffer.alloc(STT_MAX_AUDIO_BYTES + 200 * 1024, 2);
    const r = await transcribe(form(audio("huge.m4a", "audio/mp4", huge)));
    expect(r.status).toBe(413);
    expect(((await r.json()) as { error: { code: string } }).error.code).toBe("request_too_large");
  });

  it("unsupported type -> 415 unsupported_file_type", async () => {
    await start();
    const r = await transcribe(form(audio("notes.txt", "text/plain")));
    expect(r.status).toBe(415);
    expect(((await r.json()) as { error: { code: string } }).error.code).toBe("unsupported_file_type");
  });

  it("octet-stream with an allowed extension is accepted (extension fallback)", async () => {
    await start();
    expect((await transcribe(form(audio("voice.ogg", "application/octet-stream")))).status).toBe(200);
  });

  it.each([
    ["language", { language: "not a code!" }, "invalid_language"],
    ["response_format", { response_format: "srt" }, "invalid_response_format"],
    ["prompt", { prompt: "x".repeat(1001) }, "prompt_too_long"],
  ])("bad %s -> 400", async (_name, fields, code) => {
    await start();
    const r = await ok(fields);
    expect(r.status).toBe(400);
    expect(((await r.json()) as { error: { code: string } }).error.code).toBe(code);
  });
});

describe("success path", () => {
  it("default json response is {text}; upstream gets Persian defaults and the configured model", async () => {
    await start();
    const r = await ok();
    expect(r.status).toBe(200);
    expect(await r.json()).toEqual({ text: SECRET_TRANSCRIPT });

    expect(upSeen.length).toBe(1);
    const seen = upSeen[0]!;
    expect(seen.auth).toBe(`Bearer ${STT_KEY}`);
    const fields = await readMultipart(seen.body, seen.ct);
    expect(fields).not.toBeNull();
    const f = fields!;
    expect(f.get("model")).toBe("whisper-1");
    expect(f.get("language")).toBe("fa");
    expect(f.get("prompt")).toBe(STT_DEFAULT_PROMPT);
    expect(f.get("response_format")).toBe("json");
    const file = f.get("file") as File;
    expect(file.name).toBe("audio.m4a");
    expect(file.type).toBe("audio/mp4");
    expect(Buffer.from(await file.arrayBuffer()).toString()).toBe(`${AUDIO_MARKER}-data`);
  });

  it("request language and prompt override the defaults; client model and temperature are ignored", async () => {
    await start();
    const r = await ok({ language: "en", prompt: "rent, deposit", model: "gpt-x", temperature: "0.7" });
    expect(r.status).toBe(200);
    const f = (await readMultipart(upSeen[0]!.body, upSeen[0]!.ct))!;
    expect(f.get("language")).toBe("en");
    expect(f.get("prompt")).toBe("rent, deposit");
    expect(f.get("model")).toBe("whisper-1");
    expect(f.get("temperature")).toBeNull();
  });

  it("empty prompt means no hint", async () => {
    await start();
    await ok({ prompt: "" });
    const f = (await readMultipart(upSeen[0]!.body, upSeen[0]!.ct))!;
    expect(f.get("prompt")).toBeNull();
  });

  it("response_format=text returns plain text", async () => {
    upHandler = (_req, _b, res) => { res.writeHead(200, { "content-type": "text/plain" }); res.end(`${SECRET_TRANSCRIPT}\n`); };
    await start();
    const r = await ok({ response_format: "text" });
    expect(r.headers.get("content-type")).toContain("text/plain");
    expect(await r.text()).toBe(SECRET_TRANSCRIPT);
  });

  it("response_format=verbose_json keeps upstream fields and text", async () => {
    upHandler = (_req, _b, res) => json(res, 200, { text: SECRET_TRANSCRIPT, language: "persian", duration: 3.2 });
    await start();
    const r = await ok({ response_format: "verbose_json" });
    expect(await r.json()).toEqual({ text: SECRET_TRANSCRIPT, language: "persian", duration: 3.2 });
  });

  it("audioExtension: MIME allowlist, extension fallback, rejects unknown", () => {
    expect(audioExtension("audio/mp4", "x.bin")).toBe("m4a");
    expect(audioExtension("audio/webm;codecs=opus", "x")).toBe("webm");
    expect(audioExtension("", "clip.MP3")).toBe("mp3");
    expect(audioExtension("application/octet-stream", "clip.opus")).toBe("ogg");
    expect(audioExtension("video/quicktime", "clip.mov")).toBeNull();
    expect(audioExtension("", "clip.exe")).toBeNull();
  });
});

describe("upstream failures", () => {
  it.each([
    [401, 502, "stt_unavailable"],
    [429, 429, "rate_limit_exceeded"],
    [400, 400, "invalid_audio"],
    [503, 502, "stt_unavailable"],
  ])("upstream %i -> %i %s", async (upStatus, status, code) => {
    upHandler = (_req, _b, res) => json(res, upStatus, { error: `upstream says secret ${STT_KEY}` });
    await start();
    const r = await ok();
    const body = await r.text();
    expect(r.status).toBe(status);
    expect(body).toContain(code);
    expect(body).not.toContain(STT_KEY);
    expect(logs.join("\n")).not.toContain(STT_KEY);
  });

  it("upstream returns non-JSON for json format -> 502", async () => {
    upHandler = (_req, _b, res) => { res.writeHead(200); res.end("<html>oops</html>"); };
    await start();
    const r = await ok();
    expect(r.status).toBe(502);
    expect(await r.text()).not.toContain("oops");
  });

  it("upstream slower than STT_TIMEOUT_MS -> 502", async () => {
    upstreamTimeoutMs = 50;
    upHandler = (_req, _b, res) => setTimeout(() => json(res, 200, { text: "late" }), 400);
    await start();
    const r = await ok();
    expect(r.status).toBe(502);
    expect(logs.join("\n")).toContain("stt_timeout");
  });

  it("unreachable upstream (connection refused) -> 502 without leaking the URL", async () => {
    // Port 1 on loopback is closed: connection refused, no network involved.
    const deadBase = "http://127.0.0.1:1/v1";
    logs = [];
    server = createApp({
      llm,
      tokens: [TOKEN],
      limiter: new FixedWindowLimiter(100, 60_000),
      logger: createLogger({ sink: (l) => logs.push(l), logText: false }),
      stt: new OpenAiSttClient({ baseUrl: deadBase, model: "whisper-1", timeoutMs: 2000 }),
    });
    await new Promise<void>((r) => server!.listen(0, "127.0.0.1", () => r()));
    base = `http://127.0.0.1:${(server!.address() as AddressInfo).port}`;
    const r = await ok();
    expect(r.status).toBe(502);
    const text = await r.text();
    expect(text).toContain("stt_unavailable");
    expect(text).not.toContain("127.0.0.1:1");
    expect(logs.join("\n")).toContain("stt_request_failed");
  });
});

describe("no-log guarantee", () => {
  it("audio bytes and transcript are never logged by default", async () => {
    await start();
    const r = await ok();
    expect(r.status).toBe(200);
    const all = logs.join("\n");
    expect(logs.length).toBeGreaterThan(0);
    expect(all).not.toContain(AUDIO_MARKER);
    expect(all).not.toContain("محرمانه");
    expect(all).not.toContain(STT_KEY);
  });

  it("transcript is logged only with DEBUG_LOG_TEXT=1; audio bytes never are", async () => {
    logText = true;
    await start();
    await ok();
    const all = logs.join("\n");
    expect(all).toContain("محرمانه");
    expect(all).not.toContain(AUDIO_MARKER);
    expect(all).not.toContain(STT_KEY);
  });

  it("failure logs carry codes only", async () => {
    upHandler = (_req, _b, res) => json(res, 500, { error: "boom" });
    await start();
    await ok();
    const all = logs.join("\n");
    expect(all).toContain("stt_failed");
    expect(all).toContain("stt_upstream_error");
    expect(all).not.toContain(AUDIO_MARKER);
  });
});
