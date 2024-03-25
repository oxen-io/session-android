package org.session.libsession.messaging.jobs

import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.ConfigBase.Companion.protoKindFor
import nl.komponents.kovenant.functional.bind
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.control.SharedConfigurationMessage
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.snode.RawResponse
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import java.util.concurrent.atomic.AtomicBoolean

// only contact (self) and closed group destinations will be supported
data class ConfigurationSyncJob(val destination: Destination): Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 10

    val shouldRunAgain = AtomicBoolean(false)

    override suspend fun execute(dispatcherName: String) {
        val storage = MessagingModuleConfiguration.shared.storage
        val forcedConfig = TextSecurePreferences.hasForcedNewConfig(MessagingModuleConfiguration.shared.context)
        val currentTime = SnodeAPI.nowWithOffset
        val userEdKeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair()
        val userPublicKey = storage.getUserPublicKey()
        val delegate = delegate
        if (destination is Destination.ClosedGroup // TODO: closed group configs will be handled in closed group feature
            // if we haven't enabled the new configs don't run
            || !ConfigBase.isNewConfigEnabled(forcedConfig, currentTime)
            // if we don't have a user ed key pair for signing updates
            || userEdKeyPair == null
            // this will be useful to not handle null delegate cases
            || delegate == null
            // check our local identity key exists
            || userPublicKey.isNullOrEmpty()
            // don't allow pushing  configs for non-local user
            || (destination is Destination.Contact && destination.publicKey != userPublicKey)
        ) {
            Log.w(TAG, "No need to run config sync job, TODO")
            return delegate?.handleJobSucceeded(this, dispatcherName) ?: Unit
        }

        // configFactory singleton instance will come in handy for modifying hashes and fetching configs for namespace etc
        val configFactory = MessagingModuleConfiguration.shared.configFactory

        // get latest states, filter out configs that don't need push
        val configsRequiringPush = configFactory.getUserConfigs().filter { config -> config.needsPush() }

        // don't run anything if we don't need to push anything
        if (configsRequiringPush.isEmpty()) return delegate.handleJobSucceeded(this, dispatcherName)

        // need to get the current hashes before we call `push()`
        val toDeleteHashes = mutableListOf<String>()

        // allow null results here so the list index matches configsRequiringPush
        val sentTimestamp: Long = SnodeAPI.nowWithOffset
        val batchObjects: List<Pair<SharedConfigurationMessage, SnodeAPI.SnodeBatchRequestInfo>?> = configsRequiringPush.map { config ->
            val (data, seqNo, obsoleteHashes) = config.push()
            toDeleteHashes += obsoleteHashes
            SharedConfigurationMessage(config.protoKindFor(), data, seqNo) to config
        }.map { (message, config) ->
            // return a list of batch request objects
            val snodeMessage = MessageSender.buildWrappedMessageToSnode(destination, message, true)
            val authenticated = SnodeAPI.buildAuthenticatedStoreBatchInfo(
                destination.destinationPublicKey(),
                config.configNamespace(),
                snodeMessage
            ) ?: return@map null // this entry will be null otherwise
            message to authenticated // to keep track of seqNo for calling confirmPushed later
        }

        val toDeleteRequest = toDeleteHashes.let { toDeleteFromAllNamespaces ->
            if (toDeleteFromAllNamespaces.isEmpty()) null
            else SnodeAPI.buildAuthenticatedDeleteBatchInfo(destination.destinationPublicKey(), toDeleteFromAllNamespaces)
        }

        if (batchObjects.any { it == null }) {
            // stop running here, something like a signing error occurred
            return delegate.handleJobFailedPermanently(this, dispatcherName, NullPointerException("One or more requests had a null batch request info"))
        }

        val allRequests = mutableListOf<SnodeAPI.SnodeBatchRequestInfo>()
        allRequests += batchObjects.requireNoNulls().map { (_, request) -> request }
        // add in the deletion if we have any hashes
        if (toDeleteRequest != null) {
            allRequests += toDeleteRequest
            Log.d(TAG, "Including delete request for current hashes")
        }

        val batchResponse = SnodeAPI.getSingleTargetSnode(destination.destinationPublicKey()).bind { snode ->
            SnodeAPI.getRawBatchResponse(
                snode,
                destination.destinationPublicKey(),
                allRequests,
                sequence = true
            )
        }

        try {
            val rawResponses = batchResponse.get()
            @Suppress("UNCHECKED_CAST")
            val responseList = (rawResponses["results"] as List<RawResponse>)
            // we are always adding in deletions at the end
            val deletionResponse = if (toDeleteRequest != null && responseList.isNotEmpty()) responseList.last() else null
            val deletedHashes = deletionResponse?.let {
                @Suppress("UNCHECKED_CAST")
                // get the sub-request body
                (deletionResponse["body"] as? RawResponse)?.let { body ->
                    // get the swarm dict
                    body["swarm"] as? RawResponse
                }?.mapValues { (_, swarmDict) ->
                    // get the deleted values from dict
                    ((swarmDict as? RawResponse)?.get("deleted") as? List<String>)?.toSet() ?: emptySet()
                }?.values?.reduce { acc, strings ->
                    // create an intersection of all deleted hashes (common between all swarm nodes)
                    acc intersect strings
                }
            } ?: emptySet()

            // at this point responseList index should line up with configsRequiringPush index
            configsRequiringPush.forEachIndexed { index, config ->
                val (toPushMessage, _) = batchObjects[index]!!
                val response = responseList[index]
                val responseBody = response["body"] as? RawResponse
                val insertHash = responseBody?.get("hash") as? String ?: run {
                    Log.w(TAG, "No hash returned for the configuration in namespace ${config.configNamespace()}")
                    return@forEachIndexed
                }
                Log.d(TAG, "Hash ${insertHash.take(4)} returned from store request for new config")

                // confirm pushed seqno
                val thisSeqNo = toPushMessage.seqNo
                config.confirmPushed(thisSeqNo, insertHash)
                Log.d(TAG, "Successfully removed the deleted hashes from ${config.javaClass.simpleName}")
                // dump and write config after successful
                if (config.needsDump()) { // usually this will be true?
                    configFactory.persist(config, toPushMessage.sentTimestamp ?: sentTimestamp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing batch request", e)
            return delegate.handleJobFailed(this, dispatcherName, e)
        }
        delegate.handleJobSucceeded(this, dispatcherName)
        if (shouldRunAgain.get() && storage.getConfigSyncJob(destination) == null) {
            // reschedule if something has updated since we started this job
            JobQueue.shared.add(ConfigurationSyncJob(destination))
        }
    }

    fun Destination.destinationPublicKey(): String = when (this) {
        is Destination.Contact -> publicKey
        is Destination.ClosedGroup -> groupPublicKey
        else -> throw NullPointerException("Not public key for this destination")
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