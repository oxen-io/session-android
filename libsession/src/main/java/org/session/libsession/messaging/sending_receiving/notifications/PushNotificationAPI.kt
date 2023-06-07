package org.session.libsession.messaging.sending_receiving.notifications

import android.annotation.SuppressLint
import nl.komponents.kovenant.functional.map
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.Version
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.retryIfNeeded
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log

@SuppressLint("StaticFieldLeak")
object PushNotificationAPI {
    val context = MessagingModuleConfiguration.shared.context
    const val server = "https://live.apns.getsession.org"
    const val serverPublicKey = "642a6585919742e5a2d4dc51244964fbcd8bcab2b75612407de58b810740d049"
    private const val maxRetryCount = 4

    enum class ClosedGroupOperation(val rawValue: String) {
        Subscribe("subscribe_closed_group"),
        Unsubscribe("unsubscribe_closed_group");
    }

    fun performOperation(operation: ClosedGroupOperation, closedGroupPublicKey: String, publicKey: String) {
        if (!TextSecurePreferences.isUsingFCM(context)) return
        val parameters = mapOf( "closedGroupPublicKey" to closedGroupPublicKey, "pubKey" to publicKey )
        val url = "$server/${operation.rawValue}"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body)
        retryIfNeeded(maxRetryCount) {
            OnionRequestAPI.sendOnionRequest(request.build(), server, serverPublicKey, Version.V2).map { response ->
                val code = response.info["code"] as? Int
                if (code == null || code == 0) {
                    Log.d("Loki", "Couldn't subscribe/unsubscribe closed group: $closedGroupPublicKey due to error: ${response.info["message"] as? String ?: "null"}.")
                }
            }.fail { exception ->
                Log.d("Loki", "Couldn't subscribe/unsubscribe closed group: $closedGroupPublicKey due to error: ${exception}.")
            }
        }
    }
}
