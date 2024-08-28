package org.thoughtcrime.securesms.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.task.ProgressDialogAsyncTask
import org.session.libsignal.utilities.ExternalStorageUtil
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.showSessionDialog

/**
 * Saves attachment files to an external storage using [MediaStore] API.
 * Requires [android.Manifest.permission.WRITE_EXTERNAL_STORAGE] on API 28 and below.
 */
class SaveAttachmentTask @JvmOverloads constructor(context: Context, count: Int = 1) :
    ProgressDialogAsyncTask<SaveAttachmentTask.Attachment, Void, Pair<Int, String?>>(
        context,
        context.resources.getString(R.string.saving),
        context.resources.getString(R.string.saving)
    ) {

    companion object {
        @JvmStatic
        private val TAG = SaveAttachmentTask::class.simpleName

        private const val RESULT_SUCCESS = 0
        private const val RESULT_FAILURE = 1

        @JvmStatic
        @JvmOverloads
        fun showOneTimeWarningDialogOrSave(context: Context, count: Int = 1, onAcceptListener: () -> Unit = {}) {
            // If we've already warned the user that saved attachments can be accessed by other apps
            // then we'll just perform the save..
            val haveWarned = TextSecurePreferences.getHaveWarnedUserAboutSavingAttachments(context)
            if (haveWarned) {
                onAcceptListener()
            } else {
                // .. otherwise we'll show a warning dialog and only save if the user accepts the
                // potential risks of other apps accessing their saved attachments.
                context.showSessionDialog {
                    title(R.string.warning)
                    iconAttribute(R.attr.dialog_alert_icon)
                    text(context.getString(R.string.attachmentsWarning))
                    dangerButton(R.string.save) {
                        // Set our 'haveWarned' SharedPref and perform the save on accept
                        TextSecurePreferences.setHaveWarnedUserAboutSavingAttachments(context)
                        onAcceptListener()
                    }
                    button(R.string.cancel)
                }
            }
        }

        fun saveAttachment(context: Context, attachment: Attachment): String? {
            val contentType = checkNotNull(MediaUtil.getCorrectedMimeType(attachment.contentType))
            var fileName = attachment.fileName

            // Added for SES-2624 to prevent Android API 28 devices and lower from crashing because
            // for unknown reasons it provides us with an empty filename when saving files.
            // TODO: Further investigation into root cause and fix!
            if (fileName.isNullOrEmpty()) fileName = generateOutputFileName(contentType, attachment.date)

            fileName = sanitizeOutputFileName(fileName)
            val outputUri: Uri = getMediaStoreContentUriForType(contentType)
            val mediaUri = createOutputUri(context, outputUri, contentType, fileName)
            val updateValues = ContentValues()
            PartAuthority.getAttachmentStream(context, attachment.uri).use { inputStream ->
                if (inputStream == null) {
                    return null
                }
                if (outputUri.scheme == ContentResolver.SCHEME_FILE) {
                    FileOutputStream(mediaUri!!.path).use { outputStream ->
                        StreamUtil.copy(inputStream, outputStream)
                        MediaScannerConnection.scanFile(context, arrayOf(mediaUri.path), arrayOf(contentType), null)
                    }
                } else {
                    context.contentResolver.openOutputStream(mediaUri!!, "w").use { outputStream ->
                        val total: Long = StreamUtil.copy(inputStream, outputStream)
                        if (total > 0) {
                            updateValues.put(MediaStore.MediaColumns.SIZE, total)
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT > 28) {
                updateValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (updateValues.size() > 0) {
                context.contentResolver.update(mediaUri!!, updateValues, null, null)
            }
            return outputUri.lastPathSegment
        }

        private fun generateOutputFileName(contentType: String, timestamp: Long): String {
            val mimeTypeMap = MimeTypeMap.getSingleton()
            val extension = mimeTypeMap.getExtensionFromMimeType(contentType) ?: "attach"
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd-HHmmss")
            val base = "session-${dateFormatter.format(timestamp)}"

            return "${base}.${extension}";
        }

        private fun sanitizeOutputFileName(fileName: String): String {
            return File(fileName).name
        }

        private fun getMediaStoreContentUriForType(contentType: String): Uri {
            return when {
                contentType.startsWith("video/") ->
                    ExternalStorageUtil.getVideoUri()
                contentType.startsWith("audio/") ->
                    ExternalStorageUtil.getAudioUri()
                contentType.startsWith("image/") ->
                    ExternalStorageUtil.getImageUri()
                else ->
                    ExternalStorageUtil.getDownloadUri()
            }
        }

        private fun createOutputUri(context: Context, outputUri: Uri, contentType: String, fileName: String): Uri? {

            // TODO: This method may pass an empty string as the filename in Android API 28 and below. This requires
            // TODO: follow-up investigation, but has temporarily been worked around, see:
            // TODO: https://github.com/oxen-io/session-android/commit/afbb71351a74220c312a09c25cc1c79738453c12

            val fileParts: Array<String> = getFileNameParts(fileName)
            val base = fileParts[0]
            val extension = fileParts[1]
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            contentValues.put(MediaStore.MediaColumns.DATE_ADDED, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))
            contentValues.put(MediaStore.MediaColumns.DATE_MODIFIED, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))
            if (Build.VERSION.SDK_INT > 28) {
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else if (outputUri.scheme == ContentResolver.SCHEME_FILE) {
                val outputDirectory = File(outputUri.path)
                var outputFile = File(outputDirectory, "$base.$extension")
                var i = 0
                while (outputFile.exists()) {
                    outputFile = File(outputDirectory, base + "-" + ++i + "." + extension)
                }
                if (outputFile.isHidden) {
                    throw IOException("Specified name would not be visible")
                }
                return Uri.fromFile(outputFile)
            } else {
                var outputFileName = fileName
                var dataPath = String.format("%s/%s", getExternalPathToFileForType(context, contentType), outputFileName)
                var i = 0
                while (pathTaken(context, outputUri, dataPath)) {
                    Log.d(TAG, "The content exists. Rename and check again.")
                    outputFileName = base + "-" + ++i + "." + extension
                    dataPath = String.format("%s/%s", getExternalPathToFileForType(context, contentType), outputFileName)
                }
                contentValues.put(MediaStore.MediaColumns.DATA, dataPath)
            }
            return context.contentResolver.insert(outputUri, contentValues)
        }

        private fun getExternalPathToFileForType(context: Context, contentType: String): String {
            val storage: File = when {
                contentType.startsWith("video/") ->
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!
                contentType.startsWith("audio/") ->
                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)!!
                contentType.startsWith("image/") ->
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
                else ->
                    context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)!!
            }
            return storage.absolutePath
        }

        private fun getFileNameParts(fileName: String): Array<String> {
            val tokens = fileName.split("\\.(?=[^\\.]+$)".toRegex()).toTypedArray()
            return arrayOf(tokens[0], if (tokens.size > 1) tokens[1] else "")
        }

        private fun pathTaken(context: Context, outputUri: Uri, dataPath: String): Boolean {
            context.contentResolver.query(outputUri, arrayOf(MediaStore.MediaColumns.DATA),
                MediaStore.MediaColumns.DATA + " = ?", arrayOf(dataPath),
                null).use { cursor ->
                if (cursor == null) {
                    throw IOException("Something is wrong with the filename to save")
                }
                return cursor.moveToFirst()
            }
        }
    }

    private val contextReference = WeakReference(context)
    private val attachmentCount: Int = count

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg attachments: Attachment?): Pair<Int, String?> {
        if (attachments.isEmpty()) {
            throw IllegalArgumentException("Must pass in at least one attachment")
        }

        try {
            val context = contextReference.get()
            var directory: String? = null

            if (context == null) {
                return Pair(RESULT_FAILURE, null)
            }

            for (attachment in attachments) {
                if (attachment != null) {
                    directory = saveAttachment(context, attachment)
                    if (directory == null) return Pair(RESULT_FAILURE, null)
                }
            }

            return if (attachments.size > 1)
                Pair(RESULT_SUCCESS, null)
            else
                Pair(RESULT_SUCCESS, directory)
        } catch (e: IOException) {
            Log.w(TAG, e)
            return Pair(RESULT_FAILURE, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPostExecute(result: Pair<Int, String?>) {
        super.onPostExecute(result)
        val context = contextReference.get() ?: return

        when (result.first) {
            RESULT_FAILURE -> {
                val message = context.resources.getString(R.string.attachmentsSaveError)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }

            RESULT_SUCCESS -> {
                val message = context.resources.getString(R.string.saved)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }

            else -> throw IllegalStateException("Unexpected result value: " + result.first)
        }
    }

    data class Attachment(val uri: Uri, val contentType: String, val date: Long, val fileName: String?)

}