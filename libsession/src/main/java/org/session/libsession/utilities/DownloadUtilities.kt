package org.session.libsession.utilities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.session.libsession.messaging.file_server.FileServerApi
import org.session.libsession.snode.utilities.await
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.utilities.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object DownloadUtilities {

    /**
     * Blocks the calling thread.
     */
    suspend fun downloadFile(destination: File, url: String) {
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
    suspend fun downloadFile(outputStream: OutputStream, urlAsString: String) {
        val url = urlAsString.toHttpUrlOrNull()!!
        val fileID = url.pathSegments.last()
        try {
            val data = FileServerApi.download(fileID).await()
            withContext(Dispatchers.IO) {
                outputStream.write(data)
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