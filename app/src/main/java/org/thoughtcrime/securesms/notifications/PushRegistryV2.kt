package org.thoughtcrime.securesms.notifications

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.KeyPair
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.session.libsession.messaging.sending_receiving.notifications.Response
import org.session.libsession.messaging.sending_receiving.notifications.Server
import org.session.libsession.messaging.sending_receiving.notifications.SubscriptionRequest
import org.session.libsession.messaging.sending_receiving.notifications.SubscriptionResponse
import org.session.libsession.messaging.sending_receiving.notifications.UnsubscribeResponse
import org.session.libsession.messaging.sending_receiving.notifications.UnsubscriptionRequest
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.Version
import org.session.libsession.utilities.Device
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.retryIfNeeded
import javax.inject.Inject
import javax.inject.Singleton

private val TAG = PushRegistryV2::class.java.name
private const val maxRetryCount = 4

@Singleton
class PushRegistryV2 @Inject constructor(private val pushReceiver: PushReceiver) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    fun register(
        device: Device,
        token: String,
        publicKey: String,
        userEd25519Key: KeyPair,
        namespaces: List<Int>
    ): Promise<SubscriptionResponse, Exception> {
        val pnKey = pushReceiver.getOrCreateNotificationKey()

        val timestamp = SnodeAPI.nowWithOffset / 1000 // get timestamp in ms -> s
        // if we want to support passing namespace list, here is the place to do it
        val sigData = "MONITOR${publicKey}${timestamp}1${namespaces.joinToString(separator = ",")}".encodeToByteArray()
        val signature = ByteArray(Sign.BYTES)
        sodium.cryptoSignDetached(signature, sigData, sigData.size.toLong(), userEd25519Key.secretKey.asBytes)
        val requestParameters = SubscriptionRequest(
            pubkey = publicKey,
            session_ed25519 = userEd25519Key.publicKey.asHexString,
            namespaces = listOf(Namespace.DEFAULT),
            data = true, // only permit data subscription for now (?)
            service = device.service,
            sig_ts = timestamp,
            signature = Base64.encodeBytes(signature),
            service_info = mapOf("token" to token),
            enc_key = pnKey.asHexString,
        ).let(Json::encodeToString)

        return retryResponseBody<SubscriptionResponse>("subscribe", requestParameters) success {
            Log.d(TAG, "registerV2 success")
        }
    }

    fun unregister(
        device: Device,
        token: String,
        userPublicKey: String,
        userEdKey: KeyPair
    ): Promise<UnsubscribeResponse, Exception> {
        val timestamp = SnodeAPI.nowWithOffset / 1000 // get timestamp in ms -> s
        // if we want to support passing namespace list, here is the place to do it
        val sigData = "UNSUBSCRIBE${userPublicKey}${timestamp}".encodeToByteArray()
        val signature = ByteArray(Sign.BYTES)
        sodium.cryptoSignDetached(signature, sigData, sigData.size.toLong(), userEdKey.secretKey.asBytes)

        val requestParameters = UnsubscriptionRequest(
            pubkey = userPublicKey,
            session_ed25519 = userEdKey.publicKey.asHexString,
            service = device.service,
            sig_ts = timestamp,
            signature = Base64.encodeBytes(signature),
            service_info = mapOf("token" to token),
        ).let(Json::encodeToString)

        return retryResponseBody<UnsubscribeResponse>("unsubscribe", requestParameters) success {
            Log.d(TAG, "unregisterV2 success")
        }
    }

    private inline fun <reified T: Response> retryResponseBody(path: String, requestParameters: String): Promise<T, Exception> =
        retryIfNeeded(maxRetryCount) { getResponseBody(path, requestParameters) }

    private inline fun <reified T: Response> getResponseBody(path: String, requestParameters: String): Promise<T, Exception> {
        val server = Server.LATEST
        val url = "${server.url}/$path"
        val body = RequestBody.create(MediaType.get("application/json"), requestParameters)
        val request = Request.Builder().url(url).post(body).build()

        return OnionRequestAPI.sendOnionRequest(
            request,
            server.url,
            server.publicKey,
            Version.V4
        ).map { response ->
            response.body!!.inputStream()
                .let { Json.decodeFromStream<T>(it) }
                .also { if (it.isFailure()) throw Exception("error: ${it.message}.") }
        }
    }
}