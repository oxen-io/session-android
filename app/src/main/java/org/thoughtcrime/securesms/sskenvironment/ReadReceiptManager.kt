package org.thoughtcrime.securesms.sskenvironment

import android.content.Context
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadReceiptManager @Inject constructor(
    private val textSecurePreferences: TextSecurePreferences,
    private val mmsSmsDatabase: MmsSmsDatabase,
): SSKEnvironment.ReadReceiptManagerProtocol {

    override fun processReadReceipts(context: Context, fromRecipientId: String, sentTimestamps: List<Long>, readTimestamp: Long) {
        if (textSecurePreferences.isReadReceiptsEnabled()) {

            // Redirect message to master device conversation
            var address = Address.fromSerialized(fromRecipientId)
            for (timestamp in sentTimestamps) {
                Log.i("Loki", "Received encrypted read receipt: (XXXXX, $timestamp)")
                mmsSmsDatabase.incrementReadReceiptCount(SyncMessageId(address, timestamp), readTimestamp)
            }
        }
    }
}