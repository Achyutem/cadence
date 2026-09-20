# Roadmap

One phase at a time. Each phase ends with: builds clean, tests pass, app runs, docs updated.

---

## Phase 0, Foundation ✅ complete

Gradle (KTS + version catalog), Compose, Material 3, Room with the full v1 schema and exported
schema file, DataStore, type-safe Navigation Compose, the theme system (3 modes × 5 accents +
Material You), design tokens, the floating dock, four screens with Settings fully working, the
`CadenceClock` abstraction, 32 JVM tests and 10 instrumented tests.

**Verified:** debug + release builds, 42 passing tests, and the app running on an Android 16
emulator with settings persisting across process death.

---

## Phase 1, Todo core ✅ complete

Creation, completion, subtasks with derived parent state, priority, dates, times, duration,
ordering. Today and Todos read real data.

Quick-add is the interaction that matters: one tap to a field, one line, Done. The composer stays
open and clears after each save, because tasks arrive in bursts.

**Verified:** tasks created, completed and re-found after a reinstall; progress card tracking real
completion; completion animating; hide/sink completed-task behaviour working.

Still open: editing an existing task, drag reordering, tags, and the task detail screen.

---

## Phase 2, Recurrence ✅ complete

One engine for tasks and habits, in `domain/recurrence`. 31 unit tests cover every case in
`docs/RECURRENCE.md`: month-end clamping, last-weekday months, leap years, 29 February, interval
phase from the anchor, multi-weekday fortnightly drift, end dates and occurrence limits.

Still open: the recurrence picker UI, and attaching rules to tasks (habits already use them).

---

## Phase 3, Habits ✅ complete

All four metric types, targets, goal direction (at least / at most), schedules, entries, streaks
and statistics. Detail screen with the heatmap.

Every derived number is computed from entries, never stored. 19 unit tests pin the behaviour that
is easy to get wrong: weekday habits keeping their streak over the weekend, today-not-done-yet not
breaking a streak, rates measured against *scheduled* days, and averages excluding untouched days.

**Verified on device:** boolean and quantity habits created, incremented, and reflected in the
heatmap and statistics.

Still open: editing a habit, archiving from the UI, custom recurrence, habit reordering.

---

## Notes ✅ complete (added outside the original plan)

Fifth dock destination. Markdown notes with a heading and body, pinning, search, and an autosaving
editor with a formatting bar. Custom Markdown subset, parser and Compose renderer, no dependency,
covered by 19 unit tests focused on half-typed input.

---

## Data ✅ complete (added outside the original plan)

Whole-database JSON export and import through the Storage Access Framework, so no storage
permission is needed. The file format is a versioned DTO layer independent of the Room schema.
Import replaces everything, states so plainly, and runs in one transaction.

Plus a display name used only for the greeting on Today.

---

## Phase 4, Calendar

Month, week and day views; the day timeline; task scheduling; habit indicators; date navigation.

**Done when:** all three views read from range-bounded queries and stay smooth with a year of
data loaded behind them.

---

## Phase 5, Notifications

Task and habit reminders, recurring reminders, notification actions, reboot and time-zone
rescheduling, quiet hours. See `docs/NOTIFICATIONS.md`.

**Done when:** every edge case in that document is handled, especially reboot, DST and
completed-before-firing.

---

## Phase 6, Visualisation

The custom heatmap (no charting library), progress rings, habit statistics, weekly and monthly
summaries.

**Done when:** the heatmap stays smooth with years of history, loads only visible ranges, and
adapts to accent and scheme.

---

## Phase 7, Daily check-in

Mood, energy, optional note, history and visual trends.

**Done when:** it is genuinely skippable and never reads as another task.

---

## Phase 8, Widgets

The shared widget layer first, then the twelve widgets. See `docs/WIDGETS.md`.

**Done when:** a task can be completed and a habit incremented from the home screen without
opening the app, in both schemes and every accent.

---

## Phase 9, Insights

The local analytics engine. See `docs/INSIGHTS.md`; the statistical-responsibility rules are
the specification, not a caveat.

---

## Phase 10, Design polish

Typography, spacing, animation, haptics, gestures, transitions, empty/error/loading states, dark
mode, accents, widget visuals, heatmap, calendar, Today. Budget real time here.

---

## Phase 11, Reliability

Process death, restart, reboot, notification scheduling, date and time-zone changes, recurrence
edge cases, large datasets, years of history, widget refresh, database migrations.
