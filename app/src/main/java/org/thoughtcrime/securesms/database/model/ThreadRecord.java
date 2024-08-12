/*
 * Copyright (C) 2012 Moxie Marlinspike
 * Copyright (C) 2013-2017 Open Whisper Systems
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

import static org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY;
import static org.session.libsession.utilities.StringSubstitutionConstants.DISAPPEARING_MESSAGES_TYPE_KEY;
import static org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY;
import static org.session.libsession.utilities.StringSubstitutionConstants.TIME_KEY;

import android.content.Context;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.squareup.phrase.Phrase;
import org.session.libsession.utilities.ExpirationUtil;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import network.loki.messenger.R;

/**
 * The message record model which represents thread heading messages.
 *
 * @author Moxie Marlinspike
 *
 */
public class ThreadRecord extends DisplayRecord {

    private @Nullable final Uri     snippetUri;
    public @Nullable  final MessageRecord lastMessage;
    private           final long    count;
    private           final int     unreadCount;
    private           final int     unreadMentionCount;
    private           final int     distributionType;
    private           final boolean archived;
    private           final long    expiresIn;
    private           final long    lastSeen;
    private           final boolean pinned;
    private           final int     initialRecipientHash;
    private           final long    dateSent;

    public ThreadRecord(@NonNull String body, @Nullable Uri snippetUri,
                        @Nullable MessageRecord lastMessage, @NonNull Recipient recipient, long date, long count, int unreadCount,
                        int unreadMentionCount, long threadId, int deliveryReceiptCount, int status,
                        long snippetType,  int distributionType, boolean archived, long expiresIn,
                        long lastSeen, int readReceiptCount, boolean pinned)
    {
        super(body, recipient, date, date, threadId, status, deliveryReceiptCount, snippetType, readReceiptCount);
        this.snippetUri           = snippetUri;
        this.lastMessage          = lastMessage;
        this.count                = count;
        this.unreadCount          = unreadCount;
        this.unreadMentionCount   = unreadMentionCount;
        this.distributionType     = distributionType;
        this.archived             = archived;
        this.expiresIn            = expiresIn;
        this.lastSeen             = lastSeen;
        this.pinned               = pinned;
        this.initialRecipientHash = recipient.hashCode();
        this.dateSent             = date;
    }

    public @Nullable Uri getSnippetUri() {
        return snippetUri;
    }

    private String getName() {
        String name = getRecipient().getName();
        if (name == null) {
            Log.w("ThreadRecord", "Got a null name - using: Unknown");
            name = "Unknown";
        }
        return name;
    }

    private String getDisappearingMsgExpiryTypeString(Context context) {
        MessageRecord lm = this.lastMessage;
        if (lm == null) {
            Log.w("ThreadRecord", "Could not get last message to determine disappearing msg type.");
            return "Unknown";
        }
        long expireStarted = lm.getExpireStarted();

        // Note: This works because expireStarted is 0 for messages which are 'Disappear after read'
        // while it's a touch higher than the sent timestamp for "Disappear after send". We could then
        // use `expireStarted == 0`, but that's not how it's done in UpdateMessageBuilder so to keep
        // things the same I'll assume there's a reason for this and follow suit.
        // Also: `this.lastMessage.getExpiresIn()` is available.
        if (expireStarted >= dateSent) {
            return context.getString(R.string.disappearingMessagesSent);
        }
        return context.getString(R.string.read);
    }

    @Override
    public SpannableString getDisplayBody(@NonNull Context context) {
        if (isGroupUpdateMessage()) {
            return emphasisAdded(context.getString(R.string.groupUpdated));
        } else if (isOpenGroupInvitation()) {
            return emphasisAdded(context.getString(R.string.communityInvitation));
        } else if (MmsSmsColumns.Types.isLegacyType(type)) {
            String txt = Phrase.from(context, R.string.messageErrorOld)
                    .put(APP_NAME_KEY, context.getString(R.string.app_name))
                    .format().toString();
            return emphasisAdded(txt);
        } else if (MmsSmsColumns.Types.isDraftMessageType(type)) {
            String draftText = context.getString(R.string.draft);
            return emphasisAdded(draftText + " " + getBody(), 0, draftText.length());
        } else if (SmsDatabase.Types.isOutgoingCall(type)) {
            String txt = Phrase.from(context, R.string.callsYouCalled)
                    .put(NAME_KEY, getName())
                    .format().toString();
            return emphasisAdded(txt);
        } else if (SmsDatabase.Types.isIncomingCall(type)) {
            String txt = Phrase.from(context, R.string.callsCalledYou)
                    .put(NAME_KEY, getName())
                    .format().toString();
            return emphasisAdded(txt);
        } else if (SmsDatabase.Types.isMissedCall(type)) {
            String txt = Phrase.from(context, R.string.callsMissedCallFrom)
                    .put(NAME_KEY, getName())
                    .format().toString();
            return emphasisAdded(txt);
        } else if (SmsDatabase.Types.isExpirationTimerUpdate(type)) {
            int seconds = (int) (getExpiresIn() / 1000);
            if (seconds <= 0) {
                String txt = Phrase.from(context, R.string.disappearingMessagesTurnedOff)
                        .put(NAME_KEY, getName())
                        .format().toString();
                return emphasisAdded(txt);
            }

            // Implied that disappearing messages is enabled..
            String time = ExpirationUtil.getExpirationDisplayValue(context, seconds);
            String disappearAfterWhat = getDisappearingMsgExpiryTypeString(context); // Disappear after send or read?
            String txt = Phrase.from(context, R.string.disappearingMessagesSet)
                    .put(NAME_KEY, getName())
                    .put(TIME_KEY, time)
                    .put(DISAPPEARING_MESSAGES_TYPE_KEY, disappearAfterWhat)
                    .format().toString();
            return emphasisAdded(txt);

        } else if (MmsSmsColumns.Types.isMediaSavedExtraction(type)) {
            String txt = Phrase.from(context, R.string.attachmentsMediaSaved)
                    .put(NAME_KEY, getName())
                    .format().toString();
            return emphasisAdded(txt);

        } else if (MmsSmsColumns.Types.isScreenshotExtraction(type)) {
            String txt = Phrase.from(context, R.string.screenshotTaken)
                    .put(NAME_KEY, getName())
                    .format().toString();
            return emphasisAdded(txt);

        } else if (MmsSmsColumns.Types.isMessageRequestResponse(type)) {
            return emphasisAdded(context.getString(R.string.messageRequestsAccepted));
        } else if (getCount() == 0) {
            return new SpannableString(context.getString(R.string.messageEmpty));
        } else {
            // This block hits when we receive a media message from an unaccepted contact - however,
            // unaccepted contacts aren't allowed to send us media - so we'll return an empty string
            // if it's JUST an image, or the body text that accompanied the image should any exist.
            // We could return null here - but then we have to find all the usages of this
            // `getDisplayBody` method and make sure it doesn't fall over if it has a null result.
            if (TextUtils.isEmpty(getBody())) {
                return new SpannableString("");
                // Old behaviour was: return new SpannableString(emphasisAdded(context.getString(R.string.mediaMessage)));
            } else {
                return new SpannableString(getBody());
            }
        }
    }

    private SpannableString emphasisAdded(String sequence) {
        return emphasisAdded(sequence, 0, sequence.length());
    }

    private SpannableString emphasisAdded(String sequence, int start, int end) {
        SpannableString spannable = new SpannableString(sequence);
        spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    public long getCount()               { return count; }

    public int getUnreadCount()          { return unreadCount; }

    public int getUnreadMentionCount()   { return unreadMentionCount; }

    public long getDate()                { return getDateReceived(); }

    public boolean isArchived()          { return archived; }

    public int getDistributionType()     { return distributionType; }

    public long getExpiresIn()           { return expiresIn; }

    public long getLastSeen()            { return lastSeen; }

    public boolean isPinned()            { return pinned; }

    public int getInitialRecipientHash() { return initialRecipientHash; }
}
