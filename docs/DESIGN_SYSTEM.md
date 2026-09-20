# Design system

## Identity

Cadence should feel **premium, calm, fast, intentional, slightly dense**. The references in the
brief are read as principles, not as surfaces to copy:

- **TickTick** — productivity density, one-tap completion, useful widgets.
- **Linear** — restraint, typography, spacing, polished transitions.
- **GitHub** — contribution heatmaps, historical data density.
- **Apple** — calmness, animation restraint, visual clarity.

Nothing proprietary is reproduced. The identity comes from the scale, the neutral palette and the
floating dock, not from imitation.

## Colour

**Neutrals carry the UI; accent marks what is active.** Accent appears on active navigation,
progress, completion, selected states, the heatmap and focus. It never recolours surfaces.

The neutral ramp has a very slight blue cast (hue ≈ 230) rather than being pure grey. At low
saturation it reads as calm, and it stops every accent from looking dirty against it.

| | Light | Dark |
|---|---|---|
| background | `#FAFAFC` | `#0D0E11` |
| surface | `#FFFFFF` | `#131519` |
| container | `#EDEFF4` | `#1C1F26` |
| onSurface | `#14161C` | `#E9EBF1` |
| onSurfaceVariant | `#5A5F6E` | `#979EAE` |
| outlineVariant | `#DFE2EA` | `#2A2E38` |

### Accents

Five shipped accents: blue, purple, green, orange, pink. Each is a **hand-tuned pair of light and
dark ramps**, not a generated seed.

Seed generation was rejected deliberately. Generated schemes drift — the "same" blue yields a
different container tone in each scheme and contrast ratios wander — and Glance widgets cannot run
Material's colour generation at all, so widgets and app would diverge. Five fixed accents tuned
once buys predictable contrast everywhere. Every `on*` colour clears 4.5:1 against its pairing.

Material You is available as an opt-in (off by default, S+ only); Cadence's own identity is the
default.

### Heatmap ramp

Five levels, **derived** from the active accent by blending toward the surface rather than
hand-authored per accent:

- Level 0 — empty. Visible enough to read as a grid, quiet enough to disappear.
- Levels 1–4 — a wash of accent through to the accent itself.

Dark mode uses steeper blend fractions, because identical fractions read much darker against a
near-black background. `HeatmapRampTest` guards that all five levels are distinct and
monotonically increasing — a ramp with two equal steps would render "once" and "four times"
identically.

## Typography

**No font files ship.** The platform sans is excellent, already cached, and adds nothing to the
APK. Character comes from the scale, not the face:

- Tight negative tracking on large text (`displaySmall` at −1.2sp).
- Wide tracking (1.0sp) on the small uppercase section labels — that is what makes `TODOS` read as
  structure rather than content.
- Three weights in play: Light (date header only), Normal/Medium (body), SemiBold (titles).
- `includeFontPadding = false` and `LineHeightStyle.Trim.Both`, so a headline's optical box matches
  its visual box. Without trimming, large text carries a stubborn gap that makes careful spacing
  impossible.

**Tabular figures** (`TextStyle.tabularFigures`) on anything that changes in place — percentages,
streak counts, habit values, timeline clock labels. Proportional digits jitter horizontally as
they tick, which is exactly the kind of small ugliness that makes an app feel unfinished.

## Spacing and shape

Four-point scale (`Spacing`), with a 2dp step for optical alignment only. Every screen uses
`Spacing.screenGutter` (20dp) so content edges line up across tabs, and reserves
`Spacing.dockClearance` (96dp) at the bottom so lists scroll clear of the floating dock.

Radii stay in the 10–20dp range; only pills go fully round. The brief warns against excessive
rounded containers, and a screen of 28dp cards looks like a toy.

**Elevation is rare.** Only the dock and transient surfaces lift. Cards are separated by tone and
hairline outlines, not shadows.

## Motion

| Token | ms | Use |
|---|---|---|
| `MICRO` | 140 | checkmarks, taps, label fade-out |
| `QUICK` | 180 | small state changes |
| `STANDARD` | 240 | navigation, dock, most transitions |
| `EMPHASIZED` | 320 | progress bars and rings |
| `EXPRESSIVE` | 420 | heatmap period changes |

All durations pass through `CadenceTheme.duration()`, which multiplies by `LocalMotionScale`.
Reduced motion sets that scale to **0**, so animations resolve instantly rather than being removed
— state still lands correctly and no component needs to branch on "are animations on".

## The dock

Cadence's signature control: a floating pill, narrower than the screen, drawn *over* content
rather than occupying a `Scaffold` bottom bar.

What makes it feel like an app control rather than a system one: **only the selected item shows its
label**, and that label expands horizontally as the pill grows around it. Nothing jumps — the
unselected items simply give up width. Four quiet icons, one clearly-named destination.

Selected items also swap to a filled icon variant; the weight change registers before the colour
does. Hidden labels are supplied as content descriptions so screen readers always hear all four
names.

## Accessibility

- Minimum 48dp touch targets (`TouchTarget.min`), including the accent swatches.
- Content descriptions on every icon-only control; decorative elements are explicitly cleared.
- Rows that act as a single control (a settings toggle row) carry the role and clear the inner
  widget's semantics, so nothing is announced twice.
- Text scales with the system font size; no fixed-height text containers.
- Reduced motion is honoured globally.
- Colour is never the only signal — completion also changes shape and weight.
