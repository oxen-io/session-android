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
import kotlinx.serialization.json.Json
import network.loki.messenger.R
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationMetadata
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.messaging.utilities.SodiumUtilities.sodium
import org.session.libsession.utilities.bencode.Bencode
import org.session.libsession.utilities.bencode.BencodeList
import org.session.libsession.utilities.bencode.BencodeString
import org.session.libsignal.protos.SignalServiceProtos.Envelope
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import javax.inject.Inject

private const val TAG = "PushHandler"

class PushReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configFactory: ConfigFactory
) {
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
            val params = when {
                pushData.metadata?.namespace == Namespace.CLOSED_GROUP_MESSAGES() -> {
                    val groupId = AccountId(requireNotNull(pushData.metadata.account) {
                        "Received a closed group message push notification without an account ID"
                    })

                    val envelop = checkNotNull(tryDecryptGroupMessage(groupId, pushData.data)) {
                        "Unable to decrypt closed group message"
                    }

                    MessageReceiveParameters(
                        data = envelop.toByteArray(),
                        serverHash = pushData.metadata.msg_hash,
                        closedGroup = Destination.ClosedGroup(groupId.hexString)
                    )
                }

                pushData.metadata?.namespace == 0 || pushData.metadata == null -> {
                    val envelopeAsData = MessageWrapper.unwrap(pushData.data).toByteArray()
                    MessageReceiveParameters(
                        data = envelopeAsData,
                        serverHash = pushData.metadata?.msg_hash
                    )
                }

                else -> {
                    Log.w(TAG, "Received a push notification with an unknown namespace: ${pushData.metadata.namespace}")
                    return
                }
            }

            JobQueue.shared.add(BatchMessageReceiveJob(listOf(params), null))
        } catch (e: Exception) {
            Log.d(TAG, "Failed to unwrap data for message due to error.", e)
        }

    }
    

    private fun tryDecryptGroupMessage(groupId: AccountId, data: ByteArray): Envelope? {
        val (envelopBytes, sender) = checkNotNull(configFactory.withGroupConfigs(groupId) { it.groupKeys.decrypt(data) }) {
            "Failed to decrypt group message"
        }

        Log.d(TAG, "Successfully decrypted group message from ${sender.hexString}")
        return Envelope.parseFrom(envelopBytes)
            .toBuilder()
            .setSource(sender.hexString)
            .build()
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
        val nonce = encPayload.sliceArray(0 until AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val payload =
            encPayload.sliceArray(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES until encPayload.size)
        val padded = SodiumUtilities.decrypt(payload, encKey.asBytes, nonce)
            ?: error("Failed to decrypt push notification")
        val contentEndedAt = padded.indexOfLast { it.toInt() != 0 }
        val decrypted = if (contentEndedAt >= 0) padded.sliceArray(0..contentEndedAt) else padded
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
        val keyHex = IdentityKeyUtil.retrieve(context, IdentityKeyUtil.NOTIFICATION_KEY)
        if (keyHex != null) {
            return Key.fromHexString(keyHex)
        }

        // generate the key and store it
        val key = sodium.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
        IdentityKeyUtil.save(context, IdentityKeyUtil.NOTIFICATION_KEY, key.asHexString)
        return key
    }

    data class PushData(
        val data: ByteArray?,
        val metadata: PushNotificationMetadata?
    )
}
