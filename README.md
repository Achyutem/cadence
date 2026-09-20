# Cadence

A local-first personal productivity app for Android — tasks, habits, calendar, daily planning,
reminders, check-ins, heatmaps and home-screen widgets, with **no account, no backend and no
network access**.

> **Status: tasks, habits, notes, breathing and six interactive widgets are working.** Calendar,
> reminders and insights are next. See [`docs/ROADMAP.md`](docs/ROADMAP.md).

**Working today:** Today · task capture and completion with subtasks · habits in four metric types
with streaks, consistency and a contribution heatmap · Markdown notes · breath training (box,
static apnea, CO₂ and O₂ tables) · six home-screen widgets you can tick and step without opening
the app · one shared recurrence engine · light/dark × five accents × Material You · JSON export
and import.

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
minSdk 34.

No third-party libraries beyond Jetpack and kotlinx. That is a deliberate constraint, not an
oversight — the Markdown parser, the heatmap and the charts are all hand-written rather than
pulling in a library each.

The one bundled asset is **Geist Variable** (SIL OFL, 166 KB, licence in `licenses/`).

Lint runs with `warningsAsErrors`; every suppression in `app/lint.xml` carries a written reason.

## Documentation

| | |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Non-negotiable engineering and product principles |
| [`docs/PRODUCT.md`](docs/PRODUCT.md) | What the app is and is not |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Module shape, state, DI, testing strategy |
| [`docs/DATABASE.md`](docs/DATABASE.md) | Schema, storage contract, migration procedure |
| [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md) | Colour, type, spacing, motion, the dock |
| [`docs/NOTES.md`](docs/NOTES.md) | Markdown notes, and why the text stays authoritative |
| [`docs/BREATHING.md`](docs/BREATHING.md) | The four exercises, and the safety decisions behind them |
| [`docs/BACKUP.md`](docs/BACKUP.md) | Export/import format and the replace policy |
| [`docs/RECURRENCE.md`](docs/RECURRENCE.md) | The shared recurrence engine |
| [`docs/NOTIFICATIONS.md`](docs/NOTIFICATIONS.md) | Reminder scheduling and its edge cases |
| [`docs/WIDGETS.md`](docs/WIDGETS.md) | Widget architecture |
| [`docs/INSIGHTS.md`](docs/INSIGHTS.md) | Local analytics, and the rules that keep it honest |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Phased plan and definition of done per phase |

## Privacy

Cadence declares no `INTERNET` permission. All data stays in a local SQLite database and a local
DataStore file. There is no telemetry, no analytics and no crash reporting.
