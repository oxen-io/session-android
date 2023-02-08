package org.session.libsession.messaging.jobs

import network.loki.messenger.libsession_util.ConfigBase
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.utilities.Data
import org.session.libsignal.utilities.Log

// only contact (self) and closed group destinations will be supported
data class ConfigurationSyncJob(val destination: Destination): Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    override suspend fun execute() {
        val userEdKeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair()
        if (destination is Destination.ClosedGroup
            || !ConfigBase.isNewConfigEnabled
            || userEdKeyPair == null
        ) {
            // TODO: currently we only deal with single destination until closed groups refactor / implement LCG
            Log.w(TAG, "Not handling config sync job, TODO")
            delegate?.handleJobSucceeded(this)
            return
        }

        val configFactory = MessagingModuleConfiguration.shared.configFactory



    }

    override fun serialize(): Data {
        val (type, address) = when (destination) {
            is Destination.Contact -> CONTACT_TYPE to destination.publicKey
            is Destination.ClosedGroup -> GROUP_TYPE to destination.groupPublicKey
            else -> return Data.EMPTY
        }
        return Data.Builder()
            .putInt(DESTINATION_TYPE_KEY, type)
            .putString(DESTINATION_ADDRESS_KEY, address)
            .build()
    }

    override fun getFactoryKey(): String = KEY

    companion object {
        const val TAG = "ConfigSyncJob"
        const val KEY = "ConfigSyncJob"

        // Keys used for DB storage
        const val DESTINATION_ADDRESS_KEY = "destinationAddress"
        const val DESTINATION_TYPE_KEY = "destinationType"

        // type mappings
        const val CONTACT_TYPE = 1
        const val GROUP_TYPE = 2

    }

    class Factory: Job.Factory<ConfigurationSyncJob> {
        override fun create(data: Data): ConfigurationSyncJob? {
            if (!data.hasInt(DESTINATION_TYPE_KEY) || !data.hasString(DESTINATION_ADDRESS_KEY)) return null

            val address = data.getString(DESTINATION_ADDRESS_KEY)
            val destination = when (data.getInt(DESTINATION_TYPE_KEY)) {
                CONTACT_TYPE -> Destination.Contact(address)
                GROUP_TYPE -> Destination.ClosedGroup(address)
                else -> return null
            }

            return ConfigurationSyncJob(destination)
        }
    }

}