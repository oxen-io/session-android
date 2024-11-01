package network.loki.messenger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.groups.compose.EditGroup
import org.thoughtcrime.securesms.groups.EditGroupViewState
import org.thoughtcrime.securesms.groups.MemberState
import org.thoughtcrime.securesms.groups.MemberViewModel
import org.thoughtcrime.securesms.ui.theme.PreviewTheme

@RunWith(AndroidJUnit4::class)
@SmallTest
class EditGroupTests {

    @get:Rule
    val composeTest = createComposeRule()

    val oneMember = MemberViewModel(
        "Test User",
        "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
        MemberState.InviteSent,
        false
    )
    val twoMember = MemberViewModel(
        "Test User 2",
        "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1235",
        MemberState.InviteFailed,
        false
    )
    val threeMember = MemberViewModel(
        "Test User 3",
        "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1236",
        MemberState.Member,
        false
    )

    val fourMember = MemberViewModel(
        "Test User 4",
        "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1237",
        MemberState.Admin,
        false
    )

    @Test
    fun testDisplaysNameAndDesc() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext
        // Accessibility IDs
        val nameDesc = application.getString(R.string.AccessibilityId_group_name)
        val descriptionDesc = application.getString(R.string.AccessibilityId_group_description)

        with (composeTest) {
            val state = EditGroupViewState(
                "TestGroup",
                "TestDesc",
                emptyList(),
                false
            )

            setContent {
                PreviewTheme {
                    EditGroup(
                        onBackClick = {},
                        onAddMemberClick = {},
                        onResendInviteClick = {},
                        onPromoteClick = {},
                        onRemoveClick = {},
                        onEditName = {},
                        onMemberSelected = {},
                        viewState = state
                    )
                }
            }
            onNode(hasContentDescriptionExactly(nameDesc)).assertTextEquals("TestGroup")
            onNode(hasContentDescriptionExactly(descriptionDesc)).assertTextEquals("TestDesc")
        }
    }

    @Test
    fun testDisplaysReinviteProperly() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext

        // Accessibility IDs
        val reinviteDesc = application.getString(R.string.AccessibilityId_reinvite_member)
        val promoteDesc = application.getString(R.string.AccessibilityId_promote_member)

        var reinvited = false

        with (composeTest) {

            val state = EditGroupViewState(
                "TestGroup",
                "TestDesc",
                listOf(
                    twoMember
                ),
                // reinvite only shows for admin users
                true
            )

            setContent {
                PreviewTheme {
                    EditGroup(
                        onBackClick = {},
                        onAddMemberClick = {},
                        onResendInviteClick = { reinvited = true },
                        onPromoteClick = {},
                        onRemoveClick = {},
                        onEditName = {},
                        onMemberSelected = {},
                        viewState = state
                    )
                }
            }
            onNodeWithContentDescription(reinviteDesc).assertIsDisplayed().performClick()
            onNodeWithContentDescription(promoteDesc).assertDoesNotExist()
            assertThat(reinvited, equalTo(true))
        }
    }

    @Test
    fun testDisplaysRegularMemberProperly() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext

        // Accessibility IDs
        val reinviteDesc = application.getString(R.string.AccessibilityId_reinvite_member)
        val promoteDesc = application.getString(R.string.AccessibilityId_promote_member)

        var promoted = false

        with (composeTest) {

            val state = EditGroupViewState(
                "TestGroup",
                "TestDesc",
                listOf(
                    threeMember
                ),
                // reinvite only shows for admin users
                true
            )

            setContent {
                PreviewTheme {
                    EditGroup(
                        onBackClick = {},
                        onAddMemberClick = {},
                        onResendInviteClick = {},
                        onPromoteClick = { promoted = true },
                        onRemoveClick = {},
                        onEditName = {},
                        onMemberSelected = {},
                        viewState = state
                    )
                }
            }
            onNodeWithContentDescription(reinviteDesc).assertDoesNotExist()
            onNodeWithContentDescription(promoteDesc).assertIsDisplayed().performClick()
            assertThat(promoted, equalTo(true))
        }
    }

    @Test
    fun testDisplaysAdminProperly() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext

        // Accessibility IDs
        val reinviteDesc = application.getString(R.string.AccessibilityId_reinvite_member)
        val promoteDesc = application.getString(R.string.AccessibilityId_promote_member)

        with (composeTest) {

            val state = EditGroupViewState(
                "TestGroup",
                "TestDesc",
                listOf(
                    fourMember
                ),
                // reinvite only shows for admin users
                true
            )

            setContent {
                PreviewTheme {
                    EditGroup(
                        onBackClick = {},
                        onAddMemberClick = {},
                        onResendInviteClick = {},
                        onPromoteClick = {},
                        onRemoveClick = {},
                        onEditName = {},
                        onMemberSelected = {},
                        viewState = state
                    )
                }
            }
            onNodeWithContentDescription(reinviteDesc).assertDoesNotExist()
            onNodeWithContentDescription(promoteDesc).assertDoesNotExist()
        }
    }

    @Test
    fun testDisplaysPendingInviteProperly() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext

        // Accessibility IDs
        val reinviteDesc = application.getString(R.string.AccessibilityId_reinvite_member)
        val promoteDesc = application.getString(R.string.AccessibilityId_promote_member)
        val stateDesc = application.getString(R.string.AccessibilityId_member_state)
        val memberDesc = application.getString(R.string.AccessibilityId_contact)

        with (composeTest) {

            val state = EditGroupViewState(
                "TestGroup",
                "TestDesc",
                listOf(
                    oneMember
                ),
                // reinvite only shows for admin users
                true
            )

            setContent {
                PreviewTheme {
                    EditGroup(
                        onBackClick = {},
                        onAddMemberClick = {},
                        onResendInviteClick = {},
                        onPromoteClick = {},
                        onRemoveClick = {},
                        onEditName = {},
                        onMemberSelected = {},
                        viewState = state
                    )
                }
            }
            onNodeWithContentDescription(reinviteDesc).assertDoesNotExist()
            onNodeWithContentDescription(promoteDesc).assertDoesNotExist()
            onNodeWithContentDescription(stateDesc, useUnmergedTree = true).assertTextEquals("InviteSent")
            onNodeWithContentDescription(memberDesc, useUnmergedTree = true).assertTextEquals("Test User")
        }
    }

}