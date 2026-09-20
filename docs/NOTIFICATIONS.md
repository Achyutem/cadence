# Notifications

> Status: **designed in Phase 0, implemented in Phase 5.** The `reminders` table exists; no
> permissions are declared yet, deliberately — they are added with the feature that needs them.

## Model

A reminder is a **time of day plus an offset**, never a stored absolute instant.

```
ReminderEntity(timeOfDay, leadMinutes, enabled)
```

- An item with a start time fires at `startTime − leadMinutes`.
- An item without one (all-day task, habit) fires at `timeOfDay`.

The absolute alarm time is computed **per occurrence, at scheduling time, in the current zone**.
That is the whole reason reminders survive travel and DST: a stored `Instant` for "09:00 daily"
would arrive at 08:00 after a clock change, whereas a stored `LocalTime` resolves correctly every
time it is scheduled.

## Scheduling strategy

**Only a bounded horizon is scheduled** — roughly the next 7 days of occurrences — and the window
is rolled forward by a daily `WorkManager` job. Scheduling a year of alarms for a daily habit
would exhaust the alarm quota and would have to be torn down on every edit.

| Need | API |
|---|---|
| Time-critical reminder at an exact minute | `AlarmManager.setExactAndAllowWhileIdle` |
| Rolling the scheduling window forward | `WorkManager` daily periodic work |
| Re-scheduling after reboot | `BOOT_COMPLETED` receiver → enqueue the rolling job |
| Time-zone / time change | `TIMEZONE_CHANGED`, `TIME_SET` receivers → full reschedule |

**No background polling**, ever. Nothing wakes up to ask "is it time yet".

## Permissions (added in Phase 5, not before)

- `POST_NOTIFICATIONS` — runtime, requested in context the first time the user enables a
  reminder, never on first launch.
- `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` — Cadence is a calendar-and-reminder app, which is a
  permitted use of exact alarms. `AlarmManager.canScheduleExactAlarms()` is checked before every
  exact schedule, with a graceful fall back to an inexact alarm plus a visible explanation rather
  than silent failure.
- `RECEIVE_BOOT_COMPLETED` — to restore the schedule after reboot.

Still **no `INTERNET` permission.** Everything here is local.

### The WorkManager permission trap

WorkManager's manifest merges four permissions into the app whether or not they are used:
`ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE` and `RECEIVE_BOOT_COMPLETED`. The first
is network-related, and on an app that advertises having no network access it is exactly the kind
of thing a careful user checks.

This is why WorkManager is **not** a dependency in Phase 0 despite being on the roadmap — it was
added, observed in the built APK's permission list, and removed again. When it returns in Phase 5,
strip the one that is never needed:

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"
    tools:node="remove" />
```

and verify the result against the built artifact, not the source manifest:

```bash
aapt2 dump permissions app/build/outputs/apk/release/app-release-unsigned.apk
```

## Channels

| Channel | Importance | For |
|---|---|---|
| Task reminders | HIGH | timed tasks |
| Habit reminders | DEFAULT | habit prompts |
| Daily check-in | LOW | the optional evening nudge |

Separate channels so the user can silence habit nudges without losing task reminders — that
choice belongs to them, in system settings, not to an in-app toggle we invented.

## Actions

`Complete` · `Snooze` · `Open`. Complete and Snooze are handled by a `BroadcastReceiver` that
writes to the database directly, so the common case never opens the app.

## Edge cases to handle in Phase 5

- Device reboot → reschedule from the database.
- Time-zone change and DST transitions → recompute, do not shift stored values.
- Task deleted, edited, completed, or its recurrence changed → cancel and reschedule.
- Occurrence already completed when the alarm fires → suppress the notification.
- Quiet hours → defer to the end of the quiet window rather than dropping.
- Notification permission revoked while reminders exist → surface it in Settings, do not fail
  silently.
- Battery optimisation / restricted app standby bucket → detect and explain.
- A reminder whose time has already passed when scheduled → fire immediately or skip to the next
  occurrence, never schedule in the past.
