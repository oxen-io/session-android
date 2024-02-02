package org.thoughtcrime.securesms.notifications

import android.Manifest
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.protobuf.Descriptors.Descriptor
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
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.Envelope
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.prettifiedDescription
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import javax.inject.Inject
import kotlin.random.Random


private const val TAG = "PushHandler"

class PushReceiver @Inject constructor(@ApplicationContext val context: Context) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())
    private val json = Json { ignoreUnknownKeys = true }

    // This Map<String, String> version is our entry point..
    fun onPush(dataMap: Map<String, String>?) {
        // ..where the map is just a `spns` key and a `enc_payload` key.
        Log.d("[ACL]", "Hit onPush (Map)!")


        Log.d("[ACL]", "dataMap is: $dataMap")

        // To actually use the contents of the message we must decrypt it via `asByteArray`
        onPush(dataMap?.asByteArray())
    }

    // IMPORTANT: We support Android 6 / API 23 as our minimum - but API 26 is the minimum to use
    // Notification Channels (even via NotificationChannelCompat)!!!


    // Version of `onPush` that works with the decrypted byte array of our message
    private fun onPush(decryptedData: ByteArray?) {

        Log.d("[ACL]", "Hit onPush (ByteArray / Decrypted)!")

        // Just for fun

       //doTestNotification()



        // If there was no data or the decryption failed then the best we can do is inform the user
        // that they received some form of message & then bail.
        if (decryptedData == null) {
            Log.d("[ACL]", "onPush (ByteArray) data was null - bailing to 'NOPE! Bad things happened!' version of `onPush`!")
            raiseGenericMessageReceivedNotification("CALLING FROM FAILED TO DECODE")
            return
        }

        // Notify, I guess
        // CANNOT DO THIS because the notification still gets raised when sender is muted or blocked
        //raiseGenericMessageReceivedNotification("CALLING FROM DECODED")

        try {


            val envelope: Envelope = MessageWrapper.unwrap(decryptedData)
            val envelopeAsData = envelope.toByteArray()
            val msgType = envelope.type

            Log.d("[ACL]", "Envelope is: $envelope")




            val sourceBytesString = envelope.sourceBytes.toString()
            val sourceDeviceString = envelope.sourceDevice.toString()
            Log.d("[ACL]", "Source bytes string is: $sourceBytesString")
            Log.d("[ACL]", "Source device string is: $sourceDeviceString")



            //val wang = envelope.content.toByteArray().toString()
            //Log.d("[ACL]", "Wang is: $wang")

            //val mrp = MessageReceiveParameters(envelopeAsData)
            //var mrpDesc = mrp.prettifiedDescription()
            //Log.d("[ACL]", "MessageReceiveParameters is: $mrp")
            //Log.d("[ACL]", "Prettified desc. is: $mrpDesc")

            // Is this a notification a message?
            if (SignalServiceProtos.Envelope.Type.isMessageType(msgType)) {
                // If so, then is the originator of this message an accepted contact?



            }


            // For a standard message with an accepted contact the type is: SESSION_MESSAGE
            // Aaaand it's exactly the same SESSION_MESSAGE type if you haven't accepted them...
            // ..so we'll need to check if the person is in our contacts
            Log.d("[ACL]", "Push message type is: $msgType")


            // Download all messages from sender (to catch up) if required
            Log.d("[ACL]", "About to add 'get-all-messages' job to job queue!")

            val job = BatchMessageReceiveJob(listOf(MessageReceiveParameters(envelopeAsData)), null)
            JobQueue.shared.add(job)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to unwrap data for message due to error.", e)
        }
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

        Log.d("[ACL]", "decrypt() called")

        val encKey = getOrCreateNotificationKey()
        val nonce = encPayload.take(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES).toByteArray()
        val payload = encPayload.drop(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES).toByteArray()
        val padded = SodiumUtilities.decrypt(payload, encKey.asBytes, nonce)
            ?: error("Failed to decrypt push notification")
        val decrypted = padded.dropLastWhile { it.toInt() == 0 }.toByteArray()
        val bencoded = Bencode.Decoder(decrypted)
        val expectedList = (bencoded.decode() as? BencodeList)?.values
            ?: error("Failed to decode bencoded list from payload")

        //Log.d("[ACL]", "Expected list: $expectedList")


        val metadataJson = (expectedList[0] as? BencodeString)?.value ?: error("no metadata")
        val metadata: PushNotificationMetadata = json.decodeFromString(String(metadataJson))

        Log.d("[ACL]", "Decrypted metadata is: $metadata") // Ooh, so close! The account field in this is the RECEIVER's account

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

    private fun raiseGenericMessageReceivedNotification(customMsg: String? = null) {


        //val permissionToNotify = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

        //if (permissionToNotify != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "ASK FOR PERMISSIONS TO NOTIFY HERE!")

            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        // Otherwise build and raise the notification
        val builder = NotificationCompat.Builder(context, NotificationChannels.OTHER)
            .setSmallIcon(network.loki.messenger.R.drawable.ic_notification)
            .setColor(context.getColor(network.loki.messenger.R.color.textsecure_primary))
            .setContentTitle("Session")
            .setContentText(customMsg ?: "You've got a new message.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(11111, builder.build())

    }


    // This WORKS when the person sending the message is blocked (i.e., we get the test notification)
    // but if the person is NOT blocked then we get their message as a notification instead.
    private fun doTestNotification() {

        Log.d("[ACL]", "DOING TEST NOTIFICATION - Notification service is $NOTIFICATION_SERVICE")

        val manager = NotificationManagerCompat.from(context);

        if (manager.getNotificationChannel(NotificationChannels.OTHER) == null) {
            Log.w("[ACL]", "NotificationChannels.OTHER was null so creating a new one...")
            val channel = NotificationChannelCompat.Builder("123456", NotificationCompat.PRIORITY_DEFAULT).build()
            manager.createNotificationChannel(channel)
        }
        else { Log.d("[ACL]", "NotificationChannels.OTHER is NOT null.") }


        //manager.createNotificationChannel(NotificationChannels.OTHER);

        //val channel = NotificationChannelCompat(



        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.w("[ACL]", "FAILED PERMISSION CHECK!!") // WE DO NOT HIT THIS SO WE HAVE PERMISSION


            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.OTHER)
            .setSmallIcon(network.loki.messenger.R.drawable.ic_notification)
            .setColor(context.getColor(network.loki.messenger.R.color.textsecure_primary))
            .setContentTitle("Session")
            .setContentText("Here's a notification just because.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        Log.d("[ACL]", "Building notification")
        var notification = builder.build()

        Log.d("[ACL]", "About to NOTIFY!!!!!")
        val randomInt = Random.nextInt() // Notifications with the same ID don't show so randomise it
        manager.notify(randomInt, notification);

        //manager.createNotificationChannel(channel);
    }
}
