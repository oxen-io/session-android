package org.session.libsession.messaging.file_server

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log

object FileServerApi {

    private const val serverPublicKey = "da21e1d886c6fbaea313f75298bd64aab03a97ce985b46bb2dad9f2089c8ee59"
    const val server = "http://filev2.getsession.org"
    const val maxFileSize = 10_000_000 // 10 MB
    /**
     * The file server has a file size limit of `maxFileSize`, which the Service Nodes try to enforce as well. However, the limit applied by the Service Nodes
     * is on the **HTTP request** and not the actual file size. Because the file server expects the file data to be base 64 encoded, the size of the HTTP
     * request for a given file will be at least `ceil(n / 3) * 4` bytes, where n is the file size in bytes. This is the minimum size because there might also
     * be other parameters in the request. On average the multiplier appears to be about 1.5, so when checking whether the file will exceed the file size limit when
     * uploading a file we just divide the size of the file by this number. The alternative would be to actually check the size of the HTTP request but that's only
     * possible after proof of work has been calculated and the onion request encryption has happened, which takes several seconds.
     */
    const val fileSizeORMultiplier = 2 // TODO: It should be possible to set this to 1.5?

    sealed class Error(message: String) : Exception(message) {
        object ParsingFailed : Error("Invalid response.")
        object InvalidURL : Error("Invalid URL.")
    }

    data class Request(
            val verb: HTTP.Verb,
            val endpoint: String,
            val queryParameters: Map<String, String> = mapOf(),
            val parameters: Any? = null,
            val headers: Map<String, String> = mapOf(),
            val body: ByteArray? = null,
            /**
         * Always `true` under normal circumstances. You might want to disable
         * this when running over Lokinet.
         */
        val useOnionRouting: Boolean = true
    )

    private fun createBody(body: ByteArray?, parameters: Any?): RequestBody? {
        if (body != null) return RequestBody.create(MediaType.get("application/octet-stream"), body)
        if (parameters == null) return null
        val parametersAsJSON = JsonUtil.toJson(parameters)
        return RequestBody.create(MediaType.get("application/json"), parametersAsJSON)
    }

    private fun send(request: Request): Promise<ByteArray, Exception> {
        val url = HttpUrl.parse(server) ?: return Promise.ofFail(Error.InvalidURL)
        val urlBuilder = HttpUrl.Builder()
            .scheme(url.scheme())
            .host(url.host())
            .port(url.port())
            .addPathSegments(request.endpoint)
        if (request.verb == HTTP.Verb.GET) {
            for ((key, value) in request.queryParameters) {
                urlBuilder.addQueryParameter(key, value)
            }
        }
        val requestBuilder = okhttp3.Request.Builder()
            .url(urlBuilder.build())
            .headers(Headers.of(request.headers))
        when (request.verb) {
            HTTP.Verb.GET -> requestBuilder.get()
            HTTP.Verb.PUT -> requestBuilder.put(createBody(request.body, request.parameters)!!)
            HTTP.Verb.POST -> requestBuilder.post(createBody(request.body, request.parameters)!!)
            HTTP.Verb.DELETE -> requestBuilder.delete(createBody(request.body, request.parameters))
        }
        return if (request.useOnionRouting) {
            OnionRequestAPI.sendOnionRequest(requestBuilder.build(), server, serverPublicKey).map {
                it.body ?: throw Error.ParsingFailed
            }.fail { e ->
                Log.e("Loki", "File server request failed.", e)
            }
        } else {
            Promise.ofFail(IllegalStateException("It's currently not allowed to send non onion routed requests."))
        }
    }

    fun upload(file: ByteArray): Promise<Long, Exception> {
        val request = Request(
            verb = HTTP.Verb.POST,
            endpoint = "file",
            body = file,
            headers = mapOf(
                "Content-Disposition" to "attachment",
                "Content-Type" to "application/octet-stream"
            )
        )
        return send(request).map { response ->
            val json = JsonUtil.fromJson(response, Map::class.java)
            (json["id"] as? String)?.toLong() ?: throw Error.ParsingFailed
        }
    }

    fun download(file: String): Promise<ByteArray, Exception> {
        val request = Request(verb = HTTP.Verb.GET, endpoint = "file/$file")
        return send(request)
    }
}