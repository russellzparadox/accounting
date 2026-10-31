// Field-rule table from handouts/T03-domain-core.md (TransactionIntent JSON) and
// docs/ACCOUNTING_RULES.md section 8. Enforced on every intent before it is returned.

export const TYPE_IDS = [
  "PURCHASE_CASH", "PURCHASE_CREDIT", "SALE_CASH", "SALE_CREDIT",
  "EXPENSE_PAID", "EXPENSE_PAYABLE", "RECEIPT_FROM_PERSON", "PAYMENT_TO_PERSON",
  "BANK_TRANSFER", "OWNER_CAPITAL", "OWNER_WITHDRAWAL",
  "PURCHASE_RETURN", "SALE_RETURN",
] as const;

export const CATEGORY_IDS = [
  "RENT", "FREIGHT", "PHONE", "SALARY", "UTILITIES", "REPAIR", "MISC", "BANK_FEE",
] as const;

// Rial per toman (docs/ACCOUNTING_RULES.md section 2). Conversion is done here, never by the LLM.
export const RIAL_PER_TOMAN = 10;

const PAID_REQUIRED = new Set([
  "PURCHASE_CASH", "SALE_CASH", "OWNER_CAPITAL", "OWNER_WITHDRAWAL", "BANK_TRANSFER",
  "EXPENSE_PAID", "RECEIPT_FROM_PERSON", "PAYMENT_TO_PERSON",
]);
const PAID_NULL = new Set(["PURCHASE_CREDIT", "SALE_CREDIT", "EXPENSE_PAYABLE"]);
const PARTY_REQUIRED = new Set([
  "PURCHASE_CREDIT", "SALE_CREDIT", "EXPENSE_PAYABLE", "RECEIPT_FROM_PERSON", "PAYMENT_TO_PERSON",
]);
const RETURNS = new Set(["PURCHASE_RETURN", "SALE_RETURN"]);
const EXPENSE = new Set(["EXPENSE_PAID", "EXPENSE_PAYABLE"]);

export interface RuleInput {
  type: string | null;
  paid_with: string | null;
  party_name: string | null;
  category: string | null;
  bank_account: string | null;
}

/** Returns the list of violated rules; empty array means the intent is valid. */
export function fieldRuleErrors(i: RuleInput): string[] {
  const t = i.type;
  if (t === null) return ["type_missing"];
  const errs: string[] = [];
  if (PAID_REQUIRED.has(t) && i.paid_with === null) errs.push("paid_with_required");
  if (PAID_NULL.has(t) && i.paid_with !== null) errs.push("paid_with_must_be_null");
  if (PARTY_REQUIRED.has(t) && i.party_name === null) errs.push("party_name_required");
  if (RETURNS.has(t) && i.paid_with === null && i.party_name === null) errs.push("party_name_required");
  if (EXPENSE.has(t) && i.category === null) errs.push("category_required");
  if (!EXPENSE.has(t) && i.category !== null) errs.push("category_must_be_null");
  if (i.bank_account !== null && !(i.paid_with === "BANK" || t === "BANK_TRANSFER")) {
    errs.push("bank_account_not_allowed");
  }
  return errs;
}

/** Calendar-valid yyyy-MM-dd check (schema only checks the shape). */
export function isValidIsoDate(s: string): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  if (!m) return false;
  const y = Number(m[1]), mo = Number(m[2]), d = Number(m[3]);
  const dt = new Date(Date.UTC(y, mo - 1, d));
  return dt.getUTCFullYear() === y && dt.getUTCMonth() === mo - 1 && dt.getUTCDate() === d;
}
