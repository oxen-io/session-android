package org.thoughtcrime.securesms.attachments

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.utilities.Util
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.mms.PartAuthority
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import java.util.Properties
import java.util.StringTokenizer

class KAttachmentServer(private val context: Context,
                        private val attachment: Attachment) {
    companion object {
        const val TAG = "KAttachmentServer"
    }
    private val socket = ServerSocket(0, 0, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))).apply {
        soTimeout = 5000
    }
    private val port = socket.localPort
    private val auth = Hex.toStringCondensed(Util.getSecretBytes(16))

    fun getUri(): Uri = Uri.parse("http://127.0.0.1:$port/$auth")

    private val dispatcher = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val job = dispatcher.launch {
        while (isActive) {
            try {
                socket.accept().use { client ->
                    while (client.keepAlive) {
                        val task = StreamToMediaPlayerTask(client, "/$auth", context, attachment)
                        if (task.process()) {
                            task.execute()
                        }
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is IOException -> Log.e(TAG,"Error connecting to client", e)
                    else -> Log.e(TAG,e)
                }
            }
        }
    }

    fun stop() {
        job.cancel()
    }

    private class StreamToMediaPlayerTask(private val client: Socket,
                                          private val auth: String,
                                          private val context: Context,
                                          private val attachment: Attachment) {

        var cbSkip = 0L

        suspend fun process(): Boolean {
            val iStream = client.getInputStream()
            val bufferSize = 8192
            val buffer = ByteArray(bufferSize)
            var splitByte = 0
            var readLength = 0

            var read = iStream.read(buffer, 0, bufferSize)
            while (read > 0) {
                readLength += read
                splitByte = findHeaderEnd(buffer, readLength)
                if (splitByte > 0) break
                read = iStream.read(buffer, readLength, bufferSize - readLength)
            }
            // Create a BufferedReader for parsing the header.

            // Create a BufferedReader for parsing the header.
            val hbis = ByteArrayInputStream(buffer, 0, readLength)
            val hin = BufferedReader(InputStreamReader(hbis))

            val request = Properties()
            val parameters = Properties()
            val requestHeaders = Properties()

            try {
                decodeHeader(hin, request, parameters, requestHeaders)
            } catch (e1: InterruptedException) {
                Log.e(TAG, "Exception: " + e1.message)
                e1.printStackTrace()
            }

            for ((key, value) in requestHeaders.entries) {
                Log.i(TAG, "Header: $key : $value")
            }

            var range: String? = requestHeaders.getProperty("range")

            if (range != null) {
                Log.i(TAG, "range is: $range")
                range = range.substring(6)
                val charPos = range.indexOf('-')
                if (charPos > 0) {
                    range = range.substring(0, charPos)
                }
                cbSkip = range.toLong()
                Log.i(TAG, "range found!! $cbSkip")
            }

            if ("GET" != request["method"]) {
                Log.e(TAG, "Only GET is supported: " + request.get("method"))
                return false
            }

            val receivedAuth: String? = request.getProperty("uri")

            if (receivedAuth == null || !MessageDigest.isEqual(receivedAuth.toByteArray(), auth.toByteArray())) {
                Log.w(TAG, "Bad auth token!")
                return false
            }

            return true
        }

        suspend fun execute() {
            val inputStream = PartAuthority.getAttachmentStream(context, attachment.dataUri!!)
            val fileSize: Long = attachment.size

            var headers = ""
            if (cbSkip > 0) { // It is a seek or skip request if there's a Range
                // header
                headers += "HTTP/1.1 206 Partial Content\r\n"
                headers += """
              Content-Type: ${attachment.contentType}
              
              """.trimIndent()
                headers += "Accept-Ranges: bytes\r\n"
                headers += """
              Content-Length: ${fileSize - cbSkip}
              
              """.trimIndent()
                headers += """
              Content-Range: bytes $cbSkip-${fileSize - 1}/$fileSize
              
              """.trimIndent()
                headers += "Connection: Keep-Alive\r\n"
                headers += "\r\n"
            } else {
                headers += "HTTP/1.1 200 OK\r\n"
                headers += """
              Content-Type: ${attachment.contentType}
              
              """.trimIndent()
                headers += "Accept-Ranges: bytes\r\n"
                headers += "Content-Length: $fileSize\r\n"
                headers += "Connection: Keep-Alive\r\n"
                headers += "\r\n"
            }

            Log.i(TAG, "headers: $headers")

            var output: OutputStream? = null
            val buff = ByteArray(10 * 1024 * 1024)
            try {
                output = BufferedOutputStream(client.getOutputStream(), 32 * 1024)
                output.write(headers.toByteArray())
                inputStream.skip(cbSkip)
                //        dataSource.skipFully(data, cbSkip);//try to skip as much as possible

                // Loop as long as there's stuff to send and client has not closed
                var cbRead: Int = inputStream.read(buff, 0, buff.size)
                while (!client.isClosed && cbRead != -1) {
                    output.write(buff, 0, cbRead)
                    cbRead = inputStream.read(buff, 0, buff.size)
                }
            } catch (socketException: SocketException) {
                Log.e(
                    TAG,
                    "SocketException() thrown, proxy client has probably closed. This can exit harmlessly"
                )
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "Exception thrown from streaming task:")
                Log.e(TAG, e.javaClass.name + " : " + e.localizedMessage)
            }

            // Cleanup

            // Cleanup
            try {
                output?.close()
            } catch (e: IOException) {
                Log.e(TAG, "IOException while cleaning up streaming task:")
                Log.e(TAG, e.javaClass.name + " : " + e.localizedMessage)
                e.printStackTrace()
            }
        }

        private fun findHeaderEnd(buf: ByteArray, rlen: Int): Int {
            var splitbyte = 0
            val rByte = '\r'.code.toByte()
            val nByte = '\n'.code.toByte()
            while (splitbyte + 3 < rlen) {
                if (buf[splitbyte] == rByte && buf[splitbyte + 1] == nByte && buf[splitbyte + 2] == rByte && buf[splitbyte + 3] == nByte) return splitbyte + 4
                splitbyte++
            }
            return 0
        }

        @Throws(InterruptedException::class)
        private fun decodeHeader(
            `in`: BufferedReader, pre: Properties,
            parms: Properties, header: Properties
        ) {
            try {
                // Read the request line
                val inLine = `in`.readLine() ?: return
                val st = StringTokenizer(inLine)
                if (!st.hasMoreTokens()) Log.e(
                    TAG,
                    "BAD REQUEST: Syntax error. Usage: GET /example/file.html"
                )
                val method = st.nextToken()
                pre["method"] = method
                if (!st.hasMoreTokens()) Log.e(
                    TAG,
                    "BAD REQUEST: Missing URI. Usage: GET /example/file.html"
                )
                var uri = st.nextToken()

                // Decode parameters from the URI
                val qmi = uri.indexOf('?')
                if (qmi >= 0) {
                    decodeParms(uri.substring(qmi + 1), parms)
                    uri = decodePercent(uri.substring(0, qmi))
                } else uri = decodePercent(uri)

                // If there's another token, it's protocol version,
                // followed by HTTP headers. Ignore version but parse headers.
                // NOTE: this now forces header names lowercase since they are
                // case insensitive and vary by client.
                if (st.hasMoreTokens()) {
                    var line = `in`.readLine()
                    while (line != null && line.trim { it <= ' ' }.length > 0) {
                        val p = line.indexOf(':')
                        if (p >= 0) header[line.substring(0, p).trim { it <= ' ' }.toLowerCase()] =
                            line.substring(p + 1).trim { it <= ' ' }
                        line = `in`.readLine()
                    }
                }
                pre["uri"] = uri
            } catch (ioe: IOException) {
                Log.e(TAG, "SERVER INTERNAL ERROR: IOException: " + ioe.message)
            }
        }

        /**
         * Decodes parameters in percent-encoded URI-format ( e.g.
         * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
         * Properties. NOTE: this doesn't support multiple identical keys due to the
         * simplicity of Properties -- if you need multiples, you might want to
         * replace the Properties with a Hashtable of Vectors or such.
         */
        @Throws(InterruptedException::class)
        private fun decodeParms(parms: String?, p: Properties) {
            if (parms == null) return
            val st = StringTokenizer(parms, "&")
            while (st.hasMoreTokens()) {
                val e = st.nextToken()
                val sep = e.indexOf('=')
                if (sep >= 0) p[decodePercent(e.substring(0, sep))!!.trim { it <= ' ' }] =
                    decodePercent(e.substring(sep + 1))
            }
        }

        /**
         * Decodes the percent encoding scheme. <br></br>
         * For example: "an+example%20string" -> "an example string"
         */
        @Throws(InterruptedException::class)
        private fun decodePercent(str: String): String? {
            return try {
                val sb = StringBuffer()
                var i = 0
                while (i < str.length) {
                    val c = str[i]
                    when (c) {
                        '+' -> sb.append(' ')
                        '%' -> {
                            sb.append(
                                str.substring(i + 1, i + 3).toInt(16).toChar()
                            )
                            i += 2
                        }
                        else -> sb.append(c)
                    }
                    i++
                }
                sb.toString()
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "BAD REQUEST: Bad percent-encoding.")
                null
            }
        }

    }

}