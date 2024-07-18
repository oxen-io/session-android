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
import android.text.TextUtils
import android.util.Pair
import androidx.annotation.VisibleForTesting
import com.bumptech.glide.Glide
import net.zetetic.database.sqlcipher.SQLiteDatabase
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
import org.thoughtcrime.securesms.database.model.isImage
import org.thoughtcrime.securesms.database.model.isThumbnailNonNull
import org.thoughtcrime.securesms.dependencies.DatabaseComponent.Companion.get
import org.thoughtcrime.securesms.mms.MediaStream
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.BitmapDecodingException
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MediaUtil.ThumbnailData
import org.thoughtcrime.securesms.util.map
import org.thoughtcrime.securesms.video.EncryptedMediaDataSource
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.TreeSet
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

class AttachmentDatabase(
    context: Context,
    databaseHelper: SQLCipherOpenHelper,
    private val attachmentSecret: AttachmentSecret
) : Database(context, databaseHelper) {
    private val thumbnailExecutor = newSingleThreadedLifoExecutor()

    @Throws(IOException::class)
    fun getAttachmentStream(attachmentId: AttachmentId, offset: Long): InputStream =
        getDataStream(attachmentId, DATA, offset) ?: throw IOException("No stream for: $attachmentId")

    @Throws(IOException::class)
    fun getThumbnailStream(attachmentId: AttachmentId): InputStream {
        Log.d(TAG, "getThumbnailStream($attachmentId)")
        return getDataStream(attachmentId, THUMBNAIL, 0) ?: try {
            thumbnailExecutor.submit(ThumbnailFetchCallable(attachmentId)).get() ?: throw FileNotFoundException("No thumbnail stream available: $attachmentId")
        } catch (ie: InterruptedException) {
            throw AssertionError("interrupted")
        } catch (ee: ExecutionException) {
            Log.w(TAG, ee)
            throw IOException(ee)
        }
    }

    fun getAttachment(attachmentId: AttachmentId): DatabaseAttachment? = databaseHelper.readableDatabase.query(
        TABLE_NAME,
        PROJECTION,
        ROW_ID_WHERE,
        arrayOf(attachmentId.rowId.toString()),
        null,
        null,
        null
    ).use { it.takeIf(Cursor::moveToFirst)?.let(::getAttachment)?.firstOrNull() }

    fun getAttachmentsForMessage(mmsId: Long) = databaseHelper.readableDatabase.query(
        TABLE_NAME, PROJECTION, "$MMS_ID = ?", arrayOf(mmsId.toString()),
        null, null, null
    ).use { cursor -> cursor.map(::getAttachment).flatMap { it }.filterNot { it.isQuote }.toList() }

    fun deleteAttachmentsForMessages(messageIds: Array<String?>) {
        val idsAsString = messageIds.map { "$MMS_ID = $it" }.joinToString { " OR " }
        val database: SQLiteDatabase = databaseHelper.readableDatabase
        database.query(
            TABLE_NAME,
            arrayOf(DATA, THUMBNAIL, CONTENT_TYPE),
            idsAsString,
            null,
            null,
            null,
            null
        ).use { cursor ->
            cursor.map {
                MmsAttachmentInfo(
                    it.getString(0),
                    it.getString(1),
                    it.getString(2)
                )
            }
        }.let(::deleteAttachmentsOnDisk)

        database.delete(TABLE_NAME, idsAsString, null)
        notifyAttachmentListeners()
    }

    fun deleteAttachmentsForMessage(mmsId: Long) {
        val database: SQLiteDatabase = databaseHelper.writableDatabase

        database.query(
            TABLE_NAME, arrayOf(DATA, THUMBNAIL, CONTENT_TYPE), "$MMS_ID = ?",
            arrayOf(mmsId.toString()), null, null, null
        ).use {
            while (it.moveToNext()) {
                deleteAttachmentOnDisk(it.getString(0), it.getString(1), it.getString(2))
            }
        }

        database.delete(TABLE_NAME, "$MMS_ID = ?", arrayOf(mmsId.toString()))
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
            if (!cursor.moveToNext()) {
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

    private fun deleteAttachmentsOnDisk(mmsAttachmentInfos: Sequence<MmsAttachmentInfo>) = mmsAttachmentInfos.run {
        flatMap { sequenceOf(it.dataFile, it.thumbnailFile) }
            .filterNotNull()
            .filter(String::isNotEmpty)
            .map(::File)
            .filter(File::exists).toList()
            .forEach(File::delete)

        if (any { it.isImage() || it.isThumbnailNonNull() }) {
            Glide.get(context).clearDiskCache()
        }
    }

    private fun deleteAttachmentOnDisk(data: String?, thumbnail: String?, contentType: String?) {
        data?.takeUnless(String::isEmpty)?.let(::File)?.delete()
        thumbnail?.takeUnless(String::isEmpty)?.let(::File)?.delete()

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
        val values = ContentValues()
        val dataInfo = setAttachmentData(inputStream)

        if (placeholder?.isQuote == true && !placeholder.contentType.startsWith("image")) {
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

        if (databaseHelper.writableDatabase.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings()) == 0) {
            dataInfo.file.delete()
        } else {
            notifyConversationListeners(get(context).mmsDatabase().getThreadIdForMessage(mmsId))
            notifyConversationListListeners()
        }

        thumbnailExecutor.submit(ThumbnailFetchCallable(attachmentId))
    }

    fun updateAttachmentAfterUploadSucceeded(id: AttachmentId, attachment: Attachment) {
        val values = ContentValues()

        values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_DONE)
        values.put(CONTENT_LOCATION, attachment.location)
        values.put(DIGEST, attachment.digest)
        values.put(CONTENT_DISPOSITION, attachment.key)
        values.put(NAME, attachment.relay)
        values.put(SIZE, attachment.size)
        values.put(FAST_PREFLIGHT_ID, attachment.fastPreflightId)
        values.put(URL, attachment.url)

        databaseHelper.writableDatabase.update(TABLE_NAME, values, PART_ID_WHERE, id.toStrings())
    }

    fun handleFailedAttachmentUpload(id: AttachmentId) {
        val values = ContentValues()
        values.put(TRANSFER_STATE, AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED)
        databaseHelper.writableDatabase.update(TABLE_NAME, values, PART_ID_WHERE, id.toStrings())
    }

    @Throws(MmsException::class)
    fun insertAttachmentsForMessage(
        mmsId: Long,
        attachments: List<Attachment>,
        quoteAttachment: List<Attachment>
    ): Map<Attachment, AttachmentId> {
        Log.d(TAG, "insertParts(" + attachments.size + ")")

        return attachments.associateWith { insertAttachment(mmsId, it, it.isQuote) }
            .onEach { (_, id) -> Log.i(TAG, "Inserted attachment at ID: $id") } +
                quoteAttachment.associateWith { insertAttachment(mmsId, it, true) }
                    .onEach { (_, id) -> Log.i(TAG, "Inserted quoted attachment at ID: $id") }
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

        return attachments.map {
            insertAttachment(mmsId, it, it.isQuote)
                .also { Log.i(TAG, "Inserted attachment at ID: $it") }
                .rowId
        }
    }

    @Throws(MmsException::class)
    fun updateAttachmentData(
        attachment: Attachment,
        mediaStream: MediaStream
    ): Attachment {
        attachment as DatabaseAttachment

        val dataInfo = getAttachmentDataFileInfo(attachment.attachmentId, DATA)
            .let { it ?: throw MmsException("No attachment data found!") }
            .let { setAttachmentData(it.file, mediaStream.stream) }

        val contentValues = ContentValues()
        contentValues.put(SIZE, dataInfo.length)
        contentValues.put(CONTENT_TYPE, mediaStream.mimeType)
        contentValues.put(WIDTH, mediaStream.width)
        contentValues.put(HEIGHT, mediaStream.height)
        contentValues.put(DATA_RANDOM, dataInfo.random)

        databaseHelper.writableDatabase.update(
            TABLE_NAME,
            contentValues,
            PART_ID_WHERE,
            attachment.attachmentId.toStrings()
        )

        return DatabaseAttachment(
            attachment.attachmentId,
            attachment.mmsId,
            attachment.hasData,
            attachment.hasThumbnail,
            mediaStream.mimeType,
            attachment.transferState,
            dataInfo.length,
            attachment.fileName,
            attachment.location,
            attachment.key,
            attachment.relay,
            attachment.digest,
            attachment.fastPreflightId,
            attachment.isVoiceNote,
            mediaStream.width,
            mediaStream.height,
            attachment.isQuote,
            attachment.caption,
            attachment.url
        )
    }

    fun setTransferState(messageId: Long, attachmentId: AttachmentId, transferState: Int) {
        val values = ContentValues(1)

        values.put(TRANSFER_STATE, transferState)
        databaseHelper.writableDatabase.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings())
        notifyConversationListeners(get(context).mmsDatabase().getThreadIdForMessage(messageId))
    }

    @VisibleForTesting
    fun getDataStream(
        attachmentId: AttachmentId,
        dataType: String,
        offset: Long
    ): InputStream? = getAttachmentDataFileInfo(attachmentId, dataType)?.let { dataInfo ->
        try {
            if (dataInfo.random.size == 32) ModernDecryptingPartInputStream.createFor(
                attachmentSecret,
                dataInfo.random,
                dataInfo.file,
                offset
            ) else ClassicDecryptingPartInputStream.createFor(attachmentSecret, dataInfo.file)
                .takeIf { it.skip(offset) == offset }
        } catch (e: IOException) {
            Log.w(TAG, e)
            null
        }
    }

    private fun getAttachmentDataFileInfo(attachmentId: AttachmentId, dataType: String): DataInfo? {
        val randomColumn = when (dataType) {
            DATA -> DATA_RANDOM
            THUMBNAIL -> THUMBNAIL_RANDOM
            else -> throw AssertionError("Unknown data type: $dataType")
        }

        return databaseHelper.readableDatabase.query(
            TABLE_NAME,
            arrayOf(dataType, SIZE, randomColumn),
            PART_ID_WHERE,
            attachmentId.toStrings(),
            null,
            null,
            null
        ).use { cursor ->
            cursor.takeIf { it.moveToFirst() && !it.isNull(0) }?.run {
                DataInfo(
                    File(getString(0)),
                    getLong(1),
                    getBlob(2)
                )
            }
        }
    }

    @Throws(MmsException::class)
    private fun setAttachmentData(uri: Uri): DataInfo = try {
        PartAuthority.getAttachmentStream(context, uri).let(::setAttachmentData)
    } catch (e: IOException) {
        throw MmsException(e)
    }

    @Throws(MmsException::class)
    private fun setAttachmentData(inputStream: InputStream): DataInfo = try {
        val partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
        val dataFile = File.createTempFile("part", ".mms", partsDirectory)
        setAttachmentData(dataFile, inputStream)
    } catch (e: IOException) {
        throw MmsException(e)
    }

    @Throws(MmsException::class)
    private fun setAttachmentData(destination: File, inputStream: InputStream): DataInfo = try {
        val out = ModernEncryptingPartOutputStream.createFor(
            attachmentSecret, destination, false
        )
        val length = copy(inputStream, out.second)

        DataInfo(destination, length, out.first)
    } catch (e: IOException) {
        throw MmsException(e)
    }

    fun getAttachment(cursor: Cursor): List<DatabaseAttachment> {
        try {
            if (cursor.getColumnIndex(ATTACHMENT_JSON_ALIAS) != -1) {
                if (cursor.isNull(cursor.getColumnIndexOrThrow(ATTACHMENT_JSON_ALIAS))) {
                    return emptyList()
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

                result += array.asObjectSequence().filter { !it.isNull(ROW_ID) }.map {
                    DatabaseAttachment(
                        AttachmentId(it.getLong(ROW_ID), it.getLong(UNIQUE_ID)),
                        it.getLong(MMS_ID),
                        !TextUtils.isEmpty(it.getString(DATA)),
                        !TextUtils.isEmpty(it.getString(THUMBNAIL)),
                        it.getString(CONTENT_TYPE),
                        it.getInt(TRANSFER_STATE),
                        it.getLong(SIZE),
                        it.getString(FILE_NAME),
                        it.getString(CONTENT_LOCATION),
                        it.getString(CONTENT_DISPOSITION),
                        it.getString(NAME),
                        null,
                        it.getString(FAST_PREFLIGHT_ID),
                        it.getInt(VOICE_NOTE) == 1,
                        it.getInt(WIDTH),
                        it.getInt(HEIGHT),
                        it.getInt(QUOTE) == 1,
                        it.getString(CAPTION),
                        ""
                    )
                }

                return ArrayList(result)
            } else {
                val urlIndex = cursor.getColumnIndex(URL)
                return listOf(
                    DatabaseAttachment(
                        AttachmentId(
                            cursor.getLong(cursor.getColumnIndexOrThrow(ROW_ID)),
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

        dataInfo?.run {
            contentValues.put(DATA, file.absolutePath)
            contentValues.put(SIZE, length)
            contentValues.put(DATA_RANDOM, random)
        }

        val rowId = databaseHelper.writableDatabase.insert(TABLE_NAME, null, contentValues)
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
    private fun updateAttachmentThumbnail(
        attachmentId: AttachmentId,
        inputStream: InputStream,
        aspectRatio: Float
    ) {
        Log.i(TAG, "updating part thumbnail for #$attachmentId")

        val thumbnailFile = setAttachmentData(inputStream)

        val database: SQLiteDatabase = databaseHelper.writableDatabase
        val values = ContentValues(3)

        values.put(THUMBNAIL, thumbnailFile.file.absolutePath)
        values.put(THUMBNAIL_ASPECT_RATIO, aspectRatio)
        values.put(THUMBNAIL_RANDOM, thumbnailFile.random)

        database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings())

        database.query(
            TABLE_NAME,
            arrayOf(MMS_ID),
            PART_ID_WHERE,
            attachmentId.toStrings(),
            null,
            null,
            null
        ).use { cursor ->
            cursor.takeIf { it.moveToFirst() }?.let {
                notifyConversationListeners(
                    get(context).mmsDatabase().getThreadIdForMessage(
                        it.getLong(it.getColumnIndexOrThrow(MMS_ID))
                    )
                )
            }
        }
    }

    /**
     * Retrieves the audio extra values associated with the attachment. Only "audio/ *" mime type attachments are accepted.
     * @return the related audio extras or null in case any of the audio extra columns are empty or the attachment is not an audio.
     */
    @Synchronized
    fun getAttachmentAudioExtras(attachmentId: AttachmentId): DatabaseAttachmentAudioExtras? =
        databaseHelper.readableDatabase // We expect all the audio extra values to be present (not null) or reject the whole record.
            .query(
                TABLE_NAME,
                PROJECTION_AUDIO_EXTRAS,
                "$PART_ID_WHERE AND $AUDIO_VISUAL_SAMPLES IS NOT NULL AND $AUDIO_DURATION IS NOT NULL AND $PART_AUDIO_ONLY_WHERE",
                attachmentId.toStrings(),
                null, null, null, "1"
            ).use { cursor ->
                cursor.takeIf { it.moveToFirst() }
                    ?.getBlob(cursor.getColumnIndexOrThrow(AUDIO_VISUAL_SAMPLES))
                    ?.let { audioSamples ->
                        DatabaseAttachmentAudioExtras(
                            attachmentId,
                            audioSamples,
                            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(AUDIO_DURATION))
                        )
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
            "$PART_ID_WHERE AND $PART_AUDIO_ONLY_WHERE",
            extras.attachmentId.toStrings()
        )

        if (threadId >= 0) {
            notifyConversationListeners(threadId)
        }

        return alteredRows > 0
    }

    @VisibleForTesting
    internal inner class ThumbnailFetchCallable(
        private val attachmentId: AttachmentId
    ) : Callable<InputStream?> {
        @Throws(Exception::class)
        override fun call(): InputStream? {
            Log.d(TAG, "Executing thumbnail job...")
            return getDataStream(attachmentId, THUMBNAIL, 0) ?: attachmentId.takeIf {
                getAttachment(it)?.hasVideoData == true
            }?.let(::generateVideoThumbnail)?.let {
                updateAttachmentThumbnail(attachmentId, it.toDataStream(), it.aspectRatio)
                getDataStream(attachmentId, THUMBNAIL, 0)
            }
        }

        @SuppressLint("NewApi")
        private fun generateVideoThumbnail(attachmentId: AttachmentId): ThumbnailData? =
            getAttachmentDataFileInfo(attachmentId, DATA)
                .also { it ?: Log.w(TAG, "No data file found for video thumbnail...") }
                ?.run { EncryptedMediaDataSource(attachmentSecret, file, random, length) }
                ?.let { MediaMetadataRetriever().apply { setDataSource(it) } }
                ?.getFrameAtTime(1000)
                ?.also { Log.i(TAG, "Generated video thumbnail...") }
                ?.let(::ThumbnailData)
    }

    private class DataInfo(val file: File, val length: Long, val random: ByteArray)

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
        const val AUDIO_VISUAL_SAMPLES: String = "audio_visual_samples" // Small amount of audio byte samples to visualise the content (e.g. draw waveform).
        const val AUDIO_DURATION: String = "audio_duration" // Duration of the audio track in milliseconds.

        private const val PART_ID_WHERE = "$ROW_ID = ? AND $UNIQUE_ID = ?"
        private const val ROW_ID_WHERE = "$ROW_ID = ?"
        private const val PART_AUDIO_ONLY_WHERE = "$CONTENT_TYPE LIKE \"audio/%\""

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
            "CREATE INDEX IF NOT EXISTS part_mms_id_index ON $TABLE_NAME ($MMS_ID);",
            "CREATE INDEX IF NOT EXISTS pending_push_index ON $TABLE_NAME ($TRANSFER_STATE);",
            "CREATE INDEX IF NOT EXISTS part_sticker_pack_id_index ON $TABLE_NAME ($STICKER_PACK_ID);",
        )
    }
}

private val DatabaseAttachment.hasVideoData: Boolean get() =
    takeIf { it.hasData }?.contentType?.let(MediaUtil::isVideoType) == true
