package dev.achyutem.cadence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Starting and stopping a breathing session.
 *
 * The assertion that matters is the last one: leaving a session must put the dock back. A session
 * hides it to take the whole screen, and a bug that failed to restore it would strand the user on
 * a screen with no navigation; the kind of thing that is obvious in use and invisible in a
 * screenshot.
 */
@RunWith(AndroidJUnit4::class)
class BreathingFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aSessionStartsAndLeavesTheDockIntactAfterwards() {
        composeRule.onNodeWithContentDescription("Breathe").performClick()
        composeRule.onNodeWithText("Box breathing").assertIsDisplayed()

        // Tapping an exercise opens its setup sheet; Start is what begins the session.
        composeRule.onNodeWithText("Box breathing").performClick()
        composeRule.onNodeWithText("Each side").assertIsDisplayed()
        composeRule.onNodeWithText("Start").performClick()

        // The session replaces the list and hides the dock.
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Today").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Stop session").performClick()

        // Back to the list, with navigation restored.
        composeRule.onNodeWithText("EXERCISES").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Today").assertIsDisplayed()
    }

    @Test
    fun anExercisesSettingsArePersistedAndShownOnItsCard() {
        composeRule.onNodeWithContentDescription("Breathe").performClick()
        composeRule.onNodeWithText("CO\u2082 table").performClick()

        // Reset first: preferences outlive a test method, so the starting point has to be stated
        // rather than assumed.
        composeRule.onNodeWithText("Reset to default").performClick()
        composeRule.onNodeWithContentDescription("More Rounds").performClick()
        composeRule.onNodeWithContentDescription("More Rounds").performClick()

        Espresso.pressBack()

        // Eight rounds by default, so the card should now be advertising ten.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("10 \u00d7 1:00 hold, rest \u221215s each round")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun theSafetyNoticeIsAlwaysVisible() {
        // Not behind a one-time dismissal: breath-hold blackout arrives without warning, so this
        // is an instruction rather than a disclaimer.
        composeRule.onNodeWithContentDescription("Breathe").performClick()
        composeRule.onNodeWithText("Before you start").assertIsDisplayed()
    }
}
