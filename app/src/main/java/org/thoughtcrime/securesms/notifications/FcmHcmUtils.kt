@file:JvmName("FcmHcmUtils")
package org.thoughtcrime.securesms.notifications

import com.google.android.gms.tasks.Task
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import com.huawei.hms.aaid.HmsInstanceId
import kotlinx.coroutines.*
import android.content.Context
import com.huawei.hms.common.ApiException
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.notifications.LokiPushNotificationManager.DeviceType
import org.thoughtcrime.securesms.notifications.LokiPushNotificationManager.register
import org.thoughtcrime.securesms.notifications.LokiPushNotificationManager.unregister
import org.session.libsession.utilities.TextSecurePreferences


fun getFcmInstanceId(body: (Task<InstanceIdResult>)->Unit): Job = MainScope().launch(Dispatchers.IO) {
    val task = FirebaseInstanceId.getInstance().instanceId
    while (!task.isComplete && isActive) {
        // wait for task to complete while we are active
    }
    if (!isActive) return@launch // don't 'complete' task if we were canceled
    withContext(Dispatchers.Main) {
        body(task)
    }
}

fun getHcmInstanceId(context: Context, body: (HmsInstanceId)->Unit): Job = MainScope().launch(Dispatchers.IO) {
    val hmsInstanceId = HmsInstanceId.getInstance(context)
    if (!isActive) return@launch // don't 'complete' task if we were canceled
    withContext(Dispatchers.Main) {
        body(hmsInstanceId)
    }
}

class RegisterHuaweiPushService(val hmsInstanceId: HmsInstanceId, val context: ApplicationContext, val force: Boolean) : Thread() {
    override fun run() {
        val appId = "107146885"
        val tokenScope = "HCM"
        try {
            val token: String = hmsInstanceId.getToken(appId, tokenScope)
            val userPublicKey: String = TextSecurePreferences.getLocalNumber(context) ?: return
            if (TextSecurePreferences.isUsingFCM(context)) {
                register(token, userPublicKey, DeviceType.Huawei, context, force)
            } else {
                unregister(token, context)
            }
        } catch (e: ApiException) {
            Log.e("Loki", "Request HCM token failed. " + e.localizedMessage)
            context.registerForFCMIfNeeded(force)
        }
    }
}