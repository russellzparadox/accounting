// Speech-to-text: POST /v1/audio/transcriptions (OpenAI-compatible inbound) and the upstream client.
// Pure helpers only. server.ts wires auth, rate limit, body reading and logging.
//
// No new dependency: multipart parsing uses the Node built-in Request.formData() (undici), and the
// upstream call uses global fetch + FormData/Blob. Audio is held in memory only for the request.
import { oaError, type OpenAiError } from "./openai_compat.js";

export const STT_MAX_AUDIO_BYTES = 10 * 1024 * 1024;
/** Audio cap plus room for the multipart envelope and text fields. Larger bodies are rejected unread. */
export const STT_MAX_BODY_BYTES = STT_MAX_AUDIO_BYTES + 64 * 1024;
export const STT_DEFAULT_LANGUAGE = "fa";
/** Vocabulary hint for Whisper-style models. Not user-visible. Overridden by the `prompt` field. */
export const STT_DEFAULT_PROMPT = "خرید، فروش، نسیه، اجاره، نقد، چک، تومان، ریال، هزار، میلیون، میلیارد";
export const STT_MAX_PROMPT_CHARS = 1000;
export const STT_FORMATS = ["json", "text", "verbose_json"] as const;
export type SttFormat = (typeof STT_FORMATS)[number];

/** Allowed upload types. Keyed by canonical MIME. Value = filename extension sent upstream. */
const MIME_TO_EXT: Record<string, string> = {
  "audio/mp4": "m4a",
  "audio/m4a": "m4a",
  "audio/x-m4a": "m4a",
  "audio/aac": "aac",
  "audio/ogg": "ogg",
  "audio/opus": "ogg",
  "audio/webm": "webm",
  "audio/wav": "wav",
  "audio/x-wav": "wav",
  "audio/wave": "wav",
  "audio/mpeg": "mp3",
  "audio/mp3": "mp3",
};
/** Used only when the part has no usable MIME (empty or application/octet-stream). */
const EXT_TO_EXT: Record<string, string> = {
  m4a: "m4a", mp4: "m4a", aac: "aac", ogg: "ogg", opus: "ogg", webm: "webm", wav: "wav", mp3: "mp3",
};
const LANGUAGE_RE = /^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$/;

/** Extension for an accepted upload, or null when the type is not on the allowlist. */
export function audioExtension(type: string, name: string): string | null {
  const mime = (type.split(";")[0] ?? "").trim().toLowerCase();
  if (mime !== "" && mime !== "application/octet-stream") return MIME_TO_EXT[mime] ?? null;
  const m = /\.([a-zA-Z0-9]+)$/.exec(name);
  return m ? (EXT_TO_EXT[m[1]!.toLowerCase()] ?? null) : null;
}

/** Parses a multipart/form-data body. Returns null when the type is wrong or the body is malformed. */
export async function readMultipart(body: Buffer, contentType: string | undefined): Promise<FormData | null> {
  if (!contentType || !/^multipart\/form-data\b/i.test(contentType)) return null;
  try {
    const req = new Request("http://localhost/v1/audio/transcriptions", {
      method: "POST",
      headers: { "content-type": contentType },
      body,
    });
    return await req.formData();
  } catch {
    return null;
  }
}

export interface TranscriptionInput {
  audio: Uint8Array;
  /** Filename sent upstream. Providers pick the decoder from its extension. */
  filename: string;
  mime: string;
  language: string;
  /** Vocabulary hint. Omitted when empty. */
  prompt?: string;
  format: SttFormat;
}

export type ParsedTranscription =
  | { ok: true; value: TranscriptionInput; bytes: number }
  | { ok: false; err: OpenAiError };

const bad = (message: string, param: string, code: string): { ok: false; err: OpenAiError } =>
  ({ ok: false, err: oaError(400, "invalid_request_error", code, message, param) });

function textField(form: FormData, name: string): string | undefined | null {
  const v = form.get(name);
  if (v === null) return undefined;
  return typeof v === "string" ? v : null;
}

/**
 * Validates the OpenAI-shaped fields. `model` and `temperature` are accepted and ignored:
 * the configured STT model is always used. Audio bytes are read here (already size-capped by the body limit).
 */
export async function parseTranscription(form: FormData): Promise<ParsedTranscription> {
  const file = form.get("file");
  if (file === null) return bad("'file' is required.", "file", "missing_file");
  if (!(file instanceof File)) return bad("'file' must be an uploaded file.", "file", "invalid_file");
  if (file.size === 0) return bad("'file' is empty.", "file", "empty_file");
  if (file.size > STT_MAX_AUDIO_BYTES) {
    return { ok: false, err: oaError(413, "invalid_request_error", "file_too_large",
      `'file' is larger than ${STT_MAX_AUDIO_BYTES} bytes.`, "file") };
  }
  const ext = audioExtension(file.type, file.name);
  if (ext === null) {
    return { ok: false, err: oaError(415, "invalid_request_error", "unsupported_file_type",
      "Unsupported audio type. Use m4a, aac, mp4, ogg, opus, webm, wav or mp3.", "file") };
  }

  const language = textField(form, "language");
  if (language === null) return bad("'language' must be a string.", "language", "invalid_language");
  let lang = STT_DEFAULT_LANGUAGE;
  if (language) {
    if (!LANGUAGE_RE.test(language)) return bad("'language' must be an ISO 639 code such as 'fa'.", "language", "invalid_language");
    lang = language.split("-")[0]!.toLowerCase();
  }

  const prompt = textField(form, "prompt");
  if (prompt === null) return bad("'prompt' must be a string.", "prompt", "invalid_prompt");
  if (prompt !== undefined && [...prompt].length > STT_MAX_PROMPT_CHARS) {
    return bad(`'prompt' must be at most ${STT_MAX_PROMPT_CHARS} characters.`, "prompt", "prompt_too_long");
  }

  const fmt = textField(form, "response_format");
  if (fmt === null) return bad("'response_format' must be a string.", "response_format", "invalid_response_format");
  let format: SttFormat = "json";
  if (fmt) {
    if (!(STT_FORMATS as readonly string[]).includes(fmt)) {
      return bad("'response_format' must be json, text or verbose_json.", "response_format", "invalid_response_format");
    }
    format = fmt as SttFormat;
  }

  const audio = new Uint8Array(await file.arrayBuffer());
  return {
    ok: true,
    bytes: audio.byteLength,
    value: {
      audio,
      filename: `audio.${ext}`,
      mime: file.type.split(";")[0]!.trim() || "application/octet-stream",
      language: lang,
      // An explicit empty prompt means "no hint". An absent prompt means the Persian default.
      prompt: prompt === undefined ? STT_DEFAULT_PROMPT : prompt || undefined,
      format,
    },
  };
}

export type SttErrorCode =
  | "stt_auth_failed" | "stt_rate_limited" | "stt_bad_audio" | "stt_upstream_error"
  | "stt_timeout" | "stt_request_failed" | "stt_invalid_output";

/** Upstream failure. Message is fixed text, never the upstream body, URL or key. */
export class SttTransportError extends Error {
  constructor(readonly code: SttErrorCode, readonly status?: number) {
    super(code);
    this.name = "SttTransportError";
  }
}

export interface SttClient {
  transcribe(input: TranscriptionInput): Promise<{ text: string; verbose?: Record<string, unknown> }>;
}

export interface OpenAiSttOptions {
  baseUrl: string;
  model: string;
  apiKey?: string;
  timeoutMs: number;
  /** Test seam. Defaults to global fetch. */
  fetchImpl?: typeof fetch;
}

/** OpenAI-compatible speech client (OpenAI, Groq, whisper.cpp / faster-whisper servers). */
export class OpenAiSttClient implements SttClient {
  private readonly fetchImpl: typeof fetch;

  constructor(private readonly opts: OpenAiSttOptions) {
    this.fetchImpl = opts.fetchImpl ?? fetch;
  }

  async transcribe(input: TranscriptionInput): Promise<{ text: string; verbose?: Record<string, unknown> }> {
    const form = new FormData();
    form.append("file", new Blob([input.audio], { type: input.mime }), input.filename);
    form.append("model", this.opts.model);
    form.append("language", input.language);
    if (input.prompt) form.append("prompt", input.prompt);
    form.append("response_format", input.format);

    const headers: Record<string, string> = {};
    if (this.opts.apiKey) headers.authorization = `Bearer ${this.opts.apiKey}`;
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), this.opts.timeoutMs);
    let res: Response;
    try {
      res = await this.fetchImpl(`${this.opts.baseUrl}/audio/transcriptions`, {
        method: "POST", headers, body: form, signal: ctrl.signal,
      });
    } catch (e) {
      if (e instanceof Error && e.name === "AbortError") throw new SttTransportError("stt_timeout");
      throw new SttTransportError("stt_request_failed");
    } finally {
      clearTimeout(timer);
    }

    if (!res.ok) {
      await res.body?.cancel().catch(() => undefined);
      if (res.status === 401 || res.status === 403) throw new SttTransportError("stt_auth_failed", res.status);
      if (res.status === 429) throw new SttTransportError("stt_rate_limited", res.status);
      if (res.status >= 400 && res.status < 500) throw new SttTransportError("stt_bad_audio", res.status);
      throw new SttTransportError("stt_upstream_error", res.status);
    }

    if (input.format === "text") {
      const text = await res.text().catch(() => null);
      if (text === null) throw new SttTransportError("stt_invalid_output", res.status);
      return { text: text.trim() };
    }
    const json = await res.json().catch(() => null) as unknown;
    if (typeof json !== "object" || json === null || typeof (json as { text?: unknown }).text !== "string") {
      throw new SttTransportError("stt_invalid_output", res.status);
    }
    const obj = json as Record<string, unknown>;
    return { text: (obj.text as string).trim(), verbose: obj };
  }
}
