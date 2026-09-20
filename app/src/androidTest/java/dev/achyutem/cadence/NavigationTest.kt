package dev.achyutem.cadence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The critical flow Phase 0 owns: every destination is reachable from the dock.
 *
 * Note how each tab is found: an *unselected* dock item shows no label, so its name lives only in
 * its content description; the selected one shows the label as real text. Driving the test that
 * way is not a workaround — it is the assertion that the dock stays reachable by screen reader
 * even when the label is hidden.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyDockDestinationIsReachable() {
        composeRule.onNodeWithContentDescription("Todos").performClick()
        composeRule.onNodeWithText("No tasks yet").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Habits").performClick()
        composeRule.onNodeWithText("No habits yet").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("APPEARANCE").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Today").performClick()
        composeRule.onNodeWithText("TODOS").assertIsDisplayed()
    }

    @Test
    fun unselectedTabsAreNamedForScreenReaders() {
        // Today is the start destination, so the other three must all be described.
        listOf("Todos", "Habits", "Settings").forEach { name ->
            composeRule.onNodeWithContentDescription(name).assertIsDisplayed()
        }
    }

    @Test
    fun theAppOpensOnToday() {
        // Today is the start destination, so its dock tab is selected — which means its name is
        // shown as a visible label and is therefore *absent* as a content description. (Asserting
        // on the text "Today" instead would be ambiguous: the progress card is titled "Today"
        // too.)
        composeRule.onNodeWithContentDescription("Today").assertDoesNotExist()
        composeRule.onNodeWithText("TODOS").assertIsDisplayed()
        composeRule.onNodeWithText("HABITS").assertIsDisplayed()
    }

    @Test
    fun returningToATabRestoresIt() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("APPEARANCE").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Today").performClick()
        composeRule.onNodeWithText("TODOS").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("APPEARANCE").assertIsDisplayed()
    }
}
