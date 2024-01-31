package org.thoughtcrime.securesms.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService
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
import kotlin.random.Random


private const val TAG = "PushHandler"

class PushReceiver @Inject constructor(@ApplicationContext val context: Context) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())
    private val json = Json { ignoreUnknownKeys = true }

    fun onPush(dataMap: Map<String, String>?) {
        Log.d("[ACL]", "Hit onPush (Map)!")

        onPush(dataMap?.asByteArray())
    }

    // IMPORTANT: We support Android 6 / API 23 as our minimum - but API 26 is the minimum to use
    // Notification Channels (even via NotificationChannelCompat)!!!

    // This WORKS when the person sending the message is blocked (i.e., we get the test notification)
    // but if the person is NOT blocked then we get their message as a notification instead.
    fun doTestNotification() {

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

        val builder = NotificationCompat.Builder(context, NotificationChannels.OTHER)
            .setSmallIcon(network.loki.messenger.R.drawable.ic_notification)
            .setColor(context.getColor(network.loki.messenger.R.color.textsecure_primary))
            .setContentTitle("Session")
            .setContentText("Here's a notification just because.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        Log.d("[ACL]", "Building notification")
        var notification = builder.build()

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
        Log.d("[ACL]", "About to NOTIFY!!!!!")
        val randomInt = Random.nextInt() // Notifications with the same ID don't show so randomise it
        manager.notify(randomInt, notification);


        //manager.createNotificationChannel(channel);


    }

    private fun onPush(data: ByteArray?) {

        Log.d("[ACL]", "Hit onPush (ByteArray)!")

        // Just for fun

       doTestNotification()




        if (data == null) {
            Log.d("[ACL]", "onPush (ByteArray) data was null - bailing to 'NOPE! Bad things happened version of `onPush`!")
            onPush()
            return
        }

        try {
            val envelopeAsData = MessageWrapper.unwrap(data).toByteArray()
            val job = BatchMessageReceiveJob(listOf(MessageReceiveParameters(envelopeAsData)), null)

            Log.d("[ACL]", "About to add job to job queue!")

            JobQueue.shared.add(job)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to unwrap data for message due to error.", e)
        }
    }

    private fun onPush() {
        Log.d(TAG, "Failed to decode data for message.")

        Log.d("[ACL]", "In NOPE handler trying to raise a notification!!!")

        val builder = NotificationCompat.Builder(context, NotificationChannels.OTHER)
            .setSmallIcon(network.loki.messenger.R.drawable.ic_notification)
            .setColor(context.getColor(network.loki.messenger.R.color.textsecure_primary))
            .setContentTitle("Session")
            .setContentText("You've got a new message.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        /*
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("[ACL]", "Yeah, nah...")

            ActivityCompat.requestPermissions(this, arrayOf("POST_NOTIFICATIONS"))


            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        */

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

        val metadataJson = (expectedList[0] as? BencodeString)?.value ?: error("no metadata")
        val metadata: PushNotificationMetadata = json.decodeFromString(String(metadataJson))

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
