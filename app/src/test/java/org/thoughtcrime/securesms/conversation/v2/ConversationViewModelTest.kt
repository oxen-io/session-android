package org.thoughtcrime.securesms.conversation.v2

import android.app.Application
import com.goterl.lazysodium.utils.KeyPair
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.hamcrest.CoreMatchers.endsWith
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.BaseViewModelTest
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.repository.ConversationRepository

class ConversationViewModelTest: BaseViewModelTest() {

    private val repository = mock<ConversationRepository>()
    private val storage = mock<Storage>()
    private val application = mock<Application>()

    private val threadId = 123L
    private val edKeyPair = mock<KeyPair>()
    private lateinit var recipient: Recipient
    private lateinit var messageRecord: MessageRecord

    private val viewModel: ConversationViewModel by lazy {
        ConversationViewModel(threadId, edKeyPair,  application, repository, storage,
            mock(), mock(), mock(), mock(), mock())
    }

    @Before
    fun setUp() {
        recipient = mock()
        messageRecord = mock { record ->
            whenever(record.individualRecipient).thenReturn(recipient)
        }
        whenever(repository.maybeGetRecipientForThreadId(anyLong())).thenReturn(recipient)
        whenever(repository.recipientUpdateFlow(anyLong())).thenReturn(emptyFlow())
    }

    @Test
    fun `should save draft message`() {
        val draft = "Hi there"

        viewModel.saveDraft(draft)

        // The above is an async process to wait 100ms to give it a chance to complete
        verify(repository, Mockito.timeout(100).times(1)).saveDraft(threadId, draft)
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

        verify(repository).setBlocked(recipient, false)
    }

    @Test
    fun `should emit error message on ban user failure`() = runBlockingTest {
        val error = Throwable()
        whenever(repository.banUser(anyLong(), any())).thenReturn(Result.failure(error))
        whenever(application.getString(any())).thenReturn("Ban failed")

        viewModel.banUser(recipient)

        assertThat(viewModel.uiState.first().uiMessages.first().message, equalTo("Ban failed"))
    }

    @Test
    fun `should emit a message on ban user success`() = runBlockingTest {
        whenever(repository.banUser(anyLong(), any())).thenReturn(Result.success(Unit))
        whenever(application.getString(any())).thenReturn("User banned")

        viewModel.banUser(recipient)

        assertThat(
            viewModel.uiState.first().uiMessages.first().message,
            equalTo("User banned")
        )
    }

    @Test
    fun `should emit error message on ban user and delete all failure`() = runBlockingTest {
        val error = Throwable()
        whenever(repository.banAndDeleteAll(anyLong(), any())).thenReturn(Result.failure(error))
        whenever(application.getString(any())).thenReturn("Ban failed")

        viewModel.banAndDeleteAll(messageRecord)

        assertThat(viewModel.uiState.first().uiMessages.first().message, equalTo("Ban failed"))
    }

    @Test
    fun `should emit a message on ban user and delete all success`() = runBlockingTest {
        whenever(repository.banAndDeleteAll(anyLong(), any())).thenReturn(Result.success(Unit))
        whenever(application.getString(any())).thenReturn("User banned")

        viewModel.banAndDeleteAll(messageRecord)

        assertThat(
            viewModel.uiState.first().uiMessages.first().message,
            equalTo("User banned")
        )
    }

    @Test
    fun `should accept message request`() = runBlockingTest {
        viewModel.acceptMessageRequest()

        verify(repository).acceptMessageRequest(threadId, recipient)
    }

    @Test
    fun `should decline message request`() {
        viewModel.declineMessageRequest()

        verify(repository).declineMessageRequest(threadId)
    }

    @Test
    fun `should remove shown message`() = runBlockingTest {
        // Given that a message is generated
        whenever(repository.banUser(anyLong(), any())).thenReturn(Result.success(Unit))
        whenever(application.getString(any())).thenReturn("User banned")

        viewModel.banUser(recipient)
        assertThat(viewModel.uiState.value.uiMessages.size, equalTo(1))
        // When the message is shown
        viewModel.messageShown(viewModel.uiState.first().uiMessages.first().id)
        // Then it should be removed
        assertThat(viewModel.uiState.value.uiMessages.size, equalTo(0))
    }

    @Test
    fun `open group recipient should have no blinded recipient`() {
        whenever(recipient.isCommunityRecipient).thenReturn(true)
        whenever(recipient.isOpenGroupOutboxRecipient).thenReturn(false)
        whenever(recipient.isOpenGroupInboxRecipient).thenReturn(false)
        assertThat(viewModel.blindedRecipient, nullValue())
    }

    @Test
    fun `local recipient should have input and no blinded recipient`() {
        whenever(recipient.isLocalNumber).thenReturn(true)
        assertThat(viewModel.shouldHideInputBar(), equalTo(false))
        assertThat(viewModel.blindedRecipient, nullValue())
    }

    @Test
    fun `contact recipient should hide input bar if not accepting requests`() {
        whenever(recipient.isOpenGroupInboxRecipient).thenReturn(true)
        val blinded = mock<Recipient> {
            whenever(it.blocksCommunityMessageRequests).thenReturn(true)
        }
        whenever(repository.maybeGetBlindedRecipient(recipient)).thenReturn(blinded)
        assertThat(viewModel.blindedRecipient, notNullValue())
        assertThat(viewModel.shouldHideInputBar(), equalTo(true))
    }

}