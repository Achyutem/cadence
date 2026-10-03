# Cadence

A local-first personal productivity app for Android, tasks, habits, calendar, daily planning,
reminders, heatmaps and home-screen widgets, with **no account, no backend and no
network access**.

> **Status: feature complete for daily use.** See [`docs/ROADMAP.md`](docs/ROADMAP.md) for what
> is deliberately still open.

**Working today:** Today · tasks with subtasks, priorities, scheduling and full editing · habits in
four metric types with streaks, consistency and a contribution heatmap · calendar in month, week
and day views · reminders with notification actions that work without opening the app · Markdown
notes · breath training (box, static apnea, CO₂ and O₂ tables) · local insights
engine · seven interactive home-screen widgets · one shared recurrence engine · light/dark × ten
accents × Material You · JSON export and import.

## Build

Requires JDK 17+ and an Android SDK with platform 36.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:connectedDebugAndroidTest
```

`local.properties` must point at your SDK (`sdk.dir=...`). If your default `java` is a JRE,
set `JAVA_HOME` to a JDK first.

## Stack

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Navigation Compose · Glance ·
kotlinx serialization. Gradle Kotlin DSL with a version catalog. AGP 8.13.2, Kotlin 2.2.21, compileSdk 36,
minSdk 31 (Android 12).

No third-party libraries beyond Jetpack and kotlinx. That is a deliberate constraint, not an
oversight; the Markdown parser, the heatmap and the charts are all hand-written rather than
pulling in a library each.

The one bundled asset is **Geist Variable** (SIL OFL, 166 KB, licence in `licenses/`).

Lint runs with `warningsAsErrors`; every suppression in `app/lint.xml` carries a written reason.

## Documentation

| | |
|---|---|
| [`docs/BUILDING.md`](docs/BUILDING.md) | Building from a clean machine, and signing your own APK |
| [`docs/PRODUCT.md`](docs/PRODUCT.md) | What the app is and is not |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Module shape, state, DI, testing strategy |
| [`docs/DATABASE.md`](docs/DATABASE.md) | Schema, storage contract, migration procedure |
| [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md) | Colour, type, spacing, motion, the dock |
| [`docs/NOTES.md`](docs/NOTES.md) | Markdown notes, and why the text stays authoritative |
| [`docs/BREATHING.md`](docs/BREATHING.md) | The four exercises, and the safety decisions behind them |
| [`docs/BACKUP.md`](docs/BACKUP.md) | Export/import format and the replace policy |
| [`docs/RECURRENCE.md`](docs/RECURRENCE.md) | The shared recurrence engine |
| [`docs/REMINDERS.md`](docs/REMINDERS.md) | Reminder scheduling, and why a reminder is a local time |
| [`docs/WIDGETS.md`](docs/WIDGETS.md) | Widget architecture |
| [`docs/INSIGHTS.md`](docs/INSIGHTS.md) | Local analytics, and the rules that keep it honest |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Phased plan and definition of done per phase |
| [`docs/FDROID.md`](docs/FDROID.md) | Publishing on F-Droid, and what a release consists of |

## Privacy

Cadence declares no `INTERNET` permission. All data stays in a local SQLite database and a local
DataStore file. There is no telemetry, no analytics and no crash reporting.

## Licence

[GPL-3.0-or-later](LICENSE). Copyright (C) 2026 Achyutem.

Fork it, change it, ship it. The one thing the licence does not allow is taking Cadence closed,
adding the tracking it was built to avoid, and distributing that. For an app whose entire promise
is the absence of a network permission, a licence that lets someone quietly add one would make
the promise worthless.

Geist Variable is bundled under the SIL Open Font License; the text is in [`licenses/`](licenses/).
