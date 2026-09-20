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
 * All five destinations are reachable from the dock.
 *
 * Tabs are found by content description rather than by text. That is the contract, not a
 * workaround: the dock draws no labels, so the description is the only name a screen reader has,
 * and a tab that cannot be found this way is a tab a screen-reader user cannot identify.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyDockDestinationIsReachable() {
        composeRule.onNodeWithContentDescription("Todos").performClick()
        composeRule.onNodeWithText("Todos").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Habits").performClick()
        composeRule.onNodeWithText("Habits").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Notes").performClick()
        composeRule.onNodeWithText("Notes").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("APPEARANCE").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Today").performClick()
        composeRule.onNodeWithText("TODOS").assertIsDisplayed()
    }

    @Test
    fun everyTabIsNamedForScreenReaders() {
        // Including the selected one: no tab draws a visible label, so all five must be described.
        listOf("Today", "Todos", "Habits", "Notes", "Settings").forEach { name ->
            composeRule.onNodeWithContentDescription(name).assertIsDisplayed()
        }
    }

    @Test
    fun theAppOpensOnToday() {
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
