package org.thoughtcrime.securesms.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.session.libsession.messaging.sending_receiving.notifications.Response
import org.session.libsession.messaging.sending_receiving.notifications.Server
import org.session.libsession.messaging.sending_receiving.notifications.SubscriptionRequest
import org.session.libsession.messaging.sending_receiving.notifications.SubscriptionResponse
import org.session.libsession.messaging.sending_receiving.notifications.UnsubscribeResponse
import org.session.libsession.messaging.sending_receiving.notifications.UnsubscriptionRequest
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeClock
import org.session.libsession.snode.SwarmAuth
import org.session.libsession.snode.Version
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.Device
import org.session.libsignal.utilities.retryWithUniformInterval
import javax.inject.Inject
import javax.inject.Singleton

private const val maxRetryCount = 4

@Singleton
class PushRegistryV2 @Inject constructor(
    private val pushReceiver: PushReceiver,
    private val device: Device,
    private val clock: SnodeClock,
    ) {
    suspend fun register(
        token: String,
        swarmAuth: SwarmAuth,
        namespaces: List<Int>
    ) {
        val pnKey = pushReceiver.getOrCreateNotificationKey()

        val timestamp = clock.currentTimeMills() / 1000 // get timestamp in ms -> s
        val publicKey = swarmAuth.accountId.hexString
        val signed = swarmAuth.sign(
            "MONITOR${publicKey}${timestamp}1${namespaces.joinToString(separator = ",")}".encodeToByteArray()
        )
        val requestParameters = SubscriptionRequest(
            pubkey = publicKey,
            session_ed25519 = swarmAuth.ed25519PublicKeyHex,
            namespaces = namespaces,
            data = true, // only permit data subscription for now (?)
            service = device.service,
            sig_ts = timestamp,
            service_info = mapOf("token" to token),
            enc_key = pnKey.asHexString,
        ).let(Json::encodeToJsonElement).jsonObject + signed

        val response = retryResponseBody<SubscriptionResponse>(
            "subscribe",
            Json.encodeToString(requestParameters)
        )

        check(response.isSuccess()) {
            "Error subscribing to push notifications: ${response.message}"
        }
    }

    suspend fun unregister(
        token: String,
        swarmAuth: SwarmAuth
    ) {
        val publicKey = swarmAuth.accountId.hexString
        val timestamp = clock.currentTimeMills() / 1000 // get timestamp in ms -> s
        // if we want to support passing namespace list, here is the place to do it
        val signature = swarmAuth.signForPushRegistry(
            "UNSUBSCRIBE${publicKey}${timestamp}".encodeToByteArray()
        )

        val requestParameters = UnsubscriptionRequest(
            pubkey = publicKey,
            session_ed25519 = swarmAuth.ed25519PublicKeyHex,
            service = device.service,
            sig_ts = timestamp,
            service_info = mapOf("token" to token),
        ).let(Json::encodeToJsonElement).jsonObject + signature

        val response: UnsubscribeResponse = retryResponseBody("unsubscribe", Json.encodeToString(requestParameters))

        check(response.isSuccess()) {
            "Error unsubscribing to push notifications: ${response.message}"
        }
    }

    private operator fun JsonObject.plus(additional: Map<String, String>): JsonObject {
        return JsonObject(buildMap {
            putAll(this@plus)
            for ((key, value) in additional) {
                put(key, JsonPrimitive(value))
            }
        })
    }

    private suspend inline fun <reified T: Response> retryResponseBody(path: String, requestParameters: String): T =
        retryWithUniformInterval(maxRetryCount = maxRetryCount) { getResponseBody(path, requestParameters) }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend inline fun <reified T: Response> getResponseBody(path: String, requestParameters: String): T {
        val server = Server.LATEST
        val url = "${server.url}/$path"
        val body = requestParameters.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        val response = OnionRequestAPI.sendOnionRequest(
            request = request,
            server = server.url,
            x25519PublicKey = server.publicKey,
            version = Version.V4
        ).await()

        return withContext(Dispatchers.IO) {
            requireNotNull(response.body) { "Response doesn't have a body" }
                .inputStream()
                .use { Json.decodeFromStream<T>(it) }
        }
    }
}