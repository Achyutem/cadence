# Breathing

Four exercises: box breathing, static apnea, and CO₂ / O₂ tolerance tables.

## Safety first, because it is load-bearing

Breath-hold training can cause a blackout **with no warning sensation**, which is precisely why
it kills people in water. The safety notice sits permanently on the exercise list rather than
behind a one-time dismissal, and it is written as instructions rather than as a disclaimer: never
in or near water, never while driving, sitting or lying down, stop at dizziness or tingling, and
do not push maximal holds alone.

Two design decisions follow from the same concern:

- **CO₂ table rest is floored at 15 seconds.** A table whose rest decrements to zero stops being
  a training table and becomes one continuous breath-hold, useless as a stimulus and the most
  dangerous thing this screen could generate. `BreathingExerciseTest` asserts the floor holds even
  for a configuration that would otherwise cross it.
- **Static apnea is one round.** Repeated maximal holds are what the tables are for; stacking them
  freehand is how people get hurt.

There is also a **Skip** control on every phase. That is the honest thing for a hold: people
should come up when they need to, not stare at a countdown they are no longer doing. A skipped
hold is not credited.

## Exercises are pure functions

Each exercise expands to a flat `List<BreathPhase>`. Nothing in the domain knows about time
passing, coroutines or the screen, the runner just walks the list.

That is what makes a table provably correct before anything renders, and it is why adding a fifth
exercise means adding one `expand()` and nothing else.

| | Hold | Rest | Progression |
|---|---|---|---|
| Box | = inhale |, | none; every round identical |
| Static apnea | fixed | breathe-up before | single round |
| CO₂ table | **constant** | **shrinks** | CO₂ accumulates as recovery shortens |
| O₂ table | **grows** | **constant** | less oxygen each round from a fixed recovery |

## The circle is the instruction

You should be able to follow a session **without reading anything**. The circle grows through an
inhale, holds its size through a hold, shrinks through an exhale. Eyes half closed, the size alone
tells you what to do, which is the state most of these exercises are done in.

Its size is driven directly by phase progress rather than by an infinite looping animation, so it
can never drift out of step with the countdown: they are the same number. Holds shift the colour
to the warning tone, because a hold is a different thing from a breath.

The countdown text is excluded from the accessibility tree; a value changing every second makes a
screen reader unusable.

## Timing

A one-second ticker that decrements, deliberately **not** wall-clock-corrected. This is a guided
exercise, not a stopwatch: a session that silently jumped ahead after the screen was off would be
worse than one that drifts by a few hundred milliseconds. Pausing stops the ticker, so a paused
hold does not keep counting down.

## Sessions are logged, including abandoned ones

`breathing_sessions` records duration, rounds completed vs planned, the longest hold, and whether
the session was finished.

An abandoned session is real information, for a CO₂ or O₂ table, **the round you stopped at is
the measurement**, and discarding it would leave a training log that only ever shows successes.
Anything under ten seconds is treated as a mis-tap and not written.
