package org.thoughtcrime.securesms.util

import android.content.Context
import org.session.libsession.messaging.utilities.BlindedIdMapping
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.dependencies.DatabaseComponent

object ContactUtilities {

    @JvmStatic
    fun getAllContacts(context: Context): Set<Recipient> {
        val threadDatabase = DatabaseComponent.get(context).threadDatabase()
        val cursor = threadDatabase.conversationList
        val result = mutableSetOf<Recipient>()
        threadDatabase.readerFor(cursor).use { reader ->
            while (reader.next != null) {
                val thread = reader.current
                val recipient = thread.recipient
                result.add(recipient)
            }
        }
        return result
    }

    fun getBlindedIdMapping(
        context: Context,
        blindedSessionId: String,
        serverPublicKey: String
    ): BlindedIdMapping? {
        val threadDatabase = DatabaseComponent.get(context).threadDatabase()
        val cursor = threadDatabase.approvedConversationList
        threadDatabase.readerFor(cursor).use { reader ->
            while (reader.next != null) {
                val recipient = reader.current.recipient
                val sessionId = recipient.address.serialize()
                if (!recipient.isGroupRecipient && SodiumUtilities.sessionId(sessionId, blindedSessionId, serverPublicKey)) {
                    return BlindedIdMapping(blindedSessionId, sessionId, serverPublicKey)
                }
            }
        }
        return null
    }
}