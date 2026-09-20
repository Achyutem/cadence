package dev.achyutem.cadence.domain.insights

import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.TaskEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How confident the wording is allowed to be.
 *
 * The distinction is the whole feature. An observation is a count of what happened. A pattern is a
 * difference large enough and repeated enough to be worth mentioning, and is always worded as a
 * tendency. Nothing here is ever a cause.
 */
enum class InsightConfidence { OBSERVATION, PATTERN }

data class Insight(
    val id: String,
    val text: String,
    val confidence: InsightConfidence,
    /** What it was computed from, so no number on screen is unexplainable. */
    val basis: String,
)

/**
 * The local insights engine.
 *
 * ### The hard part is not the statistics, it is not overclaiming
 *
 * With a few weeks of personal data almost any pattern you look for will appear. An engine that
 * says "you are more productive on Tuesdays" off eleven Tuesdays is not a feature, it is a random
 * number generator with a confident voice.
 *
 * So the rules come before the maths, and they are enforced in code rather than in the copy:
 *
 *  - **Minimum sample sizes.** Nothing is surfaced below [MIN_DAYS] relevant days, and a
 *    comparison additionally needs [MIN_GROUP_DAYS] in *each* group. There is no override for an
 *    interesting-looking result.
 *  - **Minimum effect size.** A difference smaller than [MIN_EFFECT] is noise at these sample
 *    sizes, and is not reported at all.
 *  - **Tendencies, never causes.** Comparisons are worded "tends to" and pair two things; they
 *    never say one produced the other.
 *  - **Every insight carries its basis.** A number the user cannot trace is worse than no number.
 *
 * Every function returns null when the data does not support it, which makes "not enough data" the
 * default rather than an afterthought.
 *
 * Pure Kotlin, no clock and no database, so all of this is unit-testable.
 */
object InsightsEngine {

    /** Below this many days with any activity, nothing is reported at all. */
    const val MIN_DAYS = 14

    /** Each side of a comparison needs at least this many days. */
    const val MIN_GROUP_DAYS = 5

    /** Differences in completion rate smaller than this are noise. */
    const val MIN_EFFECT = 0.15f

    /**
     * Everything the engine can currently say, best first.
     *
     * [tasks] and [entries] should already be bounded to the analysis window by the caller.
     */
    fun analyse(
        tasks: List<TaskEntity>,
        habitEntries: List<HabitEntryEntity>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): List<Insight> {
        val byDate = tasks
            .filter { it.scheduledDate != null && it.parentTaskId == null && !it.archived }
            .groupBy { it.scheduledDate!! }
            .filterKeys { it in from..to }

        if (byDate.size < MIN_DAYS) return emptyList()

        return listOfNotNull(
            completionByWeekday(byDate),
            loadVersusCompletion(byDate),
            habitAndTaskCompletion(byDate, habitEntries),
            timeOfDayPattern(tasks, zone),
        )
    }

    /**
     * Which weekday has the best and worst completion rate.
     *
     * Needs [MIN_GROUP_DAYS] instances of *both* weekdays, so a month of data cannot produce a
     * claim about a weekday that occurred four times.
     */
    private fun completionByWeekday(byDate: Map<LocalDate, List<TaskEntity>>): Insight? {
        val rates = byDate.entries
            .groupBy { it.key.dayOfWeek }
            .filterValues { it.size >= MIN_GROUP_DAYS }
            .mapValues { (_, days) ->
                val all = days.flatMap { it.value }
                if (all.isEmpty()) null else all.count { it.completed }.toFloat() / all.size
            }
            .filterValues { it != null }
            .mapValues { it.value!! }

        if (rates.size < 2) return null

        val best = rates.maxByOrNull { it.value } ?: return null
        val worst = rates.minByOrNull { it.value } ?: return null
        if (best.key == worst.key) return null
        if (best.value - worst.value < MIN_EFFECT) return null

        return Insight(
            id = "weekday",
            text = "You tend to finish more of what you plan on ${best.key.pretty()} " +
                "than on ${worst.key.pretty()}.",
            confidence = InsightConfidence.PATTERN,
            basis = "${(best.value * 100).roundToInt()}% against ${(worst.value * 100).roundToInt()}%, " +
                "over ${rates.size} weekdays with enough data.",
        )
    }

    /**
     * Whether completion falls on heavily loaded days.
     *
     * Compares the **busiest third of days against the quietest third**, not a split at the
     * median. A median split looks reasonable and quietly fails on exactly the distribution this
     * is meant to detect: someone who alternates light days and heavy days has a median equal to
     * their heavy count, so "above the median" is empty and the insight never fires.
     *
     * Comparing extremes is also the better test. It contrasts genuinely different days rather
     * than splitting a continuum at an arbitrary point, and it costs nothing here because the
     * sample is already small enough to sort.
     *
     * If the two thirds have the same task count there is no load difference to talk about, and
     * the function says nothing.
     */
    private fun loadVersusCompletion(byDate: Map<LocalDate, List<TaskEntity>>): Insight? {
        val days = byDate.values.filter { it.isNotEmpty() }.sortedBy { it.size }
        if (days.size < MIN_DAYS) return null

        val third = days.size / 3
        if (third < MIN_GROUP_DAYS) return null

        val light = days.take(third)
        val heavy = days.takeLast(third)

        val lightMax = light.maxOf { it.size }
        val heavyMin = heavy.minOf { it.size }
        // No contrast in load means nothing to say about load.
        if (heavyMin <= lightMax) return null

        fun rate(group: List<List<TaskEntity>>): Float {
            val all = group.flatten()
            return if (all.isEmpty()) 0f else all.count { it.completed }.toFloat() / all.size
        }

        val heavyRate = rate(heavy)
        val lightRate = rate(light)
        if (abs(heavyRate - lightRate) < MIN_EFFECT) return null

        val typicalHeavy = heavy.map { it.size }.sorted()[heavy.size / 2]

        return if (lightRate > heavyRate) {
            Insight(
                id = "load",
                text = "On your busiest days, around $typicalHeavy things planned, you tend to " +
                    "finish a smaller share of them.",
                confidence = InsightConfidence.PATTERN,
                basis = "${(heavyRate * 100).roundToInt()}% on your busiest ${heavy.size} days " +
                    "against ${(lightRate * 100).roundToInt()}% on your quietest ${light.size}.",
            )
        } else {
            Insight(
                id = "load",
                text = "Your busiest days tend to be the ones you finish more of.",
                confidence = InsightConfidence.PATTERN,
                basis = "${(heavyRate * 100).roundToInt()}% on your busiest ${heavy.size} days " +
                    "against ${(lightRate * 100).roundToInt()}% on your quietest ${light.size}.",
            )
        }
    }

    /**
     * Whether days with a habit completed also have higher task completion.
     *
     * Worded as two things going together. This is the insight most likely to be read as
     * causation, so it is the one whose phrasing matters most.
     */
    private fun habitAndTaskCompletion(
        byDate: Map<LocalDate, List<TaskEntity>>,
        habitEntries: List<HabitEntryEntity>,
    ): Insight? {
        val habitDays = habitEntries.filter { it.completed }.map { it.date }.toSet()
        if (habitDays.size < MIN_GROUP_DAYS) return null

        val withHabit = byDate.filterKeys { it in habitDays }.values.flatten()
        val withoutHabit = byDate.filterKeys { it !in habitDays }.values.flatten()
        if (withHabit.size < MIN_GROUP_DAYS || withoutHabit.size < MIN_GROUP_DAYS) return null

        val withRate = withHabit.count { it.completed }.toFloat() / withHabit.size
        val withoutRate = withoutHabit.count { it.completed }.toFloat() / withoutHabit.size
        if (withRate - withoutRate < MIN_EFFECT) return null

        return Insight(
            id = "habit-task",
            text = "Days you keep a habit up tend to also be days you finish more tasks.",
            confidence = InsightConfidence.PATTERN,
            basis = "${(withRate * 100).roundToInt()}% against " +
                "${(withoutRate * 100).roundToInt()}%. This is a pattern in the two together, " +
                "not evidence that one causes the other.",
        )
    }

    /**
     * When completions actually happen.
     *
     * A plain observation rather than a pattern: it counts what the timestamps say and makes no
     * comparison, so it needs no effect size.
     */
    private fun timeOfDayPattern(tasks: List<TaskEntity>, zone: ZoneId): Insight? {
        val completions = tasks.mapNotNull { it.completedAt }
        if (completions.size < MIN_DAYS) return null

        val buckets = completions
            .map { it.atZone(zone).hour }
            .groupingBy { hour ->
                when (hour) {
                    in 5..11 -> "the morning"
                    in 12..17 -> "the afternoon"
                    in 18..22 -> "the evening"
                    else -> "late at night"
                }
            }
            .eachCount()

        val top = buckets.maxByOrNull { it.value } ?: return null
        val share = top.value.toFloat() / completions.size
        // Below half, "most" would be untrue.
        if (share < 0.5f) return null

        return Insight(
            id = "time-of-day",
            text = "Most of what you tick off happens in ${top.key}.",
            confidence = InsightConfidence.OBSERVATION,
            basis = "${top.value} of ${completions.size} completions.",
        )
    }

    private fun DayOfWeek.pretty(): String =
        getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + "s"
}
