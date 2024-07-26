package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DraftDatabase extends Database {

  public static final String TABLE_NAME  = "drafts";
  public static final String ID          = "_id";
  public static final String THREAD_ID   = "thread_id";
  public static final String DRAFT_TYPE  = "type";
  public static final String DRAFT_VALUE = "value";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
          THREAD_ID + " INTEGER, " + DRAFT_TYPE + " TEXT, " + DRAFT_VALUE + " TEXT);";

  public static final String[] CREATE_INDEXS = {
          "CREATE INDEX IF NOT EXISTS draft_thread_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
  };

  public DraftDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public void insertDrafts(long threadId, List<Draft> drafts) {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();

    for (Draft draft : drafts) {
      ContentValues values = new ContentValues(3);
      values.put(THREAD_ID, threadId);
      values.put(DRAFT_TYPE, draft.getType());
      values.put(DRAFT_VALUE, draft.getValue());

      db.insert(TABLE_NAME, null, values);
    }
  }

  public void clearDrafts(long threadId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, THREAD_ID + " = ?", new String[] {threadId+""});
  }

  void clearDrafts(Set<Long> threadIds) {
    SQLiteDatabase db        = databaseHelper.getWritableDatabase();
    StringBuilder  where     = new StringBuilder();
    List<String>   arguments = new LinkedList<>();

    for (long threadId : threadIds) {
      where.append(" OR ")
              .append(THREAD_ID)
              .append(" = ?");

      arguments.add(String.valueOf(threadId));
    }

    db.delete(TABLE_NAME, where.toString().substring(4), arguments.toArray(new String[0]));
  }

  void clearAllDrafts() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }

  public List<Draft> getDrafts(long threadId) {
    SQLiteDatabase db   = databaseHelper.getReadableDatabase();
    List<Draft> results = new LinkedList<>();
    Cursor cursor       = null;

    try {
      cursor = db.query(TABLE_NAME, null, THREAD_ID + " = ?", new String[] {threadId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        String type  = cursor.getString(cursor.getColumnIndexOrThrow(DRAFT_TYPE));
        String value = cursor.getString(cursor.getColumnIndexOrThrow(DRAFT_VALUE));

        results.add(new Draft(type, value));
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  // Class to save drafts of text (only) messages if the user is in the middle of writing a message
  // and then the app loses focus or is closed.
  public static class Draft {
    public static final String TEXT = "text";

    private final String type;
    private final String value;

    public Draft(String type, String value) {
      this.type  = type;
      this.value = value;
    }

    public String getType() {
      return type;
    }

    public String getValue() {
      return value;
    }
  }

  public static class Drafts extends LinkedList<Draft> {
    // We don't do anything with drafts of a given type anymore (image, audio etc.) - we store TEXT
    // drafts, and any files or audio get sent to the recipient when added as a message.
  }
}