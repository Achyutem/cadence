# Design system

## Identity

Cadence should feel **premium, calm, fast, intentional, slightly dense**. The references in the
brief are read as principles, not as surfaces to copy:

- **TickTick**, productivity density, one-tap completion, useful widgets.
- **Linear**, restraint, typography, spacing, polished transitions.
- **GitHub**, contribution heatmaps, historical data density.
- **Apple**, calmness, animation restraint, visual clarity.

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

Ten shipped accents: red, orange, sepia, green, spotify, cyan, blue, magenta, grey and
black-and-white. Each is a **hand-tuned pair of light and dark ramps**, not a generated seed.

The original five were muted, chosen for restraint, and read as dull rather than calm. These are
brighter, with light primaries around 45–55% lightness and dark primaries around 70–78%, which is
the range where an accent stays legible on a near-black surface without glowing.

Two need a word each:

- **Spotify** is the familiar `#1DB954`, darkened for light mode. The brand value against white is
  about 2.2:1, so white text on it would fail outright; dark mode gets the real thing.
- **Black and white** is the monochrome option: black on a light scheme, white on a dark one.

A plain "white" accent shipped briefly and was withdrawn. In a light scheme a white primary on a
white surface is an invisible button, and it collapsed the heatmap besides: the ramp walks from an
empty grid cell to the primary, so every level landed within a percent of the empty one and
`HeatmapRampTest` caught it. Every workaround turned out to be black-and-white wearing a hat.

The picker is a **dropdown, not a grid**. Ten swatches tiled across the screen turned the quietest
section of Settings into its loudest thing, and a row of unlabelled dots makes you guess which one
is "sepia". Collapsed it is one line: the current colour and its name.

The menu anchors to the **value**, not the row. `DropdownMenu` positions against its parent's
top-left corner, so hanging it off a full-width row dropped it out of the far left of the screen,
nowhere near the thing that was tapped.

Renaming and merging accents is a stored-value problem, so `AccentColor.parse` maps every retired
name to its nearest survivor rather than falling back to blue. Silently resetting the one visual
choice most people make is a small betrayal and an avoidable one; `AccentMigrationTest` pins the
mapping.

Seed generation was rejected deliberately. Generated schemes drift, the "same" blue yields a
different container tone in each scheme and contrast ratios wander, and Glance widgets cannot run
Material's colour generation at all, so widgets and app would diverge. Fixed accents tuned once
buy predictable contrast everywhere. Every `on*` colour clears 4.5:1 against its pairing.

### Control shape

Buttons and the segmented control take their corner radius from `LocalControlShape`, which the
theme sets from a preference: rounded rectangles or pills. Purely taste. Rounded reads precise and
technical, pills read softer; neither is more correct, so it is the user's call rather than a
decision baked into the design system. Nothing else in the app follows it, cards and sheets keep
their own radii, because a pill-shaped card is a different design, not a preference.

Material You is available as an opt-in (off by default, S+ only); Cadence's own identity is the
default.

### Heatmap ramp

Five levels, **derived** from the active accent by blending toward the surface rather than
hand-authored per accent:

- Level 0, empty. Visible enough to read as a grid, quiet enough to disappear.
- Levels 1–4, a wash of accent through to the accent itself.

Dark mode uses steeper blend fractions, because identical fractions read much darker against a
near-black background. `HeatmapRampTest` guards that all five levels are distinct and
monotonically increasing; a ramp with two equal steps would render "once" and "four times"
identically.

## Typography

**No font files ship.** The platform sans is excellent, already cached, and adds nothing to the
APK. Character comes from the scale, not the face:

- Tight negative tracking on large text (`displaySmall` at −1.2sp).
- Wide tracking (1.0sp) on the small uppercase section labels; that is what makes `TODOS` read as
  structure rather than content.
- Three weights in play: Light (date header only), Normal/Medium (body), SemiBold (titles).
- `includeFontPadding = false` and `LineHeightStyle.Trim.Both`, so a headline's optical box matches
  its visual box. Without trimming, large text carries a stubborn gap that makes careful spacing
  impossible.

**Tabular figures** (`TextStyle.tabularFigures`) on anything that changes in place, percentages,
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
Reduced motion sets that scale to **0**, so animations resolve instantly rather than being removed,
state still lands correctly and no component needs to branch on "are animations on".

## The dock

Cadence's signature control: a floating pill, narrower than the screen, drawn *over* content
rather than occupying a `Scaffold` bottom bar.

What makes it feel like an app control rather than a system one: **only the selected item shows its
label**, and that label expands horizontally as the pill grows around it. Nothing jumps, the
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
- Colour is never the only signal, completion also changes shape and weight.

---

## Revision: the Geist pass

The design language was reworked after the first build. The brief asked for the philosophy behind
Vercel and Next.js, clean, precise, fast, while keeping Cadence's accents prominent.

**What changed and why:**

| Before | After | Reason |
|---|---|---|
| System sans | **Geist Variable** (166 KB, OFL) | One file for every weight. Built for interfaces: even colour at small sizes, tall x-height, real tabular figures. |
| Blue-cast neutrals | True neutral greys | A tinted neutral fights every accent beside it. Pure grey lets the accent be the only chromatic thing on screen. |
| Dark base `#0D0E11` | `#0A0A0A` | Genuinely darker on OLED, and it makes hairline borders the thing that defines structure. |
| Radii 10/14/20 | 4/6/8/12/16 | The old scale read soft and consumer-ish. A crisp product UI lives at 6–12dp. |
| Shadows and tonal steps | **Hairline borders** | Structure without weight, crisp at any density, and identical in dark mode instead of vanishing. |
| 140–420ms | 120–380ms | The fastest-feeling interfaces get out of the way. Anything over ~250ms on a routine tap reads as lag. |
| `cubic-bezier(.2,0,0,1)` | `cubic-bezier(.4,0,.2,1)` | The curve well-tuned web UI converges on. |

### The segmented control

The first version cross-faded each option's background independently. That is the cheap way to
build it, and it read cheap: mid-transition, two chips are half-visible and none of them is the
selection.

Now there is exactly one chip and it **travels**. The control always shows precisely one selected
thing, and the movement itself says where the selection went. The chip is a sibling behind the
labels rather than a background on the selected item, which is what makes one continuously
animating indicator possible at all.

### The dock

The first version expanded the selected tab to reveal its label. It looked elegant in a screenshot
and was worse to use: every selection re-laid-out the whole bar, so the tab you wanted next was
never where you last saw it. With six destinations that stops being a quirk and becomes a cost.

Now tabs are **fixed width with a sliding indicator**. Positions are constant, every target is the
same size, and the only thing that moves is the indicator travelling to the tab you chose. No tab
draws a label, so all six carry content descriptions, and `NavigationTest` drives the dock that
way on purpose, which means a tab a screen reader cannot find is a failing test.

### The completion checkbox

The most-used control in the app, so it gets the most attention. Three things happen together:
the box fills on a spring, the tick is **drawn along its own path** rather than faded in, and a
haptic fires on completion only, never on un-completing, because undoing is a correction and
should not be congratulated.

A cross-faded glyph looks like a state change. A drawn stroke looks like an action you performed.

### Performance note

Both sliding indicators use `Modifier.offset { }`, the lambda overload, not `offset(x = …)`.
Reading an animated value during composition recomposes on every frame; deferring the read to the
layout phase means a frame only re-lays-out. Lint's `UseOfNonLambdaOffsetOverload` caught this,
which is a good argument for running lint with `warningsAsErrors`.
