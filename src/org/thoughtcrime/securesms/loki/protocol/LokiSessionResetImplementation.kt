package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.OutgoingTextMessage
import org.whispersystems.libsignal.loki.LokiSessionResetProtocol
import org.whispersystems.libsignal.loki.LokiSessionResetStatus
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol

class LokiSessionResetImplementation(private val context: Context) : LokiSessionResetProtocol {

    override fun getSessionResetStatus(hexEncodedPublicKey: String): LokiSessionResetStatus {
        val masterDevicePublicKey = MultiDeviceProtocol.shared.getMasterDevice(hexEncodedPublicKey) ?: hexEncodedPublicKey
        return DatabaseFactory.getLokiThreadDatabase(context).getSessionResetStatus(masterDevicePublicKey)
    }

    override fun setSessionResetStatus(hexEncodedPublicKey: String, sessionResetStatus: LokiSessionResetStatus) {
        val masterDevicePublicKey = MultiDeviceProtocol.shared.getMasterDevice(hexEncodedPublicKey) ?: hexEncodedPublicKey
        return DatabaseFactory.getLokiThreadDatabase(context).setSessionResetStatus(masterDevicePublicKey, sessionResetStatus)
    }

    override fun onNewSessionAdopted(hexEncodedPublicKey: String, oldSessionResetStatus: LokiSessionResetStatus) {
        if (oldSessionResetStatus == LokiSessionResetStatus.IN_PROGRESS) {
            val ephemeralMessage = EphemeralMessage.create(hexEncodedPublicKey)
            ApplicationContext.getInstance(context).jobManager.add(PushEphemeralMessageSendJob(ephemeralMessage))
        }
        setSessionResetStatus(hexEncodedPublicKey, LokiSessionResetStatus.NONE)
        showSessionRestorationDone(hexEncodedPublicKey)
    }

    fun showSessionRestorationDone(hexEncodedPublicKey: String) {
        val smsDB = DatabaseFactory.getSmsDatabase(context)
        val masterDevicePublicKey = MultiDeviceProtocol.shared.getMasterDevice(hexEncodedPublicKey) ?: hexEncodedPublicKey
        val recipient = Recipient.from(context, Address.fromSerialized(masterDevicePublicKey), false)
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val infoMessage = OutgoingTextMessage(recipient, "", 0, 0)
        val infoMessageID = smsDB.insertMessageOutbox(threadID, infoMessage, false, System.currentTimeMillis(), null)
        if (infoMessageID > -1) {
            smsDB.markAsLokiSessionRestorationDone(infoMessageID)
        }
    }

    override fun validatePreKeySignalMessage(sender: String, message: PreKeySignalMessage) {
        val preKeyRecord = DatabaseFactory.getLokiPreKeyRecordDatabase(context).getPreKeyRecord(sender) ?: return
        // TODO: Checking that the pre key record isn't null is causing issues when it shouldn't
        check(preKeyRecord.id == (message.preKeyId ?: -1)) { "Received a background message from an unknown source." }
    }
}