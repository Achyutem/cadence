# Insights

> Status: **designed in Phase 0, implemented in Phase 9.**

A local behavioural analytics engine that derives productivity patterns from the user's own
history — **without sending anything anywhere**. This is the genuine differentiator: the same
class of feature that normally requires a cloud pipeline, done entirely on-device.

## The hard part is not the statistics

It is **not overclaiming**. With a few weeks of personal data, almost any pattern you look for
will appear. An insights engine that says "you are more productive on Tuesdays" off eleven
Tuesdays is not a feature, it is a random number generator with a confident voice.

So the rules come first:

1. **Minimum sample sizes.** No observation is surfaced below a fixed threshold of relevant days
   (and, for comparisons, of days in *each* group). No exceptions for interesting-looking results.
2. **Observation, correlation and uncertainty are worded differently.** "You completed 8 of 10
   evening tasks this month" is an observation. "Your completion rate tends to be higher on days
   you exercise" is a correlation, and says *tends*.
3. **Never claim causation.** Not in the copy, not by implication, not with an arrow icon.
4. **Volatile findings stay hidden.** If an effect would flip with one more day of data, it is
   not shown.
5. **Every insight is traceable.** The user can see what it was computed from. An unexplainable
   number is worse than no number.
6. **No insight is a judgement.** Never "you are slipping".

## Candidate insights

- Completion rate by weekday (needs several weeks per weekday).
- Time-of-day completion patterns.
- Correlation between a habit and same-day task completion.
- Relationship between number of tasks scheduled and proportion completed — likely the most
  genuinely useful one, and the easiest to word responsibly.
- Habits trending away from their usual consistency.
- Check-in mood/energy against completion, when enough check-ins exist.

## Implementation

Deterministic, pure Kotlin in `domain/insights`. Each insight is a function from a bounded slice
of history to `Insight?` — returning null when the data does not support it, which makes "not
enough data" the default rather than an afterthought.

Computed on demand over a bounded range and cached in memory for the session. Nothing is written
back to the database: insights are derived data (`CLAUDE.md` #12).

Every insight function is unit-tested with, at minimum: a below-threshold dataset that must
return null, a clear-signal dataset that must return the observation, and a noise dataset that
must not.
