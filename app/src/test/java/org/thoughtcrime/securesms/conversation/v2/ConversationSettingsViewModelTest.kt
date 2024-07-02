package org.thoughtcrime.securesms.conversation.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.session.libsession.database.StorageProtocol
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.BaseViewModelTest
import org.thoughtcrime.securesms.conversation.settings.ConversationSettingsViewModel

class ConversationSettingsViewModelTest: BaseViewModelTest() {

    companion object {
        const val TEST_THREAD_ID = 1L
        const val TEST_LOCAL_ID = "1234"
    }


    private val mockedStorage = mock(StorageProtocol::class.java)
    private val mockedPrefs = mock(TextSecurePreferences::class.java)
    private val mockedRecipient = mock(Recipient::class.java)

    private val viewModel = ConversationSettingsViewModel(TEST_THREAD_ID, mockedStorage, mockedPrefs)

    @Before
    fun setup() {
        whenever(mockedStorage.getRecipientForThread(TEST_THREAD_ID)).thenReturn(mockedRecipient)
    }

    @Test
    fun `it should get a mocked recipient`() {
        assertEquals(mockedRecipient, viewModel.recipient)
    }

    @Test
    fun `it should report correct pin status`() {
        whenever(mockedStorage.isPinned(TEST_THREAD_ID)).thenReturn(true)
        val pinStatus = viewModel.isPinned()
        verify(mockedStorage).isPinned(TEST_THREAD_ID)
        assertTrue(pinStatus)
    }

    @Test
    fun `it should auto download attachments`() {
        whenever(mockedStorage.shouldAutoDownloadAttachments(mockedRecipient)).thenReturn(true)
        val shouldDownload = viewModel.autoDownloadAttachments()
        verify(mockedStorage).shouldAutoDownloadAttachments(mockedRecipient)
        assertTrue(shouldDownload)
    }

    @Test
    fun `it should not auto download if recipient null`() {
        whenever(mockedStorage.getRecipientForThread(TEST_THREAD_ID)).thenReturn(null)
        val shouldDownload = viewModel.autoDownloadAttachments()
        verify(mockedStorage, never()).shouldAutoDownloadAttachments(anyOrNull())
        assertFalse(shouldDownload)
    }

    @Test
    fun `it should call storage for if user is an admin`() {
        val groupAddress = Address.fromSerialized("__textsecure_group__!1234")
        whenever(mockedRecipient.isLegacyClosedGroupRecipient).thenReturn(true)
        whenever(mockedRecipient.address).thenReturn(groupAddress)
        whenever(mockedPrefs.getLocalNumber()).thenReturn(TEST_LOCAL_ID)
        val mockedGroup = mock(GroupRecord::class.java).apply {
            whenever(this.admins).thenReturn(listOf(Address.fromSerialized(TEST_LOCAL_ID)))
        }
        whenever(mockedStorage.getGroup(groupAddress.serialize())).thenReturn(mockedGroup)
        val isUserAdmin = viewModel.isUserGroupAdmin()
        assertTrue(isUserAdmin)
    }

    @Test
    fun `it should not call storage for group admin when we aren't in a group`() {
        whenever(mockedRecipient.isLegacyClosedGroupRecipient).thenReturn(false)
        val isUserAdmin = viewModel.isUserGroupAdmin()
        assertFalse(isUserAdmin)
    }

    @Test
    fun `it should `() {

    }

}