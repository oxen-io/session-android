package org.session.libsession.utilities

import okhttp3.HttpUrl
import org.session.libsession.messaging.file_server.FileServerApi
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.utilities.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object DownloadUtilities {

    /**
     * Blocks the calling thread.
     */
    @JvmStatic
    fun downloadFile(destination: File, url: String) {
        val outputStream = FileOutputStream(destination) // Throws
        var remainingAttempts = 2
        var exception: Exception? = null
        while (remainingAttempts > 0) {
            remainingAttempts -= 1
            try {
                downloadFile(outputStream, url)
                exception = null
                break
            } catch (e: Exception) {
                exception = e
            }
        }
        if (exception != null) { throw exception }
    }

    /**
     * Blocks the calling thread.
     */
    @JvmStatic
    fun downloadFile(outputStream: OutputStream, urlAsString: String) {
        val url = HttpUrl.parse(urlAsString)!!
        val fileID = url.pathSegments().last()
        try {
            FileServerApi.download(fileID).get().let {
                outputStream.write(it)
            }
        } catch (e: Exception) {
            when (e) {
                // No need for the stack trace for HTTP errors
                is HTTP.HTTPRequestFailedException -> Log.e("Loki", "Couldn't download attachment due to error: ${e.message}")
                else -> Log.e("Loki", "Couldn't download attachment", e)
            }
            throw e
        }
    }
}