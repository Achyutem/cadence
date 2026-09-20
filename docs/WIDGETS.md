# Widgets

> Status: **designed in Phase 0, implemented in Phase 8.** Glance is already on the dependency
> list; no widgets are declared yet.

Widgets are a first-class feature of Cadence, not a bonus. For a daily-use productivity app the
home screen *is* a primary surface — most days the user should be able to tick a task without
opening anything.

## Approach

Jetpack Glance, with a **shared widget layer** rather than twelve independent implementations:

- One data path: widgets read the same DAOs as the app, through a small set of suspend "widget
  state" loaders. No duplicated query logic and no separate cache to go stale.
- One theme bridge: `AccentPalette` already stores plain colour values rather than a generated
  Material scheme, precisely so Glance — which cannot run Material's colour generation — can
  render the same accents as the app. This is why accents are hand-tuned (see
  `docs/DESIGN_SYSTEM.md`).
- One update trigger: a single `updateAll` path invoked after any write that could change widget
  content, rather than each widget guessing when to refresh.

## Planned widgets

| # | Widget | Sizes | Interactive |
|---|---|---|---|
| 1 | Today progress | S, M | — |
| 2 | Todo list | S, M, L | tick to complete |
| 3 | Habits | M, L | tap to complete / increment |
| 4 | Single habit | S, M | increment |
| 5 | Streak | S | — |
| 6 | Heatmap | M, L | — |
| 7 | Daily check-in | M | tap a mood |
| 8 | Next task | S, M | complete |
| 9 | Calendar | M, L | — |
| 10 | Daily summary | M | — |
| 11 | Upcoming tasks | M, L | complete |
| 12 | Habit progress | S, M | increment |

## Interactivity

Glance `actionRunCallback` handles a tap by running a suspend function that writes to Room and
then updates the widget. Completing a task or incrementing a habit therefore never opens the app.

**Nothing fakes interactivity the platform cannot deliver.** Text input, scrolling inside small
sizes, and animation are all limited on widgets; where a gesture cannot be honoured reliably, the
widget opens the relevant screen instead of pretending.

## Configuration

Where it earns its place: active-only / completed-only / both, max items, sort order, selected
habit, selected date. Configuration is a launcher-provided activity per widget type, backed by
the same DataStore.

## Visual rules

Widgets must look like Cadence, not like a default Android demo widget: the same accents, the
same restrained type hierarchy, rounded surfaces that respect the launcher's own background
treatment, and correct behaviour in both light and dark. Responsive layouts via Glance's size
modes rather than one layout stretched.
