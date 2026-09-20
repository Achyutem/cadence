# Recurrence

> Status: **designed in Phase 0, implemented in Phase 2.** The schema and vocabulary below are
> live; `domain/recurrence` is the next thing built.

## One engine

Tasks and habits share a single recurrence implementation. Duplicating this logic is treated as a
bug — it is the most subtly-wrong-able code in the app, and two copies means two sets of edge-case
bugs that drift apart.

The engine is pure Kotlin with no Android dependency, so every case below is a fast JVM unit test.

## The rule

A `RecurrenceRule` describes **which dates** something lands on. Nothing else. No completion, no
task identity, no habit identity — which is exactly what lets one engine serve both.

| Field | Meaning |
|---|---|
| `frequency` | `DAILY · WEEKLY · MONTHLY_BY_DAY · MONTHLY_BY_WEEKDAY · YEARLY` |
| `interval` | ≥1. "Every 2 weeks" is `WEEKLY` + `interval = 2` |
| `daysOfWeek` | `WEEKLY` only |
| `dayOfMonth` | `MONTHLY_BY_DAY`, `YEARLY` |
| `weekOfMonth` | `MONTHLY_BY_WEEKDAY`: 1..4, or **-1 for last** |
| `weekdayOfMonth` | `MONTHLY_BY_WEEKDAY`: which weekday is being counted |
| `monthOfYear` | `YEARLY` |
| `anchorDate` | phase reference for interval arithmetic |
| `endDate` / `occurrenceLimit` | optional termination, mutually exclusive |

### Covering the brief's cases

| Wanted | Rule |
|---|---|
| Every day | `DAILY`, interval 1 |
| Every 2 days | `DAILY`, interval 2 |
| Weekdays | `WEEKLY`, interval 1, days `{MON..FRI}` |
| Mon / Wed / Fri | `WEEKLY`, interval 1, days `{MON, WED, FRI}` |
| Every week on Monday | `WEEKLY`, interval 1, days `{MON}` |
| Every 2 weeks | `WEEKLY`, interval 2, days from the anchor |
| 1st of every month | `MONTHLY_BY_DAY`, dayOfMonth 1 |
| Last Friday of every month | `MONTHLY_BY_WEEKDAY`, weekOfMonth -1, weekday FRI |
| Every year on 4 May | `YEARLY`, monthOfYear 5, dayOfMonth 4 |

## Why `anchorDate` is immutable

"Every 2 days" needs a phase — counted from *when*? `anchorDate` is that reference. It is never
mutated: editing a recurring item's start creates a **new rule**, so occurrences already recorded
against the old phase stay reconstructible. Mutating it would silently rewrite history.

## Occurrences are not rows

A recurring item is **one template row**. Future occurrences are generated on demand for the
visible date range; only dates the user actually touched get a row (`task_occurrences`,
`habit_entries`).

A daily task running for five years is one row, not 1,826. The calendar asks for a week and gets
a week.

## Edge cases the engine must handle

**Short months.** `dayOfMonth = 31` on a 30-day month clamps to the last day. This is a product
decision, not a technical one: "the 31st of every month" should not silently skip February.

**Last-weekday months.** "Last Friday" is the 4th Friday in some months and the 5th in others.
`isLastWeekdayOfMonth()` handles this by checking whether +7 days leaves the month, rather than by
counting — already tested in `DateTimeExtensionsTest`.

**Leap days.** `YEARLY` on 29 February falls back to 28 February in common years.

**Skips vs misses.** A skipped occurrence is excused and excluded from completion rate. A missed
one counts. These are different fields, not one boolean.

**Time-zone and DST.** Occurrence dates are `LocalDate` and never converted to UTC. Reminder
*instants* are derived per occurrence at scheduling time in the current zone — see
`docs/NOTIFICATIONS.md`.

**Termination.** `endDate` is inclusive. `occurrenceLimit` counts generated occurrences, not
completed ones.

## Planned API

```kotlin
fun RecurrenceRule.occursOn(date: LocalDate): Boolean
fun RecurrenceRule.occurrencesBetween(start: LocalDate, end: LocalDate): List<LocalDate>
fun RecurrenceRule.nextOccurrenceAfter(date: LocalDate): LocalDate?
```

Range-based by construction — there is no "all occurrences" call, because for an unbounded rule
that is an infinite list, and for a long one it is a memory problem.

## Test plan (Phase 2)

Every row of the table above, plus: month-end clamping in every month length, last-weekday in
4- and 5-occurrence months, leap years, year boundaries, `interval > 1` phase correctness from
the anchor, `endDate` inclusivity, `occurrenceLimit` counting, and empty/invalid day sets.
