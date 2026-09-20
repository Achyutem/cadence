package dev.achyutem.cadence.core.database.converter

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Storage contract for time values. Deliberately explicit — ambiguous timestamp formats are the
 * single most common source of bugs in an app like this.
 *
 * | Kotlin type | SQLite type | Encoding                      | Example        |
 * |-------------|-------------|-------------------------------|----------------|
 * | [LocalDate] | TEXT        | ISO-8601 `yyyy-MM-dd`         | `2026-09-20`   |
 * | [LocalTime] | TEXT        | ISO-8601 `HH:mm[:ss]`         | `19:30`        |
 * | [Instant]   | INTEGER     | epoch milliseconds, UTC       | `1789056000000`|
 * | Set<DayOfWeek> | TEXT     | sorted CSV of ISO values 1..7 | `1,3,5`        |
 *
 * ISO text for dates is chosen over an epoch-day integer on purpose: it sorts and compares
 * lexicographically in SQL, it is human-readable in a database dump, and it makes `BETWEEN`
 * range queries for the calendar and heatmap trivially correct.
 *
 * Only [Instant] is stored as UTC, because only [Instant] is an absolute point in time.
 */
object CadenceTypeConverters {

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun daysOfWeekToString(value: Set<DayOfWeek>?): String? =
        value?.map(DayOfWeek::getValue)?.sorted()?.joinToString(",")

    @TypeConverter
    fun stringToDaysOfWeek(value: String?): Set<DayOfWeek>? = value
        ?.split(',')
        ?.filter(String::isNotBlank)
        ?.map { DayOfWeek.of(it.trim().toInt()) }
        ?.toSet()
}
