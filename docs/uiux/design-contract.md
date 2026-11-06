# UX-00 — Professional Design Contract ("Control Deck")

Status: contractual for UX-01..UX-09. Colour/typography values below are **candidates**, not
implemented values. Nothing here is implemented in UX-00.

## 1. Direction

Professional, calm, technical, data-focused, dark-first, consistent. Explicitly rejected:
decorative hacker/neon styling, gratuitous glass/blur, misleading radar realism, fabricated
device positions, arbitrary animation, deep card nesting, oversized secondary buttons, repeated
explanatory copy. Every screen must answer in one glance: what is running, what was seen, what
can I do next.

## 2. Colour roles (Material 3 semantics, candidates)

Dark scheme only (light theme is developer-declined and out of scope).

| Role | Candidate | Use |
|---|---|---|
| background | #0F1216 | app canvas |
| surface / surfaceVariant | #151A20 / #1D242C | cards and grouped rows |
| primary | #5AA9E6 (cool blue) | primary action, selected state |
| secondary | #7FC8A9 | informational accents, charts series B |
| tertiary | #C9A227 (muted amber) | attention, freshness "Eski" |
| error | #E5695C | destructive + failure only |
| onSurface / onSurfaceVariant | #E6EAEE / #A7B0BA | text primary/secondary |

Rules: destructive actions never use primary blue; status colour is never the only signal
(pill text always names the state); charts use at most three series + neutral.

## 3. Typography

Keep Material 3 defaults' roles but tighten the display scale: screen title `headlineSmall`,
section heading `titleSmall` (was `titleMedium` in places), row primary `bodyLarge`, row
secondary/meta `bodySmall`, numeric/stat values `titleMedium` tabular. No custom font file in
UX-01 unless it ships as a resource with license header documented.

## 4. Spacing and density

Base unit 4dp. Allowed steps: 2, 4, 8, 12, 16, 24, 32. Screen horizontal padding 16dp; card
inner padding 12dp; gap between sibling cards 8dp (was up to 16dp); list row vertical padding
8dp. No raw dp values outside the scale in new code.

## 5. Shape and elevation

Cards 12dp radius, chips/pills full, dialogs 16dp, text fields 8dp. Elevation 0 for list rows,
1 for grouped cards, 3 for dialogs/bottom sheets; tonal surfaces instead of shadows on dark.
Never nest a card inside a card.

## 6. Actions and hierarchy

Exactly one primary action per screen state, filled; secondary actions outlined/text; destructive
actions separated by a divider and never adjacent to the primary. Minimum 48x48dp touch targets
including icon buttons and stars.

## 7. Information density

Observation rows: one primary line (name/source), one meta line (freshness + signal + time), one
trailing affordance (star). Maximum 2 lines of text before ellipsis. Section explanations live in
expandable disclosure, not as permanent paragraphs.

## 8. States

Every data screen defines: loading-like/neutral, empty (honest, category-specific), populated,
partial/unknown, error, blocked. Empty copy must state WHY and never imply "no devices nearby"
unless the observation session actually ran.

## 9. Charts

Deterministic buckets only; zero renders as zero, never interpolated; axis/labels in Turkish;
textual summary required for every chart; no animated transitions by default.

## 10. Accessibility and system UI

Contrast: normal text >= 4.5:1, large text >= 3:1 against its surface. Touch >= 48dp. Edge-to-edge
with correct insets for status/navigation bars and IME padding on text fields. Semantics for
every icon-only control. Light theme, 2x font scale and TalkBack remain developer-declined: code
must not regress basic semantics, but no pass is claimed.

## 11. Non-negotiables carried from the functional program

No change to scanning/permission/lifecycle/consent/retention logic; no fifth tab; no new
permissions; SanitizedOnly UI data; radar stays schematic with its honesty label; destructive
flows keep confirmation; FieldDiag gate rules unchanged; all existing JVM tests must stay green.
