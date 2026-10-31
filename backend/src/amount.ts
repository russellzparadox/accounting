import { RIAL_PER_TOMAN } from "./rules.js";

export class AmountError extends Error {
  constructor(readonly code: string) {
    super(code);
    this.name = "AmountError";
  }
}

/**
 * Deterministic toman/rial -> rial conversion. Integer math only, no floats.
 * The LLM returns amount_value as stated; this function is the only place amount_rial is computed.
 */
export function toRial(value: number, unit: "toman" | "rial"): number {
  const factor = unit === "toman" ? RIAL_PER_TOMAN : 1;
  const rial = value * factor;
  if (!Number.isSafeInteger(rial) || rial < 1) throw new AmountError("amount_out_of_range");
  return rial;
}
