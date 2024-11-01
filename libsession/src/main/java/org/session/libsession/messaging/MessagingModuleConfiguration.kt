package org.session.libsession.messaging

import android.content.Context
import com.goterl.lazysodium.utils.KeyPair
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.messaging.notifications.TokenFetcher
import org.session.libsession.snode.OwnedSwarmAuth
import org.session.libsession.snode.SnodeClock
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.Device
import org.session.libsession.utilities.Toaster
import org.session.libsignal.utilities.AccountId

class MessagingModuleConfiguration(
    val context: Context,
    val storage: StorageProtocol,
    val device: Device,
    val messageDataProvider: MessageDataProvider,
    val configFactory: ConfigFactoryProtocol,
    val lastSentTimestampCache: LastSentTimestampCache,
    val toaster: Toaster,
    val tokenFetcher: TokenFetcher,
    val groupManagerV2: GroupManagerV2,
    val clock: SnodeClock,
) {

    companion object {
        @JvmStatic
        @Deprecated("Use properly DI components instead")
        val shared: MessagingModuleConfiguration
        get() = context.getSystemService(MESSAGING_MODULE_SERVICE) as MessagingModuleConfiguration

        const val MESSAGING_MODULE_SERVICE: String = "MessagingModuleConfiguration_MESSAGING_MODULE_SERVICE"

        private lateinit var context: Context

        @JvmStatic
        fun configure(context: Context) {
            this.context = context
        }
    }
}