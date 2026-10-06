package dev.achyutem.cadence.core.datastore

import dev.achyutem.cadence.domain.breathing.BreathingPreferences
import java.time.DayOfWeek
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The shipped accents.
 *
 * An enum rather than a stored ARGB value because each accent is a hand-tuned pair of light/dark
 * ramps, not a single hue; see `designsystem/theme/Accent.kt`. A future custom accent would add
 * one `CUSTOM` case carrying a seed, without changing anything that reads this type.
 *
 * [MONO] is the monochrome option: black on a light scheme, white on a dark one. A plain "white"
 * accent was offered briefly and withdrawn, because in a light scheme a white primary on a white
 * surface is an invisible button, and every workaround for that is just [MONO] wearing a hat.
 *
 * Ordered roughly by hue, then the neutrals, because that is the order the picker shows them in.
 */
enum class AccentColor {
    RED,
    ORANGE,
    SEPIA,
    GREEN,
    JADE,
    CYAN,
    BLUE,
    MAGENTA,
    GREY,
    MONO,
    ;

    companion object {
        /**
         * Read a stored or backed-up accent name.
         *
         * Unknown names fall back to [BLUE], but an accent that this version renamed or merged is
         * mapped to its nearest surviving neighbour first. Silently resetting someone's accent to
         * blue because the palette was reworked is a small betrayal, and it is avoidable.
         */
        fun parse(name: String?): AccentColor {
            if (name == null) return BLUE
            entries.firstOrNull { it.name == name }?.let { return it }
            return when (name) {
                "INDIGO", "VIOLET" -> BLUE
                "ROSE" -> RED
                "AMBER" -> ORANGE
                "EMERALD", "TEAL" -> GREEN
                "SLATE" -> GREY
                "SPOTIFY" -> JADE
                "WHITE" -> MONO
                else -> BLUE
            }
        }
    }
}

/**
 * The shape of buttons and the segmented control.
 *
 * Purely a taste setting. Rounded rectangles read as precise and technical; pills read softer and
 * friendlier. Neither is more correct, so it is the user's call rather than a decision baked into
 * the design system.
 */
enum class ButtonShape { ROUNDED, PILL }

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
 * What a reminder sounds like.
 *
 * Three cases rather than a nullable string, because "use whatever the system plays" and "play
 * nothing" are genuinely different answers and a null would have to stand in for one of them.
 *
 * Stored as a string: the empty string is silence, the literal `system` is the default, and
 * anything else is a content URI from the system ringtone picker.
 */
sealed interface NotificationSound {
    data object SystemDefault : NotificationSound
    data object Silent : NotificationSound
    data class Custom(val uri: String) : NotificationSound

    fun store(): String = when (this) {
        SystemDefault -> SYSTEM
        Silent -> ""
        is Custom -> uri
    }

    companion object {
        private const val SYSTEM = "system"

        fun parse(stored: String?): NotificationSound = when {
            stored == null || stored == SYSTEM -> SystemDefault
            stored.isEmpty() -> Silent
            else -> Custom(stored)
        }
    }
}

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
    val buttonShape: ButtonShape = ButtonShape.ROUNDED,
    /** Honours the system "remove animations" setting when true; user can force it on. */
    val reducedMotion: Boolean = false,
    /** Home screen widget background opacity, as a percentage. 0 draws no background at all. */
    val widgetOpacity: Int = 100,

    // Behaviour
    val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    val completedTaskBehavior: CompletedTaskBehavior = CompletedTaskBehavior.MOVE_TO_BOTTOM,
    val defaultTaskDurationMinutes: Int = 30,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,

    // Notifications
    val notificationSound: NotificationSound = NotificationSound.SystemDefault,
    val defaultReminderLeadMinutes: Int = 0,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: LocalTime = LocalTime.of(22, 0),
    val quietHoursEnd: LocalTime = LocalTime.of(7, 0),

    // Today
    val showCompletedOnToday: Boolean = true,

    /** How the user has configured the four breathing exercises, and whether they make sound. */
    val breathing: BreathingPreferences = BreathingPreferences.Default,
) {
    companion object {
        val Default = UserPreferences()

        /** Longest name the greeting can render without wrapping awkwardly. */
        const val MAX_NAME_LENGTH = 24
    }
}
