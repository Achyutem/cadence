# Product

## What Cadence is

A **personal daily operating system**, not another habit tracker.

It answers five questions, and every feature has to serve one of them:

1. What do I need to do today?
2. What habits am I maintaining?
3. What is scheduled next?
4. How am I doing over time?
5. What patterns exist in my behaviour?

## What it feels like

Fast, lightweight, offline, private, calm. Dense where that helps productivity, minimal
everywhere else. Pleasant to open six times a day.

## Screens

**Today**, the most important screen and the start destination. Date, greeting, progress, what
is happening soon, unfinished scheduled tasks, and habits. Priority order: things happening soon
→ incomplete scheduled tasks → important tasks → habits → remaining tasks.

**Todos**, the full task list: create, edit, complete, subtask, prioritise, schedule, reorder.
Creation is one tap and one line of text.

**Habits**, the habit list with today's state, and a detail screen carrying the heatmap, streaks
and statistics.

**Settings**, deliberately short. Appearance, notifications, behaviour, data.

**Calendar**, month for overview, week for planning, day for a timeline. Reached from Today
rather than occupying a fifth dock slot.

## Tasks

Title, notes, date, start time, optional duration, priority, completion, completion timestamp,
reminders, recurrence, subtasks, tags, ordering, parent, archive.

A task with no time is still a first-class task. A task with a time appears on the timeline.

## Habits

Four metric types under one model: **boolean** (meditate), **count** (75/50 push-ups),
**quantity** (6/8 glasses), **duration** (42/30 min). Plus a goal direction, so "at most two
coffees" works as naturally as "at least eight glasses".

Habit history is stored per day, so streaks, rates, averages and heatmaps are all reconstructions
of what actually happened, and correcting a past day corrects everything downstream.

## Daily check-in, removed

An evening mood and energy prompt shipped briefly and was taken out again. A question the app asks
you every day is an obligation however gently it is worded, and "how are you feeling?" on a screen
whose job is to reduce daily overhead was the wrong trade. See `docs/ROADMAP.md`.

## Deliberate non-goals

No gamification of any kind. No XP, levels, badges, coins, points, confetti or achievements.
Streaks are information.

No accounts, sync, social features, collaboration, ads, analytics, or external integrations. No
LLM dependency in V1, though the data model is shaped so a deterministic parser or an optional
later intelligence layer could read it without a migration.

## Quality bar

Not "it technically works" but **"I would use this every day."**

Every screen must answer: what is the user trying to do here, what is the primary action, can
they do it with minimal friction, is the hierarchy obvious, and does it feel good to touch.
