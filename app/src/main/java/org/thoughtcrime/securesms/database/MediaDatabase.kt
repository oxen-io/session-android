package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.thoughtcrime.securesms.database.MmsSmsColumns.ADDRESS
import org.thoughtcrime.securesms.database.MmsSmsColumns.THREAD_ID
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.dependencies.DatabaseComponent.Companion.get

class MediaDatabase(context: Context?, databaseHelper: SQLCipherOpenHelper?) : Database(
    context!!, databaseHelper!!
) {
    fun getGalleryMediaForThread(threadId: Long): Cursor {
        val database: SQLiteDatabase = databaseHelper.readableDatabase
        val cursor = database.rawQuery(GALLERY_MEDIA_QUERY, arrayOf(threadId.toString() + ""))
        setNotifyConversationListeners(cursor, threadId)
        return cursor
    }

    fun subscribeToMediaChanges(observer: ContentObserver) {
        registerAttachmentListeners(observer)
    }

    fun unsubscribeToMediaChanges(observer: ContentObserver) {
        context.contentResolver.unregisterContentObserver(observer)
    }

    fun getDocumentMediaForThread(threadId: Long): Cursor =
        databaseHelper.readableDatabase.rawQuery(DOCUMENT_MEDIA_QUERY, arrayOf(threadId.toString() + ""))
            .also { setNotifyConversationListeners(it, threadId) }

    class MediaRecord private constructor(
      @JvmField val attachment: DatabaseAttachment?,
      @JvmField val address: Address?,
      @JvmField val date: Long,
      val isOutgoing: Boolean
    ) {
        val contentType: String
            get() = attachment!!.contentType

        companion object {
            @JvmStatic
            fun from(context: Context, cursor: Cursor): MediaRecord {
                val attachmentDatabase = get(context).attachmentDatabase()
                val attachments = attachmentDatabase.getAttachment(cursor)
                val serializedAddress = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsColumns.ADDRESS))
                val outgoing: Boolean = cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX)
                    .let(cursor::getLong)
                    .let(MmsSmsColumns.Types::isOutgoingMessageType)
                val address: Address? = serializedAddress?.let(::fromSerialized)

                val date = when {
                    cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX)
                        .let(cursor::getLong)
                        .let(MmsSmsColumns.Types::isPushType) -> MmsDatabase.DATE_SENT
                    else -> MmsDatabase.DATE_RECEIVED
                }.let(cursor::getColumnIndexOrThrow).let(cursor::getLong)

                return MediaRecord(
                    attachments.firstOrNull(),
                    address,
                    date,
                    outgoing
                )
            }
        }
    }


    companion object {
        private val BASE_MEDIA_QUERY =
            ("SELECT " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " AS " + AttachmentDatabase.ROW_ID + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_TYPE + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.THUMBNAIL_ASPECT_RATIO + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UNIQUE_ID + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFER_STATE + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.SIZE + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FILE_NAME + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DATA + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.THUMBNAIL + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_LOCATION + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_DISPOSITION + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DIGEST + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FAST_PREFLIGHT_ID + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VOICE_NOTE + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.WIDTH + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.HEIGHT + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.QUOTE + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_PACK_ID + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_PACK_KEY + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_ID + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CAPTION + ", "
                    + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.NAME + ", "
                    + MmsDatabase.TABLE_NAME + "." + MmsDatabase.MESSAGE_BOX + ", "
                    + MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_SENT + ", "
                    + MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_RECEIVED + ", "
                    + MmsDatabase.TABLE_NAME + "." + MmsSmsColumns.ADDRESS + " "
                    + "FROM " + AttachmentDatabase.TABLE_NAME + " LEFT JOIN " + MmsDatabase.TABLE_NAME
                    + " ON " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsSmsColumns.ID + " "
                    + "WHERE " + AttachmentDatabase.MMS_ID + " IN (SELECT " + MmsSmsColumns.ID
                    + " FROM " + MmsDatabase.TABLE_NAME
                    + " WHERE " + THREAD_ID + " = ?) AND (%s) AND "
                    + AttachmentDatabase.DATA + " IS NOT NULL AND "
                    + AttachmentDatabase.QUOTE + " = 0 AND "
                    + AttachmentDatabase.STICKER_PACK_ID + " IS NULL "
                    + "ORDER BY " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " DESC")

        private val GALLERY_MEDIA_QUERY = String.format(
            BASE_MEDIA_QUERY,
            AttachmentDatabase.CONTENT_TYPE + " LIKE 'image/%' OR " + AttachmentDatabase.CONTENT_TYPE + " LIKE 'video/%'"
        )
        private val DOCUMENT_MEDIA_QUERY = String.format(
            BASE_MEDIA_QUERY, AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'image/%' AND " +
                    AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'video/%' AND " +
                    AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'audio/%' AND " +
                    AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'text/x-signal-plain'"
        )
    }
}
