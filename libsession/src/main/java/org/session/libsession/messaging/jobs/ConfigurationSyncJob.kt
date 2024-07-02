package org.session.libsession.messaging.jobs

import network.loki.messenger.libsession_util.Config
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.GroupKeysConfig
import network.loki.messenger.libsession_util.ConfigBase.Companion.protoKindFor
import nl.komponents.kovenant.functional.bind
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.snode.RawResponse
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeAPI.SnodeBatchRequestInfo
import org.session.libsession.snode.SnodeAPI.signingKeyCallback
import org.session.libsession.snode.SnodeMessage
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.SessionId
import java.util.concurrent.atomic.AtomicBoolean

class InvalidDestination :
        Exception("Trying to push configs somewhere other than our swarm or a closed group")

class InvalidContactDestination : Exception("Trying to push to non-user config swarm")

// only contact (self) and closed group destinations will be supported
data class ConfigurationSyncJob(val destination: Destination) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 10

    val shouldRunAgain = AtomicBoolean(false)

    data class ConfigMessageInformation(
            val batch: SnodeBatchRequestInfo,
            val config: Config,
            val seqNo: Long?
    ) // seqNo will be null for keys type

    data class SyncInformation(
            val configs: List<ConfigMessageInformation>,
            val toDelete: List<String>
    )

    private fun destinationConfigs(
            delegate: JobDelegate,
            dispatcherName: String,
            configFactoryProtocol: ConfigFactoryProtocol
    ): SyncInformation {
        val toDelete = mutableListOf<String>()
        val configsRequiringPush =
                if (destination is Destination.ClosedGroup) {
                    // destination is a closed group, get all configs requiring push here
                    val groupId = SessionId.from(destination.publicKey)

                    // Get the signing key for pushing configs
                    val signingKey = configFactoryProtocol
                                .userGroups?.getClosedGroup(destination.publicKey)?.adminKey
                    if (signingKey?.isNotEmpty() == true) {
                        val info = configFactoryProtocol.getGroupInfoConfig(groupId)!!
                        val members = configFactoryProtocol.getGroupMemberConfig(groupId)!!
                        val keys = configFactoryProtocol.getGroupKeysConfig(
                            groupId,
                            info,
                            members,
                            false
                        )!!

                        val requiringPush =
                            listOf(keys, info, members).filter {
                                when (it) {
                                    is GroupKeysConfig -> it.pendingConfig()?.isNotEmpty() == true
                                    is ConfigBase -> it.needsPush()
                                    else -> false
                                }
                            }

                        // free the objects that were created but won't be used after this point
                        // in case any of the configs don't need pushing, they won't be freed later
                        (listOf(keys, info, members) subtract requiringPush).forEach(Config::free)

                        requiringPush.mapNotNull { config ->
                            if (config is GroupKeysConfig) {
                                config.messageInformation(destination.publicKey, signingKey)
                            } else if (config is ConfigBase) {
                                config.messageInformation(toDelete, destination.publicKey, signingKey)
                            } else {
                                Log.e("ConfigurationSyncJob", "Tried to create a message from an unknown config")
                                null
                            }
                        }
                    } else emptyList()
                } else if (destination is Destination.Contact) {
                    // assume our own user as check already takes place in `execute` for our own key
                    // if contact
                    configFactoryProtocol.getUserConfigs().filter { it.needsPush() }.map { config ->
                        config.messageInformation(toDelete, destination.publicKey)
                    }
                } else throw InvalidDestination()
        return SyncInformation(configsRequiringPush, toDelete)
    }

    override suspend fun execute(dispatcherName: String) {
        val storage = MessagingModuleConfiguration.shared.storage

        val userPublicKey = storage.getUserPublicKey()
        val delegate = delegate ?: return Log.e("ConfigurationSyncJob", "No Delegate")
        if (destination !is Destination.ClosedGroup &&
                        (destination !is Destination.Contact ||
                                destination.publicKey != userPublicKey)
        ) {
            return delegate.handleJobFailedPermanently(this, dispatcherName, InvalidDestination())
        }

        // configFactory singleton instance will come in handy for modifying hashes and fetching
        // configs for namespace etc
        val configFactory = MessagingModuleConfiguration.shared.configFactory

        // allow null results here so the list index matches configsRequiringPush
        val (batchObjects, toDeleteHashes) =
                destinationConfigs(delegate, dispatcherName, configFactory)

        if (batchObjects.isEmpty()) return delegate.handleJobSucceeded(this, dispatcherName)

        val toDeleteRequest =
                toDeleteHashes.let { toDeleteFromAllNamespaces ->
                    if (toDeleteFromAllNamespaces.isEmpty()) null
                    else if (destination is Destination.ClosedGroup) {
                        // Build sign callback for group's admin key
                        val signingKey =
                            configFactory.userGroups
                                ?.getClosedGroup(destination.publicKey)
                                ?.adminKey ?: return@let null

                        val signCallback = signingKeyCallback(signingKey)

                        // Destination is a closed group swarm, build with signCallback
                        SnodeAPI.buildAuthenticatedDeleteBatchInfo(
                            destination.destinationPublicKey(),
                            toDeleteFromAllNamespaces,
                            signCallback
                        )
                    } else {
                        // Destination is our own swarm
                        SnodeAPI.buildAuthenticatedDeleteBatchInfo(
                            destination.destinationPublicKey(),
                            toDeleteFromAllNamespaces
                        )
                    }
                }

        val allRequests = mutableListOf<SnodeBatchRequestInfo>()
        allRequests += batchObjects.map { (request) -> request }
        // add in the deletion if we have any hashes
        if (toDeleteRequest != null) {
            allRequests += toDeleteRequest
            Log.d(TAG, "Including delete request for current hashes")
        }

        val batchResponse =
                SnodeAPI.getSingleTargetSnode(destination.destinationPublicKey()).bind { snode ->
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

            // at this point responseList index should line up with configsRequiringPush index
            batchObjects.forEachIndexed { index, (message, config, seqNo) ->
                val response = responseList[index]
                val responseBody = response["body"] as? RawResponse
                val insertHash =
                        responseBody?.get("hash") as? String
                                ?: run {
                                    Log.w(
                                        TAG,
                                        "No hash returned for the configuration in namespace ${config.namespace()}"
                                    )
                                    return@forEachIndexed
                                }
                Log.d(TAG, "Hash ${insertHash.take(4)} returned from store request for new config")

                // confirm pushed seqno
                if (config is ConfigBase) {
                    seqNo?.let { config.confirmPushed(it, insertHash) }
                }

                Log.d(
                    TAG,
                    "Successfully removed the deleted hashes from ${config.javaClass.simpleName}"
                )
                // dump and write config after successful
                if (config is ConfigBase && config.needsDump()) { // usually this will be true? ))
                    val groupPubKey = if (destination is Destination.ClosedGroup) destination.publicKey else null
                    configFactory.persist(config, message.params["timestamp"] as Long, groupPubKey)
                } else if (config is GroupKeysConfig && config.needsDump()) {
                    Log.d("Loki", "Should persist the GroupKeysConfig")
                }
                if (destination is Destination.ClosedGroup) {
                    config.free() // after they are used, free the temporary group configs
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

    fun Destination.destinationPublicKey(): String =
            when (this) {
                is Destination.Contact -> publicKey
                is Destination.ClosedGroup -> publicKey
                else -> throw NullPointerException("Not public key for this destination")
            }

    override fun serialize(): Data {
        val (type, address) =
                when (destination) {
                    is Destination.Contact -> CONTACT_TYPE to destination.publicKey
                    is Destination.ClosedGroup -> GROUP_TYPE to destination.publicKey
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

        fun ConfigBase.messageInformation(toDelete: MutableList<String>,
                                          destinationPubKey: String,
                                          signingKey: ByteArray? = null,
                                          ed25519PubKey: String? = null): ConfigMessageInformation {
            val sentTimestamp = SnodeAPI.nowWithOffset
            val (push, seqNo, obsoleteHashes) = push()
            toDelete.addAll(obsoleteHashes)
            val message =
                SnodeMessage(
                    destinationPubKey,
                    Base64.encodeBytes(push),
                    SnodeMessage.CONFIG_TTL,
                    sentTimestamp
                )

            return ConfigMessageInformation(
                if (signingKey != null && ed25519PubKey == null) {
                    SnodeAPI.buildAuthenticatedStoreBatchInfo(
                        namespace(),
                        message,
                        signingKey,
                    )
                } else SnodeAPI.buildAuthenticatedStoreBatchInfo(
                    namespace(),
                    message,
                )!!,
                this,
                seqNo
            )
        }

        fun GroupKeysConfig.messageInformation(destinationPubKey: String, signingKey: ByteArray): ConfigMessageInformation {
            val sentTimestamp = SnodeAPI.nowWithOffset
            val message =
                SnodeMessage(
                    destinationPubKey,
                    Base64.encodeBytes(pendingConfig()!!), // should not be null from checking has pending
                    SnodeMessage.CONFIG_TTL,
                    sentTimestamp
                )

            return ConfigMessageInformation(
                SnodeAPI.buildAuthenticatedStoreBatchInfo(
                    namespace(),
                    message,
                    signingKey
                ),
                this,
                0
            )
        }
    }

    class Factory : Job.Factory<ConfigurationSyncJob> {
        override fun create(data: Data): ConfigurationSyncJob? {
            if (!data.hasInt(DESTINATION_TYPE_KEY) || !data.hasString(DESTINATION_ADDRESS_KEY))
                    return null

            val address = data.getString(DESTINATION_ADDRESS_KEY)
            val destination =
                    when (data.getInt(DESTINATION_TYPE_KEY)) {
                        CONTACT_TYPE -> Destination.Contact(address)
                        GROUP_TYPE -> Destination.ClosedGroup(address)
                        else -> return null
                    }

            return ConfigurationSyncJob(destination)
        }
    }
}

