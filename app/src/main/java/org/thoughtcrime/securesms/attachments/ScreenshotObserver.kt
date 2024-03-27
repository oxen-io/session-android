package org.thoughtcrime.securesms.attachments

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.jobmanager.impl.JsonDataSerializer

private const val TAG = "ScreenshotObserver"

class ScreenshotObserver(private val context: Context, handler: Handler, private val screenshotTriggered: ()->Unit): ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        uri ?: return

        // There is an odd bug where we can get notified for changes to 'content://media/external'
        // directly which is a protected folder, this code is to prevent that crash
        if (uri.scheme == "content" && uri.host == "media" && uri.path == "/external") { return }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryRelativeDataColumn(uri)
        } else {
            queryDataColumn(uri)
        }
    }

    private val cache = mutableSetOf<Int>()

    private fun queryDataColumn(uri: Uri) {
        val projection = arrayOf(
            MediaStore.Images.Media.DATA
        )
        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    if (path.contains("screenshot", true)) {
                        if (cache.add(uri.hashCode())) {
                            screenshotTriggered()
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryRelativeDataColumn(uri: Uri) {
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val relativePathColumn =
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                val displayNameColumn =
                    cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(displayNameColumn)
                    val relativePath = cursor.getString(relativePathColumn)
                    if (name.contains("screenshot", true) or
                        relativePath.contains("screenshot", true)) {
                        if (cache.add(uri.hashCode())) {
                            screenshotTriggered()
                        }
                    }
                }
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, e)
        }
    }
}
