import { parseTokens } from "./auth.js";

export type Provider = "anthropic" | "openai";

export interface Config {
  port: number;
  provider: Provider;
  /** Upstream model id. ANTHROPIC_MODEL for anthropic, LLM_MODEL for openai. Also echoed by /v1/models. */
  model: string;
  /** ANTHROPIC_API_KEY. Only read when provider=anthropic. Never echoed, never logged. */
  apiKey: string | undefined;
  /** OPENAI_BASE_URL (openai provider only). */
  openaiBaseUrl: string;
  /** OPENAI_API_KEY (openai provider only, optional for local servers). Never echoed, never logged. */
  openaiApiKey: string | undefined;
  /** LLM_TIMEOUT_MS (openai provider only). */
  timeoutMs: number;
  /** STT_BASE_URL. Unset = /v1/audio/transcriptions answers 501 not_configured. */
  sttBaseUrl: string | undefined;
  /** STT_API_KEY (optional, local servers need none). Never echoed, never logged. */
  sttApiKey: string | undefined;
  /** STT_MODEL. Sent upstream as the `model` field. */
  sttModel: string;
  /** STT_TIMEOUT_MS. Per upstream transcription request. */
  sttTimeoutMs: number;
  tokens: string[];
  rateLimitPerMin: number;
  logText: boolean;
}

export class ConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ConfigError";
  }
}

export const DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
export const DEFAULT_TIMEOUT_MS = 20_000;
export const DEFAULT_STT_MODEL = "whisper-1";
export const DEFAULT_STT_TIMEOUT_MS = 30_000;

/** All settings come from env. Secrets are read here only and never echoed. */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const rate = Number(env.RATE_LIMIT_PER_MIN ?? "30");
  const provider = env.LLM_PROVIDER ?? "anthropic";
  if (provider !== "anthropic" && provider !== "openai") {
    throw new ConfigError("LLM_PROVIDER must be anthropic or openai");
  }
  const model = provider === "openai"
    ? (env.LLM_MODEL ?? "")
    : (env.ANTHROPIC_MODEL ?? "claude-haiku-5-5");
  if (provider === "openai" && model.trim() === "") {
    throw new ConfigError("LLM_MODEL is required when LLM_PROVIDER=openai");
  }
  const timeout = Number(env.LLM_TIMEOUT_MS ?? String(DEFAULT_TIMEOUT_MS));
  const sttTimeout = Number(env.STT_TIMEOUT_MS ?? String(DEFAULT_STT_TIMEOUT_MS));
  return {
    port: Number(env.PORT ?? "8787"),
    provider,
    model,
    apiKey: env.ANTHROPIC_API_KEY || undefined,
    openaiBaseUrl: (env.OPENAI_BASE_URL || DEFAULT_OPENAI_BASE_URL).replace(/\/+$/, ""),
    openaiApiKey: env.OPENAI_API_KEY || undefined,
    timeoutMs: Number.isInteger(timeout) && timeout > 0 ? timeout : DEFAULT_TIMEOUT_MS,
    sttBaseUrl: (env.STT_BASE_URL ?? "").trim().replace(/\/+$/, "") || undefined,
    sttApiKey: env.STT_API_KEY || undefined,
    sttModel: (env.STT_MODEL ?? "").trim() || DEFAULT_STT_MODEL,
    sttTimeoutMs: Number.isInteger(sttTimeout) && sttTimeout > 0 ? sttTimeout : DEFAULT_STT_TIMEOUT_MS,
    tokens: parseTokens(env.PROXY_TOKENS),
    rateLimitPerMin: Number.isInteger(rate) && rate > 0 ? rate : 30,
    logText: env.DEBUG_LOG_TEXT === "1",
  };
}
