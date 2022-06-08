package org.thoughtcrime.securesms.database.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.session.libsession.utilities.Contact;
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.IdentityKeyMismatch;
import org.session.libsession.utilities.NetworkFailure;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import java.util.LinkedList;
import java.util.List;

public abstract class MmsMessageRecord extends MessageRecord {
  private final @NonNull  SlideDeck         slideDeck;
  private final @Nullable Quote             quote;
  private final @Nullable ReactionRecord    reaction;
  private final @NonNull  List<Contact>     contacts     = new LinkedList<>();
  private final @NonNull  List<LinkPreview> linkPreviews = new LinkedList<>();

  MmsMessageRecord(long id, String body, Recipient conversationRecipient,
    Recipient individualRecipient, long dateSent,
    long dateReceived, long threadId, int deliveryStatus, int deliveryReceiptCount,
    long type, List<IdentityKeyMismatch> mismatches,
    List<NetworkFailure> networkFailures, long expiresIn,
    long expireStarted, @NonNull SlideDeck slideDeck, int readReceiptCount,
    @Nullable Quote quote, @Nullable ReactionRecord reaction, @NonNull List<Contact> contacts,
    @NonNull List<LinkPreview> linkPreviews, boolean unidentified, List<ReactionRecord> reactions)
  {
    super(id, body, conversationRecipient, individualRecipient, dateSent, dateReceived, threadId, deliveryStatus, deliveryReceiptCount, type, mismatches, networkFailures, expiresIn, expireStarted, readReceiptCount, unidentified, reactions);
    this.slideDeck = slideDeck;
    this.quote     = quote;
    this.reaction  = reaction;
    this.contacts.addAll(contacts);
    this.linkPreviews.addAll(linkPreviews);
  }

  @Override
  public boolean isMms() {
    return true;
  }

  @NonNull
  public SlideDeck getSlideDeck() {
    return slideDeck;
  }

  @Override
  public boolean isMediaPending() {
    for (Slide slide : getSlideDeck().getSlides()) {
      if (slide.isInProgress() || slide.isPendingDownload()) {
        return true;
      }
    }

    return false;
  }

  public boolean containsMediaSlide() {
    return slideDeck.containsMediaSlide();
  }
  public @Nullable Quote getQuote() {
    return quote;
  }
  public @Nullable ReactionRecord getReaction() {
    return reaction;
  }
  public @NonNull List<Contact> getSharedContacts() {
    return contacts;
  }
  public @NonNull List<LinkPreview> getLinkPreviews() {
    return linkPreviews;
  }
}
