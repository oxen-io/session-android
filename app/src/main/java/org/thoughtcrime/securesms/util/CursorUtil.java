package org.thoughtcrime.securesms.util;

import android.database.Cursor;

import androidx.annotation.NonNull;


public final class CursorUtil {

  private CursorUtil() {}

  public static String requireString(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getString(cursor.getColumnIndexOrThrow(column));
  }

  public static int requireInt(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getInt(cursor.getColumnIndexOrThrow(column));
  }

  public static long requireLong(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getLong(cursor.getColumnIndexOrThrow(column));
  }

}
