package network.loki.messenger

import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.groups.compose.CreateGroup
import org.thoughtcrime.securesms.groups.ViewState
import org.thoughtcrime.securesms.ui.theme.PreviewTheme

@RunWith(AndroidJUnit4::class)
@SmallTest
class CreateGroupTests {

    @get:Rule
    val composeTest = createComposeRule()

    @Test
    fun testNavigateToCreateGroup() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext
        // Accessibility IDs
        val nameDesc = application.getString(R.string.AccessibilityId_closed_group_edit_group_name)
        val buttonDesc = application.getString(R.string.AccessibilityId_create_closed_group_create_button)

        var backPressed = false
        var closePressed = false

        composeTest.setContent {
            PreviewTheme {
                CreateGroup(
                    viewState = ViewState.DEFAULT,
                    onBack = { backPressed = true },
                    onClose = { closePressed = true },
                    onContactItemClicked = {},
                    updateState = {}
                )
            }
        }

        with(composeTest) {
            onNode(hasContentDescriptionExactly(nameDesc)).performTextInput("Name")
            onNode(hasContentDescriptionExactly(buttonDesc)).performClick()
        }

        assertThat(backPressed, equalTo(false))
        assertThat(closePressed, equalTo(false))

    }

    @Test
    fun testFailToCreate() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext
        // Accessibility IDs
        val nameDesc = application.getString(R.string.AccessibilityId_closed_group_edit_group_name)
        val buttonDesc = application.getString(R.string.AccessibilityId_create_closed_group_create_button)

        var backPressed = false
        var closePressed = false

        composeTest.setContent {
            PreviewTheme {
                CreateGroup(
                    viewState = ViewState.DEFAULT,
                    onBack = { backPressed = true },
                    onClose = { closePressed = true },
                    updateState = {},
                    onContactItemClicked = {}
                )
            }
        }
        with(composeTest) {
            onNode(hasContentDescriptionExactly(nameDesc)).performTextInput("")
            onNode(hasContentDescriptionExactly(buttonDesc)).performClick()
        }

        assertThat(backPressed, equalTo(false))
        assertThat(closePressed, equalTo(false))
    }

    @Test
    fun testBackButton() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext
        // Accessibility IDs
        val backDesc = application.getString(R.string.new_conversation_dialog_back_button_content_description)

        var backPressed = false

        composeTest.setContent {
            PreviewTheme {
                CreateGroup(
                    viewState = ViewState.DEFAULT,
                    onBack = { backPressed = true },
                    onClose = {},
                    onContactItemClicked = {},
                    updateState = {}
                )
            }
        }

        with (composeTest) {
            onNode(hasContentDescriptionExactly(backDesc)).performClick()
        }

        assertThat(backPressed, equalTo(true))
    }

    @Test
    fun testCloseButton() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext
        // Accessibility IDs
        val closeDesc = application.getString(R.string.new_conversation_dialog_close_button_content_description)
        var closePressed = false

        composeTest.setContent {
            PreviewTheme {
                CreateGroup(
                    viewState = ViewState.DEFAULT,
                    onBack = { },
                    onClose = { closePressed = true },
                    onContactItemClicked = {},
                    updateState = {}
                )
            }
        }

        with (composeTest) {
            onNode(hasContentDescriptionExactly(closeDesc)).performClick()
        }

        assertThat(closePressed, equalTo(true))
    }


}