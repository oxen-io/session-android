package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase.CONFLICT_REPLACE
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import org.session.libsession.database.ServerHashToMessageId
import org.session.libsignal.database.LokiMessageDatabaseProtocol
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.util.asSequence

class LokiMessageDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiMessageDatabaseProtocol {

    companion object {
        private const val messageIDTable = "loki_message_friend_request_database"
        private const val messageThreadMappingTable = "loki_message_thread_mapping_database"
        private const val errorMessageTable = "loki_error_message_database"
        private const val messageHashTable = "loki_message_hash_database"
        private const val smsHashTable = "loki_sms_hash_database"
        private const val mmsHashTable = "loki_mms_hash_database"
        const val groupInviteTable = "loki_group_invites"

        private const val groupInviteDeleteTrigger = "group_invite_delete_trigger"

        private const val messageID = "message_id"
        private const val serverID = "server_id"
        private const val friendRequestStatus = "friend_request_status"
        private const val threadID = "thread_id"
        private const val errorMessage = "error_message"
        private const val messageType = "message_type"
        private const val serverHash = "server_hash"
        const val invitingSessionId = "inviting_session_id"

        @JvmStatic
        val createMessageIDTableCommand = "CREATE TABLE $messageIDTable ($messageID INTEGER PRIMARY KEY, $serverID INTEGER DEFAULT 0, $friendRequestStatus INTEGER DEFAULT 0);"
        @JvmStatic
        val createMessageToThreadMappingTableCommand = "CREATE TABLE IF NOT EXISTS $messageThreadMappingTable ($messageID INTEGER PRIMARY KEY, $threadID INTEGER);"
        @JvmStatic
        val createErrorMessageTableCommand = "CREATE TABLE IF NOT EXISTS $errorMessageTable ($messageID INTEGER PRIMARY KEY, $errorMessage STRING);"
        @JvmStatic
        val updateMessageIDTableForType = "ALTER TABLE $messageIDTable ADD COLUMN $messageType INTEGER DEFAULT 0; ALTER TABLE $messageIDTable ADD CONSTRAINT PK_$messageIDTable PRIMARY KEY ($messageID, $serverID);"
        @JvmStatic
        val updateMessageMappingTable = "ALTER TABLE $messageThreadMappingTable ADD COLUMN $serverID INTEGER DEFAULT 0; ALTER TABLE $messageThreadMappingTable ADD CONSTRAINT PK_$messageThreadMappingTable PRIMARY KEY ($messageID, $serverID);"
        @JvmStatic
        val createMessageHashTableCommand = "CREATE TABLE IF NOT EXISTS $messageHashTable ($messageID INTEGER PRIMARY KEY, $serverHash STRING);"
        @JvmStatic
        val createMmsHashTableCommand = "CREATE TABLE IF NOT EXISTS $mmsHashTable ($messageID INTEGER PRIMARY KEY, $serverHash STRING);"
        @JvmStatic
        val createSmsHashTableCommand = "CREATE TABLE IF NOT EXISTS $smsHashTable ($messageID INTEGER PRIMARY KEY, $serverHash STRING);"
        @JvmStatic
        val createGroupInviteTableCommand = "CREATE TABLE IF NOT EXISTS $groupInviteTable ($threadID INTEGER PRIMARY KEY, $invitingSessionId STRING);"
        @JvmStatic
        val createThreadDeleteTrigger = "CREATE TRIGGER IF NOT EXISTS $groupInviteDeleteTrigger AFTER DELETE ON ${ThreadDatabase.TABLE_NAME} BEGIN DELETE FROM $groupInviteTable WHERE $threadID = OLD.${ThreadDatabase.ID}; END;"

        const val SMS_TYPE = 0
        const val MMS_TYPE = 1

    }

    fun getServerID(messageID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageIDTable, "${Companion.messageID} = ?", arrayOf(messageID.toString())) { cursor ->
            cursor.getInt(serverID)
        }?.toLong()
    }

    fun getServerID(messageID: Long, isSms: Boolean): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageIDTable, "${Companion.messageID} = ? AND $messageType = ?", arrayOf(messageID.toString(), if (isSms) SMS_TYPE.toString() else MMS_TYPE.toString())) { cursor ->
            cursor.getInt(serverID)
        }?.toLong()
    }

    fun getMessageID(serverID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageIDTable, "${Companion.serverID} = ?", arrayOf(serverID.toString())) { cursor ->
            cursor.getInt(messageID)
        }?.toLong()
    }

    fun deleteMessage(messageID: Long, isSms: Boolean) {
        val database = databaseHelper.writableDatabase

        val serverID = database.get(messageIDTable,
                "${Companion.messageID} = ? AND $messageType = ?",
                arrayOf(messageID.toString(), (if (isSms) SMS_TYPE else MMS_TYPE).toString())) { cursor ->
            cursor.getInt(serverID).toLong()
        }

        if (serverID == null) {
            Log.w(this::class.simpleName, "Could not get server ID to delete message with ID: $messageID")
            return
        }

        database.beginTransaction()

        database.delete(messageIDTable, "${Companion.messageID} = ? AND ${Companion.serverID} = ?", arrayOf(messageID.toString(), serverID.toString()))
        database.delete(messageThreadMappingTable, "${Companion.messageID} = ? AND ${Companion.serverID} = ?", arrayOf(messageID.toString(), serverID.toString()))

        database.setTransactionSuccessful()
        database.endTransaction()
    }

    fun deleteMessages(messageIDs: List<Long>) {
        val database = databaseHelper.writableDatabase
        database.beginTransaction()

        database.delete(
            messageIDTable,
            "${Companion.messageID} IN (${messageIDs.map { "?" }.joinToString(",")})",
            messageIDs.map { "$it" }.toTypedArray()
        )
        database.delete(
            messageThreadMappingTable,
            "${Companion.messageID} IN (${messageIDs.map { "?" }.joinToString(",")})",
            messageIDs.map { "$it" }.toTypedArray()
        )

        database.setTransactionSuccessful()
        database.endTransaction()
    }

    /**
     * @return pair of sms or mms table-specific ID and whether it is in SMS table
     */
    fun getMessageID(serverID: Long, threadID: Long): Pair<Long, Boolean>? {
        val database = databaseHelper.readableDatabase
        val mappingResult = database.get(messageThreadMappingTable, "${Companion.serverID} = ? AND ${Companion.threadID} = ?",
                arrayOf(serverID.toString(), threadID.toString())) { cursor ->
            cursor.getInt(messageID) to cursor.getInt(Companion.serverID)
        } ?: return null

        val (mappedID, mappedServerID) = mappingResult

        return database.get(messageIDTable,
                "$messageID = ? AND ${Companion.serverID} = ?",
                arrayOf(mappedID.toString(), mappedServerID.toString())) { cursor ->
            cursor.getInt(messageID).toLong() to (cursor.getInt(messageType) == SMS_TYPE)
        }
    }

    fun getMessageIDs(serverIDs: List<Long>, threadID: Long): Pair<List<Long>, List<Long>> {
        val database = databaseHelper.readableDatabase

        // Retrieve the message ids
        val messageIdCursor = database
            .rawQuery(
                """
                    SELECT ${messageThreadMappingTable}.${messageID}, ${messageIDTable}.${messageType}
                    FROM ${messageThreadMappingTable}
                    JOIN ${messageIDTable} ON ${messageIDTable}.message_id = ${messageThreadMappingTable}.${messageID} 
                    WHERE (
                        ${messageThreadMappingTable}.${Companion.threadID} = $threadID AND
                        ${messageThreadMappingTable}.${Companion.serverID} IN (${serverIDs.joinToString(",")})
                    )
                """
            )

        val smsMessageIds: MutableList<Long> = mutableListOf()
        val mmsMessageIds: MutableList<Long> = mutableListOf()
        while (messageIdCursor.moveToNext()) {
            if (messageIdCursor.getInt(1) == SMS_TYPE) {
                smsMessageIds.add(messageIdCursor.getLong(0))
            }
            else {
                mmsMessageIds.add(messageIdCursor.getLong(0))
            }
        }

        return Pair(smsMessageIds, mmsMessageIds)
    }

    override fun setServerID(messageID: Long, serverID: Long, isSms: Boolean) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(3)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.serverID, serverID)
        contentValues.put(messageType, if (isSms) SMS_TYPE else MMS_TYPE)
        database.insertWithOnConflict(messageIDTable, null, contentValues, CONFLICT_REPLACE)
    }

    fun getOriginalThreadID(messageID: Long): Long {
        val database = databaseHelper.readableDatabase
        return database.get(messageThreadMappingTable, "${Companion.messageID} = ?", arrayOf(messageID.toString())) { cursor ->
            cursor.getInt(threadID)
        }?.toLong() ?: -1L
    }

    fun setOriginalThreadID(messageID: Long, serverID: Long, threadID: Long) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(3)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.serverID, serverID)
        contentValues.put(Companion.threadID, threadID)
        database.insertWithOnConflict(messageThreadMappingTable, null, contentValues, CONFLICT_REPLACE)
    }

    fun getErrorMessage(messageID: Long): String? {
        val database = databaseHelper.readableDatabase
        return database.get(errorMessageTable, "${Companion.messageID} = ?", arrayOf(messageID.toString())) { cursor ->
            cursor.getString(errorMessage)
        }
    }

    fun setErrorMessage(messageID: Long, errorMessage: String) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.errorMessage, errorMessage)
        database.insertOrUpdate(errorMessageTable, contentValues, "${Companion.messageID} = ?", arrayOf(messageID.toString()))
    }

    fun clearErrorMessage(messageID: Long) {
        val database = databaseHelper.writableDatabase
        database.delete(errorMessageTable, "${Companion.messageID} = ?", arrayOf(messageID.toString()))
    }

    fun deleteThread(threadId: Long) {
        val database = databaseHelper.writableDatabase
        try {
            val messages = mutableSetOf<Pair<Long,Long>>()
            database.get(messageThreadMappingTable,  "$threadID = ?", arrayOf(threadId.toString())) { cursor ->
                // for each add
                while (cursor.moveToNext()) {
                    messages.add(cursor.getLong(messageID) to cursor.getLong(serverID))
                }
            }
            database.beginTransaction()
            messages.forEach { (messageId, serverId) ->
                database.delete(messageIDTable, "$messageID = ? AND $serverID = ?", arrayOf(messageId.toString(), serverId.toString()))
            }
            database.delete(messageThreadMappingTable, "$threadID = ?", arrayOf(threadId.toString()))
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun getSendersForHashes(threadId: Long, hashes: Set<String>): List<ServerHashToMessageId> {
        @Language("RoomSql")
        val query = """
             WITH 
                 sender_hash_mapping AS (
                    SELECT
                         sms_hash_table.$serverHash AS hash, 
                         sms.${MmsSmsColumns.ID} AS message_id,
                         sms.${MmsSmsColumns.ADDRESS} AS sender,
                         sms.${SmsDatabase.TYPE} AS type,
                         true AS is_sms
                    FROM $smsHashTable sms_hash_table
                    LEFT OUTER JOIN ${SmsDatabase.TABLE_NAME} sms ON sms_hash_table.${messageID} = sms.${MmsSmsColumns.ID}
                    WHERE sms.${MmsSmsColumns.THREAD_ID} = :threadId
                     
                    UNION ALL
                     
                    SELECT 
                         mms_hash_table.$serverHash, 
                         mms.${MmsSmsColumns.ID},
                         mms.${MmsSmsColumns.ADDRESS},
                         mms.${MmsDatabase.MESSAGE_TYPE},
                         false
                    FROM $mmsHashTable mms_hash_table
                    LEFT OUTER JOIN ${MmsDatabase.TABLE_NAME} mms ON mms_hash_table.${messageID} = mms.${MmsSmsColumns.ID}
                    WHERE mms.${MmsSmsColumns.THREAD_ID} = :threadId
                 ) 
             SELECT * FROM sender_hash_mapping
             WHERE hash IN (SELECT value FROM json_each(:hashes))
        """.trimIndent()

        val result = databaseHelper.readableDatabase.query(query, arrayOf(threadId, JSONArray(hashes).toString()))
            .use { cursor ->
                cursor.asSequence()
                    .map {
                        ServerHashToMessageId(
                            serverHash = cursor.getString(0),
                            messageId = cursor.getLong(1),
                            sender = cursor.getString(2),
                            isSms = cursor.getInt(4) == 1,
                            isOutgoing = MmsSmsColumns.Types.isOutgoingMessageType(cursor.getLong(3))
                        )
                    }
                    .toList()
            }

        return result
    }

    fun getMessageServerHash(messageID: Long, mms: Boolean): String? = getMessageTables(mms).firstNotNullOfOrNull {
        databaseHelper.readableDatabase.get(it, "${Companion.messageID} = ?", arrayOf(messageID.toString())) { cursor ->
            cursor.getString(serverHash)
        }
    }

    fun setMessageServerHash(messageID: Long, mms: Boolean, serverHash: String) {
        val contentValues = ContentValues(2).apply {
            put(Companion.messageID, messageID)
            put(Companion.serverHash, serverHash)
        }

        databaseHelper.writableDatabase.apply {
            insertOrUpdate(getMessageTable(mms), contentValues, "${Companion.messageID} = ?", arrayOf(messageID.toString()))
        }
    }

    fun deleteMessageServerHash(messageID: Long, mms: Boolean) {
        getMessageTables(mms).firstOrNull {
            databaseHelper.writableDatabase.delete(it, "${Companion.messageID} = ?", arrayOf(messageID.toString())) > 0
        }
    }

    fun deleteMessageServerHashes(messageIDs: List<Long>, mms: Boolean) {
        databaseHelper.writableDatabase.delete(
            getMessageTable(mms),
            "${Companion.messageID} IN (${messageIDs.joinToString(",") { "?" }})",
            messageIDs.map { "$it" }.toTypedArray()
        )
    }

    fun addGroupInviteReferrer(groupThreadId: Long, referrerSessionId: String) {
        val contentValues = ContentValues(2).apply {
            put(threadID, groupThreadId)
            put(invitingSessionId, referrerSessionId)
        }
        databaseHelper.writableDatabase.insertOrUpdate(
            groupInviteTable, contentValues, "$threadID = ?", arrayOf(groupThreadId.toString())
        )
    }

    fun groupInviteReferrer(groupThreadId: Long): String? {
        return databaseHelper.readableDatabase.get(groupInviteTable, "$threadID = ?", arrayOf(groupThreadId.toString())) {cursor ->
            cursor.getString(invitingSessionId)
        }
    }

    fun deleteGroupInviteReferrer(groupThreadId: Long) {
        databaseHelper.writableDatabase.delete(
            groupInviteTable, "$threadID = ?", arrayOf(groupThreadId.toString())
        )
    }
    private fun getMessageTables(mms: Boolean) = sequenceOf(
        getMessageTable(mms),
        messageHashTable
    )

    private fun getMessageTable(mms: Boolean) = if (mms) mmsHashTable else smsHashTable
}