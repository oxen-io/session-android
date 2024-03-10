package org.thoughtcrime.securesms.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import androidx.core.app.NotificationManagerCompat
import org.session.libsession.messaging.MessagingModuleConfiguration.Companion.shared
import org.session.libsession.messaging.messages.control.ReadReceipt
import org.session.libsession.messaging.sending_receiving.MessageSender.send
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeAPI.nowWithOffset
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.isReadReceiptsEnabled
import org.session.libsession.utilities.associateByNotNull
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.conversation.disappearingmessages.ExpiryType
import org.thoughtcrime.securesms.database.ExpirationInfo
import org.thoughtcrime.securesms.database.MarkedMessageInfo
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.SessionMetaProtocol.shouldSendReadReceipt

class MarkReadReceiver : BroadcastReceiver() {
    @SuppressLint("StaticFieldLeak")
    override fun onReceive(context: Context, intent: Intent) {
        if (CLEAR_ACTION != intent.action) return
        val threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA) ?: return
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1))
        object : AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg params: Void?): Void? {
                val currentTime = nowWithOffset
                threadIds.forEach {
                    Log.i(TAG, "Marking as read: $it")
                    shared.storage.markConversationAsRead(it, currentTime, true)
                }
                return null
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    companion object {
        private val TAG = MarkReadReceiver::class.java.simpleName
        const val CLEAR_ACTION = "network.loki.securesms.notifications.CLEAR"
        const val THREAD_IDS_EXTRA = "thread_ids"
        const val NOTIFICATION_ID_EXTRA = "notification_id"

        val messageExpirationManager = SSKEnvironment.shared.messageExpirationManager

        @JvmStatic
        fun process(
            context: Context,
            markedReadMessages: List<MarkedMessageInfo>
        ) {
            if (markedReadMessages.isEmpty()) return

            sendReadReceipts(context, markedReadMessages)

            val mmsSmsDatabase = DatabaseComponent.get(context).mmsSmsDatabase()

            // start disappear after read messages except TimerUpdates in groups.
            markedReadMessages
                .filter { it.expiryType == ExpiryType.AFTER_READ }
                .map { it.syncMessageId }
                .filter { mmsSmsDatabase.getMessageForTimestamp(it.timetamp)?.run { isExpirationTimerUpdate && recipient.isClosedGroupRecipient } == false }
                .forEach { messageExpirationManager.startDisappearAfterRead(it.timetamp, it.address.serialize()) }

            hashToDisappearAfterReadMessage(context, markedReadMessages)?.let {
                fetchUpdatedExpiriesAndScheduleDeletion(context, it)
                shortenExpiryOfDisappearingAfterRead(context, it)
            }
        }

        private fun hashToDisappearAfterReadMessage(
            context: Context,
            markedReadMessages: List<MarkedMessageInfo>
        ): Map<String, MarkedMessageInfo>? {
            val loki = DatabaseComponent.get(context).lokiMessageDatabase()

            return markedReadMessages
                .filter { it.expiryType == ExpiryType.AFTER_READ }
                .associateByNotNull { it.expirationInfo.run { loki.getMessageServerHash(id, isMms) } }
                .takeIf { it.isNotEmpty() }
        }

        private fun shortenExpiryOfDisappearingAfterRead(
            context: Context,
            hashToMessage: Map<String, MarkedMessageInfo>
        ) {
            hashToMessage.entries
                .groupBy(
                    keySelector =  { it.value.expirationInfo.expiresIn },
                    valueTransform = { it.key }
                ).forEach { (expiresIn, hashes) ->
                    SnodeAPI.alterTtl(
                        messageHashes = hashes,
                        newExpiry = nowWithOffset + expiresIn,
                        publicKey = TextSecurePreferences.getLocalNumber(context)!!,
                        shorten = true
                    )
                }
        }

        private fun sendReadReceipts(
            context: Context,
            markedReadMessages: List<MarkedMessageInfo>
        ) {
            if (!isReadReceiptsEnabled(context)) return

            markedReadMessages.map { it.syncMessageId }
                .filter { shouldSendReadReceipt(Recipient.from(context, it.address, false)) }
                .groupBy { it.address }
                .forEach { (address, messages) ->
                    messages.map { it.timetamp }
                        .let(::ReadReceipt)
                        .apply { sentTimestamp = nowWithOffset }
                        .let { send(it, address) }
                }
        }

        private fun fetchUpdatedExpiriesAndScheduleDeletion(
            context: Context,
            hashToMessage: Map<String, MarkedMessageInfo>
        ) {
            @Suppress("UNCHECKED_CAST")
            val expiries = SnodeAPI.getExpiries(hashToMessage.keys.toList(), TextSecurePreferences.getLocalNumber(context)!!).get()["expiries"] as Map<String, Long>
            hashToMessage.forEach { (hash, info) -> expiries[hash]?.let { scheduleDeletion(context, info.expirationInfo, it - info.expirationInfo.expireStarted) } }
        }

        private fun scheduleDeletion(
            context: Context?,
            expirationInfo: ExpirationInfo,
            expiresIn: Long = expirationInfo.expiresIn
        ) {
            if (expiresIn == 0L) return

            val now = nowWithOffset

            val expireStarted = expirationInfo.expireStarted

            if (expirationInfo.isDisappearAfterRead() && expireStarted == 0L || now < expireStarted) {
                val db = DatabaseComponent.get(context!!).run { if (expirationInfo.isMms) mmsDatabase() else smsDatabase() }
                db.markExpireStarted(expirationInfo.id, now)
            }

            ApplicationContext.getInstance(context).expiringMessageManager.scheduleDeletion(
                expirationInfo.id,
                expirationInfo.isMms,
                now,
                expiresIn
            )
        }
    }
}
