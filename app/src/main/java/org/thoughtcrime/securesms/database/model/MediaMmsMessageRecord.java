/**
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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview;
import org.session.libsession.utilities.Contact;
import org.session.libsession.utilities.IdentityKeyMismatch;
import org.session.libsession.utilities.NetworkFailure;
import org.session.libsession.utilities.recipients.Recipient;
import org.thoughtcrime.securesms.database.SmsDatabase.Status;
import org.thoughtcrime.securesms.mms.SlideDeck;

import java.util.List;

/**
 * Represents the message record model for MMS messages that contain
 * media (ie: they've been downloaded).
 *
 * @author Moxie Marlinspike
 *
 */

public class MediaMmsMessageRecord extends MmsMessageRecord {
  private final int partCount;

  public MediaMmsMessageRecord(long id, Recipient conversationRecipient,
                               Recipient individualRecipient, int recipientDeviceId,
                               long dateSent, long dateReceived, int deliveryReceiptCount,
                               long threadId, String body,
                               @NonNull SlideDeck slideDeck,
                               int partCount, long mailbox,
                               List<IdentityKeyMismatch> mismatches,
                               List<NetworkFailure> failures, int subscriptionId,
                               long expiresIn, long expireStarted, int readReceiptCount,
                               @Nullable Quote quote, @NonNull List<Contact> contacts,
                               @NonNull List<LinkPreview> linkPreviews,
                               @NonNull List<ReactionRecord> reactions, boolean unidentified, boolean hasMention)
  {
    super(id, body, conversationRecipient, individualRecipient, dateSent,
      dateReceived, threadId, Status.STATUS_NONE, deliveryReceiptCount, mailbox, mismatches, failures,
      expiresIn, expireStarted, slideDeck, readReceiptCount, quote, contacts,
      linkPreviews, unidentified, reactions, hasMention);
    this.partCount = partCount;
  }

  public int getPartCount() {
    return partCount;
  }

  @Override
  public boolean isMmsNotification() {
    return false;
  }

  @Override
  public CharSequence getDisplayBody(@NonNull Context context) {
    return super.getDisplayBody(context);
  }
}
