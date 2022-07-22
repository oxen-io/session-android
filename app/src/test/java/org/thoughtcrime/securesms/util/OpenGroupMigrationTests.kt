package org.thoughtcrime.securesms.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.LokiAPIDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.OpenGroupMigrator
import org.thoughtcrime.securesms.groups.OpenGroupMigrator.OpenGroupMapping
import org.thoughtcrime.securesms.groups.OpenGroupMigrator.roomStub

class OpenGroupMigrationTests {

    companion object {
        const val EXAMPLE_LEGACY_ENCODED_OPEN_GROUP = "__loki_public_chat_group__!687474703a2f2f3131362e3230332e37302e33332e6f78656e"
        const val EXAMPLE_NEW_ENCODED_OPEN_GROUP = "__loki_public_chat_group__!68747470733a2f2f6f70656e2e67657473657373696f6e2e6f72672e6f78656e"
        const val OXEN_STUB_HEX = "6f78656e"

        const val EXAMPLE_LEGACY_SERVER_ID = "http://116.203.70.33.oxen"
        const val EXAMPLE_NEW_SERVER_ID = "https://open.getsession.org.oxen"

        const val LEGACY_THREAD_ID = 1L
        const val NEW_THREAD_ID = 2L
    }

    private fun legacyOpenGroupRecipient(additionalMocks: ((KStubbing<Recipient>) -> Unit) ? = null) = mock<Recipient> {
        on { address } doReturn Address.fromSerialized(EXAMPLE_LEGACY_ENCODED_OPEN_GROUP)
        on { isOpenGroupRecipient } doReturn true
        additionalMocks?.let { it(this) }
    }

    private fun newOpenGroupRecipient(additionalMocks: ((KStubbing<Recipient>) -> Unit) ? = null) = mock<Recipient> {
        on { address } doReturn Address.fromSerialized(EXAMPLE_NEW_ENCODED_OPEN_GROUP)
        on { isOpenGroupRecipient } doReturn true
        additionalMocks?.let { it(this) }
    }

    private fun legacyThreadRecord(additionalRecipientMocks: ((KStubbing<Recipient>) -> Unit) ? = null, additionalThreadMocks: ((KStubbing<ThreadRecord>) -> Unit)? = null) = mock<ThreadRecord> {
        val returnedRecipient = legacyOpenGroupRecipient(additionalRecipientMocks)
        on { recipient } doReturn returnedRecipient
        on { threadId } doReturn LEGACY_THREAD_ID
    }

    private fun newThreadRecord(additionalRecipientMocks: ((KStubbing<Recipient>) -> Unit)? = null, additionalThreadMocks: ((KStubbing<ThreadRecord>) -> Unit)? = null) = mock<ThreadRecord> {
        val returnedRecipient = newOpenGroupRecipient(additionalRecipientMocks)
        on { recipient } doReturn returnedRecipient
        on { threadId } doReturn NEW_THREAD_ID
    }

    @Test
    fun `it should generate the correct room stubs for legacy groups`() {
        val mockRecipient = legacyOpenGroupRecipient()
        assertEquals(OXEN_STUB_HEX, mockRecipient.roomStub())
    }

    @Test
    fun `it should generate the correct room stubs for new groups`() {
        val mockNewRecipient = newOpenGroupRecipient()
        assertEquals(OXEN_STUB_HEX, mockNewRecipient.roomStub())
    }

    @Test
    fun `it should return correct mappings`() {
        val legacyThread = legacyThreadRecord()
        val newThread = newThreadRecord()

        val expectedMapping = listOf(
            OpenGroupMapping("oxen", LEGACY_THREAD_ID, NEW_THREAD_ID)
        )

        assertTrue(expectedMapping.containsAll(OpenGroupMigrator.getExistingMappings(listOf(legacyThread), listOf(newThread))))
    }

    @Test
    fun `it should return no mappings if there are no legacy open groups`() {
        val mappings = OpenGroupMigrator.getExistingMappings(listOf(), listOf())
        assertTrue(mappings.isEmpty())
    }

    @Test
    fun `it should return no mappings if there are only new open groups`() {
        val newThread = newThreadRecord()
        val mappings = OpenGroupMigrator.getExistingMappings(emptyList(), listOf(newThread))
        assertTrue(mappings.isEmpty())
    }

    @Test
    fun `it should return null new thread in mappings if there are only legacy open groups`() {
        val legacyThread = legacyThreadRecord()
        val mappings = OpenGroupMigrator.getExistingMappings(listOf(legacyThread), emptyList())
        val expectedMappings = listOf(
            OpenGroupMapping("oxen", LEGACY_THREAD_ID, null)
        )
        assertTrue(expectedMappings.containsAll(mappings))
    }

    @Test
    fun `test migration thread DB calls legacy and returns if no legacy official groups`() {
        val mockedThreadDb = mock<ThreadDatabase> {
            on { legacyOxenOpenGroups } doReturn emptyList()
        }
        val mockedDbComponent = mock<DatabaseComponent> {
            on { threadDatabase() } doReturn mockedThreadDb
        }

        OpenGroupMigrator.migrate(mockedDbComponent)

        verify(mockedDbComponent).threadDatabase()
        verify(mockedThreadDb).legacyOxenOpenGroups
        verifyNoMoreInteractions(mockedThreadDb)
    }

    @Test
    fun `test migration thread DB calls legacy and new open groups if non-zero legacy list`() {

        // mock threadDB
        val mockedThreadDb = mock<ThreadDatabase> {
            val legacyThreadRecord = legacyThreadRecord()
            on { legacyOxenOpenGroups } doReturn listOf(legacyThreadRecord)
            on { newOxenOpenGroups } doReturn emptyList()
            on { migrateEncodedGroup(any(), any()) } doAnswer {}
        }

        // mock groupDB
        val mockedGroupDb = mock<GroupDatabase> {
            on { migrateEncodedGroup(any(), any()) } doAnswer {}
        }

        // mock LokiAPIDB
        val mockedLokiApi = mock<LokiAPIDatabase> {
            on { migrateLegacyOpenGroup(any(), any()) } doAnswer {}
        }

        val mockedDbComponent = mock<DatabaseComponent> {
            on { threadDatabase() } doReturn mockedThreadDb
            on { groupDatabase() } doReturn mockedGroupDb
            on { lokiAPIDatabase() } doReturn mockedLokiApi
        }

        OpenGroupMigrator.migrate(mockedDbComponent)

        verify(mockedDbComponent).threadDatabase()
        verify(mockedThreadDb).legacyOxenOpenGroups
        verify(mockedThreadDb).newOxenOpenGroups
        verify(mockedThreadDb).migrateEncodedGroup(any(), any())
        verifyNoMoreInteractions(mockedThreadDb)
    }



}