# Cadence — engineering and product principles

These are non-negotiable. If a change conflicts with something here, the change is wrong, or this
document needs an explicit argued update — not a quiet exception.

## Product

1. **Cadence is a personal daily operating system, not a habit tracker.** Every feature must help
   answer one of: what do I need to do today, what am I maintaining, what is next, how am I doing
   over time, what patterns exist in my behaviour.
2. **Today is the most important screen.** It gets the most design attention and the fewest taps.
3. **No gamification.** No XP, levels, badges, coins, points, streak rewards, confetti or
   achievements. Streaks are *information*, never a prize and never a threat.
4. **An empty day is not a failed day.** Nothing in the UI may shame the user for a gap. Zero
   states are quiet, not cheerful and not disappointed.
5. **Optimise for quality of interaction, not feature count.** The bar is "I would use this every
   day", not "it technically works".

## Privacy and locality

6. **100% local-first, permanently.** No accounts, no login, no backend, no cloud database, no
   sync, no telemetry, no analytics SDK, no ads, no crash reporting that leaves the device.
7. **The app declares no `INTERNET` permission.** This is the load-bearing guarantee behind
   everything above. Adding it requires a deliberate, documented decision.
8. **The database is the source of truth.** Widgets, notifications and statistics all read from
   local data; none of them require a network.

## Architecture

9. **No overengineering.** No repositories wrapping repositories, no interface-per-class, no
   generic frameworks, no plugin systems, no DI container for trivial objects. One experienced
   developer must be able to read the whole app.
10. **Feature-oriented packages, single Gradle module** until a second module earns its place.
11. **Immutable UI state.** ViewModel + `StateFlow` + a single state object per screen.
12. **Derived data is computed, never stored as an authoritative field.** Streaks, completion
    rates, averages, heatmap intensity and consistency are functions of the source rows. Caching
    them in the database creates two truths that will disagree.
13. **One recurrence engine.** Tasks and habits share it. Duplicating recurrence logic is a bug.
14. **Recurring items are templates, not materialised rows.** Occurrences are generated on demand
    for the visible range; only user-touched dates get a row.

## Time

15. **Nothing calls `LocalDate.now()` directly.** Everything goes through `CadenceClock`, so date
    logic is testable across midnight, DST, leap days and time-zone changes.
16. **Local calendar concepts stay local.** A habit on "18 September" is a `LocalDate`. Only
    genuine points in time are `Instant`, and only those are stored as UTC epoch millis.
17. **Storage formats are a contract.** Dates are ISO text, times are ISO text, instants are epoch
    millis. Changing an encoding requires a migration, not a converter edit.

## Data safety

18. **Migrations are never destructive.** `fallbackToDestructiveMigration` is banned. There is no
    cloud copy of the user's years of history.
19. **Schemas are exported and committed** so migrations can be tested without a device.

## Quality

20. **"It compiles" is not done.** A feature is done when the UI works, state survives restart,
    persistence works, edge cases are handled, empty/loading/error states exist, dark mode and
    every accent work, accessibility is reasonable, tests exist, and performance is acceptable.
21. **Never claim something works without building and running it.** Do not silently skip tests.
22. **Queries are range-bounded.** Never load the whole history to render one screen.

## Notes and data

27. **A note's Markdown text is the source of truth.** The renderer is a view of it. Tapping a
    rendered checkbox rewrites the source line, never a parsed model.
28. **No save buttons on documents.** Notes autosave and flush on exit. Losing a note because
    someone pressed back is not an acceptable failure.
29. **Import replaces, atomically, and says so.** It is the only destructive action in the app and
    it runs in one transaction.
30. **The backup format is independent of the Room schema.** It is a contract with the user's
    future self; it changes only deliberately, and it is versioned.

## Design

23. **Accent colours the active elements only** — navigation, progress, completion, selection,
    heatmap, focus. Never recolour every surface.
24. **Restraint over decoration.** No excessive gradients, shadows, giant cards or illustrations.
    Whitespace is deliberate; density is high where productivity needs it.
25. **Animation communicates state, it does not perform.** Micro-interactions ~140–180ms,
    transitions ~200–300ms, expressive changes ~320–420ms. Everything scales through
    `LocalMotionScale` so reduced-motion works everywhere for free.
26. **Dependencies are a cost.** Prefer platform and Jetpack APIs. A charting library to draw one
    heatmap, or a CommonMark implementation to render six kinds of syntax, is not a trade worth
    making. The only bundled asset is Geist Variable.
26b. **Lint runs with `warningsAsErrors`.** A check is disabled only when it is wrong for this
    project, never because a finding is inconvenient, and every suppression carries a reason.

## Explicitly out of scope

Authentication, cloud sync, social features, collaboration, teams, gamification, ads, analytics,
tracking, project management, enterprise features, a web app, an iOS version, calendar
integrations, external integrations, and an LLM dependency in V1.
