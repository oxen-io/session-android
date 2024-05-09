/*
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.bumptech.glide.Glide;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId;
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress;
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment;
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachmentAudioExtras;
import org.session.libsession.utilities.MediaTypes;
import org.session.libsession.utilities.Util;
import org.session.libsignal.utilities.ExternalStorageUtil;
import org.session.libsignal.utilities.JsonUtil;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.MmsAttachmentInfo;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;
import org.thoughtcrime.securesms.mms.MediaStream;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MediaUtil.ThumbnailData;
import org.thoughtcrime.securesms.video.EncryptedMediaDataSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import kotlin.jvm.Synchronized;

public class AttachmentDatabase extends Database {
  
  private static final String TAG = AttachmentDatabase.class.getSimpleName();

  public  static final String TABLE_NAME             = "part";
  public  static final String ROW_ID                 = "_id";
          static final String ATTACHMENT_JSON_ALIAS  = "attachment_json";
  public  static final String MMS_ID                 = "mid";
          static final String CONTENT_TYPE           = "ct";
          static final String NAME                   = "name";
          static final String CONTENT_DISPOSITION    = "cd";
          static final String CONTENT_LOCATION       = "cl";
  public  static final String DATA                   = "_data";
          static final String TRANSFER_STATE         = "pending_push";
  public  static final String SIZE                   = "data_size";
          static final String FILE_NAME              = "file_name";
  public  static final String THUMBNAIL              = "thumbnail";
          static final String THUMBNAIL_ASPECT_RATIO = "aspect_ratio";
  public  static final String UNIQUE_ID              = "unique_id";
          static final String DIGEST                 = "digest";
          static final String VOICE_NOTE             = "voice_note";
          static final String QUOTE                  = "quote";
  public  static final String STICKER_PACK_ID        = "sticker_pack_id";
  public  static final String STICKER_PACK_KEY       = "sticker_pack_key";
          static final String STICKER_ID             = "sticker_id";
          static final String FAST_PREFLIGHT_ID      = "fast_preflight_id";
  public  static final String DATA_RANDOM            = "data_random";
  private static final String THUMBNAIL_RANDOM       = "thumbnail_random";
          static final String WIDTH                  = "width";
          static final String HEIGHT                 = "height";
          static final String CAPTION                = "caption";
  public  static final String URL                    = "url";
  public  static final String DIRECTORY              = "parts";
  // "audio/*" mime type only related columns.
          static final String AUDIO_VISUAL_SAMPLES   = "audio_visual_samples";  // Small amount of audio byte samples to visualise the content (e.g. draw waveform).
          static final String AUDIO_DURATION         = "audio_duration";        // Duration of the audio track in milliseconds.

  private static final String PART_ID_WHERE = ROW_ID + " = ? AND " + UNIQUE_ID + " = ?";
  private static final String ROW_ID_WHERE = ROW_ID + " = ?";
  private static final String PART_AUDIO_ONLY_WHERE = CONTENT_TYPE + " LIKE \"audio/%\"";

  private static final String[] PROJECTION = new String[] {ROW_ID,
                                                           MMS_ID, CONTENT_TYPE, NAME, CONTENT_DISPOSITION,
                                                           CONTENT_LOCATION, DATA, THUMBNAIL, TRANSFER_STATE,
                                                           SIZE, FILE_NAME, THUMBNAIL, THUMBNAIL_ASPECT_RATIO,
                                                           UNIQUE_ID, DIGEST, FAST_PREFLIGHT_ID, VOICE_NOTE,
                                                           QUOTE, DATA_RANDOM, THUMBNAIL_RANDOM, WIDTH, HEIGHT,
                                                           CAPTION, STICKER_PACK_ID, STICKER_PACK_KEY, STICKER_ID, URL};

  private static final String[] PROJECTION_AUDIO_EXTRAS = new String[] {AUDIO_VISUAL_SAMPLES, AUDIO_DURATION};

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ROW_ID + " INTEGER PRIMARY KEY, " +
    MMS_ID + " INTEGER, " + "seq" + " INTEGER DEFAULT 0, "                        +
    CONTENT_TYPE + " TEXT, " + NAME + " TEXT, " + "chset" + " INTEGER, "             +
    CONTENT_DISPOSITION + " TEXT, " + "fn" + " TEXT, " + "cid" + " TEXT, "  +
    CONTENT_LOCATION + " TEXT, " + "ctt_s" + " INTEGER, "                 +
    "ctt_t" + " TEXT, " + "encrypted" + " INTEGER, "                         +
    TRANSFER_STATE + " INTEGER, "+ DATA + " TEXT, " + SIZE + " INTEGER, "   +
    FILE_NAME + " TEXT, " + THUMBNAIL + " TEXT, " + THUMBNAIL_ASPECT_RATIO + " REAL, " +
    UNIQUE_ID + " INTEGER NOT NULL, " + DIGEST + " BLOB, " + FAST_PREFLIGHT_ID + " TEXT, " +
    VOICE_NOTE + " INTEGER DEFAULT 0, " + DATA_RANDOM + " BLOB, " + THUMBNAIL_RANDOM + " BLOB, " +
    QUOTE + " INTEGER DEFAULT 0, " + WIDTH + " INTEGER DEFAULT 0, " + HEIGHT + " INTEGER DEFAULT 0, " +
    CAPTION + " TEXT DEFAULT NULL, " + URL + " TEXT, " + STICKER_PACK_ID + " TEXT DEFAULT NULL, " +
    STICKER_PACK_KEY + " DEFAULT NULL, " + STICKER_ID + " INTEGER DEFAULT -1," +
    AUDIO_VISUAL_SAMPLES + " BLOB, " + AUDIO_DURATION + " INTEGER);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS part_mms_id_index ON " + TABLE_NAME + " (" + MMS_ID + ");",
    "CREATE INDEX IF NOT EXISTS pending_push_index ON " + TABLE_NAME + " (" + TRANSFER_STATE + ");",
    "CREATE INDEX IF NOT EXISTS part_sticker_pack_id_index ON " + TABLE_NAME + " (" + STICKER_PACK_ID + ");",
  };

  private final ExecutorService thumbnailExecutor = Util.newSingleThreadedLifoExecutor();

  private final AttachmentSecret attachmentSecret;

  public AttachmentDatabase(Context context, SQLCipherOpenHelper databaseHelper, AttachmentSecret attachmentSecret) {
    super(context, databaseHelper);
    this.attachmentSecret = attachmentSecret;
  }

  public @NonNull InputStream getAttachmentStream(AttachmentId attachmentId, long offset)
      throws IOException
  {
    InputStream dataStream = getDataStream(attachmentId, DATA, offset);

    if (dataStream == null) throw new IOException("No stream for: " + attachmentId);
    else                    return dataStream;
  }

  public @NonNull InputStream getThumbnailStream(@NonNull AttachmentId attachmentId)
      throws IOException
  {
    Log.d(TAG, "getThumbnailStream(" + attachmentId + ")");
    InputStream dataStream = getDataStream(attachmentId, THUMBNAIL, 0);

    if (dataStream != null) {
      return dataStream;
    }

    try {
      InputStream generatedStream = thumbnailExecutor.submit(new ThumbnailFetchCallable(attachmentId)).get();

      if (generatedStream == null) throw new FileNotFoundException("No thumbnail stream available: " + attachmentId);
      else                         return generatedStream;
    } catch (InterruptedException ie) {
      throw new AssertionError("interrupted");
    } catch (ExecutionException ee) {
      Log.w(TAG, ee);
      throw new IOException(ee);
    }
  }

  public void setTransferProgressFailed(AttachmentId attachmentId, long mmsId)
      throws MmsException
  {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues();
    values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED);

    database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings());
    notifyConversationListeners(DatabaseComponent.get(context).mmsDatabase().getThreadIdForMessage(mmsId));
  }

  public @Nullable DatabaseAttachment getAttachment(@NonNull AttachmentId attachmentId)
  {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, PROJECTION, ROW_ID_WHERE, new String[]{String.valueOf(attachmentId.getRowId())}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        List<DatabaseAttachment> list = getAttachment(cursor);

        if (list != null && list.size() > 0) {
          return list.get(0);
        }
      }

      return null;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public @NonNull List<DatabaseAttachment> getAttachmentsForMessage(long mmsId) {
    SQLiteDatabase           database = databaseHelper.getReadableDatabase();
    List<DatabaseAttachment> results  = new LinkedList<>();
    Cursor                   cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, PROJECTION, MMS_ID + " = ?", new String[] {mmsId+""},
                              null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        List<DatabaseAttachment> attachments = getAttachment(cursor);
        for (DatabaseAttachment attachment : attachments) {
          if (attachment.isQuote()) continue;
          results.add(attachment);
        }
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public @NonNull List<DatabaseAttachment> getPendingAttachments() {
    final SQLiteDatabase           database    = databaseHelper.getReadableDatabase();
    final List<DatabaseAttachment> attachments = new LinkedList<>();

    Cursor cursor = null;
    try {
      cursor = database.query(TABLE_NAME, PROJECTION, TRANSFER_STATE + " = ?", new String[] {String.valueOf(AttachmentTransferProgress.TRANSFER_PROGRESS_STARTED)}, null, null, null);
      while (cursor != null && cursor.moveToNext()) {
        attachments.addAll(getAttachment(cursor));
      }
    } finally {
      if (cursor != null) cursor.close();
    }

    return attachments;
  }

  void deleteAttachmentsForMessages(String[] messageIds) {
    StringBuilder queryBuilder = new StringBuilder();
    for (int i = 0; i < messageIds.length; i++) {
      queryBuilder.append(MMS_ID+" = ").append(messageIds[i]);
      if (i+1 < messageIds.length) {
        queryBuilder.append(" OR ");
      }
    }
    String idsAsString = queryBuilder.toString();
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor = null;
    List<MmsAttachmentInfo> attachmentInfos = new ArrayList<>();
    try {
      cursor = database.query(TABLE_NAME, new String[] { DATA, THUMBNAIL, CONTENT_TYPE}, idsAsString, null, null, null, null);
      while (cursor != null && cursor.moveToNext()) {
        attachmentInfos.add(new MmsAttachmentInfo(cursor.getString(0), cursor.getString(1), cursor.getString(2)));
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    deleteAttachmentsOnDisk(attachmentInfos);
    database.delete(TABLE_NAME, idsAsString, null);
    notifyAttachmentListeners();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  void deleteAttachmentsForMessage(long mmsId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {DATA, THUMBNAIL, CONTENT_TYPE}, MMS_ID + " = ?",
                              new String[] {mmsId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        deleteAttachmentOnDisk(cursor.getString(0), cursor.getString(1), cursor.getString(2));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    database.delete(TABLE_NAME, MMS_ID + " = ?", new String[] {mmsId + ""});
    notifyAttachmentListeners();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  void deleteAttachmentsForMessages(long[] mmsIds) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    Cursor cursor           = null;
    String mmsIdString = StringUtils.join(mmsIds, ',');

    try {
      cursor = database.query(TABLE_NAME, new String[] {DATA, THUMBNAIL, CONTENT_TYPE}, MMS_ID + " IN (?)",
              new String[] {mmsIdString}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        deleteAttachmentOnDisk(cursor.getString(0), cursor.getString(1), cursor.getString(2));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    database.delete(TABLE_NAME, MMS_ID + " IN (?)", new String[] {mmsIdString});
    notifyAttachmentListeners();
  }

  public void deleteAttachment(@NonNull AttachmentId id) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    try (Cursor cursor = database.query(TABLE_NAME,
                                        new String[]{DATA, THUMBNAIL, CONTENT_TYPE},
                                        PART_ID_WHERE,
                                        id.toStrings(),
                                        null,
                                        null,
                                        null))
    {
      if (cursor == null || !cursor.moveToNext()) {
        Log.w(TAG, "Tried to delete an attachment, but it didn't exist.");
        return;
      }
      String data        = cursor.getString(0);
      String thumbnail   = cursor.getString(1);
      String contentType = cursor.getString(2);

      database.delete(TABLE_NAME, PART_ID_WHERE, id.toStrings());
      deleteAttachmentOnDisk(data, thumbnail, contentType);
      notifyAttachmentListeners();
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  void deleteAllAttachments() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);

    File   attachmentsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
    File[] attachments          = attachmentsDirectory.listFiles();

    for (File attachment : attachments) {
      attachment.delete();
    }

    notifyAttachmentListeners();
  }

  private void deleteAttachmentsOnDisk(List<MmsAttachmentInfo> mmsAttachmentInfos) {
    for (MmsAttachmentInfo info : mmsAttachmentInfos) {
      if (info.getDataFile() != null && !TextUtils.isEmpty(info.getDataFile())) {
        File data = new File(info.getDataFile());
        if (data.exists()) {
          data.delete();
        }
      }
      if (info.getThumbnailFile() != null && !TextUtils.isEmpty(info.getThumbnailFile())) {
        File thumbnail = new File(info.getThumbnailFile());
        if (thumbnail.exists()) {
          thumbnail.delete();
        }
      }
    }

    boolean anyImageType = MmsAttachmentInfo.anyImages(mmsAttachmentInfos);
    boolean anyThumbnail = MmsAttachmentInfo.anyThumbnailNonNull(mmsAttachmentInfos);

    if (anyImageType || anyThumbnail) {
      Glide.get(context).clearDiskCache();
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void deleteAttachmentOnDisk(@Nullable String data, @Nullable String thumbnail, @Nullable String contentType) {
    if (!TextUtils.isEmpty(data)) {
      new File(data).delete();
    }

    if (!TextUtils.isEmpty(thumbnail)) {
      new File(thumbnail).delete();
    }

    if (MediaUtil.isImageType(contentType) || thumbnail != null) {
      Glide.get(context).clearDiskCache();
    }
  }

  public void insertAttachmentsForPlaceholder(long mmsId, @NonNull AttachmentId attachmentId, @NonNull InputStream inputStream)
      throws MmsException
  {
    DatabaseAttachment placeholder = getAttachment(attachmentId);
    SQLiteDatabase     database    = databaseHelper.getWritableDatabase();
    ContentValues      values      = new ContentValues();
    DataInfo           dataInfo    = setAttachmentData(inputStream);

    if (placeholder != null && placeholder.isQuote() && !placeholder.getContentType().startsWith("image")) {
      values.put(THUMBNAIL, dataInfo.file.getAbsolutePath());
      values.put(THUMBNAIL_RANDOM, dataInfo.random);
    } else {
      values.put(DATA, dataInfo.file.getAbsolutePath());
      values.put(SIZE, dataInfo.length);
      values.put(DATA_RANDOM, dataInfo.random);
    }

    values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_DONE);
    values.put(CONTENT_LOCATION, (String)null);
    values.put(CONTENT_DISPOSITION, (String)null);
    values.put(DIGEST, (byte[])null);
    values.put(NAME, (String) null);
    values.put(FAST_PREFLIGHT_ID, (String)null);
    values.put(URL, "");

    if (database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings()) == 0) {
      //noinspection ResultOfMethodCallIgnored
      dataInfo.file.delete();
    } else {
      notifyConversationListeners(DatabaseComponent.get(context).mmsDatabase().getThreadIdForMessage(mmsId));
      notifyConversationListListeners();
    }

    thumbnailExecutor.submit(new ThumbnailFetchCallable(attachmentId));
  }

  public void updateAttachmentAfterUploadSucceeded(@NonNull AttachmentId id, @NonNull Attachment attachment) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues();

    values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_DONE);
    values.put(CONTENT_LOCATION, attachment.getLocation());
    values.put(DIGEST, attachment.getDigest());
    values.put(CONTENT_DISPOSITION, attachment.getKey());
    values.put(NAME, attachment.getRelay());
    values.put(SIZE, attachment.getSize());
    values.put(FAST_PREFLIGHT_ID, attachment.getFastPreflightId());
    values.put(URL, attachment.getUrl());

    database.update(TABLE_NAME, values, PART_ID_WHERE, id.toStrings());
  }

  public void handleFailedAttachmentUpload(@NonNull AttachmentId id) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues();

    values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED);

    database.update(TABLE_NAME, values, PART_ID_WHERE, id.toStrings());
  }

  @NonNull Map<Attachment, AttachmentId> insertAttachmentsForMessage(long mmsId, @NonNull List<Attachment> attachments, @NonNull List<Attachment> quoteAttachment)
      throws MmsException
  {
    Log.d(TAG, "insertParts(" + attachments.size() + ")");

    Map<Attachment, AttachmentId> insertedAttachments = new HashMap<>();

    for (Attachment attachment : attachments) {
      AttachmentId attachmentId = insertAttachment(mmsId, attachment, attachment.isQuote());
      insertedAttachments.put(attachment, attachmentId);
      Log.i(TAG, "Inserted attachment at ID: " + attachmentId);
    }

    for (Attachment attachment : quoteAttachment) {
      AttachmentId attachmentId = insertAttachment(mmsId, attachment, true);
      insertedAttachments.put(attachment, attachmentId);
      Log.i(TAG, "Inserted quoted attachment at ID: " + attachmentId);
    }

    return insertedAttachments;
  }

  /**
   * Insert attachments in database and return the IDs of the inserted attachments
   *
   * @param mmsId message ID
   * @param attachments attachments to persist
   * @return IDs of the persisted attachments
   * @throws MmsException
   */
  @NonNull List<Long> insertAttachments(long mmsId, @NonNull List<Attachment> attachments)
          throws MmsException
  {
    Log.d(TAG, "insertParts(" + attachments.size() + ")");

    List<Long> insertedAttachmentsIDs = new LinkedList<>();

    for (Attachment attachment : attachments) {
      AttachmentId attachmentId = insertAttachment(mmsId, attachment, attachment.isQuote());
      insertedAttachmentsIDs.add(attachmentId.getRowId());
      Log.i(TAG, "Inserted attachment at ID: " + attachmentId);
    }

    return insertedAttachmentsIDs;
  }

  public @NonNull Attachment updateAttachmentData(@NonNull Attachment attachment,
                                                  @NonNull MediaStream mediaStream)
      throws MmsException
  {
    SQLiteDatabase     database           = databaseHelper.getWritableDatabase();
    DatabaseAttachment databaseAttachment = (DatabaseAttachment) attachment;
    DataInfo           dataInfo           = getAttachmentDataFileInfo(databaseAttachment.getAttachmentId(), DATA);

    if (dataInfo == null) {
      throw new MmsException("No attachment data found!");
    }

    dataInfo = setAttachmentData(dataInfo.file, mediaStream.getStream());

    ContentValues contentValues = new ContentValues();
    contentValues.put(SIZE, dataInfo.length);
    contentValues.put(CONTENT_TYPE, mediaStream.getMimeType());
    contentValues.put(WIDTH, mediaStream.getWidth());
    contentValues.put(HEIGHT, mediaStream.getHeight());
    contentValues.put(DATA_RANDOM, dataInfo.random);

    database.update(TABLE_NAME, contentValues, PART_ID_WHERE, databaseAttachment.getAttachmentId().toStrings());

    return new DatabaseAttachment(databaseAttachment.getAttachmentId(),
                                  databaseAttachment.getMmsId(),
                                  databaseAttachment.hasData(),
                                  databaseAttachment.hasThumbnail(),
                                  mediaStream.getMimeType(),
                                  databaseAttachment.getTransferState(),
                                  dataInfo.length,
                                  databaseAttachment.getFileName(),
                                  databaseAttachment.getLocation(),
                                  databaseAttachment.getKey(),
                                  databaseAttachment.getRelay(),
                                  databaseAttachment.getDigest(),
                                  databaseAttachment.getFastPreflightId(),
                                  databaseAttachment.isVoiceNote(),
                                  mediaStream.getWidth(),
                                  mediaStream.getHeight(),
                                  databaseAttachment.isQuote(),
                                  databaseAttachment.getCaption(),
                                  databaseAttachment.getUrl());
  }

  public void markAttachmentUploaded(long messageId, Attachment attachment) {
    ContentValues  values   = new ContentValues(1);
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_DONE);
    database.update(TABLE_NAME, values, PART_ID_WHERE, ((DatabaseAttachment)attachment).getAttachmentId().toStrings());

    notifyConversationListeners(DatabaseComponent.get(context).mmsDatabase().getThreadIdForMessage(messageId));
    ((DatabaseAttachment) attachment).setUploaded(true);
  }

  public void setTransferState(long messageId, @NonNull AttachmentId attachmentId, int transferState) {
    final ContentValues  values   = new ContentValues(1);
    final SQLiteDatabase database = databaseHelper.getWritableDatabase();

    values.put(TRANSFER_STATE, transferState);
    database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings());
    notifyConversationListeners(DatabaseComponent.get(context).mmsDatabase().getThreadIdForMessage(messageId));
  }

  @SuppressWarnings("WeakerAccess")
  @VisibleForTesting
  protected @Nullable InputStream getDataStream(AttachmentId attachmentId, String dataType, long offset)
  {
    DataInfo dataInfo = getAttachmentDataFileInfo(attachmentId, dataType);

    if (dataInfo == null) {
      return null;
    }

    try {
      if (dataInfo.random != null && dataInfo.random.length == 32) {
        return ModernDecryptingPartInputStream.createFor(attachmentSecret, dataInfo.random, dataInfo.file, offset);
      } else {
        InputStream stream  = ClassicDecryptingPartInputStream.createFor(attachmentSecret, dataInfo.file);
        long        skipped = stream.skip(offset);

        if (skipped != offset) {
          Log.w(TAG, "Skip failed: " + skipped + " vs " + offset);
          return null;
        }

        return stream;
      }
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private @Nullable DataInfo getAttachmentDataFileInfo(@NonNull AttachmentId attachmentId, @NonNull String dataType)
  {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    String randomColumn;

    switch (dataType) {
      case DATA:      randomColumn = DATA_RANDOM;      break;
      case THUMBNAIL: randomColumn = THUMBNAIL_RANDOM; break;
      default:throw   new AssertionError("Unknown data type: " + dataType);
    }

    try {
      cursor = database.query(TABLE_NAME, new String[]{dataType, SIZE, randomColumn}, PART_ID_WHERE, attachmentId.toStrings(),
                              null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        if (cursor.isNull(0)) {
          return null;
        }

        return new DataInfo(new File(cursor.getString(0)),
                            cursor.getLong(1),
                            cursor.getBlob(2));
      } else {
        return null;
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

  }

  private @NonNull DataInfo setAttachmentData(@NonNull Uri uri)
      throws MmsException
  {
    try {
      InputStream inputStream = PartAuthority.getAttachmentStream(context, uri);
      return setAttachmentData(inputStream);
    } catch (IOException e) {
      throw new MmsException(e);
    }
  }

  private @NonNull DataInfo setAttachmentData(@NonNull InputStream in)
      throws MmsException
  {
    try {
      File partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
      File dataFile       = File.createTempFile("part", ".mms", partsDirectory);
      return setAttachmentData(dataFile, in);
    } catch (IOException e) {
      throw new MmsException(e);
    }
  }

  private @NonNull DataInfo setAttachmentData(@NonNull File destination, @NonNull InputStream in)
      throws MmsException
  {
    try {
      Pair<byte[], OutputStream> out    = ModernEncryptingPartOutputStream.createFor(attachmentSecret, destination, false);
      long                       length = Util.copy(in, out.second);
      return new DataInfo(destination, length, out.first);
    } catch (IOException e) {
      throw new MmsException(e);
    }
  }

  // Method to attempt to get the column index of a given column name. Prints an error & flips the
  // flag ref if something bad happens. We pass in the error occurred flag as a ref to modify the
  // exact thing passed in rather than some pass-by-value copy.
  private int wrappedGetColumnIndex(Cursor cursor, String columnName, AtomicReference<Boolean> errorOccurredRef) {
      try { return cursor.getColumnIndexOrThrow(columnName); }
      catch (IllegalArgumentException e) {
        Log.e(TAG, "Failed to get index of column named: " + columnName);
        errorOccurredRef.set(true);
        return -1;
      }
  }

  public List<DatabaseAttachment> getAttachment(@NonNull Cursor cursor) {
    try {
      if (cursor.getColumnIndex(AttachmentDatabase.ATTACHMENT_JSON_ALIAS) != -1) {
        if (cursor.isNull(cursor.getColumnIndexOrThrow(ATTACHMENT_JSON_ALIAS))) {
          return new LinkedList<>();
        }

        Set<DatabaseAttachment> result = new TreeSet<>((o1, o2) -> o1.getAttachmentId().equals(o2.getAttachmentId()) ? 0 : 1);
        JSONArray                array  = new JSONArray(cursor.getString(cursor.getColumnIndexOrThrow(ATTACHMENT_JSON_ALIAS)));

        for (int i=0;i<array.length();i++) {
          JsonUtil.SaneJSONObject object = new JsonUtil.SaneJSONObject(array.getJSONObject(i));

          if (!object.isNull(ROW_ID)) {
            result.add(new DatabaseAttachment(new AttachmentId(object.getLong(ROW_ID), object.getLong(UNIQUE_ID)),
                                              object.getLong(MMS_ID),
                                              !TextUtils.isEmpty(object.getString(DATA)),
                                              !TextUtils.isEmpty(object.getString(THUMBNAIL)),
                                              object.getString(CONTENT_TYPE),
                                              object.getInt(TRANSFER_STATE),
                                              object.getLong(SIZE),
                                              object.getString(FILE_NAME),
                                              object.getString(CONTENT_LOCATION),
                                              object.getString(CONTENT_DISPOSITION),
                                              object.getString(NAME),
                                              null,
                                              object.getString(FAST_PREFLIGHT_ID),
                                              object.getInt(VOICE_NOTE) == 1,
                                              object.getInt(WIDTH),
                                              object.getInt(HEIGHT),
                                              object.getInt(QUOTE) == 1,
                                              object.getString(CAPTION),
                                              "")); // TODO: Not sure if this will break something
          }
        }

        return new ArrayList<>(result);
      }
      else // If the 'attachment json alias' IS precisely -1...
      {
        // Note: Added all this separation to try to identify where a crash is occurring & prevent.
        // See: https://optf.atlassian.net/browse/SES-1889
        AtomicReference<Boolean> errorOccurredRef = new AtomicReference<Boolean>(false);
        int rowIndex                = wrappedGetColumnIndex(cursor, ROW_ID, errorOccurredRef);
        int uniqueIdIndex           = wrappedGetColumnIndex(cursor, UNIQUE_ID, errorOccurredRef);
        int mmsIdIndex              = wrappedGetColumnIndex(cursor, MMS_ID, errorOccurredRef);
        int dataIndex               = wrappedGetColumnIndex(cursor, DATA, errorOccurredRef);
        int thumbnailIndex          = wrappedGetColumnIndex(cursor, THUMBNAIL, errorOccurredRef);
        int contentTypeIndex        = wrappedGetColumnIndex(cursor, CONTENT_TYPE, errorOccurredRef);
        int transferStateIndex      = wrappedGetColumnIndex(cursor, TRANSFER_STATE, errorOccurredRef);
        int sizeIndex               = wrappedGetColumnIndex(cursor, SIZE, errorOccurredRef);
        int filenameIndex           = wrappedGetColumnIndex(cursor, FILE_NAME, errorOccurredRef);
        int contentLocationIndex    = wrappedGetColumnIndex(cursor, CONTENT_LOCATION, errorOccurredRef);
        int contentDispositionIndex = wrappedGetColumnIndex(cursor, CONTENT_DISPOSITION, errorOccurredRef);
        int nameIndex               = wrappedGetColumnIndex(cursor, NAME, errorOccurredRef);
        int digestIndex             = wrappedGetColumnIndex(cursor, DIGEST, errorOccurredRef);
        int fastPreflightIdIndex    = wrappedGetColumnIndex(cursor, FAST_PREFLIGHT_ID, errorOccurredRef);
        int voiceNoteIndex          = wrappedGetColumnIndex(cursor, VOICE_NOTE, errorOccurredRef);
        int widthIndex              = wrappedGetColumnIndex(cursor, WIDTH, errorOccurredRef);
        int heightIndex             = wrappedGetColumnIndex(cursor, HEIGHT, errorOccurredRef);
        int quoteIndex              = wrappedGetColumnIndex(cursor, QUOTE, errorOccurredRef);
        int captionIndex            = wrappedGetColumnIndex(cursor, CAPTION, errorOccurredRef);
        int urlIndex                = wrappedGetColumnIndex(cursor, URL, errorOccurredRef);

        // If the error occurred ref got tripped by anything in the above we'll moan & bail
        if (errorOccurredRef.get()) {
          Log.e(TAG, "Cannot continue with getAttachment (bad column index) - returning null!");
          return null;
        }

        // Construct our AttachmentId (which will go into the DatabaseAttachment)
        long rowId = cursor.getLong(rowIndex);
        long uniqueId = cursor.getLong(uniqueIdIndex);
        AttachmentId attachmentId = new AttachmentId(rowId, uniqueId);

        // Grab all the other required data..
        long    mmsId           = cursor.getLong(mmsIdIndex);
        boolean haveData        = !cursor.isNull(dataIndex);
        boolean haveThumb       = !cursor.isNull(thumbnailIndex);
        String  contentType     = cursor.getString(contentTypeIndex);
        int     transferState   = cursor.getInt(transferStateIndex);
        long    size            = cursor.getLong(sizeIndex);
        String  filename        = cursor.getString(filenameIndex);
        String  location        = cursor.getString(contentLocationIndex);
        String  disposition     = cursor.getString(contentDispositionIndex);
        String  name            = cursor.getString(nameIndex);
        byte[]  blob            = cursor.getBlob(digestIndex);
        String  fastPreflightId = cursor.getString(fastPreflightIdIndex);
        boolean isVoiceNote     = cursor.getInt(voiceNoteIndex) == 1;
        int     width           = cursor.getInt(widthIndex);
        int     height          = cursor.getInt(heightIndex);
        boolean isQuote         = cursor.getInt(quoteIndex) == 1;
        String  caption         = cursor.getString(captionIndex);
        String  url             = urlIndex > 0 ? cursor.getString(urlIndex) : "";

        // ..then build the database attachment using this convenient and fool-proof constructor..
        DatabaseAttachment attachment = new DatabaseAttachment(attachmentId,
                                                               mmsId,
                                                               haveData,
                                                               haveThumb,
                                                               contentType,
                                                               transferState,
                                                               size,
                                                               filename,
                                                               location,
                                                               disposition,
                                                               name,
                                                               blob,
                                                               fastPreflightId,
                                                               isVoiceNote,
                                                               width,
                                                               height,
                                                               isQuote,
                                                               caption,
                                                               url);

        // ..and then return it as a list of precisely one object, because why not.
        return Collections.singletonList(attachment);
      }
    } catch (JSONException e) { throw new AssertionError(e); }
  }


  private AttachmentId insertAttachment(long mmsId, Attachment attachment, boolean quote)
      throws MmsException
  {
    Log.d(TAG, "Inserting attachment for mms id: " + mmsId);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    DataInfo       dataInfo = null;
    long           uniqueId = System.currentTimeMillis();

    if (attachment.getDataUri() != null) {
      dataInfo = setAttachmentData(attachment.getDataUri());
      Log.d(TAG, "Wrote part to file: " + dataInfo.file.getAbsolutePath());
    }

    ContentValues contentValues = new ContentValues();
    contentValues.put(MMS_ID, mmsId);
    contentValues.put(CONTENT_TYPE, attachment.getContentType());
    contentValues.put(TRANSFER_STATE, attachment.getTransferState());
    contentValues.put(UNIQUE_ID, uniqueId);
    contentValues.put(CONTENT_LOCATION, attachment.getLocation());
    contentValues.put(DIGEST, attachment.getDigest());
    contentValues.put(CONTENT_DISPOSITION, attachment.getKey());
    contentValues.put(NAME, attachment.getRelay());
    contentValues.put(FILE_NAME, ExternalStorageUtil.getCleanFileName(attachment.getFileName()));
    contentValues.put(SIZE, attachment.getSize());
    contentValues.put(FAST_PREFLIGHT_ID, attachment.getFastPreflightId());
    contentValues.put(VOICE_NOTE, attachment.isVoiceNote() ? 1 : 0);
    contentValues.put(WIDTH, attachment.getWidth());
    contentValues.put(HEIGHT, attachment.getHeight());
    contentValues.put(QUOTE, quote);
    contentValues.put(CAPTION, attachment.getCaption());
    contentValues.put(URL, attachment.getUrl());

    if (dataInfo != null) {
      contentValues.put(DATA, dataInfo.file.getAbsolutePath());
      contentValues.put(SIZE, dataInfo.length);
      contentValues.put(DATA_RANDOM, dataInfo.random);
    }

    long         rowId        = database.insert(TABLE_NAME, null, contentValues);
    AttachmentId attachmentId = new AttachmentId(rowId, uniqueId);
    Uri          thumbnailUri = attachment.getThumbnailUri();
    boolean      hasThumbnail = false;

    if (thumbnailUri != null) {
      try (InputStream attachmentStream = PartAuthority.getAttachmentStream(context, thumbnailUri)) {
        Pair<Integer, Integer> dimens;
        if (attachment.getContentType().equals(MediaTypes.IMAGE_GIF)) {
          dimens = new Pair<>(attachment.getWidth(), attachment.getHeight());
        } else {
          dimens = BitmapUtil.getDimensions(attachmentStream);
        }
        updateAttachmentThumbnail(attachmentId,
                                  PartAuthority.getAttachmentStream(context, thumbnailUri),
                                  (float) dimens.first / (float) dimens.second);
        hasThumbnail = true;
      } catch (IOException | BitmapDecodingException e) {
        Log.w(TAG, "Failed to save existing thumbnail.", e);
      }
    }

    if (!hasThumbnail && dataInfo != null) {
      if (MediaUtil.hasVideoThumbnail(attachment.getDataUri())) {
        Bitmap bitmap = MediaUtil.getVideoThumbnail(context, attachment.getDataUri());

        if (bitmap != null) {
          ThumbnailData thumbnailData = new ThumbnailData(bitmap);
          updateAttachmentThumbnail(attachmentId, thumbnailData.toDataStream(), thumbnailData.getAspectRatio());
        } else {
          Log.w(TAG, "Retrieving video thumbnail failed, submitting thumbnail generation job...");
          thumbnailExecutor.submit(new ThumbnailFetchCallable(attachmentId));
        }
      } else {
        Log.i(TAG, "Submitting thumbnail generation job...");
        thumbnailExecutor.submit(new ThumbnailFetchCallable(attachmentId));
      }
    }

    return attachmentId;
  }

  @SuppressWarnings("WeakerAccess")
  @VisibleForTesting
  protected void updateAttachmentThumbnail(AttachmentId attachmentId, InputStream in, float aspectRatio)
      throws MmsException
  {
    Log.i(TAG, "updating part thumbnail for #" + attachmentId);

    DataInfo thumbnailFile = setAttachmentData(in);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues(2);

    values.put(THUMBNAIL, thumbnailFile.file.getAbsolutePath());
    values.put(THUMBNAIL_ASPECT_RATIO, aspectRatio);
    values.put(THUMBNAIL_RANDOM, thumbnailFile.random);

    database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings());

    Cursor cursor = database.query(TABLE_NAME, new String[] {MMS_ID}, PART_ID_WHERE, attachmentId.toStrings(), null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst()) {
        notifyConversationListeners(DatabaseComponent.get(context).mmsDatabase().getThreadIdForMessage(cursor.getLong(cursor.getColumnIndexOrThrow(MMS_ID))));
      }
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  /**
   * Retrieves the audio extra values associated with the attachment. Only "audio/*" mime type attachments are accepted.
   * @return the related audio extras or null in case any of the audio extra columns are empty or the attachment is not an audio.
   */
  @Synchronized
  public @Nullable DatabaseAttachmentAudioExtras getAttachmentAudioExtras(@NonNull AttachmentId attachmentId) {
    try (Cursor cursor = databaseHelper.getReadableDatabase()
      // We expect all the audio extra values to be present (not null) or reject the whole record.
      .query(TABLE_NAME,
        PROJECTION_AUDIO_EXTRAS,
        PART_ID_WHERE +
        " AND " + AUDIO_VISUAL_SAMPLES + " IS NOT NULL" +
        " AND " + AUDIO_DURATION + " IS NOT NULL" +
        " AND " + PART_AUDIO_ONLY_WHERE,
        attachmentId.toStrings(),
        null, null, null, "1")) {

      if (cursor == null || !cursor.moveToFirst()) return null;

      byte[] audioSamples = cursor.getBlob(cursor.getColumnIndexOrThrow(AUDIO_VISUAL_SAMPLES));
      long   duration     = cursor.getLong(cursor.getColumnIndexOrThrow(AUDIO_DURATION));

      return new DatabaseAttachmentAudioExtras(attachmentId, audioSamples, duration);
    }
  }

  /**
   * Updates audio extra columns for the "audio/*" mime type attachments only.
   * @return true if the update operation was successful.
   */
  @Synchronized
  public boolean setAttachmentAudioExtras(@NonNull DatabaseAttachmentAudioExtras extras, long threadId) {
    ContentValues values = new ContentValues();
    values.put(AUDIO_VISUAL_SAMPLES, extras.getVisualSamples());
    values.put(AUDIO_DURATION, extras.getDurationMs());

    int alteredRows = databaseHelper.getWritableDatabase().update(TABLE_NAME,
      values,
      PART_ID_WHERE + " AND " + PART_AUDIO_ONLY_WHERE,
      extras.getAttachmentId().toStrings());

    if (threadId >= 0) {
      notifyConversationListeners(threadId);
    }

    return alteredRows > 0;
  }

  /**
   * Updates audio extra columns for the "audio/*" mime type attachments only.
   * @return true if the update operation was successful.
   */
  @Synchronized
  public boolean setAttachmentAudioExtras(@NonNull DatabaseAttachmentAudioExtras extras) {
    return setAttachmentAudioExtras(extras, -1); // -1 for no update
  }

  @VisibleForTesting
  class ThumbnailFetchCallable implements Callable<InputStream> {

    private final AttachmentId attachmentId;

    ThumbnailFetchCallable(AttachmentId attachmentId) {
      this.attachmentId = attachmentId;
    }

    @Override
    public @Nullable InputStream call() throws Exception {
      Log.d(TAG, "Executing thumbnail job...");
      final InputStream stream = getDataStream(attachmentId, THUMBNAIL, 0);

      if (stream != null) {
        return stream;
      }

      DatabaseAttachment attachment = getAttachment(attachmentId);

      if (attachment == null || !attachment.hasData()) {
        return null;
      }

      ThumbnailData data = null;

      if (MediaUtil.isVideoType(attachment.getContentType())) {
        data = generateVideoThumbnail(attachmentId);
      }

      if (data == null) {
        return null;
      }

      updateAttachmentThumbnail(attachmentId, data.toDataStream(), data.getAspectRatio());

      return getDataStream(attachmentId, THUMBNAIL, 0);
    }

    @SuppressLint("NewApi")
    private ThumbnailData generateVideoThumbnail(AttachmentId attachmentId) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        Log.w(TAG, "Video thumbnails not supported...");
        return null;
      }

      DataInfo dataInfo = getAttachmentDataFileInfo(attachmentId, DATA);

      if (dataInfo == null) {
        Log.w(TAG, "No data file found for video thumbnail...");
        return null;
      }

      EncryptedMediaDataSource dataSource = new EncryptedMediaDataSource(attachmentSecret, dataInfo.file, dataInfo.random, dataInfo.length);
      MediaMetadataRetriever   retriever  = new MediaMetadataRetriever();
      retriever.setDataSource(dataSource);

      Bitmap bitmap = retriever.getFrameAtTime(1000);

      Log.i(TAG, "Generated video thumbnail...");
      return new ThumbnailData(bitmap);
    }
  }

  private static class DataInfo {
    private final File   file;
    private final long   length;
    private final byte[] random;

    private DataInfo(File file, long length, byte[] random) {
      this.file = file;
      this.length = length;
      this.random = random;
    }
  }
}
