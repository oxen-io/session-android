package org.thoughtcrime.securesms.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getString
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.utils.Key
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import network.loki.messenger.R
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationMetadata
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.messaging.utilities.SodiumUtilities.sodium
import org.session.libsession.utilities.bencode.Bencode
import org.session.libsession.utilities.bencode.BencodeList
import org.session.libsession.utilities.bencode.BencodeString
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import javax.inject.Inject

private const val TAG = "PushHandler"

class PushReceiver @Inject constructor(@ApplicationContext val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Both push services should hit this method once they receive notification data
     * As long as it is properly formatted
     */
    fun onPushDataReceived(dataMap: Map<String, String>?) {
        addMessageReceiveJob(dataMap?.asPushData())
    }

    /**
     * This is a fallback method in case the Huawei data is malformated,
     * but it shouldn't happen. Old code used to send different data so this is kept as a safety
     */
    fun onPushDataReceived(data: ByteArray?) {
        addMessageReceiveJob(PushData(data = data, metadata = null))
    }

    private fun addMessageReceiveJob(pushData: PushData?){
        // send a generic notification if we have no data
        if (pushData?.data == null) {
            sendGenericNotification()
            return
        }

        try {
            val envelopeAsData = MessageWrapper.unwrap(pushData.data).toByteArray()
            val job = BatchMessageReceiveJob(listOf(
                MessageReceiveParameters(
                    data = envelopeAsData,
                    serverHash = pushData.metadata?.msg_hash
                )
            ), null)
            JobQueue.shared.add(job)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to unwrap data for message due to error.", e)
        }
    }

    private fun sendGenericNotification() {
        Log.d(TAG, "Failed to decode data for message.")

        // no need to do anything if notification permissions are not granted
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.OTHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.textsecure_primary))
            .setContentTitle(getString(context, R.string.app_name))

            // Note: We set the count to 1 in the below plurals string so it says "You've got a new message" (singular)
            .setContentText(context.resources.getQuantityString(R.plurals.messageNewYouveGot, 1, 1))

            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(11111, builder.build())
    }

    private fun Map<String, String>.asPushData(): PushData =
        when {
            // this is a v2 push notification
            containsKey("spns") -> {
                try {
                    decrypt(Base64.decode(this["enc_payload"]))
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid push notification", e)
                    PushData(null, null)
                }
            }
            // old v1 push notification; we still need this for receiving legacy closed group notifications
            else -> PushData(this["ENCRYPTED_DATA"]?.let(Base64::decode), null)
        }

    private fun decrypt(encPayload: ByteArray): PushData {
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
        val metadata: PushNotificationMetadata = json.decodeFromString(String(metadataJson))

        return PushData(
            data = (expectedList.getOrNull(1) as? BencodeString)?.value,
            metadata = metadata
        ).also { pushData ->
            // null data content is valid only if we got a "data_too_long" flag
            pushData.data?.let { check(metadata.data_len == it.size) { "wrong message data size" } }
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

    data class PushData(
        val data: ByteArray?,
        val metadata: PushNotificationMetadata?
    )
}
