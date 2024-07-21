/*
 * Copyright (C) 2012 Moxie Marlinspike
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database.model

import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.mms.SlideDeck

/**
 * Represents the message record model for MMS messages that are
 * notifications (ie: they're pointers to undownloaded media).
 *
 * @author Moxie Marlinspike
 */
class NotificationMmsMessageRecord(
    id: Long, conversationRecipient: Recipient?,
    individualRecipient: Recipient?,
    dateSent: Long,
    dateReceived: Long,
    deliveryReceiptCount: Int,
    threadId: Long,
    private val messageSize: Long,
    private val expiry: Long,
    val status: Int,
    mailbox: Long,
    slideDeck: SlideDeck?,
    readReceiptCount: Int,
    hasMention: Boolean
) : MmsMessageRecord(
    id, "", conversationRecipient, individualRecipient,
    dateSent, dateReceived, threadId, SmsDatabase.Status.STATUS_NONE, deliveryReceiptCount, mailbox,
    emptyList(), emptyList(),
    0, 0, slideDeck!!, readReceiptCount, null, emptyList(), emptyList(), false, emptyList(), hasMention
) {
    fun getMessageSize(): Long {
        return (messageSize + 1023) / 1024
    }

    val expiration: Long
        get() = expiry * 1000

    override fun isOutgoing(): Boolean {
        return false
    }

    override fun isPending(): Boolean {
        return false
    }

    override fun isMmsNotification(): Boolean {
        return true
    }

    override fun isMediaPending(): Boolean {
        return true
    }
}
