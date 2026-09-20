# Architecture

## Shape

Cadence is **one Gradle module** (`:app`) with **feature-oriented packages**.

Multi-module Android projects buy parallel compilation and enforced boundaries. Cadence is a
single-developer app with roughly a dozen screens; it would pay the module tax — duplicated build
scripts, `api`/`implementation` bookkeeping, cross-module navigation plumbing — for boundaries that
package structure already expresses. If build times become a real problem, the seams below are
where modules would be cut, and nothing in the code would need to move.

```
dev.achyutem.cadence
├── core
│   ├── common          AppContainer, ViewModel factory helpers
│   ├── database        Room: entities, DAOs, converters, migrations
│   ├── datastore       Preferences (UserPreferences, SettingsRepository)
│   ├── designsystem    tokens · theme · components
│   ├── notifications   (Phase 5) scheduling, channels, receivers
│   └── time            CadenceClock, date/time extensions
├── domain
│   ├── recurrence      (Phase 2) the one recurrence engine
│   ├── task            (Phase 1) task models + derived state
│   ├── habit           (Phase 3) habit models, entry logic
│   ├── statistics      (Phase 6) streaks, completion rates, heatmap intensity
│   └── insights        (Phase 9) local behavioural analysis
├── feature
│   ├── today           todos · habits · calendar · settings
│   └── …               one package per screen: Screen + ViewModel + UiState
├── widget              (Phase 8) Glance widgets, one package each
└── navigation          routes, NavHost, app shell
```

**Dependency direction:** `feature` → `domain` → `core`. `core` knows nothing about features;
`domain` is pure Kotlin where possible, so it is unit-testable without Android.

## State

One `ViewModel` per screen, exposing one immutable `UiState` via `StateFlow`. Screens are
`@Composable` functions that take state and callbacks — the stateless inner composable
(`TodayContent`) is what `@Preview` renders, so every screen is previewable in both schemes and
every accent without a running app.

```
Room Flow ──► domain (pure functions) ──► ViewModel StateFlow ──► Composable
     ▲                                                                │
     └──────────────── suspend DAO calls ◄───────────────────────────┘
```

Reads are Flows from Room, so a write anywhere re-renders everything affected with no manual
invalidation. Writes are suspending DAO calls from `viewModelScope`.

## Dependency injection

`AppContainer`, constructed in `CadenceApplication`, holding the database, the settings repository
and the clock. ViewModels declare a one-line factory:

```kotlin
companion object {
    val Factory = cadenceViewModelFactory { TodayViewModel(it.clock) }
}
```

Hilt would add a compiler plugin and a second annotation processor to solve a problem this app
does not have. What actually matters for testing — that no class constructs its own dependencies —
is already true: tests pass fakes to constructors directly and never touch the container.

The database and DataStore inside the container are `by lazy`, so application startup performs no
disk I/O before the first frame.

## Threading

Room generates suspending DAOs and Flows that run on its own executor. There are no manual
dispatchers in feature code. Pure domain calculations run on the caller's dispatcher; anything
that grows expensive (multi-year statistics) moves behind `withContext(Dispatchers.Default)` at
the point it is measured to be a problem — not before.

## Why these boundaries and not more

The brief is explicit about avoiding abstraction for its own sake, so:

- **No repository layer over Room.** DAOs already are the data access layer. A repository that
  forwards `taskDao.observeScheduledOn(date)` adds a file and removes nothing.
- **No use-case classes.** Domain logic lives in named pure functions, which are easier to test
  and read than a class with one `invoke`.
- **No interface for every class.** Interfaces appear where there is a real second
  implementation — `CadenceClock` has two (system and mutable-for-tests), which is why it is one.

## Testing strategy

| Layer | Tested by | Where |
|---|---|---|
| Time/date arithmetic, recurrence, statistics | JVM unit tests, no Android | `src/test` |
| Storage encoding | JVM unit tests against the converters | `src/test` |
| Schema, cascades, indices, range queries | Instrumented, in-memory Room | `src/androidTest` |
| Migrations | Instrumented, against exported schemas | `src/androidTest` |
| Critical UI flows | Compose UI tests | `src/androidTest` |

Recurrence and date maths are the parts most likely to be subtly wrong and least likely to fail
loudly, so they carry the heaviest test weight.
