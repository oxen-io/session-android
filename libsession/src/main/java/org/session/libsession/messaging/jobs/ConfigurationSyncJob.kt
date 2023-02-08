package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.utilities.Data

// only contact (self) and closed group destinations will be supported
data class ConfigurationSyncJob(val destination: Destination): Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    override suspend fun execute() {
        TODO("Not yet implemented")
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