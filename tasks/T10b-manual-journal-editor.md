# T10b — Manual journal editor (free-form سند حسابداری, no LLM, no backend)
Status: todo
Depends: T08, T10
Read first: CLAUDE.md, handouts/T03-domain-core.md, handouts/T04-room-persistence.md, handouts/T08a-entry-viewmodel.md, handouts/T08-entry-ui.md, handouts/T10-ledger-ui.md, docs/DESIGN.md, docs/ACCOUNTING_RULES.md
Skills to invoke: design:design-critique, design:accessibility-review, design:ux-copy, persian-writing:persian-writing

## Goal
- Let the user record ANY balanced journal document by hand: pick accounts, enter Dr/Cr amounts, memo, Jalali date. Works fully offline. Complements the template-based quick manual form (T08) and the LLM path.

## Deliverables
- `feature/ledger` (or new `feature/journal`) `ManualJournalViewModel` + screen: date (Jalali picker from `JalaliDate`), memo, 2..N lines (account picker over postable accounts incl. party معین via search, side Dr/Cr, amount toman/rial per setting with Persian digits), live totals + difference indicator, "افزودن سطر", delete row, save disabled until Dr == Cr > 0 and each line valid.
- Validation: reuse `JournalEntry` init rules (balanced, >= 2 lines, lines > 0), `ChartOfAccounts.requirePostable` (no گروه/کل-with-children accounts; معین required when کل has children), date inside open fiscal year; Persian error messages.
- Commit via `LedgerRepository.commit` (template id `MANUAL` — add to domain `TemplateId` only if it does not break the 14 templates or T06 schema; otherwise store as nullable/`MANUAL` kind in the entry and document). Undo = reversal doc (existing `reverse`).
- Entry points: FAB/menu on journal screen ("سند دستی"); from a committed/draft card "ویرایش به صورت سند" (prefill lines from a Draft before confirm).
- Tests: VM unit tests (balanced/unbalanced, postable rules, fiscal-year check, toman<->rial conversion, double submit once), Compose UI test (add rows, difference indicator, save), trial balance stays balanced after commit.

## Acceptance criteria
- [ ] Works with backend unset and airplane mode.
- [ ] Unbalanced or invalid documents can never be committed (VM + repo level).
- [ ] No Float/Double for money; UI uses Persian digits; touch targets >= 48dp; RTL.
- [ ] CI green (android workflow), no tests skipped; max 6 pushes; commit prefix `T10b:`.

## Out of scope
- Editing/deleting committed documents (immutable; reversal only), attachments, multi-currency.
