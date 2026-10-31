# Handout T02 — accounting-rules
Status: done

## Summary (3 bullets max)
- Chart: 34 accounts (6 گروه, 17 کل, 4 معین examples) in `chart_of_accounts.json`; codes unique, parents valid. Validator passes.
- Rules doc: `docs/ACCOUNTING_RULES.md` covers rial/toman, fiscal year, گروه/کل/معین, periodic purchases, doc rules, 14 template ids with Dr/Cr table.
- Purchases use خرید 5100 (periodic). 1400 exists but no v1 template posts to it. Needs accountant sign-off (Open issues).

## Files created/changed
- `core/domain/src/main/resources/chart_of_accounts.json` (new dirs created)
- `docs/ACCOUNTING_RULES.md` (was skeleton)
- `handouts/T02-accounting-rules.md` (this file)
- `tasks/T02-accounting-rules.md` (Status only)

## Public interfaces / contracts (signatures, JSON shapes) — next tasks rely on these
- JSON: `{schema_version:1, currency:{base:"IRR", display_unit:"toman", rial_per_toman:10}, accounts:[{code, name_fa, level, parent, type, normal_balance}]}`
  - code: 4-digit string. level: "گروه"|"کل"|"معین". parent: code or null (گروه only). type: asset|liability|equity|revenue|cogs|expense. normal_balance: debit|credit.
- Template ids (T03 implements, T06 uses as transaction type). Full Dr/Cr table: `docs/ACCOUNTING_RULES.md` section 8. Per id, terse:
  - PURCHASE_CASH: Dr 5100 / Cr 1100 (صندوق) or 1200 (بانک). Balanced.
  - PURCHASE_CREDIT: Dr 5100 / Cr 2100 (معین تأمین‌کننده).
  - SALE_CASH: Dr 1100 or 1200 / Cr 4100.
  - SALE_CREDIT: Dr 1300 (معین مشتری) / Cr 4100.
  - EXPENSE_PAID: Dr 6100..6800 (by category) / Cr 1100 or 1200.
  - EXPENSE_PAYABLE: Dr 6100..6800 / Cr 2100.
  - RECEIPT_FROM_PERSON: Dr 1100 or 1200 / Cr 1300 (معین مشتری).
  - PAYMENT_TO_PERSON: Dr 2100 (معین) / Cr 1100 or 1200.
  - BANK_TRANSFER: Dr 1200 / Cr 1100 (واریز); reverse for برداشت; bank→bank uses two معین of 1200.
  - OWNER_CAPITAL: Dr 1100 or 1200 / Cr 3100.
  - OWNER_WITHDRAWAL: Dr 3200 / Cr 1100 or 1200.
  - PURCHASE_RETURN: Dr 2100 (credit purchase) or 1100/1200 (cash refund) / Cr 5200.
  - SALE_RETURN: Dr 4200 / Cr 1300 (credit sale) or pay-out Dr 4200 / Cr 1100/1200.
  - REVERSAL: mirror every line of the original (swap Dr/Cr). No own codes.
- Balance rule for all: Σdebit = Σcredit = amount (Long rials). Each line one side, > 0.
- Money: Long rials; toman = rial / 10 (display only).

## Decisions made (+why)
- Periodic system: purchases to 5100 (خرید), not 1400. Why: v1 has no per-item inventory; matches small-business scope.
- 1400 kept in chart for future perpetual switch.
- Withdrawal = 3200 برداشت (not a separate جاری شرکا). Why: single owner assumed in v1.
- Cash/bank = 1100/1200 variants chosen by user. Why: explicit in brief.
- Expense categories fixed to 6100..6800 (rent, freight, phone, salary, utilities, repair, misc, bank fee).
- Doc numbering proposal: sequential per fiscal year, reversal gets new number. No source found.
- Fiscal year default 1 فروردین–اسفند (Jalali), stored Gregorian.
- Sources: search snippets only. beancount.io and sepidarsystem.com failed DNS in this session, so pages were not read in full. Cited URLs are from search results.

## How verified (commands + result)
- `python3 -m json.tool core/domain/src/main/resources/chart_of_accounts.json > /dev/null` → OK.
- `python3 -I /tmp/t02/validate.py` → accounts 34; template rows 27; distinct ids 14; no errors. Checks: unique codes, level values, parent exists with correct level (کل→گروه, معین→کل), no Arabic ي/ك, template ids exactly the 14 required, every code in the template table exists in JSON.
- `grep -c "" docs/ACCOUNTING_RULES.md` → 164 (>0).
- persian-writing skill loaded; fa_lint/persian_cleanup not run (time).

## Open issues / risks (for accountant review)
1. Purchase method: periodic (v1) vs perpetual. Periodic = simpler, no per-purchase COGS, needs year-end count (out of v1). Perpetual = COGS per sale, needs 1400 on every purchase/sale. Pick before T03.
2. Document numbering rule (sequential per fiscal year) has no source. Confirm or replace.
3. Fiscal year: start month default and closing entry method undefined. Tax-year article (ماده ۱۵۵) sources conflict.
4. Toman rounding: rial not divisible by 10 displayed how? Proposal: round for display only.
5. Loans or capital from a person: no account. RECEIPT_FROM_PERSON assumes customer repayment only.
6. Cheques/notes: 1350 and 2200 exist, no template in v1.
7. Withdrawal: 3200 vs separate جاری شرکا (e.g. 3400). Confirm.
8. معین: templates cite کل codes. If کل has معین children (1200 بانک, 1300 مشتری, 2100 تأمین‌کننده), T03 must require معین choice. Confirm rule.
9. Expense categories fixed. Custom categories not supported in v1.
10. VAT: v2 (not in v1 JSON). Payroll: 6400 exists but no payroll logic.
11. Chart is simplified: 4-digit codes and small account set are a proposal, not copied from a standard. Accountant to confirm numbering.
12. Sources incomplete (see above). No official law text read.

## Next task must know
- T03 implements the 14 template ids exactly as in `docs/ACCOUNTING_RULES.md` section 8 (copy the id strings). Resolve معین when parent کل has children (open issue 8).
- Validate with: `python3 -m json.tool` on the JSON; codes in template table must exist in JSON.
- Do not add VAT accounts in v1.
