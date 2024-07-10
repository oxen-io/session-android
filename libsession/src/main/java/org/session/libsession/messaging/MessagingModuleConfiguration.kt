package org.session.libsession.messaging

import android.annotation.SuppressLint
import android.content.Context
import com.goterl.lazysodium.utils.KeyPair
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.database.StorageProtocol
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.Device
import org.session.libsession.utilities.prefs

class MessagingModuleConfiguration(
    val context: Context,
    val storage: StorageProtocol,
    val device: Device,
    val messageDataProvider: MessageDataProvider,
    val getUserED25519KeyPair: () -> KeyPair?,
    val configFactory: ConfigFactoryProtocol,
    val lastSentTimestampCache: LastSentTimestampCache
) {
    val prefs get() = context.prefs

    companion object {
        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        lateinit var shared: MessagingModuleConfiguration
    }
}
