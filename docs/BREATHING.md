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

## Every number is the user's

Each exercise declares which of its numbers can be changed, as a list of `BreathField`. A field
carries its own bounds and step, so "a CO₂ rest decrement is 0 to 60 seconds in steps of 5" is
written once and enforced in three places: the editor's buttons, `expand()`'s clamping, and the
import path. One sheet renders whatever fields an exercise declares, which is why box breathing
and an O₂ table share a settings screen and why a fifth exercise would need no new UI.

| Exercise | Fields |
|---|---|
| Box | seconds per side, rounds |
| Static apnea | breathe-up, hold |
| CO₂ table | rounds, hold, first rest, rest decrement |
| O₂ table | rounds, first hold, hold increment, rest |

Edits save as they are made, not behind a Save button: the common case is opening the sheet to
change one number, and the second most common is opening it to check what the numbers are.

Underneath the fields is a strip of every hold the session will ask for, in order. "Three rounds,
one minute, plus ten seconds" is easy to type and hard to picture; 1:00, 1:10, 1:20 is neither,
and it is what stops someone starting an eight-round table that ends on a three-minute hold by
accident.

## A session runs itself

Once started, nothing has to be touched. Every phase of every round is already in the expanded
list and the ticker walks it to the end. Pause, skip and stop exist; none of them is required to
get from the first round to the last.

Two things make that real rather than nominal:

- **The screen is held on for the length of the session.** A table can run twenty minutes without
  a touch, and a phone that locks itself in round three takes the countdown with it. The flag is
  scoped to the session, not to the app.
- **Sound marks every phase change**, so the session works face down and eyes shut.

## Sound

Tones are synthesised, not bundled and not taken from `ToneGenerator`. `ToneGenerator`'s catalogue
is call-progress and DTMF tones: it can make a noise at a phase change, but it cannot make
*breathe in* sound like the opposite of *breathe out*, which is the entire requirement.

| Cue | Sound |
|---|---|
| Breathe in | rising pair, 440 → 660 Hz |
| Breathe out | falling pair, 660 → 440 Hz |
| Hold | one low sustained note, 300 Hz |
| Hold empty | the same, lower, 220 Hz |
| Breathe freely | soft falling pair, quieter |
| Last three seconds | short quiet tick, 880 Hz |
| Finished | three rising notes |

Rising means breathe in, falling means breathe out, low and flat means stop moving air. That is
learnable in one session.

The countdown tick is suppressed on phases shorter than eight seconds: a four-second box inhale
that ticks three times is a metronome, not a countdown, and it drowns out the phase change that
actually matters.

Cues are an enum emitted by the runner, so the whole session including exactly when it beeps is
assertable in a unit test with no audio hardware anywhere near it. The screen collects them and
plays them; muting is an early return rather than a branch inside the runner. Playback goes to the
media stream, so the volume keys adjust it and a silenced phone stays silent, which is the right
call for something people do to calm down.

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
