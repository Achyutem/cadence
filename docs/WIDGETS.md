# Widgets

Six widgets, all interactive where it makes sense, built on one shared layer.

## The shared layer

| Piece | Job |
|---|---|
| `WidgetTheme.kt` | The app's accents as plain `ColorProvider` values |
| `WidgetData.kt` | One snapshot loader, reading the app's own DAOs and domain functions |
| `WidgetActions.kt` | `ActionCallback`s that write and refresh |
| `WidgetComponents.kt` | Rows, headers, progress, empty states |
| `CadenceWidgets.kt` | One `updateAll()` that every write path calls |

**Widgets read the same DAOs and the same domain functions as the app.** There is no widget cache,
no parallel query layer, and no second notion of "is this habit due today", which is the usual
way a widget and its app quietly start disagreeing about the user's day. A streak on the home
screen is computed by the same `HabitStatistics.currentStreak` the detail screen uses.

Loading is a one-shot suspend read in `provideGlance`, not a Flow: `provideGlance` runs once per
update and the composition is then serialised into a `RemoteViews` tree. There is nothing on the
other side to receive a second emission.

## Why the accents are hand-tuned constants

This is the payoff for a decision made back in the design system. Glance renders into the
launcher's process and **cannot run Material's colour generation**, so a seed-generated palette
would be unavailable here, and widgets would have to approximate the app's colours.

Because `AccentPalette` is five fixed pairs of plain values, the same accent resolves identically
in both places. See `docs/DESIGN_SYSTEM.md`.

Light/dark is handed to Glance as a pair so the launcher picks per its own configuration, a
widget must follow the system theme even when the app is pinned to Light or Dark, because it lives
on someone else's surface. An explicit app override collapses both sides of the pair.

## The widgets

| Widget | Sizes | Interactive |
|---|---|---|
| Today | S / M / L | tick tasks, step habits |
| Todo list | M / L | tick tasks |
| Habits | M / L | step each habit |
| Single habit | 2×2 | step, ± |
| Heatmap | M / L |, |
| Next task | 2×1 | tick |

Today, Todo list, Habits and Heatmap use `SizeMode.Responsive`: one composition that reads
`LocalSize`, rather than three widget classes. There is no way for the medium layout to drift from
the large one, because they are the same code.

## Interactivity

`actionRunCallback` runs a suspend function that writes through the app's DAOs and then calls
`CadenceWidgets.updateAll`. **Completing a task or logging a glass of water never opens the app.**

Verified on device: with the app force-stopped, tapping a widget checkbox set `completed = 1` in
the database, advanced the widget to the next task, and left the launcher in the foreground.

The app also calls `refreshWidgets()` after its own writes. Without that, a widget would only
update when *it* was the thing that changed, complete a task in the app and the home screen would
keep showing it outstanding until the next periodic refresh, which is exactly the staleness that
makes people stop trusting a widget. Failures are swallowed: a widget that cannot be updated must
never take down the write that triggered it.

## Two Glance traps worth writing down

**`defaultWeight()` takes no weight value.** A progress bar built from two weighted cells in a row
always splits 50/50 regardless of the progress passed in; a bar that looks plausible in code and
renders every value as half full. Use Glance's own `LinearProgressIndicator`, which is the only
primitive here that can express a fraction.

**`RemoteViews` will not inflate a plain `<View>`.** A preview layout using one shows
"Can't load widget" in the picker, with the real cause only visible in logcat as
`Class not allowed to be inflated android.view.View`. Use `ImageView` for a decorative bar.

## The heatmap widget is 13 weeks, not a year

The in-app heatmap is a single `Canvas` because 365 composables would be too many layout nodes.
Glance has no Canvas at all, so the widget grid is literal boxes, and `RemoteViews` has a real
size limit that, once exceeded, makes the launcher silently drop the widget.

91 cells sits comfortably inside that limit and is about as much as is legible at widget scale
anyway.

## Permissions

Glance depends on WorkManager, which merges `ACCESS_NETWORK_STATE` into any app that uses it.
Cadence never sets a network constraint, and on an app that advertises having no network access it
is exactly the entry a careful user checks, so it is removed with `tools:node="remove"` and
verified against the built artifact:

```bash
aapt2 dump permissions app/build/outputs/apk/release/app-release-unsigned.apk
```

`WAKE_LOCK`, `FOREGROUND_SERVICE` and `RECEIVE_BOOT_COMPLETED` stay: WorkManager genuinely uses
them, and removing a permission a library relies on trades a cosmetic win for a runtime crash.

All receivers are `exported="false"`, the framework's `AppWidgetHost` binds them, so nothing
outside the app needs to reach them directly.
