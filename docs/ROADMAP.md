# Roadmap

One phase at a time. Each phase ends with: builds clean, tests pass, app runs, docs updated.

---

## Phase 0 — Foundation ✅ complete

Gradle (KTS + version catalog), Compose, Material 3, Room with the full v1 schema and exported
schema file, DataStore, type-safe Navigation Compose, the theme system (3 modes × 5 accents +
Material You), design tokens, the floating dock, four screens with Settings fully working, the
`CadenceClock` abstraction, 32 JVM tests and 10 instrumented tests.

**Verified:** debug + release builds, 42 passing tests, and the app running on an Android 16
emulator with settings persisting across process death.

---

## Phase 1 — Todo core (next)

Task creation, editing, deletion, completion, subtasks, priority, dates, times, duration,
ordering. The Today and Todos screens become real.

Quick-add is the make-or-break interaction: one tap to a text field, one line, done. Structured
configuration comes *after* creation, never before it.

**Done when:** a task can be created, completed and re-found after an app restart; the Today
progress card reflects real data; completion animates; reordering persists; empty states and
completed-task behaviour all work.

---

## Phase 2 — Recurrence

`domain/recurrence` — one engine for tasks and habits. See `docs/RECURRENCE.md` for the full
case list and test plan. This phase is mostly tests.

**Done when:** every case in that document passes, including month-end clamping, last-weekday
months, leap years and interval phase from the anchor.

---

## Phase 3 — Habits

Creation, all four metric types, targets and goal direction, recurrence, entries, streaks,
statistics. Habit detail screen.

**Done when:** streaks and rates are computed from entries (never stored), editing a past day
corrects all derived numbers, and archived habits stay out of active lists but in history.

---

## Phase 4 — Calendar

Month, week and day views; the day timeline; task scheduling; habit indicators; date navigation.

**Done when:** all three views read from range-bounded queries and stay smooth with a year of
data loaded behind them.

---

## Phase 5 — Notifications

Task and habit reminders, recurring reminders, notification actions, reboot and time-zone
rescheduling, quiet hours. See `docs/NOTIFICATIONS.md`.

**Done when:** every edge case in that document is handled — especially reboot, DST and
completed-before-firing.

---

## Phase 6 — Visualisation

The custom heatmap (no charting library), progress rings, habit statistics, weekly and monthly
summaries.

**Done when:** the heatmap stays smooth with years of history, loads only visible ranges, and
adapts to accent and scheme.

---

## Phase 7 — Daily check-in

Mood, energy, optional note, history and visual trends.

**Done when:** it is genuinely skippable and never reads as another task.

---

## Phase 8 — Widgets

The shared widget layer first, then the twelve widgets. See `docs/WIDGETS.md`.

**Done when:** a task can be completed and a habit incremented from the home screen without
opening the app, in both schemes and every accent.

---

## Phase 9 — Insights

The local analytics engine. See `docs/INSIGHTS.md` — the statistical-responsibility rules are
the specification, not a caveat.

---

## Phase 10 — Design polish

Typography, spacing, animation, haptics, gestures, transitions, empty/error/loading states, dark
mode, accents, widget visuals, heatmap, calendar, Today. Budget real time here.

---

## Phase 11 — Reliability

Process death, restart, reboot, notification scheduling, date and time-zone changes, recurrence
edge cases, large datasets, years of history, widget refresh, database migrations.
