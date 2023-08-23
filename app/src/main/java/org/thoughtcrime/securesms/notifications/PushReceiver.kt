package org.thoughtcrime.securesms.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.utils.Key
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationMetadata
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.bencode.Bencode
import org.session.libsession.utilities.bencode.BencodeList
import org.session.libsession.utilities.bencode.BencodeString
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import javax.inject.Inject

private const val TAG = "PushHandler"

class PushReceiver @Inject constructor(@ApplicationContext val context: Context) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    fun onPush(dataMap: Map<String, String>?) {
        onPush(dataMap?.asByteArray())
    }

    fun onPush(data: ByteArray?) {
        if (data == null) {
            onPush()
            return
        }

        try {
            val envelopeAsData = MessageWrapper.unwrap(data).toByteArray()
            val job = BatchMessageReceiveJob(listOf(MessageReceiveParameters(envelopeAsData)), null)
            JobQueue.shared.add(job)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to unwrap data for message due to error.", e)
        }
    }

    private fun onPush() {
        Log.d(TAG, "Failed to decode data for message.")
        val builder = NotificationCompat.Builder(context, NotificationChannels.OTHER)
            .setSmallIcon(network.loki.messenger.R.drawable.ic_notification)
            .setColor(context.getColor(network.loki.messenger.R.color.textsecure_primary))
            .setContentTitle("Session")
            .setContentText("You've got a new message.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(11111, builder.build())
    }

    private fun Map<String, String>.asByteArray() =
        when {
            // this is a v2 push notification
            containsKey("spns") -> {
                try {
                    decrypt(Base64.decode(this["enc_payload"]))
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid push notification", e)
                    null
                }
            }
            // old v1 push notification; we still need this for receiving legacy closed group notifications
            else -> this["ENCRYPTED_DATA"]?.let(Base64::decode)
        }

    private fun decrypt(encPayload: ByteArray): ByteArray? {
        Log.d(TAG, "decrypt() called")

        val encKey = getOrCreateNotificationKey()
        val nonce = encPayload.take(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES).toByteArray()
        val payload = encPayload.drop(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES).toByteArray()
        val padded = SodiumUtilities.decrypt(payload, encKey.asBytes, nonce)
            ?: error("Failed to decrypt push notification")
        val decrypted = padded.dropLastWhile { it.toInt() == 0 }.toByteArray()
        val bencoded = Bencode.Decoder(decrypted)
        val expectedList = (bencoded.decode() as? BencodeList)?.values
            ?: error("Failed to decode bencoded list from payload")

        val metadataJson = (expectedList[0] as? BencodeString)?.value ?: error("no metadata")
        val metadata: PushNotificationMetadata = Json.decodeFromString(String(metadataJson))

        return (expectedList.getOrNull(1) as? BencodeString)?.value.also {
            // null content is valid only if we got a "data_too_long" flag
            it?.let { check(metadata.data_len == it.size) { "wrong message data size" } }
                ?: check(metadata.data_too_long) { "missing message data, but no too-long flag" }
        }
    }

    fun getOrCreateNotificationKey(): Key {
        if (IdentityKeyUtil.retrieve(context, IdentityKeyUtil.NOTIFICATION_KEY) == null) {
            // generate the key and store it
            val key = sodium.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
            IdentityKeyUtil.save(context, IdentityKeyUtil.NOTIFICATION_KEY, key.asHexString)
        }
        return Key.fromHexString(
            IdentityKeyUtil.retrieve(
                context,
                IdentityKeyUtil.NOTIFICATION_KEY
            )
        )
    }
}
