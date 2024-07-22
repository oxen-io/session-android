package org.thoughtcrime.securesms.conversation.v2

import android.text.Selection
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.open_groups.GroupMemberRole
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.MainCoroutineRule
import org.thoughtcrime.securesms.conversation.v2.mention.MentionViewModel

@RunWith(RobolectricTestRunner::class)
class MentionViewModelTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var mentionViewModel: MentionViewModel

    private val threadID = 123L

    private data class MemberInfo(
        val name: String,
        val pubKey: String,
        val roles: List<GroupMemberRole>
    )

    private val threadMembers = listOf(
        MemberInfo("Alice", "pubkey1", listOf(GroupMemberRole.ADMIN)),
        MemberInfo("Bob", "pubkey2", listOf(GroupMemberRole.STANDARD)),
        MemberInfo("Charlie", "pubkey3", listOf(GroupMemberRole.MODERATOR)),
        MemberInfo("David", "pubkey4", listOf(GroupMemberRole.HIDDEN_ADMIN)),
        MemberInfo("Eve", "pubkey5", listOf(GroupMemberRole.HIDDEN_MODERATOR)),
        MemberInfo("李云海", "pubkey6", listOf(GroupMemberRole.ZOOMBIE)),
    )

    private val memberContacts = threadMembers.map { m ->
        Contact(m.pubKey).also {
            it.name = m.name
        }
    }

    private val openGroup = OpenGroup(
        server = "",
        room = "",
        id = "open_group_id_1",
        name = "Open Group",
        publicKey = "",
        imageId = null,
        infoUpdates = 0,
        canWrite = true
    )

    @Before
    fun setUp() {
        @Suppress("UNCHECKED_CAST")
        mentionViewModel = MentionViewModel(
            threadID,
            contentResolver = mock { },
            threadDatabase = mock {
                on { getRecipientForThreadId(threadID) } doAnswer {
                    mock<Recipient> {
                        on { isClosedGroupRecipient } doReturn false
                        on { isCommunityRecipient } doReturn true
                        on { isContactRecipient } doReturn false
                    }
                }
            },
            groupDatabase = mock {
            },
            mmsDatabase = mock {
                on { getRecentChatMemberIDs(eq(threadID), any()) } doAnswer {
                    val limit = it.arguments[1] as Int
                    threadMembers.take(limit).map { m -> m.pubKey }
                }
            },
            contactDatabase = mock {
                on { getContacts(any()) } doAnswer {
                    val ids = it.arguments[0] as Collection<String>
                    memberContacts.filter { it.accountID in ids }
                }
            },
            memberDatabase = mock {
                on { getGroupMembersRoles(eq(openGroup.id), any()) } doAnswer {
                    val memberIDs = it.arguments[1] as Collection<String>
                    memberIDs.associateWith { id ->
                        threadMembers.first { it.pubKey == id }.roles
                    }
                }
            },
            storage = mock {
                on { getOpenGroup(threadID) } doReturn openGroup
            },
            dispatcher = StandardTestDispatcher()
        )
    }

    @Test
    fun `should show candidates after 'at' symbol`() = runTest {
        mentionViewModel.autoCompleteState.test {
            assertThat(awaitItem())
                .isEqualTo(MentionViewModel.AutoCompleteState.Idle)

            val editable = mentionViewModel.editableFactory.newEditable("")
            editable.append("Hello @")
            expectNoEvents() // Nothing should happen before cursor is put after @
            Selection.setSelection(editable, editable.length)

            assertThat(awaitItem())
                .isEqualTo(MentionViewModel.AutoCompleteState.Loading)

            // Should show all the candidates
            awaitItem().let { result ->
                assertThat(result)
                    .isInstanceOf(MentionViewModel.AutoCompleteState.Result::class.java)
                result as MentionViewModel.AutoCompleteState.Result

                assertThat(result.members).isEqualTo(threadMembers.mapIndexed { index, m ->
                    val name =
                        memberContacts[index].displayName(Contact.ContactContext.OPEN_GROUP).orEmpty()

                    MentionViewModel.Candidate(
                        MentionViewModel.Member(m.pubKey, name, m.roles.any { it.isModerator }),
                        name,
                        0
                    )
                })
            }


            // Continue typing to filter candidates
            editable.append("li")
            Selection.setSelection(editable, editable.length)

            // Should show only Alice and Charlie
            awaitItem().let { result ->
                assertThat(result)
                    .isInstanceOf(MentionViewModel.AutoCompleteState.Result::class.java)
                result as MentionViewModel.AutoCompleteState.Result

                assertThat(result.members[0].member.name).isEqualTo("Alice (pubk...key1)")
                assertThat(result.members[1].member.name).isEqualTo("Charlie (pubk...key3)")
            }
        }
    }

    @Test
    fun `should have normalised message with candidates selected`() = runTest {
        mentionViewModel.autoCompleteState.test {
            assertThat(awaitItem())
                .isEqualTo(MentionViewModel.AutoCompleteState.Idle)

            val editable = mentionViewModel.editableFactory.newEditable("")
            editable.append("Hi @")
            Selection.setSelection(editable, editable.length)

            assertThat(awaitItem())
                .isEqualTo(MentionViewModel.AutoCompleteState.Loading)

            // Select a candidate now
            assertThat(awaitItem())
                .isInstanceOf(MentionViewModel.AutoCompleteState.Result::class.java)
            mentionViewModel.onCandidateSelected("pubkey1")

            // Should have normalised message with selected candidate
            assertThat(mentionViewModel.normalizeMessageBody())
                .isEqualTo("Hi @pubkey1 ")
        }
    }
}