package dev.achyutem.cadence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The note flow end to end: create, type, leave, and find it in the list.
 *
 * The assertion that matters is the last one — there is no save button, so if autosave did not
 * fire on the way out, the note simply would not be there.
 */
@RunWith(AndroidJUnit4::class)
class NotesFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * The title is unique per run.
     *
     * Instrumented tests share one installed app, so a note left behind by an earlier run would
     * make a fixed title match twice and fail the assertion for a reason that has nothing to do
     * with the behaviour under test. Making the fixture unique is cheaper and more honest than
     * wiping the database between tests.
     */
    private val title = "Reading list ${System.currentTimeMillis()}"

    @Test
    fun aNoteSurvivesLeavingTheEditor() {
        composeRule.onNodeWithContentDescription("Notes").performClick()
        composeRule.onNodeWithText("New").performClick()

        composeRule.onNodeWithText("Heading").performTextInput(title)
        composeRule.onNodeWithText("Start writing…")
            .performTextInput("- [ ] Designing Data-Intensive Applications")

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.waitForIdle()
        // No save button: if autosave did not flush on the way out, this note would not exist.
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }
}
