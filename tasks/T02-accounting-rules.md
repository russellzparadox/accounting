# T02 — accounting-rules
Status: todo
Depends: T00
Read first: CLAUDE.md, handouts/T00-bootstrap.md
Skills to invoke: persian-writing:persian-writing

## Goal
- Research Iranian bookkeeping (web). Write posting rules, chart of accounts (کدینگ: گروه / کل / معین), fiscal year, doc numbering, toman/rial.

## Deliverables (files)
- `docs/ACCOUNTING_RULES.md`
- `core/domain/src/main/resources/chart_of_accounts.json` (path as given in T00 table; see note)

## Acceptance criteria
- [ ] `docs/ACCOUNTING_RULES.md` covers posting templates: cash/credit purchase, cash/credit sale, expense paid/payable, receipt/payment with person, bank transfer, owner capital/withdrawal, purchase/sale return, reversal.
- [ ] Each template lists Dr/Cr lines by account code and states the balance rule.
- [ ] Fiscal year rules, doc numbering, toman/rial rules documented.
- [ ] Web sources cited with URLs in the doc.
- [ ] `chart_of_accounts.json` parses. Each account has: code, name_fa, level (گروه/کل/معین), parent code, type, normal balance.
- [ ] Account codes are unique. Each معین has a parent کل that exists.
- [ ] Every account code used in a template exists in the JSON.
- [ ] Unclear points go to handout Open issues. Do not guess silently.

## Out of scope
- Kotlin code, Room, UI, backend.

## Note
- T00 table path is `core/domain/...`. T01 makes module `core:domain` at `core/domain/`. Written as given. Main agent to confirm the path before running T03.

## Verify (commands)
- `python3 -m json.tool core/domain/src/main/resources/chart_of_accounts.json > /dev/null`
- `grep -c "" docs/ACCOUNTING_RULES.md` (expect > 0)
