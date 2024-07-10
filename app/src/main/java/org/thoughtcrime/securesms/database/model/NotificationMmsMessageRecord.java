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
package org.thoughtcrime.securesms.database.model;

import static java.util.Collections.emptyList;

import android.content.Context;
import android.text.SpannableString;
import androidx.annotation.NonNull;
import org.session.libsession.utilities.recipients.Recipient;
import org.thoughtcrime.securesms.database.SmsDatabase.Status;
import org.thoughtcrime.securesms.mms.SlideDeck;

/**
 * Represents the message record model for MMS messages that are
 * notifications (ie: they're pointers to undownloaded media).
 *
 * @author Moxie Marlinspike
 *
 */
public class NotificationMmsMessageRecord extends MmsMessageRecord {
  private final byte[] contentLocation;
  private final long   messageSize;
  private final long   expiry;
  private final int    status;
  private final byte[] transactionId;

  // A single static final spannable string to prevent us having to allocate memory when returning it
  private static final SpannableString EMPTY_SS = new SpannableString("");

  public NotificationMmsMessageRecord(long id, Recipient conversationRecipient,
    Recipient individualRecipient,
    long dateSent, long dateReceived, int deliveryReceiptCount,
    long threadId, byte[] contentLocation, long messageSize,
    long expiry, int status, byte[] transactionId, long mailbox,
    SlideDeck slideDeck, int readReceiptCount, boolean hasMention)
  {
    super(id, "", conversationRecipient, individualRecipient,
      dateSent, dateReceived, threadId, Status.STATUS_NONE, deliveryReceiptCount, mailbox,
            emptyList(), emptyList(),
      0, 0, slideDeck, readReceiptCount, null, emptyList(), emptyList(), false, emptyList(), hasMention);

    this.contentLocation = contentLocation;
    this.messageSize     = messageSize;
    this.expiry          = expiry;
    this.status          = status;
    this.transactionId   = transactionId;
  }

  public byte[] getTransactionId() { return transactionId; }

  public int getStatus() { return this.status; }

  public byte[] getContentLocation() { return contentLocation; }

  public long getMessageSize() { return (messageSize + 1023) / 1024; }

  public long getExpiration() {  return expiry * 1000; }

  @Override
  public boolean isOutgoing() { return false; }

  @Override
  public boolean isPending() { return false; }

  @Override
  public boolean isMmsNotification() { return true; }

  @Override
  public boolean isMediaPending() { return true; }

  @Override
  public SpannableString getDisplayBody(@NonNull Context context) {
    // To the best of my knowledge and ability to trace through the code this never gets used in any
    // way that is ever displayed to the user. Previously, it would return:
    //
    //     - NotificationMmsMessageRecord_multimedia_message ("Multimedia message")
    //         - If `status` was DOWNLOAD_INITIALIZED,
    //     - NotificationMmsMessageRecord_downloading_mms_message ("Downloading MMS message")
    //         - if `status` was DOWNLOAD_CONNECTING, and
    //     - NotificationMmsMessageRecord_error_downloading_mms_message ("Error downloading MMS message, tap to retry")
    //         - otherwise.
    //
    // As i.) the user never sees these, ii.) the strings are flagged for removal in SS-40, and
    // iii.) SES-562 will provide updated attachment download controls - I'm going to delete the
    // above strings as part of SS-40 and return an empty SpannableString which is never seen or
    // used just to satisfy the method contract.
    return EMPTY_SS;
  }
}
