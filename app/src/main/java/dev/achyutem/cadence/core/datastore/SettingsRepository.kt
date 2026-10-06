package dev.achyutem.cadence.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.achyutem.cadence.domain.breathing.BreathingPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Preferences storage.
 *
 * Unknown or corrupt stored values fall back to the default rather than throwing, a preferences
 * file is not worth crashing over, and an enum removed in a later version must not brick the app.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.preferencesDataStore

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { throwable ->
            // Only IO failures are recoverable; anything else is a real bug and should surface.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map(::toUserPreferences)

    suspend fun setDisplayName(name: String) =
        put(Keys.displayName, name.trim().take(UserPreferences.MAX_NAME_LENGTH))

    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.themeMode, mode.name)

    suspend fun setAccentColor(accent: AccentColor) = put(Keys.accentColor, accent.name)

    suspend fun setUseDynamicColor(enabled: Boolean) = put(Keys.useDynamicColor, enabled)

    suspend fun setButtonShape(shape: ButtonShape) = put(Keys.buttonShape, shape.name)

    suspend fun setReducedMotion(enabled: Boolean) = put(Keys.reducedMotion, enabled)

    suspend fun setWidgetOpacity(percent: Int) = put(Keys.widgetOpacity, percent.coerceIn(0, 100))

    suspend fun setWeekStartsOn(day: DayOfWeek) = put(Keys.weekStartsOn, day.value)

    suspend fun setCompletedTaskBehavior(behavior: CompletedTaskBehavior) =
        put(Keys.completedTaskBehavior, behavior.name)

    suspend fun setDefaultTaskDurationMinutes(minutes: Int) =
        put(Keys.defaultTaskDuration, minutes.coerceIn(5, 8 * 60))

    suspend fun setTimeFormat(format: TimeFormat) = put(Keys.timeFormat, format.name)

    suspend fun setDefaultReminderLeadMinutes(minutes: Int) =
        put(Keys.defaultReminderLead, minutes.coerceIn(0, 24 * 60))

    suspend fun setQuietHoursEnabled(enabled: Boolean) = put(Keys.quietHoursEnabled, enabled)

    suspend fun setQuietHours(start: LocalTime, end: LocalTime) {
        dataStore.edit {
            it[Keys.quietHoursStart] = start.toString()
            it[Keys.quietHoursEnd] = end.toString()
        }
    }

    suspend fun setShowCompletedOnToday(show: Boolean) = put(Keys.showCompletedOnToday, show)

    suspend fun setNotificationSound(sound: NotificationSound) =
        put(Keys.notificationSound, sound.store())

    suspend fun setBreathingSoundEnabled(enabled: Boolean) = put(Keys.breathingSound, enabled)

    /**
     * Save one exercise's numbers.
     *
     * Takes the whole [BreathingPreferences] rather than a field at a time so that editing an
     * exercise is a single write, and so the editor never has to know which key belongs to which
     * knob.
     */
    suspend fun setBreathing(breathing: BreathingPreferences) {
        dataStore.edit { it.putBreathing(breathing) }
    }

    /**
     * Replace every preference at once.
     *
     * Used by import. A single `edit` block means a restore is atomic: the app can never come up
     * with half the old settings and half the new ones.
     */
    suspend fun replaceAll(preferences: UserPreferences) {
        dataStore.edit { prefs ->
            prefs[Keys.displayName] = preferences.displayName
            prefs[Keys.themeMode] = preferences.themeMode.name
            prefs[Keys.accentColor] = preferences.accentColor.name
            prefs[Keys.useDynamicColor] = preferences.useDynamicColor
            prefs[Keys.buttonShape] = preferences.buttonShape.name
            prefs[Keys.reducedMotion] = preferences.reducedMotion
            prefs[Keys.widgetOpacity] = preferences.widgetOpacity
            prefs[Keys.weekStartsOn] = preferences.weekStartsOn.value
            prefs[Keys.completedTaskBehavior] = preferences.completedTaskBehavior.name
            prefs[Keys.defaultTaskDuration] = preferences.defaultTaskDurationMinutes
            prefs[Keys.timeFormat] = preferences.timeFormat.name
            prefs[Keys.notificationSound] = preferences.notificationSound.store()
            prefs[Keys.defaultReminderLead] = preferences.defaultReminderLeadMinutes
            prefs[Keys.quietHoursEnabled] = preferences.quietHoursEnabled
            prefs[Keys.quietHoursStart] = preferences.quietHoursStart.toString()
            prefs[Keys.quietHoursEnd] = preferences.quietHoursEnd.toString()
            prefs[Keys.showCompletedOnToday] = preferences.showCompletedOnToday
            prefs.putBreathing(preferences.breathing)
        }
    }

    private fun MutablePreferences.putBreathing(breathing: BreathingPreferences) {
        this[Keys.breathingSound] = breathing.soundEnabled
        this[Keys.boxSeconds] = breathing.boxSeconds
        this[Keys.boxRounds] = breathing.boxRounds
        this[Keys.staticBreatheUp] = breathing.staticBreatheUpSeconds
        this[Keys.staticHold] = breathing.staticHoldSeconds
        this[Keys.staticHoldIncrement] = breathing.staticHoldIncrementSeconds
        this[Keys.staticRounds] = breathing.staticRounds
        this[Keys.co2Hold] = breathing.co2HoldSeconds
        this[Keys.co2StartRest] = breathing.co2StartRestSeconds
        this[Keys.co2RestDecrement] = breathing.co2RestDecrementSeconds
        this[Keys.co2Rounds] = breathing.co2Rounds
        this[Keys.o2Rest] = breathing.o2RestSeconds
        this[Keys.o2StartHold] = breathing.o2StartHoldSeconds
        this[Keys.o2HoldIncrement] = breathing.o2HoldIncrementSeconds
        this[Keys.o2Rounds] = breathing.o2Rounds
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private object Keys {
        val displayName = stringPreferencesKey("display_name")
        val themeMode = stringPreferencesKey("theme_mode")
        val accentColor = stringPreferencesKey("accent_color")
        val useDynamicColor = booleanPreferencesKey("use_dynamic_color")
        val buttonShape = stringPreferencesKey("button_shape")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val widgetOpacity = intPreferencesKey("widget_opacity")
        val weekStartsOn = intPreferencesKey("week_starts_on")
        val completedTaskBehavior = stringPreferencesKey("completed_task_behavior")
        val defaultTaskDuration = intPreferencesKey("default_task_duration")
        val timeFormat = stringPreferencesKey("time_format")
        val notificationSound = stringPreferencesKey("notification_sound")
        val defaultReminderLead = intPreferencesKey("default_reminder_lead")
        val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
        val quietHoursStart = stringPreferencesKey("quiet_hours_start")
        val quietHoursEnd = stringPreferencesKey("quiet_hours_end")
        val showCompletedOnToday = booleanPreferencesKey("show_completed_on_today")
        val breathingSound = booleanPreferencesKey("breathing_sound")
        val boxSeconds = intPreferencesKey("breathing_box_seconds")
        val boxRounds = intPreferencesKey("breathing_box_rounds")
        val staticBreatheUp = intPreferencesKey("breathing_static_breathe_up")
        val staticHold = intPreferencesKey("breathing_static_hold")
        val staticHoldIncrement = intPreferencesKey("breathing_static_hold_increment")
        val staticRounds = intPreferencesKey("breathing_static_rounds")
        val co2Hold = intPreferencesKey("breathing_co2_hold")
        val co2StartRest = intPreferencesKey("breathing_co2_start_rest")
        val co2RestDecrement = intPreferencesKey("breathing_co2_rest_decrement")
        val co2Rounds = intPreferencesKey("breathing_co2_rounds")
        val o2Rest = intPreferencesKey("breathing_o2_rest")
        val o2StartHold = intPreferencesKey("breathing_o2_start_hold")
        val o2HoldIncrement = intPreferencesKey("breathing_o2_hold_increment")
        val o2Rounds = intPreferencesKey("breathing_o2_rounds")
    }

    private fun toUserPreferences(prefs: Preferences): UserPreferences {
        val defaults = UserPreferences.Default
        return UserPreferences(
            displayName = prefs[Keys.displayName] ?: defaults.displayName,
            themeMode = prefs[Keys.themeMode].toEnumOr(defaults.themeMode),
            accentColor = AccentColor.parse(prefs[Keys.accentColor]),
            useDynamicColor = prefs[Keys.useDynamicColor] ?: defaults.useDynamicColor,
            buttonShape = prefs[Keys.buttonShape].toEnumOr(defaults.buttonShape),
            reducedMotion = prefs[Keys.reducedMotion] ?: defaults.reducedMotion,
            widgetOpacity = prefs[Keys.widgetOpacity]?.coerceIn(0, 100) ?: defaults.widgetOpacity,
            weekStartsOn = prefs[Keys.weekStartsOn]
                ?.takeIf { it in 1..7 }
                ?.let(DayOfWeek::of)
                ?: defaults.weekStartsOn,
            completedTaskBehavior = prefs[Keys.completedTaskBehavior]
                .toEnumOr(defaults.completedTaskBehavior),
            defaultTaskDurationMinutes = prefs[Keys.defaultTaskDuration]
                ?: defaults.defaultTaskDurationMinutes,
            timeFormat = prefs[Keys.timeFormat].toEnumOr(defaults.timeFormat),
            notificationSound = NotificationSound.parse(prefs[Keys.notificationSound]),
            defaultReminderLeadMinutes = prefs[Keys.defaultReminderLead]
                ?: defaults.defaultReminderLeadMinutes,
            quietHoursEnabled = prefs[Keys.quietHoursEnabled] ?: defaults.quietHoursEnabled,
            quietHoursStart = prefs[Keys.quietHoursStart].toLocalTimeOr(defaults.quietHoursStart),
            quietHoursEnd = prefs[Keys.quietHoursEnd].toLocalTimeOr(defaults.quietHoursEnd),
            showCompletedOnToday = prefs[Keys.showCompletedOnToday] ?: defaults.showCompletedOnToday,
            breathing = toBreathingPreferences(prefs),
        )
    }

    private fun toBreathingPreferences(prefs: Preferences): BreathingPreferences {
        val defaults = BreathingPreferences.Default
        return BreathingPreferences(
            soundEnabled = prefs[Keys.breathingSound] ?: defaults.soundEnabled,
            boxSeconds = prefs[Keys.boxSeconds] ?: defaults.boxSeconds,
            boxRounds = prefs[Keys.boxRounds] ?: defaults.boxRounds,
            staticBreatheUpSeconds = prefs[Keys.staticBreatheUp] ?: defaults.staticBreatheUpSeconds,
            staticHoldSeconds = prefs[Keys.staticHold] ?: defaults.staticHoldSeconds,
            staticHoldIncrementSeconds = prefs[Keys.staticHoldIncrement]
                ?: defaults.staticHoldIncrementSeconds,
            staticRounds = prefs[Keys.staticRounds] ?: defaults.staticRounds,
            co2HoldSeconds = prefs[Keys.co2Hold] ?: defaults.co2HoldSeconds,
            co2StartRestSeconds = prefs[Keys.co2StartRest] ?: defaults.co2StartRestSeconds,
            co2RestDecrementSeconds = prefs[Keys.co2RestDecrement]
                ?: defaults.co2RestDecrementSeconds,
            co2Rounds = prefs[Keys.co2Rounds] ?: defaults.co2Rounds,
            o2RestSeconds = prefs[Keys.o2Rest] ?: defaults.o2RestSeconds,
            o2StartHoldSeconds = prefs[Keys.o2StartHold] ?: defaults.o2StartHoldSeconds,
            o2HoldIncrementSeconds = prefs[Keys.o2HoldIncrement] ?: defaults.o2HoldIncrementSeconds,
            o2Rounds = prefs[Keys.o2Rounds] ?: defaults.o2Rounds,
        )
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

private fun String?.toLocalTimeOr(fallback: LocalTime): LocalTime =
    this?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback
