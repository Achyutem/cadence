# Database

Room over SQLite. Version **3**. Schemas exported to `app/schemas/` and committed.

## Storage contract

This is the part most likely to cause silent corruption years later, so it is fixed and tested
(`CadenceTypeConvertersTest`).

| Kotlin | SQLite | Encoding | Example |
|---|---|---|---|
| `LocalDate` | TEXT | ISO-8601 `yyyy-MM-dd` | `2026-09-20` |
| `LocalTime` | TEXT | ISO-8601 `HH:mm[:ss]` | `19:30` |
| `Instant` | INTEGER | epoch millis, UTC | `1789056000000` |
| `Set<DayOfWeek>` | TEXT | sorted CSV of ISO values | `1,3,5` |
| enums | TEXT | constant name | `QUANTITY` |

**Dates are text, not epoch-day integers.** ISO text sorts lexicographically in exactly
chronological order, which makes `BETWEEN` range queries for the calendar and heatmap correct
without any conversion, and makes a database dump readable.

**Only `Instant` is UTC.** A habit on "18 September" is a local calendar concept; converting it to
UTC would move it a day for anyone east of Greenwich after 00:00 local.

**Day sets are sorted before storage**, so `{Fri, Mon, Wed}` and `{Mon, Wed, Fri}` produce the same
text and compare equal in SQL.

## Tables

### `tasks`
`id · title · notes · scheduledDate · startTime · durationMinutes · priority · completed ·
completedAt · parentTaskId → tasks · recurrenceRuleId → recurrence_rules · reminderId → reminders ·
sortOrder · archived · createdAt · updatedAt`

Three shapes are all first-class: date only, date + time, date + time + duration. A task with no
`scheduledDate` is a backlog item and still a real task.

`parentTaskId` is one level of nesting, `ON DELETE CASCADE`. **Parent completion is derived from
children in the domain layer and never written back**, so the two can never disagree.

A task with a `recurrenceRuleId` is a *template*. It is one row regardless of how long it recurs
for; per-date state lives in `task_occurrences`.

Indices: `scheduledDate`, `parentTaskId`, `recurrenceRuleId`, `reminderId`, `(archived, completed)`.

### `task_occurrences`
`id · taskId → tasks · date · completed · completedAt · skipped · createdAt · updatedAt`

Unique on `(taskId, date)`. Rows exist only for dates the user actually touched, an untouched
future date simply has no row. `skipped` is distinct from "not completed": a skipped occurrence is
excused and does not count against completion rate; a missed one does.

### `habits`
`id · name · description · type · targetValue · goalDirection · unit · recurrenceRuleId ·
reminderId · startDate · sortOrder · archived · createdAt · updatedAt`

`type` ∈ `BOOLEAN | COUNT | QUANTITY | DURATION`, one metric model, so streaks, heatmaps and
statistics have one code path. `goalDirection` ∈ `AT_LEAST | AT_MOST`, because "at most 2 coffees"
needs the opposite completion test to "at least 8 glasses".

`startDate` exists so statistics never look behind a habit's creation and invent a year of
"missed" days.

A null `recurrenceRuleId` means "every day", the engine substitutes a daily rule rather than
special-casing null everywhere.

### `habit_entries`
`id · habitId → habits · date · value · completed · note · createdAt · updatedAt`

Unique on `(habitId, date)`, `ON DELETE CASCADE`. **This is the historical record from which every
derived number is computed.** Editing a past day immediately corrects all history.

`completed` is stored rather than recomputed from `value`, because the habit's target can change
later and a day that genuinely met the target it had at the time should stay met.

### `daily_check_ins` (retired)
`id · date (unique) · mood · energy · note · createdAt · updatedAt`

The feature was removed; see `docs/PRODUCT.md`. The table stays, and export and import still carry
it, because dropping it would be a destructive migration against rows a user may already have, and
because a backup written by the version that had check-ins should still restore completely.
Nothing writes to it any more.

### `recurrence_rules`
`id · frequency · interval · daysOfWeek · dayOfMonth · weekOfMonth · weekdayOfMonth · monthOfYear ·
anchorDate · endDate · occurrenceLimit`

A pure description of *which dates* something lands on, no completion state, no task or habit
identity. That is precisely what lets one engine serve both.

`anchorDate` is the phase reference for interval arithmetic ("every 2 days" counted from where?).
It is never mutated; editing a recurring item's start creates a new rule, so historical occurrences
stay reconstructible.

### `reminders`
`id · timeOfDay · leadMinutes · enabled`

A time-of-day plus an offset, **not an absolute instant**. The alarm time is derived per occurrence
at scheduling time in the current zone, which is what makes reminders survive travel and DST.

### `notes`
`id · title · content · preview · pinned · archived · sortOrder · createdAt · updatedAt`

Added in v2. `content` is raw Markdown; `preview` is a write-time cache of it. See
[`NOTES.md`](NOTES.md) for why the text stays authoritative and why the cache is the one
denormalised field in the schema.

### `breathing_sessions`
`id · date · kind · durationSeconds · roundsCompleted · roundsPlanned · longestHoldSeconds ·
completed · createdAt`

Added in v3. `completed` distinguishes finishing a table from stopping early, and **both are
kept**, for a CO₂ or O₂ table the round you stopped at is the measurement. `longestHoldSeconds`
is stored rather than derived because it is the one number that cannot be recomputed from the
exercise definition: it depends on where the user actually stopped.

### `tags`, `task_tags`
Simple many-to-many, both sides `ON DELETE CASCADE`.

## Rules

- **Foreign keys are enforced.** Room disables them by default; a `RoomDatabase.Callback` runs
  `PRAGMA foreign_keys = ON` on every connection, because the cascades above are load-bearing.
- **No destructive migration, ever.** `fallbackToDestructiveMigration` is banned. A missing
  migration must fail loudly in development, not wipe someone's years of history.
- **No derived values stored as authoritative fields.** No `streak` column, no `consistency`
  column. See `docs/INSIGHTS.md` and `CLAUDE.md` #12.
- **Every read is range-bounded or id-bounded.** There is deliberately no `SELECT * FROM tasks`.

## Two timestamps, not one

`tasks.completedAt` is nullable and `tasks.updatedAt` is NOT NULL, and they are never the same
parameter. `setCompleted` briefly shared one between them, so unticking passed null to both and
the write died on the constraint, crashing the app on the second tap of any checkbox. Guarded by
`DatabaseSmokeTest`.

Queries also do not decide policy. `observeBacklog` used to filter `completed = 0`, which meant
ticking an undated task erased it from the UI no matter what the user had chosen under "completed
tasks". Whether to show finished work is a preference the ViewModel applies; the query's job is
to return the rows.

## Migrations so far

**v1 → v2**, added the `notes` table.

**v2 → v3**, added the `breathing_sessions` table.

Written by hand, and it must match the schema Room expects exactly (column order, types, NOT NULL,
DEFAULT) or Room's identity check fails at open time *on an upgraded install while passing on a
fresh one*. That asymmetry is precisely why migration tests exist.

## Migration procedure

1. Change entities.
2. Bump `version` in `CadenceDatabase`.
3. Write a `Migration(n, n+1)` and add it to `CadenceDatabase.MIGRATIONS`.
4. Build once to export the new `app/schemas/<n+1>.json`; commit it.
5. Add a case to `MigrationTest` that opens `n`, inserts representative rows, migrates, and
   asserts the data survived.
