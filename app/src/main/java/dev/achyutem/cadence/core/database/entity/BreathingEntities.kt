package dev.achyutem.cadence.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

enum class BreathingKind { BOX, STATIC_APNEA, CO2_TABLE, O2_TABLE }

/**
 * A completed (or abandoned) breathing session.
 *
 * [completed] distinguishes finishing the table from stopping early, and both are kept. An
 * abandoned session is real information, for CO2 and O2 tables, the round you stopped at *is*
 * the measurement, and deleting it would leave a training log that only ever shows successes.
 *
 * [longestHoldSeconds] is stored rather than derived because it is the one number that cannot be
 * recomputed from the exercise definition: it depends on where the user actually stopped.
 */
@Entity(
    tableName = "breathing_sessions",
    indices = [Index("date"), Index("kind")],
)
data class BreathingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val kind: BreathingKind,
    /** Seconds actually spent in the session, not the planned length. */
    val durationSeconds: Int,
    val roundsCompleted: Int,
    val roundsPlanned: Int,
    val longestHoldSeconds: Int,
    val completed: Boolean,
    val createdAt: Instant,
)
