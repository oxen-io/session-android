package org.thoughtcrime.securesms.database.helpers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteException;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.BlindedIdMappingDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.EmojiSearchDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupMemberDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.LokiAPIDatabase;
import org.thoughtcrime.securesms.database.LokiBackupFilesDatabase;
import org.thoughtcrime.securesms.database.LokiMessageDatabase;
import org.thoughtcrime.securesms.database.LokiThreadDatabase;
import org.thoughtcrime.securesms.database.LokiUserDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.ReactionDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SearchDatabase;
import org.thoughtcrime.securesms.database.SessionContactDatabase;
import org.thoughtcrime.securesms.database.SessionJobDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.notifications.NotificationChannels;

import java.io.File;

import network.loki.messenger.R;

public class SQLCipherOpenHelper extends SQLiteOpenHelper {

  @SuppressWarnings("unused")
  private static final String TAG = SQLCipherOpenHelper.class.getSimpleName();

  // First public release (1.0.0) DB version was 27.
  // So we have to keep the migrations onwards.
  private static final int lokiV7                           = 28;
  private static final int lokiV8                           = 29;
  private static final int lokiV9                           = 30;
  private static final int lokiV10                          = 31;
  private static final int lokiV11                          = 32;
  private static final int lokiV12                          = 33;
  private static final int lokiV13                          = 34;
  private static final int lokiV14_BACKUP_FILES             = 35;
  private static final int lokiV15                          = 36;
  private static final int lokiV16                          = 37;
  private static final int lokiV17                          = 38;
  private static final int lokiV18_CLEAR_BG_POLL_JOBS       = 39;
  private static final int lokiV19                          = 40;
  private static final int lokiV20                          = 41;
  private static final int lokiV21                          = 42;
  private static final int lokiV22                          = 43;
  private static final int lokiV23                          = 44;
  private static final int lokiV24                          = 45;
  private static final int lokiV25                          = 46;
  private static final int lokiV26                          = 47;
  private static final int lokiV27                          = 48;
  private static final int lokiV28                          = 49;
  private static final int lokiV29                          = 50;
  private static final int lokiV30                          = 51;
  private static final int lokiV31                          = 52;
  private static final int lokiV32                          = 53;
  private static final int lokiV33                          = 54;
  private static final int lokiV34                          = 55;
  private static final int lokiV35                          = 56;
  private static final int lokiV36                          = 57;
  private static final int lokiV37                          = 58;
  private static final int lokiV38                          = 59;
  private static final int lokiV39                          = 60;
  private static final int lokiV40                          = 61;

  // Loki - onUpgrade(...) must be updated to use Loki version numbers if Signal makes any database changes
  private static final int    DATABASE_VERSION         = lokiV40;
  private static final int    MIN_DATABASE_VERSION     = lokiV7;
  private static final String CIPHER3_DATABASE_NAME    = "signal.db";
  public static final String  DATABASE_NAME            = "signal_v4.db";

  private final Context        context;
  private final DatabaseSecret databaseSecret;

  public SQLCipherOpenHelper(@NonNull Context context, @NonNull DatabaseSecret databaseSecret) {
    super(
      context,
      DATABASE_NAME,
      databaseSecret.asString(),
      null,
      DATABASE_VERSION,
      MIN_DATABASE_VERSION,
      null,
      new SQLiteDatabaseHook() {
        @Override
        public void preKey(SQLiteConnection connection) {
          SQLCipherOpenHelper.applySQLCipherPragmas(connection, true);
        }

        @Override
        public void postKey(SQLiteConnection connection) {
          SQLCipherOpenHelper.applySQLCipherPragmas(connection, true);

          // if not vacuumed in a while, perform that operation
          long currentTime = System.currentTimeMillis();
          // 7 days
          if (currentTime - TextSecurePreferences.getLastVacuumTime(context) > 604_800_000) {
            connection.execute("VACUUM;", null, null);
            TextSecurePreferences.setLastVacuumNow(context);
          }
        }
      },
      // Note: Now that we support concurrent database reads the migrations are actually non-blocking
      // because of this we need to initially open the database with writeAheadLogging (WAL mode) disabled
      // and enable it once the database officially opens it's connection (which will cause it to re-connect
      // in WAL mode) - this is a little inefficient but will prevent SQL-related errors/crashes due to
      // incomplete migrations
      false
    );

    this.context        = context.getApplicationContext();
    this.databaseSecret = databaseSecret;
  }

  private static void applySQLCipherPragmas(SQLiteConnection connection, boolean useSQLCipher4) {
    if (useSQLCipher4) {
      connection.execute("PRAGMA kdf_iter = '256000';", null, null);
    }
    else {
      connection.execute("PRAGMA cipher_compatibility = 3;", null, null);
      connection.execute("PRAGMA kdf_iter = '1';", null, null);
    }

    connection.execute("PRAGMA cipher_page_size = 4096;", null, null);
  }

  private static SQLiteDatabase open(String path, DatabaseSecret databaseSecret, boolean useSQLCipher4) throws SQLiteException {
    return SQLiteDatabase.openDatabase(path, databaseSecret.asString(), null, SQLiteDatabase.OPEN_READWRITE, new SQLiteDatabaseHook() {
      @Override
      public void preKey(SQLiteConnection connection) { SQLCipherOpenHelper.applySQLCipherPragmas(connection, useSQLCipher4); }

      @Override
      public void postKey(SQLiteConnection connection) { SQLCipherOpenHelper.applySQLCipherPragmas(connection, useSQLCipher4); }
    });
  }

  public static void migrateSqlCipher3To4IfNeeded(@NonNull Context context, @NonNull DatabaseSecret databaseSecret) throws Exception {
    String oldDbPath = context.getDatabasePath(CIPHER3_DATABASE_NAME).getPath();
    File oldDbFile = new File(oldDbPath);

    // If the old SQLCipher3 database file doesn't exist then no need to do anything
    if (!oldDbFile.exists()) { return; }

    // Define the location for the new database
    String newDbPath = context.getDatabasePath(DATABASE_NAME).getPath();
    File newDbFile = new File(newDbPath);

    try {
      // If the new database file already exists then check if it's valid first, if it's in an
      // invalid state we should delete it and try to migrate again
      if (newDbFile.exists()) {
        // If the old database hasn't been modified since the new database was created, then we can
        // assume the user hasn't downgraded for some reason and made changes to the old database and
        // can remove the old database file (it won't be used anymore)
        if (oldDbFile.lastModified() <= newDbFile.lastModified()) {
          try {
            SQLiteDatabase newDb = SQLCipherOpenHelper.open(newDbPath, databaseSecret, true);
            int version = newDb.getVersion();
            newDb.close();

            // Make sure the new database has it's version set correctly (if not then the migration didn't
            // fully succeed and the database will try to create all it's tables and immediately fail so
            // we will need to remove and remigrate)
            if (version > 0) {
              // TODO: Delete 'CIPHER3_DATABASE_NAME' once enough time has past
//            //noinspection ResultOfMethodCallIgnored
//            oldDbFile.delete();
              return;
            }
          }
          catch (Exception e) {
            Log.i(TAG, "Failed to retrieve version from new database, assuming invalid and remigrating");
          }
        }

        // If the old database does have newer changes then the new database could have stale/invalid
        // data and we should re-migrate to avoid losing any data or issues
        if (!newDbFile.delete()) {
          throw new Exception("Failed to remove invalid new database");
        }
      }

      if (!newDbFile.createNewFile()) {
        throw new Exception("Failed to create new database");
      }

      // Open the old database and extract it's version
      SQLiteDatabase oldDb = SQLCipherOpenHelper.open(oldDbPath, databaseSecret, false);
      int oldDbVersion = oldDb.getVersion();

      // Export the old database to the new one (will have the default 'kdf_iter' and 'page_size' settings)
      oldDb.rawExecSQL(
        String.format("ATTACH DATABASE '%s' AS sqlcipher4 KEY '%s'", newDbPath, databaseSecret.asString())
      );
      Cursor cursor = oldDb.rawQuery("SELECT sqlcipher_export('sqlcipher4')");
      cursor.moveToLast();
      cursor.close();
      oldDb.rawExecSQL("DETACH DATABASE sqlcipher4");
      oldDb.close();

      // Open the newly migrated database (to ensure it works) and set it's version so we don't try
      // to run any of our custom migrations
      SQLiteDatabase newDb = SQLCipherOpenHelper.open(newDbPath, databaseSecret, true);
      newDb.setVersion(oldDbVersion);
      newDb.close();

      // TODO: Delete 'CIPHER3_DATABASE_NAME' once enough time has past
      // Remove the old database file since it will no longer be used
//      //noinspection ResultOfMethodCallIgnored
//      oldDbFile.delete();
    }
    catch (Exception e) {
      Log.e(TAG, "Migration from SQLCipher3 to SQLCipher4 failed", e);

      // If an exception was thrown then we should remove the new database file (it's probably invalid)
      if (!newDbFile.delete()) {
        Log.e(TAG, "Unable to delete invalid new database file");
      }

      // Notify the user of the issue so they know they can downgrade until the issue is fixed
      NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
      String channelId = context.getString(R.string.NotificationChannel_failures);

      if (NotificationChannels.supported()) {
        NotificationChannel channel = new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH);
        channel.enableVibration(true);
        notificationManager.createNotificationChannel(channel);
      }

      NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(context.getResources().getColor(R.color.textsecure_primary))
        .setCategory(NotificationCompat.CATEGORY_ERROR)
        .setContentTitle(context.getString(R.string.ErrorNotifier_migration))
        .setContentText(context.getString(R.string.ErrorNotifier_migration_downgrade))
        .setAutoCancel(true);

      if (!NotificationChannels.supported()) {
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
      }

      notificationManager.notify(5874, builder.build());

      // Throw the error (app will crash but there is nothing else we can do unfortunately)
      throw e;
    }
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(SmsDatabase.CREATE_TABLE);
    db.execSQL(MmsDatabase.CREATE_TABLE);
    db.execSQL(AttachmentDatabase.CREATE_TABLE);
    db.execSQL(ThreadDatabase.CREATE_TABLE);
    db.execSQL(DraftDatabase.CREATE_TABLE);
    db.execSQL(PushDatabase.CREATE_TABLE);
    db.execSQL(GroupDatabase.CREATE_TABLE);
    db.execSQL(RecipientDatabase.CREATE_TABLE);
    db.execSQL(GroupReceiptDatabase.CREATE_TABLE);
    for (String sql : SearchDatabase.CREATE_TABLE) {
      db.execSQL(sql);
    }
    db.execSQL(LokiAPIDatabase.getCreateSnodePoolTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateOnionRequestPathTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateSwarmTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateLastMessageHashValueTable2Command());
    db.execSQL(LokiAPIDatabase.getCreateReceivedMessageHashValuesTable3Command());
    db.execSQL(LokiAPIDatabase.getCreateOpenGroupAuthTokenTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateLastMessageServerIDTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateLastDeletionServerIDTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateDeviceLinkCacheCommand());
    db.execSQL(LokiAPIDatabase.getCreateUserCountTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateSessionRequestTimestampCacheCommand());
    db.execSQL(LokiAPIDatabase.getCreateSessionRequestSentTimestampTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateSessionRequestProcessedTimestampTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateOpenGroupPublicKeyTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateOpenGroupProfilePictureTableCommand());
    db.execSQL(LokiAPIDatabase.getCreateClosedGroupEncryptionKeyPairsTable());
    db.execSQL(LokiAPIDatabase.getCreateClosedGroupPublicKeysTable());
    db.execSQL(LokiAPIDatabase.getCreateServerCapabilitiesCommand());
    db.execSQL(LokiAPIDatabase.getCreateLastInboxMessageServerIdCommand());
    db.execSQL(LokiAPIDatabase.getCreateLastOutboxMessageServerIdCommand());
    db.execSQL(LokiMessageDatabase.getCreateMessageIDTableCommand());
    db.execSQL(LokiMessageDatabase.getCreateMessageToThreadMappingTableCommand());
    db.execSQL(LokiMessageDatabase.getCreateErrorMessageTableCommand());
    db.execSQL(LokiMessageDatabase.getCreateMessageHashTableCommand());
    db.execSQL(LokiThreadDatabase.getCreateSessionResetTableCommand());
    db.execSQL(LokiThreadDatabase.getCreatePublicChatTableCommand());
    db.execSQL(LokiUserDatabase.getCreateDisplayNameTableCommand());
    db.execSQL(LokiBackupFilesDatabase.getCreateTableCommand());
    db.execSQL(SessionJobDatabase.getCreateSessionJobTableCommand());
    db.execSQL(LokiMessageDatabase.getUpdateMessageIDTableForType());
    db.execSQL(LokiMessageDatabase.getUpdateMessageMappingTable());
    db.execSQL(SessionContactDatabase.getCreateSessionContactTableCommand());
    db.execSQL(RecipientDatabase.getCreateNotificationTypeCommand());
    db.execSQL(ThreadDatabase.getCreatePinnedCommand());
    db.execSQL(GroupDatabase.getCreateUpdatedTimestampCommand());
    db.execSQL(RecipientDatabase.getCreateApprovedCommand());
    db.execSQL(RecipientDatabase.getCreateApprovedMeCommand());
    db.execSQL(MmsDatabase.CREATE_MESSAGE_REQUEST_RESPONSE_COMMAND);
    db.execSQL(MmsDatabase.CREATE_REACTIONS_UNREAD_COMMAND);
    db.execSQL(SmsDatabase.CREATE_REACTIONS_UNREAD_COMMAND);
    db.execSQL(MmsDatabase.CREATE_REACTIONS_LAST_SEEN_COMMAND);
    db.execSQL(LokiAPIDatabase.CREATE_FORK_INFO_TABLE_COMMAND);
    db.execSQL(LokiAPIDatabase.CREATE_DEFAULT_FORK_INFO_COMMAND);
    db.execSQL(LokiAPIDatabase.UPDATE_HASHES_INCLUDE_NAMESPACE_COMMAND);
    db.execSQL(LokiAPIDatabase.UPDATE_RECEIVED_INCLUDE_NAMESPACE_COMMAND);
    db.execSQL(LokiAPIDatabase.INSERT_LAST_HASH_DATA);
    db.execSQL(LokiAPIDatabase.DROP_LEGACY_LAST_HASH);
    db.execSQL(LokiAPIDatabase.INSERT_RECEIVED_HASHES_DATA);
    db.execSQL(LokiAPIDatabase.DROP_LEGACY_RECEIVED_HASHES);
    db.execSQL(BlindedIdMappingDatabase.CREATE_BLINDED_ID_MAPPING_TABLE_COMMAND);
    db.execSQL(GroupMemberDatabase.CREATE_GROUP_MEMBER_TABLE_COMMAND);
    db.execSQL(LokiAPIDatabase.RESET_SEQ_NO); // probably not needed but consistent with all migrations
    db.execSQL(EmojiSearchDatabase.CREATE_EMOJI_SEARCH_TABLE_COMMAND);
    db.execSQL(ReactionDatabase.CREATE_REACTION_TABLE_COMMAND);
    db.execSQL(ThreadDatabase.getUnreadMentionCountCommand());
    db.execSQL(SmsDatabase.CREATE_HAS_MENTION_COMMAND);
    db.execSQL(MmsDatabase.CREATE_HAS_MENTION_COMMAND);

    executeStatements(db, SmsDatabase.CREATE_INDEXS);
    executeStatements(db, MmsDatabase.CREATE_INDEXS);
    executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
    executeStatements(db, ThreadDatabase.CREATE_INDEXS);
    executeStatements(db, DraftDatabase.CREATE_INDEXS);
    executeStatements(db, GroupDatabase.CREATE_INDEXS);
    executeStatements(db, GroupReceiptDatabase.CREATE_INDEXES);
    executeStatements(db, ReactionDatabase.CREATE_INDEXS);

    executeStatements(db, ReactionDatabase.CREATE_REACTION_TRIGGERS);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    super.onConfigure(db);

    db.execSQL("PRAGMA cache_size = 10000");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.i(TAG, "Upgrading database: " + oldVersion + ", " + newVersion);

    db.beginTransaction();

    try {

      if (oldVersion < lokiV7) {
        db.execSQL(LokiMessageDatabase.getCreateErrorMessageTableCommand());
      }

      if (oldVersion < lokiV8) {
        db.execSQL(LokiAPIDatabase.getCreateSessionRequestTimestampCacheCommand());
      }

      if (oldVersion < lokiV9) {
        db.execSQL(LokiAPIDatabase.getCreateSnodePoolTableCommand());
        db.execSQL(LokiAPIDatabase.getCreateOnionRequestPathTableCommand());
      }

      if (oldVersion < lokiV10) {
        db.execSQL(LokiAPIDatabase.getCreateSessionRequestSentTimestampTableCommand());
        db.execSQL(LokiAPIDatabase.getCreateSessionRequestProcessedTimestampTableCommand());
      }

      if (oldVersion < lokiV11) {
        db.execSQL(LokiAPIDatabase.getCreateOpenGroupPublicKeyTableCommand());
      }

      if (oldVersion < lokiV12) {
        db.execSQL(LokiAPIDatabase.getCreateLastMessageHashValueTable2Command());
      }

      if (oldVersion < lokiV13) {
        db.execSQL(LokiAPIDatabase.getCreateReceivedMessageHashValuesTable3Command());
      }

      if (oldVersion < lokiV14_BACKUP_FILES) {
        db.execSQL(LokiBackupFilesDatabase.getCreateTableCommand());
      }

      if (oldVersion < lokiV16) {
        db.execSQL(LokiAPIDatabase.getCreateOpenGroupProfilePictureTableCommand());
      }

      if (oldVersion < lokiV17) {
        db.execSQL("ALTER TABLE part ADD COLUMN audio_visual_samples BLOB");
        db.execSQL("ALTER TABLE part ADD COLUMN audio_duration INTEGER");
      }

      if (oldVersion < lokiV18_CLEAR_BG_POLL_JOBS) {
        // BackgroundPollJob was replaced with BackgroundPollWorker. Clear all the scheduled job records.
        db.execSQL("DELETE FROM job_spec WHERE factory_key = 'BackgroundPollJob'");
        db.execSQL("DELETE FROM constraint_spec WHERE factory_key = 'BackgroundPollJob'");
      }

      // Many classes were removed. We need to update DB structure and data to match the code changes.
      if (oldVersion < lokiV19) {
        db.execSQL(LokiAPIDatabase.getCreateClosedGroupEncryptionKeyPairsTable());
        db.execSQL(LokiAPIDatabase.getCreateClosedGroupPublicKeysTable());
        db.execSQL("DROP TABLE identities");
        deleteJobRecords(db, "RetrieveProfileJob");
        deleteJobRecords(db,
                "RefreshAttributesJob",
                "RotateProfileKeyJob",
                "RefreshUnidentifiedDeliveryAbilityJob",
                "RotateCertificateJob"
        );
      }

      if (oldVersion < lokiV20) {
        deleteJobRecords(db,
                "CleanPreKeysJob",
                "RefreshPreKeysJob",
                "CreateSignedPreKeyJob",
                "RotateSignedPreKeyJob",
                "MultiDeviceBlockedUpdateJob",
                "MultiDeviceConfigurationUpdateJob",
                "MultiDeviceContactUpdateJob",
                "MultiDeviceGroupUpdateJob",
                "MultiDeviceOpenGroupUpdateJob",
                "MultiDeviceProfileKeyUpdateJob",
                "MultiDeviceReadUpdateJob",
                "MultiDeviceStickerPackOperationJob",
                "MultiDeviceStickerPackSyncJob",
                "MultiDeviceVerifiedUpdateJob",
                "ServiceOutageDetectionJob",
                "SessionRequestMessageSendJob"
        );
      }

      if (oldVersion < lokiV21) {
        deleteJobRecords(db,
                "ClosedGroupUpdateMessageSendJob",
                "NullMessageSendJob",
                "StickerDownloadJob",
                "StickerPackDownloadJob",
                "MmsSendJob",
                "MmsReceiveJob",
                "MmsDownloadJob",
                "SmsSendJob",
                "SmsSentJob",
                "SmsReceiveJob",
                "PushGroupUpdateJob",
                "ResetThreadSessionJob");
      }

      if (oldVersion < lokiV22) {
        db.execSQL(SessionJobDatabase.getCreateSessionJobTableCommand());
        deleteJobRecords(db,
                "PushGroupSendJob",
                "PushMediaSendJob",
                "PushTextSendJob",
                "SendReadReceiptJob",
                "TypingSendJob",
                "AttachmentUploadJob",
                "RequestGroupInfoJob",
                "ClosedGroupUpdateMessageSendJobV2",
                "SendDeliveryReceiptJob");
      }

      if (oldVersion < lokiV23) {
        db.execSQL("ALTER TABLE groups ADD COLUMN zombie_members TEXT");
        db.execSQL(LokiMessageDatabase.getUpdateMessageIDTableForType());
        db.execSQL(LokiMessageDatabase.getUpdateMessageMappingTable());
      }

      if (oldVersion < lokiV24) {
        String swarmTable = LokiAPIDatabase.Companion.getSwarmTable();
        String snodePoolTable = LokiAPIDatabase.Companion.getSnodePoolTable();
        db.execSQL("DROP TABLE " + swarmTable);
        db.execSQL("DROP TABLE " + snodePoolTable);
        db.execSQL(LokiAPIDatabase.getCreateSnodePoolTableCommand());
        db.execSQL(LokiAPIDatabase.getCreateSwarmTableCommand());
      }

      if (oldVersion < lokiV25) {
        String jobTable = SessionJobDatabase.sessionJobTable;
        db.execSQL("DROP TABLE " + jobTable);
        db.execSQL(SessionJobDatabase.getCreateSessionJobTableCommand());
      }

      if (oldVersion < lokiV26) {
        db.execSQL(SessionContactDatabase.getCreateSessionContactTableCommand());
      }

      if (oldVersion < lokiV27) {
        db.execSQL(RecipientDatabase.getCreateNotificationTypeCommand());
      }

      if (oldVersion < lokiV28) {
        db.execSQL(LokiMessageDatabase.getCreateMessageHashTableCommand());
      }

      if (oldVersion < lokiV29) {
        db.execSQL(ThreadDatabase.getCreatePinnedCommand());
      }

      if (oldVersion < lokiV30) {
        db.execSQL(GroupDatabase.getCreateUpdatedTimestampCommand());
      }

      if (oldVersion < lokiV31) {
        db.execSQL(RecipientDatabase.getCreateApprovedCommand());
        db.execSQL(RecipientDatabase.getCreateApprovedMeCommand());
        db.execSQL(RecipientDatabase.getUpdateApprovedCommand());
        db.execSQL(MmsDatabase.CREATE_MESSAGE_REQUEST_RESPONSE_COMMAND);
      }

      if (oldVersion < lokiV32) {
        db.execSQL(RecipientDatabase.getUpdateResetApprovedCommand());
        db.execSQL(RecipientDatabase.getUpdateApprovedSelectConversations());
      }

      if (oldVersion < lokiV33) {
        db.execSQL(LokiAPIDatabase.CREATE_FORK_INFO_TABLE_COMMAND);
        db.execSQL(LokiAPIDatabase.CREATE_DEFAULT_FORK_INFO_COMMAND);
        db.execSQL(LokiAPIDatabase.UPDATE_HASHES_INCLUDE_NAMESPACE_COMMAND);
        db.execSQL(LokiAPIDatabase.UPDATE_RECEIVED_INCLUDE_NAMESPACE_COMMAND);
      }

      if (oldVersion < lokiV34) {
        db.execSQL(LokiAPIDatabase.INSERT_LAST_HASH_DATA);
        db.execSQL(LokiAPIDatabase.DROP_LEGACY_LAST_HASH);
        db.execSQL(LokiAPIDatabase.INSERT_RECEIVED_HASHES_DATA);
        db.execSQL(LokiAPIDatabase.DROP_LEGACY_RECEIVED_HASHES);
      }

      if (oldVersion < lokiV35) {
        db.execSQL(LokiAPIDatabase.getCreateServerCapabilitiesCommand());
        db.execSQL(LokiAPIDatabase.getCreateLastInboxMessageServerIdCommand());
        db.execSQL(LokiAPIDatabase.getCreateLastOutboxMessageServerIdCommand());
        db.execSQL(BlindedIdMappingDatabase.CREATE_BLINDED_ID_MAPPING_TABLE_COMMAND);
        db.execSQL(GroupMemberDatabase.CREATE_GROUP_MEMBER_TABLE_COMMAND);
      }

      if (oldVersion < lokiV36) {
        db.execSQL(LokiAPIDatabase.RESET_SEQ_NO);
      }

      if (oldVersion < lokiV37) {
        db.execSQL(MmsDatabase.CREATE_REACTIONS_UNREAD_COMMAND);
        db.execSQL(SmsDatabase.CREATE_REACTIONS_UNREAD_COMMAND);
        db.execSQL(MmsDatabase.CREATE_REACTIONS_LAST_SEEN_COMMAND);
        db.execSQL(ReactionDatabase.CREATE_REACTION_TABLE_COMMAND);
        executeStatements(db, ReactionDatabase.CREATE_REACTION_TRIGGERS);
      }

      if (oldVersion < lokiV38) {
        db.execSQL(EmojiSearchDatabase.CREATE_EMOJI_SEARCH_TABLE_COMMAND);
      }

      if (oldVersion < lokiV39) {
        executeStatements(db, ReactionDatabase.CREATE_INDEXS);
      }

      if (oldVersion < lokiV40) {
        db.execSQL(ThreadDatabase.getUnreadMentionCountCommand());
        db.execSQL(SmsDatabase.CREATE_HAS_MENTION_COMMAND);
        db.execSQL(MmsDatabase.CREATE_HAS_MENTION_COMMAND);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Override
  public void onOpen(SQLiteDatabase db) {
    super.onOpen(db);

    // Now that the database is officially open (ie. the migrations are completed) we want to enable
    // write ahead logging (WAL mode) to officially support concurrent read connections
    db.enableWriteAheadLogging();
  }

  public void markCurrent(SQLiteDatabase db) {
    db.setVersion(DATABASE_VERSION);
  }

  private void executeStatements(SQLiteDatabase db, String[] statements) {
    for (String statement : statements)
      db.execSQL(statement);
  }

  private static boolean columnExists(@NonNull SQLiteDatabase db, @NonNull String table, @NonNull String column) {
    try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
      int nameColumnIndex = cursor.getColumnIndexOrThrow("name");

      while (cursor.moveToNext()) {
        String name = cursor.getString(nameColumnIndex);

        if (name.equals(column)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Cleans up all the records related to the job keys specified.
   * This method should be called once the Signal job class is deleted from the project.
   */
  private static void deleteJobRecords(SQLiteDatabase db, String... jobKeys) {
    for (String jobKey : jobKeys) {
      db.execSQL("DELETE FROM job_spec WHERE factory_key = ?", new String[]{jobKey});
      db.execSQL("DELETE FROM constraint_spec WHERE factory_key = ?", new String[]{jobKey});
    }
  }
}
