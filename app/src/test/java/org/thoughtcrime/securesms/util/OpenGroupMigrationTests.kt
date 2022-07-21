package org.thoughtcrime.securesms.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.groups.OpenGroupMigrator.roomStub

class OpenGroupMigrationTests {

    companion object {
        const val EXAMPLE_LEGACY_OPEN_GROUP = "__loki_public_chat_group__!687474703a2f2f3131362e3230332e37302e33332e6f78656e"
        const val EXAMPLE_NEW_OPEN_GROUP = "__loki_public_chat_group__!68747470733a2f2f6f70656e2e67657473657373696f6e2e6f72672e6f78656e"
        const val OXEN_STUB_HEX = "6f78656e"
    }

    @Test
    fun `it should generate the correct room stubs for legacy groups`() {
        val mockRecipient = mock<Recipient> {
            on { address } doReturn Address.fromSerialized(EXAMPLE_LEGACY_OPEN_GROUP)
            on { isOpenGroupRecipient } doReturn true
        }
        assertEquals(OXEN_STUB_HEX, mockRecipient.roomStub())
    }

    @Test
    fun `it should generate the correct room stubs for new groups`() {
        val mockNewRecipient = mock<Recipient> {
            on { address } doReturn Address.fromSerialized(EXAMPLE_NEW_OPEN_GROUP)
            on { isOpenGroupRecipient } doReturn true
        }
        assertEquals(OXEN_STUB_HEX, mockNewRecipient.roomStub())
    }

    @Test
    fun `it should return correct mappings`() {
        TODO()
    }

    @Test
    fun `it should return no mappings if there are no legacy open groups`() {
        TODO()
    }

    @Test
    fun `it should return no mappings if there are only new open groups`() {
        TODO()
    }

}