package dev.achyutem.cadence.core.datastore

import java.time.DayOfWeek
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The five shipped accents. This is an enum rather than a stored ARGB value because each accent
 * is a hand-tuned pair of light/dark ramps, not a single hue; see `designsystem/theme/Accent.kt`.
 * A future custom accent would add one `CUSTOM` case carrying a seed, without changing anything
 * that reads this type.
 */
enum class AccentColor { BLUE, PURPLE, GREEN, ORANGE, PINK }

/** What happens to a task the moment it is ticked off. */
enum class CompletedTaskBehavior {
    /** Stays exactly where it is, struck through. Least jarring; good for reviewing a day. */
    KEEP_IN_PLACE,

    /** Animates down to a "Completed" group at the end of the list. */
    MOVE_TO_BOTTOM,

    /** Disappears from the list entirely. Still in the database, still in statistics. */
    HIDE,
}

enum class TimeFormat { SYSTEM, TWELVE_HOUR, TWENTY_FOUR_HOUR }

/**
 * Every user-facing preference, as one immutable snapshot. The UI observes a single
 * `StateFlow<UserPreferences>` rather than a dozen independent flows, so a theme change and an
 * accent change can never render a half-applied frame.
 */
data class UserPreferences(
    /**
     * What the user would like to be called. Used only for the greeting on Today.
     *
     * Blank is a first-class value, not a missing one: the greeting simply drops the name. This
     * is never required, never prompted for, and never leaves the device.
     */
    val displayName: String = "",

    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.BLUE,
    /** Material You. Off by default: Cadence has its own visual identity. */
    val useDynamicColor: Boolean = false,
    /** Honours the system "remove animations" setting when true; user can force it on. */
    val reducedMotion: Boolean = false,

    // Behaviour
    val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    val completedTaskBehavior: CompletedTaskBehavior = CompletedTaskBehavior.MOVE_TO_BOTTOM,
    val defaultTaskDurationMinutes: Int = 30,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,

    // Notifications
    val defaultReminderLeadMinutes: Int = 0,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: LocalTime = LocalTime.of(22, 0),
    val quietHoursEnd: LocalTime = LocalTime.of(7, 0),

    // Today
    val showCompletedOnToday: Boolean = true,
    val checkInPromptEnabled: Boolean = true,
) {
    companion object {
        val Default = UserPreferences()

        /** Longest name the greeting can render without wrapping awkwardly. */
        const val MAX_NAME_LENGTH = 24
    }
}
