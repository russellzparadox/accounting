import { parseTokens } from "./auth.js";

export interface Config {
  port: number;
  model: string;
  apiKey: string | undefined;
  tokens: string[];
  rateLimitPerMin: number;
  logText: boolean;
}

/** All settings come from env. ANTHROPIC_API_KEY is read here only and never echoed. */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const rate = Number(env.RATE_LIMIT_PER_MIN ?? "30");
  return {
    port: Number(env.PORT ?? "8787"),
    model: env.ANTHROPIC_MODEL ?? "claude-haiku-5-5",
    apiKey: env.ANTHROPIC_API_KEY || undefined,
    tokens: parseTokens(env.PROXY_TOKENS),
    rateLimitPerMin: Number.isInteger(rate) && rate > 0 ? rate : 30,
    logText: env.DEBUG_LOG_TEXT === "1",
  };
}
