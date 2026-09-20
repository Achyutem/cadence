package dev.achyutem.cadence.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes. Arguments travel as typed properties rather than string paths, so
 * a wrong argument is a compile error instead of a crash at navigation time.
 *
 * Detail routes are declared here already even though their screens arrive in later phases; the
 * navigation graph is a place where late additions are expensive to retrofit.
 */
sealed interface CadenceRoute {

    @Serializable data object Today : CadenceRoute

    @Serializable data object Todos : CadenceRoute

    @Serializable data object Habits : CadenceRoute

    @Serializable data object Notes : CadenceRoute

    @Serializable data object Breathing : CadenceRoute

    @Serializable data object Settings : CadenceRoute

    @Serializable data class TaskDetail(val taskId: Long) : CadenceRoute

    @Serializable data class HabitDetail(val habitId: Long) : CadenceRoute

    /** [noteId] of 0 means "a new note", the editor creates the row on first save. */
    @Serializable data class NoteDetail(val noteId: Long) : CadenceRoute

    /** [epochDay] rather than a formatted string: no parsing, no locale, no ambiguity. */
    @Serializable data class Calendar(val epochDay: Long? = null) : CadenceRoute
}

/**
 * The four dock destinations, in order.
 *
 * Today is first and is the start destination: the brief is explicit that it is the most
 * important screen, and it is what the app should open to every time.
 */
enum class TopLevelDestination(
    val route: CadenceRoute,
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    TODAY(
        route = CadenceRoute.Today,
        key = "today",
        labelRes = dev.achyutem.cadence.R.string.nav_today,
        icon = Icons.Outlined.Today,
        selectedIcon = Icons.Rounded.Today,
    ),
    TODOS(
        route = CadenceRoute.Todos,
        key = "todos",
        labelRes = dev.achyutem.cadence.R.string.nav_todos,
        icon = Icons.Outlined.CheckCircle,
        selectedIcon = Icons.Rounded.CheckCircle,
    ),
    HABITS(
        route = CadenceRoute.Habits,
        key = "habits",
        labelRes = dev.achyutem.cadence.R.string.nav_habits,
        icon = Icons.Outlined.Repeat,
        selectedIcon = Icons.Rounded.Repeat,
    ),
    NOTES(
        route = CadenceRoute.Notes,
        key = "notes",
        labelRes = dev.achyutem.cadence.R.string.nav_notes,
        icon = Icons.Outlined.StickyNote2,
        selectedIcon = Icons.Rounded.StickyNote2,
    ),
    BREATHING(
        route = CadenceRoute.Breathing,
        key = "breathing",
        labelRes = dev.achyutem.cadence.R.string.nav_breathing,
        icon = Icons.Outlined.Air,
        selectedIcon = Icons.Rounded.Air,
    ),
    SETTINGS(
        route = CadenceRoute.Settings,
        key = "settings",
        labelRes = dev.achyutem.cadence.R.string.nav_settings,
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Rounded.Settings,
    );

    companion object {
        fun fromKey(key: String): TopLevelDestination? = entries.firstOrNull { it.key == key }
    }
}
