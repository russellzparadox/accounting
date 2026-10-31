# DESIGN
Status: v1 spec (T01b). Maps 1:1 to Compose M3 `ColorScheme` / `Typography` for `core/ui/.../theme/Color.kt` and `Type.kt`.
Specs in English. UI strings in Persian (fa), keys go in `app/src/main/res/values/strings.xml`.

## 0. Principles
- Persian-first RTL. Use start/end, never left/right.
- Calm, exact, accountant-like. Few colors. Color is never the only signal for debit/credit.
- Touch targets >= 48dp. Layout survives system font scale 200%.

## 1. Color tokens

Hex values are final v1 placeholders for the brand palette (owner to approve). Contrast = WCAG 2.1 relative luminance, computed.

### 1.1 Text pairs (must be >= 4.5:1)
| Role (M3 slot) | Light | Dark | Light ratio | Dark ratio |
|---|---|---|---|---|
| background / onBackground | F6F8FB / 141A22 | 0F1419 / E3E8EE | 16.44 | 15.02 |
| surface / onSurface | FFFFFF / 141A22 | 171D24 / E3E8EE | 17.49 | 13.77 |
| surfaceVariant / onSurfaceVariant (hint, secondary text) | E6EBF2 / 424B59 | 2A323D / C3CBD6 | 7.36 (on surfaceVariant), 8.81 (on surface) | 7.91 (on surfaceVariant), 10.37 (on surface) |
| primary / onPrimary | 1B5A85 / FFFFFF | 9CCBEF / 0A2739 | 7.37 | 8.96 |
| primaryContainer / onPrimaryContainer | D3E4F1 / 0B2C43 | 1D4764 / D3E4F1 | 11.09 | 7.55 |
| error / onError | B3261E / FFFFFF | F2B8B5 / 5C1310 | 6.54 | 7.91 |
| errorContainer / onErrorContainer | F9DEDC / 410E0B | 8C1D18 / F9DEDC | 12.77 | 7.17 |
| primary as text on surface (links) | 1B5A85 on FFFFFF | 9CCBEF on 171D24 | 7.37 | 9.86 |

### 1.2 Custom semantic (not in M3, exposed via `AccountingColors` CompositionLocal)
| Token | Light | Dark | Light ratio (on surface) | Dark ratio (on surface) | Use |
|---|---|---|---|---|---|
| income | 17653A | 7DD39A | 7.10 | 9.42 | income account totals |
| expense | A8322A | F2A59E | 6.66 | 8.61 | expense account totals |

### 1.3 Non-text (>= 3:1 for UI boundaries)
| Token (M3 `outline`) | Light | Dark | Ratio on surface (L / D) |
|---|---|---|---|
| outline (input border, focus-adjacent, dividers when needed) | 6F7885 | 8E97A4 | 4.47 / 5.75 |

Focus ring: 2dp `primary` (1B5A85 light / 9CCBEF dark), 7.37 / 9.86 on surface.
Disabled controls are exempt from WCAG contrast; render at onSurface 38% alpha.

### 1.4 Compose mapping (Color.kt)
- Replace `Gray*` placeholders. Keep the names `LightColors` and `DarkColors`.
- `lightColorScheme(primary=1B5A85, onPrimary=FFFFFF, primaryContainer=D3E4F1, onPrimaryContainer=0B2C43, background=F6F8FB, onBackground=141A22, surface=FFFFFF, onSurface=141A22, surfaceVariant=E6EBF2, onSurfaceVariant=424B59, outline=6F7885, error=B3261E, onError=FFFFFF, errorContainer=F9DEDC, onErrorContainer=410E0B)`
- `darkColorScheme(primary=9CCBEF, onPrimary=0A2739, primaryContainer=1D4764, onPrimaryContainer=D3E4F1, background=0F1419, onBackground=E3E8EE, surface=171D24, onSurface=E3E8EE, surfaceVariant=2A323D, onSurfaceVariant=C3CBD6, outline=8E97A4, error=F2B8B5, onError=5C1310, errorContainer=8C1D18, onErrorContainer=F9DEDC)`
- `data class AccountingColors(val income: Color, val expense: Color)`, with light and dark instances. Provided by `AccountingTheme` next to `MaterialTheme`.
- Dynamic color stays off. `isSystemInDarkTheme()` stays.

## 2. Typography (Type.kt)
Vazirmatn static files: Regular (400), Medium (500), Bold (700) only. Do not use SemiBold, Light or Black. Compose would synthesize them.
Persian needs tall line height: minimum 1.5x font size.

| M3 slot | Size / line height (sp) | Weight | Use |
|---|---|---|---|
| displayMedium | 40 / 52 | Bold | Hero total on confirmation card |
| headlineSmall | 24 / 32 | Bold | Screen title |
| titleLarge | 20 / 28 | Bold | Card title |
| titleMedium | 16 / 24 | Medium | Account name in journal row |
| bodyLarge | 16 / 26 | Regular | Input text, body |
| bodyMedium | 14 / 22 | Regular | Supporting text, description |
| bodySmall | 12 / 18 | Regular | Caption, date (Jalali) |
| labelLarge | 14 / 20 | Medium | Button labels |
| labelMedium | 12 / 16 | Medium | Side label (بدهکار/بستانکار), chips |
| labelSmall | 12 / 18 | Medium | Badge. Minimum size 12sp. |
| amount (custom, `AccountingTypography.amount`) | 18 / 26 | Medium | All money values in lists and lines |

Compose: `Typography(defaultFontFamily = VazirmatnFamily, displayMedium = ..., ...)`. Set `letterSpacing = 0.sp` everywhere (Persian must not be letter-spaced).
Open: check whether Vazirmatn digits are tabular. If not, amounts in columns align by right (start in RTL).

## 3. Spacing and shape
- Grid: 4dp. Steps: 4, 8, 12, 16, 24, 32, 48.
- Screen horizontal padding 16dp. Card padding 16dp. Gap between journal rows 8dp. Section gap 24dp.
- Min touch target 48dp (`minimumInteractiveComponentSize`). Visual size may be smaller, hit area is not.
- Corners: small 8dp, medium 12dp, large 16dp (cards, confirmation card), input bar 28dp (pill), bottom sheet top 28dp.
- Elevation: tonal (surface tint) only. No drop shadows except FAB level 3.

## 4. RTL rules
- `AccountingTheme` forces `LayoutDirection.Rtl`. Do not override per screen.
- Use `start`/`end` in padding, `Arrangement.Start/End`, `Alignment.Start/End`. Never `left`/`right`.
- Directional icons (send, back, chevron, undo) use `autoMirror = true`. Non-directional icons (mic, check, close, trash) do not mirror.
- Visual placement in RTL: `start` = right. Send button sits at `end` (visual left).
- Digits: Persian (۰–۹) in all UI text. Latin digits only in URLs, codes, version strings. Formatting is done in the T05 persian utils, not in the theme.
- Money: `[LRI]` + formatted number + `[PDI]` (U+2066 / U+2069), then a space and unit, inside the RTL paragraph. This keeps sign, separators (٬) and decimal (٫) in order. Persian digits are kept.
- Mixed text (user-typed Latin product names): input fields use `TextStyle(textDirection = TextDirection.Content)`. Wrap Latin tokens (`Docker`, `npm`) in FSI/PDI when they appear in Persian sentences.
- Never start a Persian sentence with a Latin word in copy (see persian-writing rules). Lead with a Persian word.
- Dates: stored Gregorian ISO. Shown Jalali with Persian digits. Example format: ۱۴۰۵/۰۸/۰۹.
- Numbered lists: markers are Persian digits written as text, not Compose bullets with Latin numbers.
- Debit/credit lines never use a minus sign. Side is shown as a word.

## 5. Components
Sizes assume 360dp width. All text containers use `heightIn(min=...)`, not fixed height, so 200% font scale does not clip.

### 5.1 Chat input bar
- Anatomy (RTL order, start to end): [mic 48dp] [text field, weight 1] [send 48dp].
- Container: surface, 1dp `outline` border, corner 28dp, min height 56dp, horizontal padding 8dp.
- Text: bodyLarge, `TextDirection.Content`, single line growing to 4 lines.
- Placeholder: bodyLarge, `onSurfaceVariant` (8.81 on surface).
- Focus: 2dp `primary` ring.
- Send enabled only when text is not blank.

States:
| State | Visual | Behavior |
|---|---|---|
| empty | Placeholder `input_hint` shown, send disabled | Focus opens keyboard |
| typing | Send enabled | Send submits |
| loading | Text read-only, send replaced by 20dp progress, label `loading_parse` under bar | No second submit |
| error | Supporting text in `error` color under bar, border `error` 2dp | Text kept so user can edit |

### 5.2 Mic button
- Idle: 56dp circle, `primaryContainer` fill, `onPrimaryContainer` icon. Min touch 56dp.
- Listening: `primary` fill, `onPrimary` icon, label `input_listening` beside the bar. Ring pulse is decorative. Reduced motion: static 2dp ring.
- Permission denied: icon stays, tap opens `mic_permission_body` with action `mic_permission_action`.
- Voice error: snackbar-like inline text `input_voice_unclear`, retry tap possible.

States: idle, listening, permission-denied, error. Loading is the same as listening until transcript arrives.

### 5.3 Confirmation card
- Anatomy: title (`confirm_title`), hero total, journal lines (account, side label, amount), total row (`confirm_total_label`), actions.
- Container: `surface` with 1dp `outline` border, corner 16dp, padding 16dp.
- Hero total: displayMedium, `onSurface`. Shows the amount being recorded.
- Lines: account name titleMedium; side label labelMedium (`side_debit` / `side_credit`); amount in `amount` style. Line height min 48dp.
- Total row: both sums shown. They must be equal. This is the balance check the user sees.
- Actions: primary `confirm_action` (filled, 48dp min), secondary `confirm_edit_action` (outlined), tertiary `confirm_cancel_action` (text). Primary at `start`, secondary next to it.

States:
| State | Visual | Behavior |
|---|---|---|
| default | As above, actions enabled | Confirm writes to Room |
| loading | Actions disabled, `loading_save` text beside primary, 20dp progress | No second confirm |
| error (parse) | Card replaced by error block: `error_parse_title` + `error_parse_body`, actions `confirm_edit_action` and `action_retry` | Nothing written |
| error (unbalanced) | Total row in `error` color plus `error_unbalanced_body` | Nothing written |
| error (network) | `error_network_title` + `error_network_body`, action `action_retry` | Nothing written |

Role: `liveRegion = Polite` on the card and on error blocks.

### 5.4 Journal row
- Height min 64dp, full width tap target.
- Line 1: account name titleMedium, `onSurface`.
- Line 2: date (bodySmall, Jalali) + ` · ` + description (bodyMedium, `onSurfaceVariant`).
- Trailing (end): amount (`amount` style) above side label (labelMedium).
- Reversed entry: badge `row_reversed_badge` (labelSmall, `errorContainer` fill, `onErrorContainer` text). Row is not struck through.
- Divider: 1dp `surfaceVariant`, not a text color, so no contrast requirement beyond decorative.

States:
| State | Visual | Behavior |
|---|---|---|
| default | As above | Tap opens entry detail |
| pressed | 12% `onSurface` overlay | |
| loading | Skeleton: 2 grey bars in `surfaceVariant`, static under reduced motion | Shown while Flow first emits |
| empty | Not a row. Shows StateBlock 5.6 with `ledger_empty_title`, `ledger_empty_body` | |

### 5.5 Amount text (`AmountText`)
- Input: `minor: Long` rials, `unit: Toman | Rial` (display setting). Toman = rial / 10.
- Output: `[LRI]۵۰۰٬۰۰۰[PDI] تومان`. Style `amount`, color `onSurface`. Income/expense tint only in account totals (1.2).
- Rounding: whole toman when rial is divisible by 10. Otherwise one decimal with ٫. Open: confirm with owner (T02 accounting rules).
- Negative values never displayed. Reversals appear as separate entries.
- Accessibility: `contentDescription = "{amount} {unit}، {side}"`, e.g. `۵۰۰٬۰۰۰ تومان، بدهکار`.

### 5.6 StateBlock (empty / error / loading full screen)
- Stack, centered in content area: icon 48dp (decorative, `contentDescription = null`), title titleLarge, body bodyMedium `onSurfaceVariant`, optional action button 48dp.
- Loading: CircularProgressIndicator 32dp + `loading_list`.
- Empty: no icon animation.
- Error: icon in `error`, action `action_retry`.

### 5.7 Buttons
- Filled (primary), outlined (outline), text (primary). Labels labelLarge. Min 48dp height.
- Disabled: onSurface 38% text on 12% container.
- Loading: text stays, progress 20dp at start.

## 6. States summary
| Component | Empty | Error | Loading |
|---|---|---|---|
| Chat input bar | placeholder | inline under bar, text kept | read-only field, progress |
| Mic button | idle | permission block, voice unclear | listening |
| Confirmation card | n/a (only exists with a draft) | parse, unbalanced, network | disabled actions, progress |
| Journal row / ledger list | StateBlock empty | StateBlock error + retry | skeleton rows |
| Reports | StateBlock `reports_empty_body` | StateBlock error + retry | skeleton |

## 7. Accessibility (WCAG 2.1 AA)
- Text contrast: all pairs in 1.1 and 1.2 are >= 4.5:1 light and dark.
- Non-text: outline 4.47 (light) / 5.75 (dark) on surface, >= 3:1. Focus ring >= 3:1.
- Touch targets >= 48dp (2.5.5 uses 44 CSS px; we use 48dp).
- Color is never the only cue. Debit/credit always uses the word.
- Focus order: top to bottom, start to end, matches RTL reading order. Keyboard/switch access works on all actions.
- Every icon-only button has a Persian `contentDescription`: `input_send_cd`, `input_mic_cd`, `input_mic_stop_cd`, `nav_back_cd` (بازگشت).
- Headings: screen titles use `semantics { heading() }`.
- Live regions: confirmation card and error blocks use Polite.
- Font scale 200%: no clipping. Use min heights and wrap text.
- Reduced motion: honor system animator-duration-scale 0. Pulse, skeleton shimmer and progress animate only when allowed.
- Voice: errors are shown as text, not only spoken.

## 8. Persian UX copy (fa)
Keys match planned `strings.xml` names. All copy is Persian. ZWNJ (‌) used in می‌شود, نمی‌کنند, ‌تان, کامل‌تر, ثبت‌شده. Register: formal-but-human (شما).

Capture
- `input_hint`: «مثلاً: ۴ قوطی نوشابه روی هم ۵۰۰٬۰۰۰ تومان خریدم»
- `input_send_cd`: «ارسال»
- `input_mic_cd`: «ضبط صدا»
- `input_mic_stop_cd`: «توقف ضبط»
- `input_listening`: «در حال گوش دادن…»
- `input_voice_unclear`: «صدا واضح نبود. دوباره بگویید.»
- `mic_permission_body`: «برای ضبط صدا به دسترسی میکروفون نیاز داریم.»
- `mic_permission_action`: «اجازه دسترسی»
- `nav_back_cd`: «بازگشت»

Confirm
- `confirm_title`: «بررسی تراکنش»
- `confirm_total_label`: «جمع»
- `confirm_action`: «ثبت تراکنش»
- `confirm_edit_action`: «ویرایش»
- `confirm_cancel_action`: «انصراف»
- `side_debit`: «بدهکار»
- `side_credit`: «بستانکار»
- `amount_cd`: «{مبلغ} {واحد}، {طرف}»

Errors
- `error_parse_title`: «متوجه نشدیم»
- `error_parse_body`: «مبلغ یا شرح تراکنش مشخص نیست. آن را کامل‌تر بنویسید.»
- `error_unbalanced_body`: «جمع بدهکار و جمع بستانکار برابر نیست؛ تراکنش ثبت نشد.»
- `error_network_title`: «اتصال برقرار نشد»
- `error_network_body`: «اینترنت را بررسی کنید و دوباره تلاش کنید. چیزی ثبت نشده است.»
- `action_retry`: «تلاش دوباره»

Loading
- `loading_parse`: «در حال خواندن تراکنش…»
- `loading_save`: «در حال ثبت…»
- `loading_list`: «در حال بارگذاری…»

Empty
- `ledger_empty_title`: «هنوز تراکنشی ثبت نشده»
- `ledger_empty_body`: «اولین تراکنش‌تان را بنویسید یا بگویید. هر تراکنش در دفتر روزنامه ثبت می‌شود.»
- `reports_empty_body`: «برای این بازه تراکنشی ثبت نشده است.»

Journal and reversal
- `row_reversed_badge`: «معکوس شده»
- `reverse_title`: «اصلاح تراکنش»
- `reverse_body`: «تراکنش‌های ثبت‌شده حذف یا تغییر نمی‌کنند. برای اصلاح، یک تراکنش معکوس ثبت می‌شود.»
- `reverse_action`: «ثبت تراکنش معکوس»

Units (settings, T12)
- `unit_toman`: «تومان»
- `unit_rial`: «ریال»

## 9. Open questions
- Brand palette: v1 values are neutral-blue placeholders. Owner approves before T14.
- Vazirmatn tabular digits: confirm in T08 with a sample column.
- Decimal display for rial values not divisible by 10: confirm rule with owner.
- Whether the confirmation card can be dismissed by swipe (affects a11y: prefer explicit cancel button).
