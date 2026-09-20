package dev.achyutem.cadence.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
 * Unknown or corrupt stored values fall back to the default rather than throwing — a preferences
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

    suspend fun setReducedMotion(enabled: Boolean) = put(Keys.reducedMotion, enabled)

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

    suspend fun setCheckInPromptEnabled(enabled: Boolean) = put(Keys.checkInPrompt, enabled)

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
            prefs[Keys.reducedMotion] = preferences.reducedMotion
            prefs[Keys.weekStartsOn] = preferences.weekStartsOn.value
            prefs[Keys.completedTaskBehavior] = preferences.completedTaskBehavior.name
            prefs[Keys.defaultTaskDuration] = preferences.defaultTaskDurationMinutes
            prefs[Keys.timeFormat] = preferences.timeFormat.name
            prefs[Keys.defaultReminderLead] = preferences.defaultReminderLeadMinutes
            prefs[Keys.quietHoursEnabled] = preferences.quietHoursEnabled
            prefs[Keys.quietHoursStart] = preferences.quietHoursStart.toString()
            prefs[Keys.quietHoursEnd] = preferences.quietHoursEnd.toString()
            prefs[Keys.showCompletedOnToday] = preferences.showCompletedOnToday
            prefs[Keys.checkInPrompt] = preferences.checkInPromptEnabled
        }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private object Keys {
        val displayName = stringPreferencesKey("display_name")
        val themeMode = stringPreferencesKey("theme_mode")
        val accentColor = stringPreferencesKey("accent_color")
        val useDynamicColor = booleanPreferencesKey("use_dynamic_color")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val weekStartsOn = intPreferencesKey("week_starts_on")
        val completedTaskBehavior = stringPreferencesKey("completed_task_behavior")
        val defaultTaskDuration = intPreferencesKey("default_task_duration")
        val timeFormat = stringPreferencesKey("time_format")
        val defaultReminderLead = intPreferencesKey("default_reminder_lead")
        val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
        val quietHoursStart = stringPreferencesKey("quiet_hours_start")
        val quietHoursEnd = stringPreferencesKey("quiet_hours_end")
        val showCompletedOnToday = booleanPreferencesKey("show_completed_on_today")
        val checkInPrompt = booleanPreferencesKey("check_in_prompt")
    }

    private fun toUserPreferences(prefs: Preferences): UserPreferences {
        val defaults = UserPreferences.Default
        return UserPreferences(
            displayName = prefs[Keys.displayName] ?: defaults.displayName,
            themeMode = prefs[Keys.themeMode].toEnumOr(defaults.themeMode),
            accentColor = prefs[Keys.accentColor].toEnumOr(defaults.accentColor),
            useDynamicColor = prefs[Keys.useDynamicColor] ?: defaults.useDynamicColor,
            reducedMotion = prefs[Keys.reducedMotion] ?: defaults.reducedMotion,
            weekStartsOn = prefs[Keys.weekStartsOn]
                ?.takeIf { it in 1..7 }
                ?.let(DayOfWeek::of)
                ?: defaults.weekStartsOn,
            completedTaskBehavior = prefs[Keys.completedTaskBehavior]
                .toEnumOr(defaults.completedTaskBehavior),
            defaultTaskDurationMinutes = prefs[Keys.defaultTaskDuration]
                ?: defaults.defaultTaskDurationMinutes,
            timeFormat = prefs[Keys.timeFormat].toEnumOr(defaults.timeFormat),
            defaultReminderLeadMinutes = prefs[Keys.defaultReminderLead]
                ?: defaults.defaultReminderLeadMinutes,
            quietHoursEnabled = prefs[Keys.quietHoursEnabled] ?: defaults.quietHoursEnabled,
            quietHoursStart = prefs[Keys.quietHoursStart].toLocalTimeOr(defaults.quietHoursStart),
            quietHoursEnd = prefs[Keys.quietHoursEnd].toLocalTimeOr(defaults.quietHoursEnd),
            showCompletedOnToday = prefs[Keys.showCompletedOnToday] ?: defaults.showCompletedOnToday,
            checkInPromptEnabled = prefs[Keys.checkInPrompt] ?: defaults.checkInPromptEnabled,
        )
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

private fun String?.toLocalTimeOr(fallback: LocalTime): LocalTime =
    this?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback
