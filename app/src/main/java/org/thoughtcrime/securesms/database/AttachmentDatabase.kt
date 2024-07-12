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
package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Pair
import androidx.annotation.VisibleForTesting
import com.bumptech.glide.Glide
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONException
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachmentAudioExtras
import org.session.libsession.utilities.MediaTypes
import org.session.libsession.utilities.Util.copy
import org.session.libsession.utilities.Util.newSingleThreadedLifoExecutor
import org.session.libsignal.utilities.ExternalStorageUtil.getCleanFileName
import org.session.libsignal.utilities.JsonUtil.SaneJSONObject
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.crypto.AttachmentSecret
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.database.model.MmsAttachmentInfo
import org.thoughtcrime.securesms.database.model.MmsAttachmentInfo.Companion.anyImages
import org.thoughtcrime.securesms.database.model.MmsAttachmentInfo.Companion.anyThumbnailNonNull
import org.thoughtcrime.securesms.dependencies.DatabaseComponent.Companion.get
import org.thoughtcrime.securesms.mms.MediaStream
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.BitmapDecodingException
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MediaUtil.ThumbnailData
import org.thoughtcrime.securesms.video.EncryptedMediaDataSource
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.LinkedList
import java.util.TreeSet
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

class AttachmentDatabase(
    context: Context?,
    databaseHelper: SQLCipherOpenHelper?,
    private val attachmentSecret: AttachmentSecret
) : Database(
    context!!, databaseHelper!!
) {
    private val thumbnailExecutor = newSingleThreadedLifoExecutor()

    @Throws(IOException::class)
    fun getAttachmentStream(attachmentId: AttachmentId, offset: Long): InputStream {
        val dataStream = getDataStream(attachmentId, DATA, offset)

        if (dataStream == null) throw IOException("No stream for: $attachmentId")
        else return dataStream
    }

    @Throws(IOException::class)
    fun getThumbnailStream(attachmentId: AttachmentId): InputStream {
        Log.d(TAG, "getThumbnailStream($attachmentId)")
        val dataStream = getDataStream(attachmentId, THUMBNAIL, 0)

        if (dataStream != null) {
            return dataStream
        }

        try {
            val generatedStream =
                thumbnailExecutor.submit(ThumbnailFetchCallable(attachmentId)).get()

            if (generatedStream == null) throw FileNotFoundException("No thumbnail stream available: $attachmentId")
            else return generatedStream
        } catch (ie: InterruptedException) {
            throw AssertionError("interrupted")
        } catch (ee: ExecutionException) {
            Log.w(TAG, ee)
            throw IOException(ee)
        }
    }

    @Throws(MmsException::class)
    fun setTransferProgressFailed(attachmentId: AttachmentId, mmsId: Long) {
        val database: SQLiteDatabase = databaseHelper.writableDatabase
        val values = ContentValues()
        values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED)

        database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings())
        notifyConversationListeners(get(context).mmsDatabase().getThreadIdForMessage(mmsId))
    }

    fun getAttachment(attachmentId: AttachmentId): DatabaseAttachment? {
        val database: SQLiteDatabase = databaseHelper.readableDatabase
        var cursor: Cursor? = null

        try {
            cursor = database.query(
                TABLE_NAME,
                PROJECTION,
                ROW_ID_WHERE,
                arrayOf(attachmentId.rowId.toString()),
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val list = getAttachment(cursor)

                if (list != null && list.size > 0) {
                    return list[0]
                }
            }

            return null
        } finally {
            cursor?.close()
        }
    }

    fun getAttachmentsForMessage(mmsId: Long): List<DatabaseAttachment> {
        val database: SQLiteDatabase = databaseHelper.readableDatabase
        val results: MutableList<DatabaseAttachment> = LinkedList()
        var cursor: Cursor? = null

        try {
            cursor = database.query(
                TABLE_NAME, PROJECTION, MMS_ID + " = ?", arrayOf(mmsId.toString() + ""),
                null, null, null
            )

            while (cursor != null && cursor.moveToNext()) {
                val attachments = getAttachment(cursor)
                for (attachment in attachments) {
                    if (attachment.isQuote) continue
                    results.add(attachment)
                }
            }

            return results
        } finally {
            cursor?.close()
        }
    }

    val pendingAttachments: List<DatabaseAttachment>
        get() {
            val database: SQLiteDatabase = databaseHelper.readableDatabase
            val attachments: MutableList<DatabaseAttachment> = LinkedList()

            var cursor: Cursor? = null
            try {
                cursor = database.query(
                    TABLE_NAME,
                    PROJECTION,
                    TRANSFER_STATE + " = ?",
                    arrayOf(AttachmentTransferProgress.TRANSFER_PROGRESS_STARTED.toString()),
                    null,
                    null,
                    null
                )
                while (cursor != null && cursor.moveToNext()) {
                    attachments.addAll(getAttachment(cursor))
                }
            } finally {
                cursor?.close()
            }

            return attachments
        }

    fun deleteAttachmentsForMessages(messageIds: Array<String?>) {
        val queryBuilder = StringBuilder()
        for (i in messageIds.indices) {
            queryBuilder.append(MMS_ID + " = ").append(messageIds[i])
            if (i + 1 < messageIds.size) {
                queryBuilder.append(" OR ")
            }
        }
        val idsAsString = queryBuilder.toString()
        val database: SQLiteDatabase = databaseHelper.readableDatabase
        var cursor: Cursor? = null
        val attachmentInfos: MutableList<MmsAttachmentInfo> = ArrayList()
        try {
            cursor = database.query(
                TABLE_NAME,
                arrayOf(DATA, THUMBNAIL, CONTENT_TYPE),
                idsAsString,
                null,
                null,
                null,
                null
            )
            while (cursor != null && cursor.moveToNext()) {
                attachmentInfos.add(
                    MmsAttachmentInfo(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2)
                    )
                )
            }
        } finally {
            cursor?.close()
        }
        deleteAttachmentsOnDisk(attachmentInfos)
        database.delete(TABLE_NAME, idsAsString, null)
        notifyAttachmentListeners()
    }

    fun deleteAttachmentsForMessage(mmsId: Long) {
        val database: SQLiteDatabase = databaseHelper.writableDatabase
        var cursor: Cursor? = null

        try {
            cursor = database.query(
                TABLE_NAME, arrayOf(DATA, THUMBNAIL, CONTENT_TYPE), MMS_ID + " = ?",
                arrayOf(mmsId.toString() + ""), null, null, null
            )

            while (cursor != null && cursor.moveToNext()) {
                deleteAttachmentOnDisk(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2)
                )
            }
        } finally {
            cursor?.close()
        }

        database.delete(TABLE_NAME, MMS_ID + " = ?", arrayOf(mmsId.toString() + ""))
        notifyAttachmentListeners()
    }

    fun deleteAttachmentsForMessages(mmsIds: LongArray?) {
        val database: SQLiteDatabase = databaseHelper.writableDatabase
        var cursor: Cursor? = null
        val mmsIdString = StringUtils.join(mmsIds, ',')

        try {
            cursor = database.query(
                TABLE_NAME, arrayOf(DATA, THUMBNAIL, CONTENT_TYPE), MMS_ID + " IN (?)",
                arrayOf(mmsIdString), null, null, null
            )

            while (cursor != null && cursor.moveToNext()) {
                deleteAttachmentOnDisk(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2)
                )
            }
        } finally {
            cursor?.close()
        }

        database.delete(TABLE_NAME, MMS_ID + " IN (?)", arrayOf(mmsIdString))
        notifyAttachmentListeners()
    }

    fun deleteAttachment(id: AttachmentId) {
        val database: SQLiteDatabase = databaseHelper.writableDatabase

        database.query(
            TABLE_NAME,
            arrayOf(DATA, THUMBNAIL, CONTENT_TYPE),
            PART_ID_WHERE,
            id.toStrings(),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor == null || !cursor.moveToNext()) {
                Log.w(TAG, "Tried to delete an attachment, but it didn't exist.")
                return
            }
            val data = cursor.getString(0)
            val thumbnail = cursor.getString(1)
            val contentType = cursor.getString(2)

            database.delete(TABLE_NAME, PART_ID_WHERE, id.toStrings())
            deleteAttachmentOnDisk(data, thumbnail, contentType)
            notifyAttachmentListeners()
        }
    }

    fun deleteAllAttachments() {
        val database: SQLiteDatabase = databaseHelper.writableDatabase
        database.delete(TABLE_NAME, null, null)

        val attachmentsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
        val attachments = attachmentsDirectory.listFiles()

        for (attachment in attachments) {
            attachment.delete()
        }

        notifyAttachmentListeners()
    }

    private fun deleteAttachmentsOnDisk(mmsAttachmentInfos: List<MmsAttachmentInfo>) {
        for ((dataFile, thumbnailFile) in mmsAttachmentInfos) {
            if (dataFile != null && !TextUtils.isEmpty(dataFile)) {
                val data = File(dataFile)
                if (data.exists()) {
                    data.delete()
                }
            }
            if (thumbnailFile != null && !TextUtils.isEmpty(thumbnailFile)) {
                val thumbnail = File(thumbnailFile)
                if (thumbnail.exists()) {
                    thumbnail.delete()
                }
            }
        }

        val anyImageType: Boolean = mmsAttachmentInfos.anyImages()
        val anyThumbnail: Boolean = mmsAttachmentInfos.anyThumbnailNonNull()

        if (anyImageType || anyThumbnail) {
            Glide.get(context).clearDiskCache()
        }
    }

    private fun deleteAttachmentOnDisk(data: String?, thumbnail: String?, contentType: String?) {
        if (!TextUtils.isEmpty(data)) {
            File(data).delete()
        }

        if (!TextUtils.isEmpty(thumbnail)) {
            File(thumbnail).delete()
        }

        if (MediaUtil.isImageType(contentType) || thumbnail != null) {
            Glide.get(context).clearDiskCache()
        }
    }

    @Throws(MmsException::class)
    fun insertAttachmentsForPlaceholder(
        mmsId: Long,
        attachmentId: AttachmentId,
        inputStream: InputStream
    ) {
        val placeholder = getAttachment(attachmentId)
        val database: SQLiteDatabase = databaseHelper.writableDatabase
        val values = ContentValues()
        val dataInfo = setAttachmentData(inputStream)

        if (placeholder != null && placeholder.isQuote && !placeholder.contentType.startsWith("image")) {
            values.put(THUMBNAIL, dataInfo.file.absolutePath)
            values.put(THUMBNAIL_RANDOM, dataInfo.random)
        } else {
            values.put(DATA, dataInfo.file.absolutePath)
            values.put(SIZE, dataInfo.length)
            values.put(DATA_RANDOM, dataInfo.random)
        }

        values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_DONE)
        values.put(CONTENT_LOCATION, null as String?)
        values.put(CONTENT_DISPOSITION, null as String?)
        values.put(DIGEST, null as ByteArray?)
        values.put(NAME, null as String?)
        values.put(FAST_PREFLIGHT_ID, null as String?)
        values.put(URL, "")

        if (database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings()) == 0) {
            dataInfo.file.delete()
        } else {
            notifyConversationListeners(get(context).mmsDatabase().getThreadIdForMessage(mmsId))
            notifyConversationListListeners()
        }

        thumbnailExecutor.submit(ThumbnailFetchCallable(attachmentId))
    }

    fun updateAttachmentAfterUploadSucceeded(id: AttachmentId, attachment: Attachment) {
        val database: SQLiteDatabase = databaseHelper.writableDatabase
        val values = ContentValues()

        values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_DONE)
        values.put(CONTENT_LOCATION, attachment.location)
        values.put(DIGEST, attachment.digest)
        values.put(CONTENT_DISPOSITION, attachment.key)
        values.put(NAME, attachment.relay)
        values.put(SIZE, attachment.size)
        values.put(FAST_PREFLIGHT_ID, attachment.fastPreflightId)
        values.put(URL, attachment.url)

        database.update(TABLE_NAME, values, PART_ID_WHERE, id.toStrings())
    }

    fun handleFailedAttachmentUpload(id: AttachmentId) {
        val database: SQLiteDatabase = databaseHelper.writableDatabase
        val values = ContentValues()

        values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED)

        database.update(TABLE_NAME, values, PART_ID_WHERE, id.toStrings())
    }

    @Throws(MmsException::class)
    fun insertAttachmentsForMessage(
        mmsId: Long,
        attachments: List<Attachment>,
        quoteAttachment: List<Attachment>
    ): Map<Attachment, AttachmentId> {
        Log.d(TAG, "insertParts(" + attachments.size + ")")

        val insertedAttachments: MutableMap<Attachment, AttachmentId> = HashMap()

        for (attachment in attachments) {
            val attachmentId = insertAttachment(mmsId, attachment, attachment.isQuote)
            insertedAttachments[attachment] = attachmentId
            Log.i(TAG, "Inserted attachment at ID: $attachmentId")
        }

        for (attachment in quoteAttachment) {
            val attachmentId = insertAttachment(mmsId, attachment, true)
            insertedAttachments[attachment] = attachmentId
            Log.i(TAG, "Inserted quoted attachment at ID: $attachmentId")
        }

        return insertedAttachments
    }

    /**
     * Insert attachments in database and return the IDs of the inserted attachments
     *
     * @param mmsId message ID
     * @param attachments attachments to persist
     * @return IDs of the persisted attachments
     * @throws MmsException
     */
    @Throws(MmsException::class)
    fun insertAttachments(mmsId: Long, attachments: List<Attachment>): List<Long> {
        Log.d(TAG, "insertParts(" + attachments.size + ")")

        val insertedAttachmentsIDs: MutableList<Long> = LinkedList()

        for (attachment in attachments) {
            val attachmentId = insertAttachment(mmsId, attachment, attachment.isQuote)
            insertedAttachmentsIDs.add(attachmentId.rowId)
            Log.i(TAG, "Inserted attachment at ID: $attachmentId")
        }

        return insertedAttachmentsIDs
    }

    @Throws(MmsException::class)
    fun updateAttachmentData(
        attachment: Attachment,
        mediaStream: MediaStream
    ): Attachment {
        val database: SQLiteDatabase = databaseHelper.writableDatabase
        val databaseAttachment = attachment as DatabaseAttachment
        var dataInfo = getAttachmentDataFileInfo(databaseAttachment.attachmentId, DATA)
            ?: throw MmsException("No attachment data found!")

        dataInfo = setAttachmentData(dataInfo.file, mediaStream.stream)

        val contentValues = ContentValues()
        contentValues.put(SIZE, dataInfo.length)
        contentValues.put(CONTENT_TYPE, mediaStream.mimeType)
        contentValues.put(WIDTH, mediaStream.width)
        contentValues.put(HEIGHT, mediaStream.height)
        contentValues.put(DATA_RANDOM, dataInfo.random)

        database.update(
            TABLE_NAME,
            contentValues,
            PART_ID_WHERE,
            databaseAttachment.attachmentId.toStrings()
        )

        return DatabaseAttachment(
            databaseAttachment.attachmentId,
            databaseAttachment.mmsId,
            databaseAttachment.hasData(),
            databaseAttachment.hasThumbnail(),
            mediaStream.mimeType,
            databaseAttachment.transferState,
            dataInfo.length,
            databaseAttachment.fileName,
            databaseAttachment.location,
            databaseAttachment.key,
            databaseAttachment.relay,
            databaseAttachment.digest,
            databaseAttachment.fastPreflightId,
            databaseAttachment.isVoiceNote,
            mediaStream.width,
            mediaStream.height,
            databaseAttachment.isQuote,
            databaseAttachment.caption,
            databaseAttachment.url
        )
    }

    fun markAttachmentUploaded(messageId: Long, attachment: Attachment) {
        val values = ContentValues(1)
        val database: SQLiteDatabase = databaseHelper.writableDatabase

        values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_DONE)
        database.update(
            TABLE_NAME,
            values,
            PART_ID_WHERE,
            (attachment as DatabaseAttachment).attachmentId.toStrings()
        )

        notifyConversationListeners(get(context).mmsDatabase().getThreadIdForMessage(messageId))
        attachment.isUploaded = true
    }

    fun setTransferState(messageId: Long, attachmentId: AttachmentId, transferState: Int) {
        val values = ContentValues(1)
        val database: SQLiteDatabase = databaseHelper.writableDatabase

        values.put(TRANSFER_STATE, transferState)
        database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings())
        notifyConversationListeners(get(context).mmsDatabase().getThreadIdForMessage(messageId))
    }

    @VisibleForTesting
    protected fun getDataStream(
        attachmentId: AttachmentId,
        dataType: String,
        offset: Long
    ): InputStream? {
        val dataInfo = getAttachmentDataFileInfo(attachmentId, dataType) ?: return null

        try {
            if (dataInfo.random != null && dataInfo.random.size == 32) {
                return ModernDecryptingPartInputStream.createFor(
                    attachmentSecret,
                    dataInfo.random,
                    dataInfo.file,
                    offset
                )
            } else {
                val stream = ClassicDecryptingPartInputStream.createFor(
                    attachmentSecret, dataInfo.file
                )
                val skipped = stream.skip(offset)

                if (skipped != offset) {
                    Log.w(TAG, "Skip failed: $skipped vs $offset")
                    return null
                }

                return stream
            }
        } catch (e: IOException) {
            Log.w(TAG, e)
            return null
        }
    }

    private fun getAttachmentDataFileInfo(attachmentId: AttachmentId, dataType: String): DataInfo? {
        val database: SQLiteDatabase = databaseHelper.readableDatabase
        var cursor: Cursor? = null

        val randomColumn = when (dataType) {
            DATA -> DATA_RANDOM
            THUMBNAIL -> THUMBNAIL_RANDOM
            else -> throw AssertionError("Unknown data type: $dataType")
        }
        try {
            cursor = database.query(
                TABLE_NAME,
                arrayOf(dataType, SIZE, randomColumn),
                PART_ID_WHERE,
                attachmentId.toStrings(),
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                if (cursor.isNull(0)) {
                    return null
                }

                return DataInfo(
                    File(cursor.getString(0)),
                    cursor.getLong(1),
                    cursor.getBlob(2)
                )
            } else {
                return null
            }
        } finally {
            cursor?.close()
        }
    }

    @Throws(MmsException::class)
    private fun setAttachmentData(uri: Uri): DataInfo {
        try {
            val inputStream = PartAuthority.getAttachmentStream(context, uri)
            return setAttachmentData(inputStream)
        } catch (e: IOException) {
            throw MmsException(e)
        }
    }

    @Throws(MmsException::class)
    private fun setAttachmentData(`in`: InputStream): DataInfo {
        try {
            val partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
            val dataFile = File.createTempFile("part", ".mms", partsDirectory)
            return setAttachmentData(dataFile, `in`)
        } catch (e: IOException) {
            throw MmsException(e)
        }
    }

    @Throws(MmsException::class)
    private fun setAttachmentData(destination: File, `in`: InputStream): DataInfo {
        try {
            val out = ModernEncryptingPartOutputStream.createFor(
                attachmentSecret, destination, false
            )
            val length = copy(`in`, out.second)

            return DataInfo(destination, length, out.first)
        } catch (e: IOException) {
            throw MmsException(e)
        }
    }

    fun getAttachment(cursor: Cursor): List<DatabaseAttachment> {
        try {
            if (cursor.getColumnIndex(ATTACHMENT_JSON_ALIAS) != -1) {
                if (cursor.isNull(cursor.getColumnIndexOrThrow(ATTACHMENT_JSON_ALIAS))) {
                    return LinkedList()
                }

                val result: MutableSet<DatabaseAttachment> =
                    TreeSet { o1: DatabaseAttachment, o2: DatabaseAttachment -> if (o1.attachmentId == o2.attachmentId) 0 else 1 }
                val array = JSONArray(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            ATTACHMENT_JSON_ALIAS
                        )
                    )
                )

                for (i in 0 until array.length()) {
                    val `object` = SaneJSONObject(array.getJSONObject(i))

                    if (!`object`.isNull(ROW_ID)) {
                        result.add(
                            DatabaseAttachment(
                                AttachmentId(
                                    `object`.getLong(ROW_ID), `object`.getLong(
                                        UNIQUE_ID
                                    )
                                ),
                                `object`.getLong(MMS_ID),
                                !TextUtils.isEmpty(`object`.getString(DATA)),
                                !TextUtils.isEmpty(`object`.getString(THUMBNAIL)),
                                `object`.getString(CONTENT_TYPE),
                                `object`.getInt(TRANSFER_STATE),
                                `object`.getLong(SIZE),
                                `object`.getString(FILE_NAME),
                                `object`.getString(CONTENT_LOCATION),
                                `object`.getString(CONTENT_DISPOSITION),
                                `object`.getString(NAME),
                                null,
                                `object`.getString(FAST_PREFLIGHT_ID),
                                `object`.getInt(VOICE_NOTE) == 1,
                                `object`.getInt(WIDTH),
                                `object`.getInt(HEIGHT),
                                `object`.getInt(QUOTE) == 1,
                                `object`.getString(CAPTION),
                                ""
                            )
                        ) // TODO: Not sure if this will break something
                    }
                }

                return ArrayList(result)
            } else {
                val urlIndex = cursor.getColumnIndex(URL)
                return listOf(
                    DatabaseAttachment(
                        AttachmentId(
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                    ROW_ID
                                )
                            ),
                            cursor.getLong(cursor.getColumnIndexOrThrow(UNIQUE_ID))
                        ),
                        cursor.getLong(cursor.getColumnIndexOrThrow(MMS_ID)),
                        !cursor.isNull(cursor.getColumnIndexOrThrow(DATA)),
                        !cursor.isNull(cursor.getColumnIndexOrThrow(THUMBNAIL)),
                        cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_TYPE)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(TRANSFER_STATE)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(SIZE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(FILE_NAME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_LOCATION)),
                        cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_DISPOSITION)),
                        cursor.getString(cursor.getColumnIndexOrThrow(NAME)),
                        cursor.getBlob(cursor.getColumnIndexOrThrow(DIGEST)),
                        cursor.getString(cursor.getColumnIndexOrThrow(FAST_PREFLIGHT_ID)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(VOICE_NOTE)) == 1,
                        cursor.getInt(cursor.getColumnIndexOrThrow(WIDTH)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(HEIGHT)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(QUOTE)) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow(CAPTION)),
                        if (urlIndex > 0) cursor.getString(urlIndex) else ""
                    )
                )
            }
        } catch (e: JSONException) {
            throw AssertionError(e)
        }
    }


    @Throws(MmsException::class)
    private fun insertAttachment(
        mmsId: Long,
        attachment: Attachment,
        quote: Boolean
    ): AttachmentId {
        Log.d(TAG, "Inserting attachment for mms id: $mmsId")

        val database: SQLiteDatabase = databaseHelper.writableDatabase
        var dataInfo: DataInfo? = null
        val uniqueId = System.currentTimeMillis()

        if (attachment.dataUri != null) {
            dataInfo = setAttachmentData(attachment.dataUri!!)
            Log.d(TAG, "Wrote part to file: " + dataInfo.file.absolutePath)
        }

        val contentValues = ContentValues()
        contentValues.put(MMS_ID, mmsId)
        contentValues.put(CONTENT_TYPE, attachment.contentType)
        contentValues.put(TRANSFER_STATE, attachment.transferState)
        contentValues.put(UNIQUE_ID, uniqueId)
        contentValues.put(CONTENT_LOCATION, attachment.location)
        contentValues.put(DIGEST, attachment.digest)
        contentValues.put(CONTENT_DISPOSITION, attachment.key)
        contentValues.put(NAME, attachment.relay)
        contentValues.put(FILE_NAME, getCleanFileName(attachment.fileName))
        contentValues.put(SIZE, attachment.size)
        contentValues.put(FAST_PREFLIGHT_ID, attachment.fastPreflightId)
        contentValues.put(VOICE_NOTE, if (attachment.isVoiceNote) 1 else 0)
        contentValues.put(WIDTH, attachment.width)
        contentValues.put(HEIGHT, attachment.height)
        contentValues.put(QUOTE, quote)
        contentValues.put(CAPTION, attachment.caption)
        contentValues.put(URL, attachment.url)

        if (dataInfo != null) {
            contentValues.put(DATA, dataInfo.file.absolutePath)
            contentValues.put(SIZE, dataInfo.length)
            contentValues.put(DATA_RANDOM, dataInfo.random)
        }

        val rowId = database.insert(TABLE_NAME, null, contentValues)
        val attachmentId = AttachmentId(rowId, uniqueId)
        val thumbnailUri = attachment.thumbnailUri
        var hasThumbnail = false

        if (thumbnailUri != null) {
            try {
                PartAuthority.getAttachmentStream(context, thumbnailUri).use { attachmentStream ->
                    val dimens = if (attachment.contentType == MediaTypes.IMAGE_GIF) {
                        Pair(attachment.width, attachment.height)
                    } else {
                        BitmapUtil.getDimensions(attachmentStream)
                    }
                    updateAttachmentThumbnail(
                        attachmentId,
                        PartAuthority.getAttachmentStream(context, thumbnailUri),
                        dimens.first.toFloat() / dimens.second.toFloat()
                    )
                    hasThumbnail = true
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to save existing thumbnail.", e)
            } catch (e: BitmapDecodingException) {
                Log.w(TAG, "Failed to save existing thumbnail.", e)
            }
        }

        if (!hasThumbnail && dataInfo != null) {
            if (MediaUtil.hasVideoThumbnail(attachment.dataUri)) {
                val bitmap = MediaUtil.getVideoThumbnail(context, attachment.dataUri)

                if (bitmap != null) {
                    val thumbnailData = ThumbnailData(bitmap)
                    updateAttachmentThumbnail(
                        attachmentId,
                        thumbnailData.toDataStream(),
                        thumbnailData.aspectRatio
                    )
                } else {
                    Log.w(
                        TAG,
                        "Retrieving video thumbnail failed, submitting thumbnail generation job..."
                    )
                    thumbnailExecutor.submit(ThumbnailFetchCallable(attachmentId))
                }
            } else {
                Log.i(TAG, "Submitting thumbnail generation job...")
                thumbnailExecutor.submit(ThumbnailFetchCallable(attachmentId))
            }
        }

        return attachmentId
    }

    @VisibleForTesting
    @Throws(MmsException::class)
    protected fun updateAttachmentThumbnail(
        attachmentId: AttachmentId,
        `in`: InputStream,
        aspectRatio: Float
    ) {
        Log.i(TAG, "updating part thumbnail for #$attachmentId")

        val thumbnailFile = setAttachmentData(`in`)

        val database: SQLiteDatabase = databaseHelper.writableDatabase
        val values = ContentValues(2)

        values.put(THUMBNAIL, thumbnailFile.file.absolutePath)
        values.put(THUMBNAIL_ASPECT_RATIO, aspectRatio)
        values.put(THUMBNAIL_RANDOM, thumbnailFile.random)

        database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings())

        val cursor = database.query(
            TABLE_NAME,
            arrayOf(MMS_ID),
            PART_ID_WHERE,
            attachmentId.toStrings(),
            null,
            null,
            null
        )

        try {
            if (cursor != null && cursor.moveToFirst()) {
                notifyConversationListeners(
                    get(context).mmsDatabase().getThreadIdForMessage(
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                MMS_ID
                            )
                        )
                    )
                )
            }
        } finally {
            cursor?.close()
        }
    }

    /**
     * Retrieves the audio extra values associated with the attachment. Only "audio/ *" mime type attachments are accepted.
     * @return the related audio extras or null in case any of the audio extra columns are empty or the attachment is not an audio.
     */
    @Synchronized
    fun getAttachmentAudioExtras(attachmentId: AttachmentId): DatabaseAttachmentAudioExtras? {
        databaseHelper.readableDatabase // We expect all the audio extra values to be present (not null) or reject the whole record.
            .query(
                TABLE_NAME,
                PROJECTION_AUDIO_EXTRAS,
                PART_ID_WHERE +
                        " AND " + AUDIO_VISUAL_SAMPLES + " IS NOT NULL" +
                        " AND " + AUDIO_DURATION + " IS NOT NULL" +
                        " AND " + PART_AUDIO_ONLY_WHERE,
                attachmentId.toStrings(),
                null, null, null, "1"
            ).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) return null
                val audioSamples: ByteArray = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(
                        AUDIO_VISUAL_SAMPLES
                    )
                )
                val duration: Long = cursor.getLong(cursor.getColumnIndexOrThrow(AUDIO_DURATION))
                return DatabaseAttachmentAudioExtras(attachmentId, audioSamples, duration)
            }
    }

    /**
     * Updates audio extra columns for the "audio/ *" mime type attachments only.
     * @return true if the update operation was successful.
     */
    @Synchronized
    fun setAttachmentAudioExtras(extras: DatabaseAttachmentAudioExtras, threadId: Long): Boolean {
        val values = ContentValues()
        values.put(AUDIO_VISUAL_SAMPLES, extras.visualSamples)
        values.put(AUDIO_DURATION, extras.durationMs)

        val alteredRows: Int = databaseHelper.writableDatabase.update(
            TABLE_NAME,
            values,
            PART_ID_WHERE + " AND " + PART_AUDIO_ONLY_WHERE,
            extras.attachmentId.toStrings()
        )

        if (threadId >= 0) {
            notifyConversationListeners(threadId)
        }

        return alteredRows > 0
    }

    /**
     * Updates audio extra columns for the "audio/ *" mime type attachments only.
     * @return true if the update operation was successful.
     */
    @Synchronized
    fun setAttachmentAudioExtras(extras: DatabaseAttachmentAudioExtras): Boolean {
        return setAttachmentAudioExtras(extras, -1) // -1 for no update
    }

    @VisibleForTesting
    internal inner class ThumbnailFetchCallable(private val attachmentId: AttachmentId) :
        Callable<InputStream?> {
        @Throws(Exception::class)
        override fun call(): InputStream? {
            Log.d(TAG, "Executing thumbnail job...")
            val stream = getDataStream(attachmentId, THUMBNAIL, 0)

            if (stream != null) {
                return stream
            }

            val attachment = getAttachment(attachmentId)

            if (attachment == null || !attachment.hasData()) {
                return null
            }

            var data: ThumbnailData? = null

            if (MediaUtil.isVideoType(attachment.contentType)) {
                data = generateVideoThumbnail(attachmentId)
            }

            if (data == null) {
                return null
            }

            updateAttachmentThumbnail(attachmentId, data.toDataStream(), data.aspectRatio)

            return getDataStream(attachmentId, THUMBNAIL, 0)
        }

        @SuppressLint("NewApi")
        private fun generateVideoThumbnail(attachmentId: AttachmentId): ThumbnailData? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                Log.w(TAG, "Video thumbnails not supported...")
                return null
            }

            val dataInfo = getAttachmentDataFileInfo(attachmentId, DATA)

            if (dataInfo == null) {
                Log.w(TAG, "No data file found for video thumbnail...")
                return null
            }

            val dataSource = EncryptedMediaDataSource(
                attachmentSecret,
                dataInfo.file,
                dataInfo.random,
                dataInfo.length
            )
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(dataSource)

            val bitmap = retriever.getFrameAtTime(1000)

            Log.i(TAG, "Generated video thumbnail...")
            return ThumbnailData(bitmap)
        }
    }

    private class DataInfo(val file: File, val length: Long, random: ByteArray) {
        val random: ByteArray? = random
    }

    companion object {
        private val TAG: String = AttachmentDatabase::class.java.simpleName

        const val TABLE_NAME: String = "part"
        const val ROW_ID: String = "_id"
        const val ATTACHMENT_JSON_ALIAS: String = "attachment_json"
        const val MMS_ID: String = "mid"
        const val CONTENT_TYPE: String = "ct"
        const val NAME: String = "name"
        const val CONTENT_DISPOSITION: String = "cd"
        const val CONTENT_LOCATION: String = "cl"
        const val DATA: String = "_data"
        const val TRANSFER_STATE: String = "pending_push"
        const val SIZE: String = "data_size"
        const val FILE_NAME: String = "file_name"
        const val THUMBNAIL: String = "thumbnail"
        const val THUMBNAIL_ASPECT_RATIO: String = "aspect_ratio"
        const val UNIQUE_ID: String = "unique_id"
        const val DIGEST: String = "digest"
        const val VOICE_NOTE: String = "voice_note"
        const val QUOTE: String = "quote"
        const val STICKER_PACK_ID: String = "sticker_pack_id"
        const val STICKER_PACK_KEY: String = "sticker_pack_key"
        const val STICKER_ID: String = "sticker_id"
        const val FAST_PREFLIGHT_ID: String = "fast_preflight_id"
        const val DATA_RANDOM: String = "data_random"
        private const val THUMBNAIL_RANDOM = "thumbnail_random"
        const val WIDTH: String = "width"
        const val HEIGHT: String = "height"
        const val CAPTION: String = "caption"
        const val URL: String = "url"
        const val DIRECTORY: String = "parts"

        // "audio/*" mime type only related columns.
        const val AUDIO_VISUAL_SAMPLES: String =
            "audio_visual_samples" // Small amount of audio byte samples to visualise the content (e.g. draw waveform).
        const val AUDIO_DURATION: String =
            "audio_duration" // Duration of the audio track in milliseconds.

        private const val PART_ID_WHERE = ROW_ID + " = ? AND " + UNIQUE_ID + " = ?"
        private const val ROW_ID_WHERE = ROW_ID + " = ?"
        private const val PART_AUDIO_ONLY_WHERE = CONTENT_TYPE + " LIKE \"audio/%\""

        private val PROJECTION = arrayOf(
            ROW_ID,
            MMS_ID, CONTENT_TYPE, NAME, CONTENT_DISPOSITION,
            CONTENT_LOCATION, DATA, THUMBNAIL, TRANSFER_STATE,
            SIZE, FILE_NAME, THUMBNAIL, THUMBNAIL_ASPECT_RATIO,
            UNIQUE_ID, DIGEST, FAST_PREFLIGHT_ID, VOICE_NOTE,
            QUOTE, DATA_RANDOM, THUMBNAIL_RANDOM, WIDTH, HEIGHT,
            CAPTION, STICKER_PACK_ID, STICKER_PACK_KEY, STICKER_ID, URL
        )

        private val PROJECTION_AUDIO_EXTRAS = arrayOf(AUDIO_VISUAL_SAMPLES, AUDIO_DURATION)

        const val CREATE_TABLE: String =
            "CREATE TABLE " + TABLE_NAME + " (" + ROW_ID + " INTEGER PRIMARY KEY, " +
                    MMS_ID + " INTEGER, " + "seq" + " INTEGER DEFAULT 0, " +
                    CONTENT_TYPE + " TEXT, " + NAME + " TEXT, " + "chset" + " INTEGER, " +
                    CONTENT_DISPOSITION + " TEXT, " + "fn" + " TEXT, " + "cid" + " TEXT, " +
                    CONTENT_LOCATION + " TEXT, " + "ctt_s" + " INTEGER, " +
                    "ctt_t" + " TEXT, " + "encrypted" + " INTEGER, " +
                    TRANSFER_STATE + " INTEGER, " + DATA + " TEXT, " + SIZE + " INTEGER, " +
                    FILE_NAME + " TEXT, " + THUMBNAIL + " TEXT, " + THUMBNAIL_ASPECT_RATIO + " REAL, " +
                    UNIQUE_ID + " INTEGER NOT NULL, " + DIGEST + " BLOB, " + FAST_PREFLIGHT_ID + " TEXT, " +
                    VOICE_NOTE + " INTEGER DEFAULT 0, " + DATA_RANDOM + " BLOB, " + THUMBNAIL_RANDOM + " BLOB, " +
                    QUOTE + " INTEGER DEFAULT 0, " + WIDTH + " INTEGER DEFAULT 0, " + HEIGHT + " INTEGER DEFAULT 0, " +
                    CAPTION + " TEXT DEFAULT NULL, " + URL + " TEXT, " + STICKER_PACK_ID + " TEXT DEFAULT NULL, " +
                    STICKER_PACK_KEY + " DEFAULT NULL, " + STICKER_ID + " INTEGER DEFAULT -1," +
                    AUDIO_VISUAL_SAMPLES + " BLOB, " + AUDIO_DURATION + " INTEGER);"

        @JvmField
        val CREATE_INDEXS: Array<String> = arrayOf(
            "CREATE INDEX IF NOT EXISTS part_mms_id_index ON " + TABLE_NAME + " (" + MMS_ID + ");",
            "CREATE INDEX IF NOT EXISTS pending_push_index ON " + TABLE_NAME + " (" + TRANSFER_STATE + ");",
            "CREATE INDEX IF NOT EXISTS part_sticker_pack_id_index ON " + TABLE_NAME + " (" + STICKER_PACK_ID + ");",
        )
    }
}
