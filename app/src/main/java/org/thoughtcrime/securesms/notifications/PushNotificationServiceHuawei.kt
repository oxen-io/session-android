package org.thoughtcrime.securesms.notifications

import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.notifications.LokiPushNotificationManager.DeviceType

class PushNotificationServiceHuawei : HmsMessageService() {
    override fun onNewToken(token: String?, bundle: Bundle?) {
        Log.d("Loki", "New HCM token: $token.")

        if (!token.isNullOrEmpty()) {
            val userPublicKey = TextSecurePreferences.getLocalNumber(this) ?: return
            LokiPushNotificationManager.register(token, userPublicKey, DeviceType.Huawei, this, false)
        }
    }

    override fun onMessageReceived(message: RemoteMessage?) {
        Log.d("Loki", "Received a push notification.")
        val base64EncodedData = message?.dataOfMap?.get("ENCRYPTED_DATA")
        val data = base64EncodedData?.let { Base64.decode(it) }
        if (data != null) {
            try {
                val envelopeAsData = MessageWrapper.unwrap(data).toByteArray()
                val job = BatchMessageReceiveJob(listOf(MessageReceiveParameters(envelopeAsData)), null)
                JobQueue.shared.add(job)
            } catch (e: Exception) {
                Log.d("Loki", "Failed to unwrap data for message due to error: $e.")
            }
        } else {
            Log.d("Loki", "Failed to decode data for message.")
            val builder = NotificationCompat.Builder(this, NotificationChannels.OTHER)
                .setSmallIcon(network.loki.messenger.R.drawable.ic_notification)
                .setColor(this.getResources().getColor(network.loki.messenger.R.color.textsecure_primary))
                .setContentTitle("Session")
                .setContentText("You've got a new message.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            with(NotificationManagerCompat.from(this)) {
                notify(11111, builder.build())
            }
        }
    }

}