import { Ajv, type ValidateFunction } from "ajv";
import { readFileSync } from "node:fs";

// Schemas live in backend/schema/, one level up from both src/ (tsx/vitest) and dist/ (tsc output).
function load(name: string): unknown {
  return JSON.parse(readFileSync(new URL(`../schema/${name}`, import.meta.url), "utf8"));
}

const ajv = new Ajv({ allErrors: true, strict: true });
const llmValidator: ValidateFunction = ajv.compile(load("llm_output.schema.json") as object);
const responseValidator: ValidateFunction = ajv.compile(load("transaction_intent.schema.json") as object);

export const validateLlmOutput = (x: unknown): boolean => llmValidator(x) === true;
export const validateResponse = (x: unknown): boolean => responseValidator(x) === true;
export const llmOutputSchema = load("llm_output.schema.json");
