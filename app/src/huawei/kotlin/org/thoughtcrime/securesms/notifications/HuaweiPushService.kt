package org.thoughtcrime.securesms.notifications

import android.os.Bundle
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.messaging.notifications.TokenFetcher
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import javax.inject.Inject

private val TAG = HuaweiPushService::class.java.simpleName

@AndroidEntryPoint
class HuaweiPushService: HmsMessageService() {
    @Inject lateinit var tokenFetcher: TokenFetcher
    @Inject lateinit var pushReceiver: PushReceiver

    override fun onMessageReceived(message: RemoteMessage?) {
        Log.d(TAG, "onMessageReceived")
        message?.dataOfMap?.takeIf { it.isNotEmpty() }?.let(pushReceiver::onPushDataReceived) ?:
        pushReceiver.onPushDataReceived(message?.data?.let(Base64::decode))
    }

    override fun onNewToken(token: String?) {
        if (token != null) {
            tokenFetcher.onNewToken(token)
        }
    }

    override fun onNewToken(token: String?, bundle: Bundle?) {
        Log.d(TAG, "New HCM token: $token.")
        if (token != null) {
            tokenFetcher.onNewToken(token)
        }
    }
}
