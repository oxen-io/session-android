package org.thoughtcrime.securesms.notifications

import android.content.Context
import android.content.pm.PackageManager
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

    fun onPush(dataMap: Map<String, String>?) {
        val result = dataMap?.decodeAndDecrypt()
        val data = result?.first
        if (data == null) {
            onPush()
            return
        }

        handlePushData(data = data, metadata = result.second)
    }

    private fun handlePushData(data: ByteArray, metadata: PushNotificationMetadata?) {
        try {
            val params = when {
                metadata?.namespace == Namespace.CLOSED_GROUP_MESSAGES() -> {
                    val groupId = AccountId(requireNotNull(metadata.account) {
                        "Received a closed group message push notification without an account ID"
                    })

                    val envelop = checkNotNull(tryDecryptGroupMessage(groupId, data)) {
                        "Unable to decrypt closed group message"
                    }

                    MessageReceiveParameters(
                        data = envelop.toByteArray(),
                        serverHash = metadata.msg_hash,
                        closedGroup = Destination.ClosedGroup(groupId.hexString)
                    )
                }

                metadata?.namespace == 0 || metadata == null -> {
                    MessageReceiveParameters(
                        data = MessageWrapper.unwrap(data).toByteArray(),
                    )
                }

                else -> {
                    Log.w(TAG, "Received a push notification with an unknown namespace: ${metadata.namespace}")
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

    private fun onPush() {
        Log.d(TAG, "Failed to decode data for message.")
        val builder = NotificationCompat.Builder(context, NotificationChannels.OTHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.textsecure_primary))
            .setContentTitle(getString(context, R.string.app_name))

            // Note: We set the count to 1 in the below plurals string so it says "You've got a new message" (singular)
            .setContentText(context.resources.getQuantityString(R.plurals.messageNewYouveGot, 1, 1))

            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(11111, builder.build())
        }
    }

    private fun Map<String, String>.decodeAndDecrypt() =
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
            else -> this["ENCRYPTED_DATA"]?.let { Base64.decode(it) to null }
        }

    private fun decrypt(encPayload: ByteArray): Pair<ByteArray?, PushNotificationMetadata?> {
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

        return (expectedList.getOrNull(1) as? BencodeString)?.value.also {
            // null content is valid only if we got a "data_too_long" flag
            it?.let { check(metadata.data_len == it.size) { "wrong message data size" } }
                ?: check(metadata.data_too_long) { "missing message data, but no too-long flag" }
        } to metadata
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
}
