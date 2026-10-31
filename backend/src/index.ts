import { loadConfig } from "./config.js";
import { AnthropicLlmClient } from "./llm.js";
import { createLogger } from "./log.js";
import { FixedWindowLimiter } from "./ratelimit.js";
import { createApp } from "./server.js";

const cfg = loadConfig();
if (!cfg.apiKey) {
  process.stderr.write("ANTHROPIC_API_KEY is not set. Refusing to start.\n");
  process.exit(1);
}
if (cfg.tokens.length === 0) {
  process.stderr.write("PROXY_TOKENS is empty. Refusing to start (every request would be 401).\n");
  process.exit(1);
}

const logger = createLogger({ logText: cfg.logText });
const app = createApp({
  llm: new AnthropicLlmClient({ apiKey: cfg.apiKey, model: cfg.model }),
  tokens: cfg.tokens,
  limiter: new FixedWindowLimiter(cfg.rateLimitPerMin, 60_000),
  logger,
});
app.listen(cfg.port, () => logger.info("listening", { port: cfg.port, model: cfg.model }));
