package dev.achyutem.cadence.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.achyutem.cadence.core.designsystem.component.CadenceDock
import dev.achyutem.cadence.core.designsystem.component.DockItem
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Spacing
import androidx.navigation.toRoute
import dev.achyutem.cadence.feature.breathing.BreathingScreen
import dev.achyutem.cadence.feature.habits.HabitDetailScreen
import dev.achyutem.cadence.feature.habits.HabitsScreen
import dev.achyutem.cadence.feature.notes.NoteEditorScreen
import dev.achyutem.cadence.feature.notes.NotesScreen
import dev.achyutem.cadence.feature.settings.SettingsScreen
import dev.achyutem.cadence.feature.today.TodayScreen
import dev.achyutem.cadence.feature.todos.TodosScreen

/**
 * The application shell: one [NavHost] for the four top-level destinations, with the floating
 * dock drawn over it.
 *
 * The dock is an overlay rather than a `Scaffold` bottom bar because it is narrower than the
 * screen and content is meant to scroll *underneath* it. Screens reserve room for it with
 * [Spacing.dockClearance] as bottom content padding, so the last list item can still be reached
 * and nothing is permanently hidden behind the dock.
 */
@Composable
fun CadenceApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Raised by a screen that wants the dock out of the way — currently only a running
    // breathing session.
    var immersive by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val isTopLevel = remember(currentDestination) {
        currentDestination != null && TopLevelDestination.entries.any { currentDestination.matches(it) }
    }

    val selected = remember(currentDestination) {
        TopLevelDestination.entries.firstOrNull { destination ->
            currentDestination?.matches(destination) == true
        } ?: TopLevelDestination.TODAY
    }

    val dockItems = TopLevelDestination.entries.map { destination ->
        DockItem(
            key = destination.key,
            label = stringResource(destination.labelRes),
            icon = destination.icon,
            selectedIcon = destination.selectedIcon,
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CadenceNavHost(
                navController = navController,
                onImmersiveChange = { immersive = it },
            )

            // The dock belongs to the top-level destinations only. A detail screen is a
            // different mode with its own toolbar, and leaving a navigation bar floating over it
            // would invite tapping away from an unsaved edit.
            AnimatedVisibility(
                visible = isTopLevel && !immersive,
                enter = fadeIn(tween(CadenceTheme.duration(Motion.QUICK))) +
                    slideInVertically(tween(CadenceTheme.duration(Motion.STANDARD))) { it / 2 },
                exit = fadeOut(tween(CadenceTheme.duration(Motion.MICRO))) +
                    slideOutVertically(tween(CadenceTheme.duration(Motion.QUICK))) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.md),
            ) {
            CadenceDock(
                items = dockItems,
                selectedKey = selected.key,
                onSelect = { key ->
                    TopLevelDestination.fromKey(key)?.let { navController.navigateToTopLevel(it) }
                },
            )
            }
        }
    }
}

@Composable
private fun CadenceNavHost(
    navController: NavHostController,
    onImmersiveChange: (Boolean) -> Unit,
) {
    // Top-level tabs are siblings, not a stack: a cross-fade with a small upward drift reads as
    // "swap", where a horizontal slide would wrongly imply hierarchy. Durations run through the
    // theme so reduced-motion collapses them to an instant cut.
    val enterMillis = CadenceTheme.duration(Motion.STANDARD)
    val exitMillis = CadenceTheme.duration(Motion.QUICK)

    NavHost(
        navController = navController,
        startDestination = CadenceRoute.Today,
        enterTransition = {
            if (enterMillis == 0) {
                EnterTransition.None
            } else {
                fadeIn(tween(enterMillis, easing = Motion.enterEasing)) +
                    slideInVertically(tween(enterMillis, easing = Motion.enterEasing)) { it / 24 }
            }
        },
        exitTransition = {
            if (exitMillis == 0) ExitTransition.None else fadeOut(tween(exitMillis))
        },
        popEnterTransition = {
            if (enterMillis == 0) EnterTransition.None else fadeIn(tween(enterMillis))
        },
        popExitTransition = {
            if (exitMillis == 0) ExitTransition.None else fadeOut(tween(exitMillis))
        },
    ) {
        composable<CadenceRoute.Today> {
            TodayScreen(onOpenHabit = { id -> navController.navigate(CadenceRoute.HabitDetail(id)) })
        }
        composable<CadenceRoute.Todos> { TodosScreen() }
        composable<CadenceRoute.Habits> {
            HabitsScreen(onOpenHabit = { id -> navController.navigate(CadenceRoute.HabitDetail(id)) })
        }
        composable<CadenceRoute.Notes> {
            NotesScreen(onOpenNote = { noteId -> navController.navigate(CadenceRoute.NoteDetail(noteId)) })
        }
        composable<CadenceRoute.Breathing> { BreathingScreen(onImmersiveChange = onImmersiveChange) }
        composable<CadenceRoute.Settings> { SettingsScreen() }

        composable<CadenceRoute.HabitDetail> { entry ->
            val route = entry.toRoute<CadenceRoute.HabitDetail>()
            HabitDetailScreen(habitId = route.habitId, onBack = { navController.popBackStack() })
        }

        composable<CadenceRoute.NoteDetail> { entry ->
            val route = entry.toRoute<CadenceRoute.NoteDetail>()
            NoteEditorScreen(noteId = route.noteId, onBack = { navController.popBackStack() })
        }
    }
}

/**
 * Standard bottom-navigation semantics: one back stack entry per tab, state preserved when
 * switching away and restored when returning, and no growing stack of repeated tabs.
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The graph is flat, so the current destination *is* the tab destination — no hierarchy walk
 * needed. [hasRoute] compares the serialized route class rather than a string, which is why
 * renaming a route object can never silently break tab highlighting.
 */
private fun NavDestination.matches(destination: TopLevelDestination): Boolean =
    when (destination) {
        TopLevelDestination.TODAY -> hasRoute(CadenceRoute.Today::class)
        TopLevelDestination.TODOS -> hasRoute(CadenceRoute.Todos::class)
        TopLevelDestination.HABITS -> hasRoute(CadenceRoute.Habits::class)
        TopLevelDestination.NOTES -> hasRoute(CadenceRoute.Notes::class)
        TopLevelDestination.BREATHING -> hasRoute(CadenceRoute.Breathing::class)
        TopLevelDestination.SETTINGS -> hasRoute(CadenceRoute.Settings::class)
    }
