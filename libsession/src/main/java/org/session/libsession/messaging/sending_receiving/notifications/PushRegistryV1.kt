package org.session.libsession.messaging.sending_receiving.notifications

import android.annotation.SuppressLint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import nl.komponents.kovenant.Promise
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.OnionResponse
import org.session.libsession.snode.Version
import org.session.libsession.snode.utilities.asyncPromise
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.Device
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.emptyPromise
import org.session.libsignal.utilities.retryWithUniformInterval

@SuppressLint("StaticFieldLeak")
object PushRegistryV1 {
    private val TAG = PushRegistryV1::class.java.name

    val context = MessagingModuleConfiguration.shared.context
    private const val MAX_RETRY_COUNT = 4

    private val server = Server.LEGACY

    @Suppress("OPT_IN_USAGE")
    private val scope: CoroutineScope = GlobalScope

    fun register(
        device: Device,
        isPushEnabled: Boolean = TextSecurePreferences.isPushEnabled(context),
        publicKey: String? = TextSecurePreferences.getLocalNumber(context),
        legacyGroupPublicKeys: Collection<String> = MessagingModuleConfiguration.shared.storage.getAllClosedGroupPublicKeys()
    ): Promise<*, Exception> = scope.asyncPromise {
        if (isPushEnabled) {
            retryWithUniformInterval(maxRetryCount = MAX_RETRY_COUNT) { doRegister(publicKey, device, legacyGroupPublicKeys) }
        }
    }

    private suspend fun doRegister(publicKey: String?, device: Device, legacyGroupPublicKeys: Collection<String>) {
        Log.d(TAG, "doRegister() called")

        val token = MessagingModuleConfiguration.shared.tokenFetcher.fetch()
        publicKey ?: return

        val parameters = mapOf(
            "token" to token,
            "pubKey" to publicKey,
            "device" to device.value,
            "legacyGroupPublicKeys" to legacyGroupPublicKeys
        )

        val url = "${server.url}/register_legacy_groups_only"
        val body =  JsonUtil.toJson(parameters).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        sendOnionRequest(request).await().checkError()
        Log.d(TAG, "registerV1 success")
    }

    /**
     * Unregister push notifications for 1-1 conversations as this is now done in FirebasePushManager.
     */
    fun unregister(): Promise<*, Exception> = scope.asyncPromise {
        Log.d(TAG, "unregisterV1 requested")

        retryWithUniformInterval(maxRetryCount = MAX_RETRY_COUNT) {
            val token = MessagingModuleConfiguration.shared.tokenFetcher.fetch()
            val parameters = mapOf("token" to token)
            val url = "${server.url}/unregister"
            val body = JsonUtil.toJson(parameters).toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            sendOnionRequest(request).await().checkError()
            Log.d(TAG, "unregisterV1 success")
        }
    }

    // Legacy Closed Groups

    fun subscribeGroup(
        closedGroupSessionId: String,
        isPushEnabled: Boolean = TextSecurePreferences.isPushEnabled(context),
        publicKey: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
    ) = if (isPushEnabled) {
        performGroupOperation("subscribe_closed_group", closedGroupSessionId, publicKey)
    } else emptyPromise()

    fun unsubscribeGroup(
        closedGroupPublicKey: String,
        isPushEnabled: Boolean = TextSecurePreferences.isPushEnabled(context),
        publicKey: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
    ) = if (isPushEnabled) {
        performGroupOperation("unsubscribe_closed_group", closedGroupPublicKey, publicKey)
    } else emptyPromise()

    private fun performGroupOperation(
        operation: String,
        closedGroupPublicKey: String,
        publicKey: String
    ): Promise<*, Exception> = scope.asyncPromise {
        val parameters = mapOf("closedGroupPublicKey" to closedGroupPublicKey, "pubKey" to publicKey)
        val url = "${server.url}/$operation"
        val body = JsonUtil.toJson(parameters).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        retryWithUniformInterval(MAX_RETRY_COUNT) {
            sendOnionRequest(request)
                .await()
                .checkError()
        }
    }

    private fun OnionResponse.checkError() {
        check(code != null && code != 0) {
            "error: $message."
        }
    }

    private fun sendOnionRequest(request: Request): Promise<OnionResponse, Exception> = OnionRequestAPI.sendOnionRequest(
        request,
        server.url,
        server.publicKey,
        Version.V2
    )
}
