# Handout T01b — design-system
Status: done

## Summary
- `docs/DESIGN.md` written: color tokens (light+dark, M3 slots + custom income/expense), Vazirmatn type scale, 4dp spacing, RTL rules, components (chat input bar, mic button, confirmation card, journal row, amount text, StateBlock, buttons), states, a11y, Persian UX copy list.
- All text pairs >= 4.5:1 light and dark (computed WCAG relative luminance).
- Touch targets >= 48dp.
- No code changed. Compose files not touched; T01 still has the placeholder `Color.kt`.

## Files created/changed
- `docs/DESIGN.md` (replaced skeleton)
- `handouts/T01b-design-system.md` (this file)
- `tasks/T01b-design-system.md` (Status line only)

## Public interfaces / contracts
- Color.kt: `LightColors`, `DarkColors` (names unchanged). Full M3 slot list in DESIGN.md §1.4.
- New: `data class AccountingColors(income: Color, expense: Color)` exposed via CompositionLocal next to `MaterialTheme`. Light and dark instances.
- Type.kt: `VazirmatnFamily` and `AppTypography` names unchanged. Set all M3 slots plus custom `AccountingTypography.amount` (18/26 Medium). Weights used: Regular, Medium, Bold only.
- Strings: keys listed in DESIGN.md §8 (`input_hint`, `confirm_action`, `error_parse_body`, etc.). Planned in `app/src/main/res/values/strings.xml`, default fa.
- Amount rendering: `[LRI]number[PDI] unit` in RTL paragraph. Unit `تومان` or `ریال`. Rounding rule open.

## Decisions made (+why)
- Palette: neutral-blue placeholder with v1 hex values. Replaces grey placeholder so T01 can adopt it without naming changes. Owner approves before release (T14).
- Only Regular/Medium/Bold, since those are the bundled static Vazirmatn files (T01 handout). No SemiBold/Light.
- Debit/credit shown as words, never minus sign, never color alone.
- `outline` at 4.47:1 (light) is above the 3:1 non-text floor. Kept.
- Disabled controls exempt from contrast (WCAG), 38% alpha.
- Skill fallback used: `impeccable` and `ui-ux-pro-max` are not installed. Used design:design-system, design:ux-copy, design:accessibility-review, persian-writing:persian-writing.
- Persian copy uses formal-but-human (شما). Proposal/app copy keeps formal vocabulary.

## How verified (commands + result)
- Contrast: python WCAG formula over every pair in DESIGN.md §1.1–1.3. All text >= 4.5. Outline 4.47 / 5.75 >= 3.
- `test -s docs/DESIGN.md` -> non-empty.
- `grep -c "48dp" docs/DESIGN.md` -> 8.
- `grep -ci "contrast" docs/DESIGN.md` -> 4.
- Not run: Android build (no Google Maven, see T01 handout). No Compose compile check.

## Open issues / risks
- Vazirmatn tabular digits: unknown. Amount column alignment depends on it. Check in T08.
- Rial rounding and decimal display (٫) rule: owner decision (T02).
- Brand palette is a placeholder, not a brand decision.
- Confirmation card dismissal by swipe is not decided; spec prefers explicit cancel.
- `autoMirror` and `TextDirection.Content` usage not yet tested on device.
- Persian copy strings are drafts. Not run through `fa_lint.py` / `persian_cleanup.py` (no script run here; ZWNJ placed by hand).

## Next task must know
- T01c / T08 implement from DESIGN.md. Use exact M3 slot names and hex values in §1.4 for Color.kt.
- Type.kt: set all slots from §2 table, `letterSpacing = 0.sp`.
- Strings: use keys from §8 verbatim so later tasks can grep them.
- Money formatting lives in T05 (Persian digits, ٬, ٫, LRI/PDI wrap). Theme does not format.
