# Reminders

AlarmManager, a rolling horizon, and no server.

## A reminder is a local time, not an instant

`ReminderEntity` stores a time of day plus an offset. The absolute instant is computed **per
occurrence, at scheduling time, in the current zone**.

This is the decision everything else rests on. A stored `Instant` for "09:00 daily" arrives at
08:00 after a daylight saving change or a flight, and nothing downstream can tell that it is
wrong. Because nothing absolute is persisted, recomputing after a clock change is simply correct.

`ReminderSchedulingTest` pins this: the same reminder keeps its wall-clock time across a DST
boundary with genuinely different UTC offsets either side, follows the device zone when the zone
changes, and resolves forward out of a DST gap where the local time does not exist.

## A rolling horizon, rebuilt rather than reconciled

Only **seven days** of alarms are ever pending. A daily habit has infinitely many occurrences;
scheduling a year of them would exhaust the alarm quota and have to be torn down on every edit.

The window is rolled forward on every app launch, on reboot, on app update, and on any time or
time-zone change.

Rebuilding is a full cancel-and-re-add rather than a diff. Reconciling "what should exist" against
"what does exist" needs a record of what was scheduled, which is a second source of truth that can
drift from the database. A rebuild is a few dozen alarms, runs in milliseconds, and cannot drift.
`ScheduledReminder.requestCode` is **derived** from target, entity and date rather than allocated,
precisely so a rebuild replaces alarms instead of stacking duplicates.

## Exact, with a fallback

Cadence is a calendar-and-reminder app, which is a permitted use of exact alarms. Every schedule
still calls `canScheduleExactAlarms()` first and falls back to `setAndAllowWhileIdle`. A reminder
a few minutes late is worth far more than no reminder and a silent failure, and Settings offers a
way to grant the permission rather than nagging.

## Actions work without opening the app

`Complete` and `Snooze` are handled by a `BroadcastReceiver` that writes through the same DAOs the
app uses, cancels the notification, and refreshes the widgets. Database work runs under
`goAsync()` so the process survives long enough to commit; without it the receiver can return
before the coroutine finishes and the tap silently does nothing.

Verified on device: tapping Complete in the shade set `completed = 1` in the database, dismissed
the notification, and left the launcher in the foreground throughout.

Snooze is a fixed ten minutes. A snooze the user has to think about is a decision, and the whole
point of the button is to postpone without deciding anything.

## Suppression and quiet hours

A reminder is dropped at fire time if the thing already happened: a completed task, a habit
already logged. A reminder for something you finished an hour ago is
worse than no reminder.

A trigger already in the past when the horizon is built is skipped rather than fired immediately.

Quiet hours **defer** to the end of the window rather than dropping. A reminder someone asked for
should still arrive, just not at 3am. Windows that wrap midnight are handled by testing the two
halves separately.

## Three channels

Tasks (high) and habits (default). Separate so the user can silence habit nudges
without losing task reminders, in system settings where that choice belongs, rather than through
an in-app toggle we invented.

## Permissions

`POST_NOTIFICATIONS` is requested in context, the first time a reminder is switched on, never at
launch. A permission prompt before the user has asked for anything is how people learn to deny by
reflex.
