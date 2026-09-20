# Cadence

A local-first personal productivity app for Android — tasks, habits, calendar, daily planning,
reminders, check-ins, heatmaps and home-screen widgets, with **no account, no backend and no
network access**.

> **Status: Phase 0 (foundation) complete.** Builds, runs and is fully themed; task and habit
> features land in Phases 1–3. See [`docs/ROADMAP.md`](docs/ROADMAP.md).

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

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Navigation Compose · WorkManager ·
Glance (Phase 8). Gradle Kotlin DSL with a version catalog. AGP 8.13.2, Kotlin 2.2.21,
compileSdk 36, minSdk 34.

No third-party libraries beyond Jetpack and kotlinx. That is a deliberate constraint, not an
oversight.

## Documentation

| | |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Non-negotiable engineering and product principles |
| [`docs/PRODUCT.md`](docs/PRODUCT.md) | What the app is and is not |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Module shape, state, DI, testing strategy |
| [`docs/DATABASE.md`](docs/DATABASE.md) | Schema, storage contract, migration procedure |
| [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md) | Colour, type, spacing, motion, the dock |
| [`docs/RECURRENCE.md`](docs/RECURRENCE.md) | The shared recurrence engine |
| [`docs/NOTIFICATIONS.md`](docs/NOTIFICATIONS.md) | Reminder scheduling and its edge cases |
| [`docs/WIDGETS.md`](docs/WIDGETS.md) | Widget architecture |
| [`docs/INSIGHTS.md`](docs/INSIGHTS.md) | Local analytics, and the rules that keep it honest |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Phased plan and definition of done per phase |

## Privacy

Cadence declares no `INTERNET` permission. All data stays in a local SQLite database and a local
DataStore file. There is no telemetry, no analytics and no crash reporting.
