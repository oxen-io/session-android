package org.thoughtcrime.securesms.notifications

import android.os.Bundle
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONException
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import java.lang.Exception
import javax.inject.Inject

private val TAG = HuaweiPushService::class.java.simpleName

@AndroidEntryPoint
class HuaweiPushService: HmsMessageService() {
    @Inject lateinit var pushRegistry: PushRegistry
    @Inject lateinit var pushReceiver: PushReceiver

    override fun onMessageReceived(message: RemoteMessage?) {
        Log.d(TAG, "onMessageReceived")
        message?.dataOfMap?.takeIf { it.isNotEmpty() }?.let(pushReceiver::onPush) ?:
        pushReceiver.onPush(message?.data?.let(Base64::decode))
    }

    override fun onNewToken(token: String?) {
        pushRegistry.register(token)
    }

    override fun onNewToken(token: String?, bundle: Bundle?) {
        Log.d(TAG, "New HCM token: $token.")
        pushRegistry.register(token)
    }

    override fun onDeletedMessages() {
        Log.d(TAG, "onDeletedMessages")
        pushRegistry.refresh(false)
    }
}
