package org.thoughtcrime.securesms.search;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MergeCursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.session.libsession.messaging.contacts.Contact;
import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.GroupRecord;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.database.CursorList;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SearchDatabase;
import org.thoughtcrime.securesms.database.SessionContactDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.search.model.MessageResult;
import org.thoughtcrime.securesms.search.model.SearchResult;
import org.thoughtcrime.securesms.util.Stopwatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import kotlin.Pair;

/**
 * Manages data retrieval for search.
 */
public class SearchRepository {

  private static final String TAG = SearchRepository.class.getSimpleName();

  private static final Set<Character> BANNED_CHARACTERS = new HashSet<>();
  static {
    // Several ranges of invalid ASCII characters
    for (int i = 33; i <= 47; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
    for (int i = 58; i <= 64; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
    for (int i = 91; i <= 96; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
    for (int i = 123; i <= 126; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
  }

  private final Context                context;
  private final SearchDatabase         searchDatabase;
  private final ThreadDatabase         threadDatabase;
  private final GroupDatabase          groupDatabase;
  private final SessionContactDatabase contactDatabase;
  private final ContactAccessor        contactAccessor;
  private final Executor               executor;

  public SearchRepository(@NonNull Context context,
                          @NonNull SearchDatabase searchDatabase,
                          @NonNull ThreadDatabase threadDatabase,
                          @NonNull GroupDatabase groupDatabase,
                          @NonNull SessionContactDatabase contactDatabase,
                          @NonNull ContactAccessor contactAccessor,
                          @NonNull Executor executor)
  {
    this.context          = context.getApplicationContext();
    this.searchDatabase   = searchDatabase;
    this.threadDatabase   = threadDatabase;
    this.groupDatabase    = groupDatabase;
    this.contactDatabase  = contactDatabase;
    this.contactAccessor  = contactAccessor;
    this.executor         = executor;
  }

  public void query(@NonNull String query, @NonNull Callback<SearchResult> callback) {
    if (TextUtils.isEmpty(query)) {
      callback.onResult(SearchResult.EMPTY);
      return;
    }

    executor.execute(() -> {
      Stopwatch timer = new Stopwatch("FtsQuery");

      String cleanQuery = sanitizeQuery(query);
      timer.split("clean");

      Pair<CursorList<Contact>, List<String>> contacts = queryContacts(cleanQuery);
      timer.split("contacts");

      CursorList<ThreadRecord> conversations = queryConversations(cleanQuery, contacts.getSecond());
      timer.split("conversations");

      CursorList<MessageResult> messages = queryMessages(cleanQuery);
      timer.split("messages");

      timer.stop(TAG);

      callback.onResult(new SearchResult(cleanQuery, contacts.getFirst(), conversations, messages));
    });
  }

  public void query(@NonNull String query, long threadId, @NonNull Callback<CursorList<MessageResult>> callback) {
    if (TextUtils.isEmpty(query)) {
      callback.onResult(CursorList.emptyList());
      return;
    }

    executor.execute(() -> {
      long startTime = System.currentTimeMillis();
      CursorList<MessageResult> messages = queryMessages(sanitizeQuery(query), threadId);
      Log.d(TAG, "[ConversationQuery] " + (System.currentTimeMillis() - startTime) + " ms");

      callback.onResult(messages);
    });
  }

  private Pair<CursorList<Contact>, List<String>> queryContacts(String query) {

    Cursor contacts = contactDatabase.queryContactsByName(query);
    List<Address> contactList = new ArrayList<>();
    List<String> contactStrings = new ArrayList<>();

    while (contacts.moveToNext()) {
      try {
        Contact contact = contactDatabase.contactFromCursor(contacts);
        String contactSessionId = contact.getSessionID();
        Address address = Address.fromSerialized(contactSessionId);
        contactList.add(address);
        contactStrings.add(contactSessionId);
      } catch (Exception e) {
        Log.e("Loki", "Error building Contact from cursor in query", e);
      }
    }

    contacts.close();

    Cursor individualRecipients = threadDatabase.getFilteredConversationList(contactList);
    if (individualRecipients == null) {
      return new Pair<>(CursorList.emptyList(),contactStrings);
    }

    return new Pair<>(new CursorList<>(individualRecipients, new ContactModelBuilder(contactDatabase, threadDatabase)), contactStrings);

  }

  private CursorList<ThreadRecord> queryConversations(@NonNull String query, List<String> matchingAddresses) {
    List<String>  numbers   = contactAccessor.getNumbersForThreadSearchFilter(context, query);
    Set<Address> addresses = new HashSet<>(Stream.of(numbers).map(number -> Address.fromExternal(context, number)).toList());

    Cursor membersGroupList = groupDatabase.getGroupsFilteredByMembers(matchingAddresses);
    if (membersGroupList != null) {
      GroupDatabase.Reader reader = new GroupDatabase.Reader(membersGroupList);
      while (membersGroupList.moveToNext()) {
        GroupRecord record = reader.getCurrent();
        if (record == null) continue;

        addresses.add(Address.fromSerialized(record.getEncodedId()));
      }
      membersGroupList.close();
    }


    Cursor conversations = threadDatabase.getFilteredConversationList(new ArrayList<>(addresses));

    return conversations != null ? new CursorList<>(conversations, new ThreadModelBuilder(threadDatabase))
            : CursorList.emptyList();
  }

  private CursorList<MessageResult> queryMessages(@NonNull String query) {
    Cursor messages = searchDatabase.queryMessages(query);
    return messages != null ? new CursorList<>(messages, new MessageModelBuilder(context))
                            : CursorList.emptyList();
  }

  private CursorList<MessageResult> queryMessages(@NonNull String query, long threadId) {
    Cursor messages = searchDatabase.queryMessages(query, threadId);
    return messages != null ? new CursorList<>(messages, new MessageModelBuilder(context))
                            : CursorList.emptyList();
  }

  /**
   * Unfortunately {@link DatabaseUtils#sqlEscapeString(String)} is not sufficient for our purposes.
   * MATCH queries have a separate format of their own that disallow most "special" characters.
   *
   * Also, SQLite can't search for apostrophes, meaning we can't normally find words like "I'm".
   * However, if we replace the apostrophe with a space, then the query will find the match.
   */
  private String sanitizeQuery(@NonNull String query) {
    StringBuilder out = new StringBuilder();

    for (int i = 0; i < query.length(); i++) {
      char c = query.charAt(i);
      if (!BANNED_CHARACTERS.contains(c)) {
        out.append(c);
      } else if (c == '\'') {
        out.append(' ');
      }
    }

    return out.toString();
  }

  private static class ContactModelBuilder implements CursorList.ModelBuilder<Contact> {

    private final SessionContactDatabase contactDb;
    private final ThreadDatabase threadDb;

    public ContactModelBuilder(SessionContactDatabase contactDb, ThreadDatabase threadDb) {
      this.contactDb = contactDb;
      this.threadDb = threadDb;
    }

    @Override
    public Contact build(@NonNull Cursor cursor) {
      ThreadRecord threadRecord = threadDb.readerFor(cursor).getCurrent();
      return contactDb.getContactWithSessionID(threadRecord.getRecipient().getAddress().serialize());
    }
  }

  private static class RecipientModelBuilder implements CursorList.ModelBuilder<Recipient> {

    private final Context context;

    RecipientModelBuilder(@NonNull Context context) {
      this.context = context;
    }

    @Override
    public Recipient build(@NonNull Cursor cursor) {
      Address address = Address.fromExternal(context, cursor.getString(1));
      return Recipient.from(context, address, false);
    }
  }

  private static class ThreadModelBuilder implements CursorList.ModelBuilder<ThreadRecord> {

    private final ThreadDatabase threadDatabase;

    ThreadModelBuilder(@NonNull ThreadDatabase threadDatabase) {
      this.threadDatabase = threadDatabase;
    }

    @Override
    public ThreadRecord build(@NonNull Cursor cursor) {
      return threadDatabase.readerFor(cursor).getCurrent();
    }
  }

  private static class MessageModelBuilder implements CursorList.ModelBuilder<MessageResult> {

    private final Context context;

    MessageModelBuilder(@NonNull Context context) {
      this.context = context;
    }

    @Override
    public MessageResult build(@NonNull Cursor cursor) {
      Address   conversationAddress   = Address.fromSerialized(cursor.getString(cursor.getColumnIndex(SearchDatabase.CONVERSATION_ADDRESS)));
      Address   messageAddress        = Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(SearchDatabase.MESSAGE_ADDRESS)));
      Recipient conversationRecipient = Recipient.from(context, conversationAddress, false);
      Recipient messageRecipient      = Recipient.from(context, messageAddress, false);
      String    body                  = cursor.getString(cursor.getColumnIndexOrThrow(SearchDatabase.SNIPPET));
      long      receivedMs            = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.NORMALIZED_DATE_RECEIVED));
      long      threadId              = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.THREAD_ID));

      return new MessageResult(conversationRecipient, messageRecipient, body, threadId, receivedMs);
    }
  }

  public interface Callback<E> {
    void onResult(@NonNull E result);
  }
}
