package org.thoughtcrime.securesms.conversation.v2

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.repository.ConversationRepository
import org.mockito.Mockito.`when` as whenever

class ConversationViewModelTest {

    private val repository = mock(ConversationRepository::class.java)

    private val threadId = 123L
    private lateinit var recipient: Recipient

    private val viewModel: ConversationViewModel by lazy {
        ConversationViewModel(threadId, repository)
    }

    @Before
    fun setUp() {
        recipient = mock(Recipient::class.java)
        whenever(repository.getRecipientForThreadId(anyLong())).thenReturn(recipient)
    }

    @Test
    fun `should save draft message`() {
        val draft = "Hi there"

        viewModel.saveDraft(draft)

        verify(repository).saveDraft(threadId, draft)
    }

    @Test
    fun `should retrieve draft message`() {
        val draft = "Hi there"
        whenever(repository.getDraft(anyLong())).thenReturn(draft)

        val result = viewModel.getDraft()

        verify(repository).getDraft(threadId)
        assertThat(result, equalTo(draft))
    }

    @Test
    fun `should invite contacts`() {
        val contacts = listOf<Recipient>()

        viewModel.inviteContacts(contacts)

        verify(repository).inviteContacts(threadId, contacts)
    }

    @Test
    fun `should unblock contact recipient`() {
        whenever(recipient.isContactRecipient).thenReturn(true)

        viewModel.unblock()

        verify(repository).unblock(recipient)
    }

    @Test
    fun `should delete locally`() {
        val message = mock(MessageRecord::class.java)

        viewModel.deleteLocally(message)

        verify(repository).deleteLocally(recipient, message)
    }

}