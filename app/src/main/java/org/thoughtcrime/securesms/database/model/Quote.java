package org.thoughtcrime.securesms.database.model;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel;
import org.session.libsession.utilities.Address;
import org.thoughtcrime.securesms.mms.SlideDeck;

import java.util.Objects;

public class Quote {

  private final long      id;
  private final Address   author;
  private final String    text;
  private final boolean   missing;
  private final SlideDeck attachment;

  public Quote(long id, @NonNull Address author, @Nullable String text, boolean missing, @NonNull SlideDeck attachment) {
    this.id         = id;
    this.author     = author;
    this.text       = text;
    this.missing    = missing;
    this.attachment = attachment;
  }

  public long getId() {
    return id;
  }

  public @NonNull Address getAuthor() {
    return author;
  }

  public @Nullable String getText() {
    return text;
  }

  public boolean isOriginalMissing() {
    return missing;
  }

  public @NonNull SlideDeck getAttachment() {
    return attachment;
  }

  public QuoteModel getQuoteModel() {
    return new QuoteModel(id, author, text, missing, attachment.asAttachments());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Quote quote = (Quote) o;
    return id == quote.id && missing == quote.missing && Objects.equals(author, quote.author) && Objects.equals(text, quote.text) && Objects.equals(attachment, quote.attachment);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, author, text, missing, attachment);
  }
}
