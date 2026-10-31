import { ConfigError, loadConfig } from "./config.js";
import { AnthropicLlmClient, type LlmClient } from "./llm.js";
import { OpenAiLlmClient } from "./llm_openai.js";
import { OpenAiSttClient, type SttClient } from "./stt.js";
import { createLogger } from "./log.js";
import { FixedWindowLimiter } from "./ratelimit.js";
import { createApp } from "./server.js";

let cfg: ReturnType<typeof loadConfig>;
try {
  cfg = loadConfig();
} catch (e) {
  process.stderr.write(`${e instanceof ConfigError ? e.message : "Invalid configuration."} Refusing to start.\n`);
  process.exit(1);
}
if (cfg.provider === "anthropic" && !cfg.apiKey) {
  process.stderr.write("ANTHROPIC_API_KEY is not set. Refusing to start.\n");
  process.exit(1);
}
if (cfg.tokens.length === 0) {
  process.stderr.write("PROXY_TOKENS is empty. Refusing to start (every request would be 401).\n");
  process.exit(1);
}

const llm: LlmClient = cfg.provider === "openai"
  ? new OpenAiLlmClient({
    baseUrl: cfg.openaiBaseUrl,
    model: cfg.model,
    apiKey: cfg.openaiApiKey,
    timeoutMs: cfg.timeoutMs,
  })
  : new AnthropicLlmClient({ apiKey: cfg.apiKey!, model: cfg.model });

const stt: SttClient | undefined = cfg.sttBaseUrl
  ? new OpenAiSttClient({
    baseUrl: cfg.sttBaseUrl,
    model: cfg.sttModel,
    apiKey: cfg.sttApiKey,
    timeoutMs: cfg.sttTimeoutMs,
  })
  : undefined;

const logger = createLogger({ logText: cfg.logText });
const app = createApp({
  llm,
  stt,
  tokens: cfg.tokens,
  limiter: new FixedWindowLimiter(cfg.rateLimitPerMin, 60_000),
  logger,
  model: cfg.model,
});
app.listen(cfg.port, () => logger.info("listening", {
  port: cfg.port, provider: cfg.provider, model: cfg.model, stt: stt !== undefined,
}));
