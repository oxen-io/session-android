@file:Suppress("NAME_SHADOWING")

package org.session.libsession.snode

import com.goterl.lazysodium.exceptions.SodiumException
import com.goterl.lazysodium.interfaces.GenericHash
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import kotlinx.coroutines.coroutineScope
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import nl.komponents.kovenant.unwrap
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.messaging.utilities.SodiumUtilities.sodium
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.buildMutableMap
import org.session.libsession.utilities.mapValuesNotNull
import org.session.libsession.utilities.toByteArray
import org.session.libsignal.crypto.secureRandom
import org.session.libsignal.crypto.shuffledRandom
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Broadcaster
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.Snode
import org.session.libsignal.utilities.prettifiedDescription
import org.session.libsignal.utilities.retryIfNeeded
import org.session.libsignal.utilities.retryWithUniformInterval
import java.util.Locale
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.properties.Delegates.observable

object SnodeAPI {
    internal val database: LokiAPIDatabaseProtocol
        get() = SnodeModule.shared.storage
    private val broadcaster: Broadcaster
        get() = SnodeModule.shared.broadcaster

    private var snodeFailureCount: MutableMap<Snode, Int> = mutableMapOf()
    internal var snodePool: Set<Snode>
        get() = database.getSnodePool()
        set(newValue) { database.setSnodePool(newValue) }
    /**
     * The offset between the user's clock and the Service Node's clock. Used in cases where the
     * user's clock is incorrect.
     */
    internal var clockOffset = 0L

    @JvmStatic
    val nowWithOffset
        get() = System.currentTimeMillis() + clockOffset

    internal var forkInfo by observable(database.getForkInfo()) { _, oldValue, newValue ->
        if (newValue > oldValue) {
            Log.d("Loki", "Setting new fork info new: $newValue, old: $oldValue")
            database.setForkInfo(newValue)
        }
    }

    // Settings
    private const val maxRetryCount = 6
    private const val minimumSnodePoolCount = 12
    private const val minimumSwarmSnodeCount = 3
    // Use port 4433 to enforce pinned certificates
    private val seedNodePort = 4443

    private val seedNodePool = if (SnodeModule.shared.useTestNet) setOf(
        "http://public.loki.foundation:38157"
    ) else setOf(
        "https://seed1.getsession.org:$seedNodePort",
        "https://seed2.getsession.org:$seedNodePort",
        "https://seed3.getsession.org:$seedNodePort",
    )

    private const val snodeFailureThreshold = 3
    private const val useOnionRequests = true

    private const val KEY_IP = "public_ip"
    private const val KEY_PORT = "storage_port"
    private const val KEY_X25519 = "pubkey_x25519"
    private const val KEY_ED25519 = "pubkey_ed25519"
    private const val KEY_VERSION = "storage_server_version"

    // Error
    sealed class Error(val description: String) : Exception(description) {
        object Generic : Error("An error occurred.")
        object ClockOutOfSync : Error("Your clock is out of sync with the Service Node network.")
        object NoKeyPair : Error("Missing user key pair.")
        object SigningFailed : Error("Couldn't sign verification data.")
        // ONS
        object DecryptionFailed : Error("Couldn't decrypt ONS name.")
        object HashingFailed : Error("Couldn't compute ONS name hash.")
        object ValidationFailed : Error("ONS name validation failed.")
    }

    // Batch
    data class SnodeBatchRequestInfo(
        val method: String,
        val params: Map<String, Any>,
        @Transient
        val namespace: Int?
    ) // assume signatures, pubkey and namespaces are attached in parameters if required

    // Internal API
    internal fun invoke(
        method: Snode.Method,
        snode: Snode,
        parameters: Map<String, Any>,
        publicKey: String? = null,
        version: Version = Version.V3
    ): RawResponsePromise = when {
        useOnionRequests -> OnionRequestAPI.sendOnionRequest(method, parameters, snode, version, publicKey).map {
            JsonUtil.fromJson(it.body ?: throw Error.Generic, Map::class.java)
        }
        else -> task {
            HTTP.execute(
                HTTP.Verb.POST,
                url = "${snode.address}:${snode.port}/storage_rpc/v1",
                parameters = buildMap {
                    this["method"] = method.rawValue
                    this["params"] = parameters
                }
            ).toString().let {
                JsonUtil.fromJson(it, Map::class.java)
            }
        }.fail { e ->
            when (e) {
                is HTTP.HTTPRequestFailedException -> handleSnodeError(e.statusCode, e.json, snode, publicKey)
                else -> Log.d("Loki", "Unhandled exception: $e.")
            }
        }
    }

    private val GET_RANDOM_SNODE_PARAMS = buildMap<String, Any> {
        this["method"] = "get_n_service_nodes"
        this["params"] = buildMap {
            this["active_only"] = true
            this["fields"] = sequenceOf(KEY_IP, KEY_PORT, KEY_X25519, KEY_ED25519, KEY_VERSION).associateWith { true }
        }
    }

    internal fun getRandomSnode(): Promise<Snode, Exception> =
        snodePool.takeIf { it.size >= minimumSnodePoolCount }?.secureRandom()?.let { Promise.of(it) } ?: task {
            val target = seedNodePool.random()
            Log.d("Loki", "Populating snode pool using: $target.")
            val url = "$target/json_rpc"
            val response = HTTP.execute(HTTP.Verb.POST, url, GET_RANDOM_SNODE_PARAMS, useSeedNodeConnection = true)
            val json = runCatching { JsonUtil.fromJson(response, Map::class.java) }.getOrNull()
                ?: buildMap { this["result"] = response.toString() }
            val intermediate = json["result"] as? Map<*, *> ?: throw Error.Generic
                .also { Log.d("Loki", "Failed to update snode pool, intermediate was null.") }
            val rawSnodes = intermediate["service_node_states"] as? List<*> ?: throw Error.Generic
                .also { Log.d("Loki", "Failed to update snode pool, rawSnodes was null.") }

            rawSnodes.asSequence().mapNotNull { it as? Map<*, *> }.mapNotNull { rawSnode ->
                createSnode(
                    address = rawSnode[KEY_IP] as? String,
                    port = rawSnode[KEY_PORT] as? Int,
                    ed25519Key = rawSnode[KEY_ED25519] as? String,
                    x25519Key = rawSnode[KEY_X25519] as? String,
                    version = (rawSnode[KEY_VERSION] as? List<*>)
                        ?.filterIsInstance<Int>()
                        ?.let(Snode::Version)
                ).also { if (it == null) Log.d("Loki", "Failed to parse: ${rawSnode.prettifiedDescription()}.") }
            }.toSet().also {
                Log.d("Loki", "Persisting snode pool to database.")
                snodePool = it
            }.takeUnless { it.isEmpty() }?.secureRandom() ?: throw SnodeAPI.Error.Generic
        }

    private fun createSnode(address: String?, port: Int?, ed25519Key: String?, x25519Key: String?, version: Snode.Version? = Snode.Version.ZERO): Snode? {
        return Snode(
            address?.takeUnless { it == "0.0.0.0" }?.let { "https://$it" } ?: return null,
            port ?: return null,
            Snode.KeySet(ed25519Key ?: return null, x25519Key ?: return null),
            version ?: return null
        )
    }

    internal fun dropSnodeFromSwarmIfNeeded(snode: Snode, publicKey: String) {
        database.getSwarm(publicKey)?.takeIf { snode in it }?.let {
            database.setSwarm(publicKey, it - snode)
        }
    }

    internal fun getSingleTargetSnode(publicKey: String): Promise<Snode, Exception> {
        // SecureRandom should be cryptographically secure
        return getSwarm(publicKey).map { it.shuffledRandom().random() }
    }

    // Public API
    fun getAccountID(onsName: String): Promise<String, Exception> = task {
        val validationCount = 3
        val accountIDByteCount = 33
        // Hash the ONS name using BLAKE2b
        val onsName = onsName.toLowerCase(Locale.US)
        val nameAsData = onsName.toByteArray()
        val nameHash = ByteArray(GenericHash.BYTES)
        if (!sodium.cryptoGenericHash(nameHash, nameHash.size, nameAsData, nameAsData.size.toLong())) {
            throw Error.HashingFailed
        }
        val base64EncodedNameHash = Base64.encodeBytes(nameHash)
        // Ask 3 different snodes for the Account ID associated with the given name hash
        val parameters = buildMap<String, Any> {
            this["endpoint"] = "ons_resolve"
            this["params"] = buildMap {
                this["type"] = 0
                this["name_hash"] = base64EncodedNameHash
            }
        }
        val promises = List(validationCount) {
            getRandomSnode().bind { snode ->
                retryIfNeeded(maxRetryCount) {
                    invoke(Snode.Method.OxenDaemonRPCCall, snode, parameters)
                }
            }
        }
        all(promises).map { results ->
            results.map { json ->
                val intermediate = json["result"] as? Map<*, *> ?: throw Error.Generic
                val hexEncodedCiphertext = intermediate["encrypted_value"] as? String ?: throw Error.Generic
                val ciphertext = Hex.fromStringCondensed(hexEncodedCiphertext)
                val isArgon2Based = (intermediate["nonce"] == null)
                if (isArgon2Based) {
                    // Handle old Argon2-based encryption used before HF16
                    val salt = ByteArray(PwHash.SALTBYTES)
                    val nonce = ByteArray(SecretBox.NONCEBYTES)
                    val accountIDAsData = ByteArray(accountIDByteCount)
                    val key = try {
                        Key.fromHexString(sodium.cryptoPwHash(onsName, SecretBox.KEYBYTES, salt, PwHash.OPSLIMIT_MODERATE, PwHash.MEMLIMIT_MODERATE, PwHash.Alg.PWHASH_ALG_ARGON2ID13)).asBytes
                    } catch (e: SodiumException) {
                        throw Error.HashingFailed
                    }
                    if (!sodium.cryptoSecretBoxOpenEasy(accountIDAsData, ciphertext, ciphertext.size.toLong(), nonce, key)) {
                        throw Error.DecryptionFailed
                    }
                    Hex.toStringCondensed(accountIDAsData)
                } else {
                    val hexEncodedNonce = intermediate["nonce"] as? String ?: throw Error.Generic
                    val nonce = Hex.fromStringCondensed(hexEncodedNonce)
                    val key = ByteArray(GenericHash.BYTES)
                    if (!sodium.cryptoGenericHash(key, key.size, nameAsData, nameAsData.size.toLong(), nameHash, nameHash.size)) {
                        throw Error.HashingFailed
                    }
                    val accountIDAsData = ByteArray(accountIDByteCount)
                    if (!sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(accountIDAsData, null, null, ciphertext, ciphertext.size.toLong(), null, 0, nonce, key)) {
                        throw Error.DecryptionFailed
                    }
                    Hex.toStringCondensed(accountIDAsData)
                }
            }.takeIf { it.size == validationCount && it.toSet().size == 1 }?.first()
                ?: throw Error.ValidationFailed
        }
    }.unwrap()

    fun getSwarm(publicKey: String): Promise<Set<Snode>, Exception> =
        database.getSwarm(publicKey)?.takeIf { it.size >= minimumSwarmSnodeCount }?.let(Promise.Companion::of)
            ?: getRandomSnode().bind {
                invoke(Snode.Method.GetSwarm, it, parameters = buildMap { this["pubKey"] = publicKey }, publicKey)
            }.map {
                parseSnodes(it).toSet()
            }.success {
                database.setSwarm(publicKey, it)
            }

    private fun signAndEncodeCatching(data: ByteArray, userED25519KeyPair: KeyPair): Result<String> =
        runCatching { signAndEncode(data, userED25519KeyPair) }
    private fun signAndEncode(data: ByteArray, userED25519KeyPair: KeyPair): String =
        sign(data, userED25519KeyPair).let(Base64::encodeBytes)
    private fun sign(data: ByteArray, userED25519KeyPair: KeyPair): ByteArray = ByteArray(Sign.BYTES).also {
        sodium.cryptoSignDetached(it, data, data.size.toLong(), userED25519KeyPair.secretKey.asBytes)
    }

    private fun getUserED25519KeyPairCatchingOrNull() = runCatching { MessagingModuleConfiguration.shared.getUserED25519KeyPair() }.getOrNull()
    private fun getUserED25519KeyPair(): KeyPair? = MessagingModuleConfiguration.shared.getUserED25519KeyPair()
    private fun getUserPublicKey() = MessagingModuleConfiguration.shared.storage.getUserPublicKey()

    fun getRawMessages(snode: Snode, publicKey: String, requiresAuth: Boolean = true, namespace: Int = 0): RawResponsePromise {
        // Get last message hash
        val lastHashValue = database.getLastMessageHashValue(snode, publicKey, namespace) ?: ""
        val parameters = buildMutableMap<String, Any> {
            this["pubKey"] = publicKey
            this["last_hash"] = lastHashValue
            // If the namespace is default (0) here it will be implicitly read as 0 on the storage server
            // we only need to specify it explicitly if we want to (in future) or if it is non-zero
            namespace.takeIf { it != 0 }?.let { this["namespace"] = it }
        }
        // Construct signature
        if (requiresAuth) {
            val userED25519KeyPair = try {
                getUserED25519KeyPair() ?: return Promise.ofFail(Error.NoKeyPair)
            } catch (e: Exception) {
                Log.e("Loki", "Error getting KeyPair", e)
                return Promise.ofFail(Error.NoKeyPair)
            }
            val timestamp = System.currentTimeMillis() + clockOffset
            val ed25519PublicKey = userED25519KeyPair.publicKey.asHexString
            val verificationData = buildString {
                append("retrieve")
                namespace.takeIf { it != 0 }?.let(::append)
                append(timestamp)
            }.toByteArray()
            parameters["signature"] = signAndEncodeCatching(verificationData, userED25519KeyPair).getOrNull()
                ?: return Promise.ofFail(Error.SigningFailed)
            parameters["timestamp"] = timestamp
            parameters["pubkey_ed25519"] = ed25519PublicKey
        }

        // Make the request
        return invoke(Snode.Method.Retrieve, snode, parameters, publicKey)
    }

    fun buildAuthenticatedStoreBatchInfo(namespace: Int, message: SnodeMessage): SnodeBatchRequestInfo? {
        // used for sig generation since it is also the value used in timestamp parameter
        val messageTimestamp = message.timestamp

        val userED25519KeyPair = getUserED25519KeyPairCatchingOrNull() ?: return null

        val verificationData = "store$namespace$messageTimestamp".toByteArray()
        val signature = signAndEncodeCatching(verificationData, userED25519KeyPair).run {
            getOrNull() ?: return null.also { Log.e("Loki", "Signing data failed with user secret key", exceptionOrNull()) }
        }

        val params = buildMap {
            // load the message data params into the sub request
            // currently loads:
            // pubKey
            // data
            // ttl
            // timestamp
            putAll(message.toJSON())
            this["namespace"] = namespace
            // timestamp already set
            this["pubkey_ed25519"] = userED25519KeyPair.publicKey.asHexString
            this["signature"] = signature
        }

        return SnodeBatchRequestInfo(
            Snode.Method.SendMessage.rawValue,
            params,
            namespace
        )
    }

    /**
     * Message hashes can be shared across multiple namespaces (for a single public key destination)
     * @param publicKey the destination's identity public key to delete from (05...)
     * @param messageHashes a list of stored message hashes to delete from the server
     * @param required indicates that *at least one* message in the list is deleted from the server, otherwise it will return 404
     */
    fun buildAuthenticatedDeleteBatchInfo(publicKey: String, messageHashes: List<String>, required: Boolean = false): SnodeBatchRequestInfo? {
        val userEd25519KeyPair = getUserED25519KeyPairCatchingOrNull() ?: return null
        val ed25519PublicKey = userEd25519KeyPair.publicKey.asHexString
        val verificationData = sequenceOf("delete").plus(messageHashes).toByteArray()
        val signature = try {
            signAndEncode(verificationData, userEd25519KeyPair)
        } catch (e: Exception) {
            Log.e("Loki", "Signing data failed with user secret key", e)
            return null
        }
        val params = buildMap {
            this["pubkey"] = publicKey
            this["required"] = required // could be omitted technically but explicit here
            this["messages"] = messageHashes
            this["pubkey_ed25519"] = ed25519PublicKey
            this["signature"] = signature
        }
        return SnodeBatchRequestInfo(
            Snode.Method.DeleteMessage.rawValue,
            params,
            null
        )
    }

    fun buildAuthenticatedRetrieveBatchRequest(snode: Snode, publicKey: String, namespace: Int = 0, maxSize: Int? = null): SnodeBatchRequestInfo? {
        val lastHashValue = database.getLastMessageHashValue(snode, publicKey, namespace) ?: ""
        val userEd25519KeyPair = getUserED25519KeyPairCatchingOrNull() ?: return null
        val ed25519PublicKey = userEd25519KeyPair.publicKey.asHexString
        val timestamp = System.currentTimeMillis() + clockOffset
        val verificationData = if (namespace == 0) "retrieve$timestamp".toByteArray()
        else "retrieve$namespace$timestamp".toByteArray()
        val signature = try {
            signAndEncode(verificationData, userEd25519KeyPair)
        } catch (e: Exception) {
            Log.e("Loki", "Signing data failed with user secret key", e)
            return null
        }
        val params = buildMap {
            this["pubkey"] = publicKey
            this["last_hash"] = lastHashValue
            this["timestamp"] = timestamp
            this["pubkey_ed25519"] = ed25519PublicKey
            this["signature"] = signature
            if (namespace != 0) this["namespace"] = namespace
            if (maxSize != null) this["max_size"] = maxSize
        }
        return SnodeBatchRequestInfo(
            Snode.Method.Retrieve.rawValue,
            params,
            namespace
        )
    }

    fun buildAuthenticatedAlterTtlBatchRequest(
        messageHashes: List<String>,
        newExpiry: Long,
        publicKey: String,
        shorten: Boolean = false,
        extend: Boolean = false): SnodeBatchRequestInfo? {
        val params = buildAlterTtlParams(messageHashes, newExpiry, publicKey, extend, shorten) ?: return null
        return SnodeBatchRequestInfo(
            Snode.Method.Expire.rawValue,
            params,
            null
        )
    }

    fun getRawBatchResponse(snode: Snode, publicKey: String, requests: List<SnodeBatchRequestInfo>, sequence: Boolean = false): RawResponsePromise {
        val parameters = buildMap { this["requests"] = requests }
        return invoke(if (sequence) Snode.Method.Sequence else Snode.Method.Batch, snode, parameters, publicKey).success { rawResponses ->
            rawResponses["results"].let { it as List<RawResponse> }
                .asSequence()
                .filter { it["code"] as? Int != 200 }
                .forEach { response ->
                    Log.w("Loki", "response code was not 200")
                    handleSnodeError(
                        response["code"] as? Int ?: 0,
                        response["body"] as? Map<*, *>,
                        snode,
                        publicKey
                    )
                }
        }
    }

    fun getExpiries(messageHashes: List<String>, publicKey: String) : RawResponsePromise {
        val userEd25519KeyPair = getUserED25519KeyPairCatchingOrNull() ?: return Promise.ofFail(NullPointerException("No user key pair"))
        val hashes = messageHashes.takeIf { it.size != 1 } ?: (messageHashes + "///////////////////////////////////////////") // TODO remove this when bug is fixed on nodes.
        return retryIfNeeded(maxRetryCount) {
            val timestamp = System.currentTimeMillis() + clockOffset
            val signData = sequenceOf(Snode.Method.GetExpiries.rawValue).plus(timestamp.toString()).plus(hashes).toByteArray()

            val ed25519PublicKey = userEd25519KeyPair.publicKey.asHexString
            val signature = try {
                signAndEncode(signData, userEd25519KeyPair)
            } catch (e: Exception) {
                Log.e("Loki", "Signing data failed with user secret key", e)
                return@retryIfNeeded Promise.ofFail(e)
            }
            val params = buildMap {
                this["pubkey"] = publicKey
                this["messages"] = hashes
                this["timestamp"] = timestamp
                this["pubkey_ed25519"] = ed25519PublicKey
                this["signature"] = signature
            }
            getSingleTargetSnode(publicKey) bind { snode ->
                invoke(Snode.Method.GetExpiries, snode, params, publicKey)
            }
        }
    }

    fun alterTtl(messageHashes: List<String>, newExpiry: Long, publicKey: String, extend: Boolean = false, shorten: Boolean = false): RawResponsePromise =
        retryIfNeeded(maxRetryCount) {
            val params = buildAlterTtlParams(messageHashes, newExpiry, publicKey, extend, shorten)
                ?: return@retryIfNeeded Promise.ofFail(
                    Exception("Couldn't build signed params for alterTtl request for newExpiry=$newExpiry, extend=$extend, shorten=$shorten")
                )
            getSingleTargetSnode(publicKey).bind { snode ->
                invoke(Snode.Method.Expire, snode, params, publicKey)
            }
        }

    private fun buildAlterTtlParams( // TODO: in future this will probably need to use the closed group subkeys / admin keys for group swarms
        messageHashes: List<String>,
        newExpiry: Long,
        publicKey: String,
        extend: Boolean = false,
        shorten: Boolean = false
    ): Map<String, Any>? {
        val userEd25519KeyPair = getUserED25519KeyPairCatchingOrNull() ?: return null

        val shortenOrExtend = if (extend) "extend" else if (shorten) "shorten" else ""

        val signData = sequenceOf(Snode.Method.Expire.rawValue).plus(shortenOrExtend).plus(newExpiry.toString()).plus(messageHashes).toByteArray()

        val signature = try {
            signAndEncode(signData, userEd25519KeyPair)
        } catch (e: Exception) {
            Log.e("Loki", "Signing data failed with user secret key", e)
            return null
        }

        return buildMap {
            this["expiry"] = newExpiry
            this["messages"] = messageHashes
            when {
                extend -> this["extend"] = true
                shorten -> this["shorten"] = true
            }
            this["pubkey"] = publicKey
            this["pubkey_ed25519"] = userEd25519KeyPair.publicKey.asHexString
            this["signature"] = signature
        }
    }

    fun getMessages(publicKey: String): MessageListPromise = retryIfNeeded(maxRetryCount) {
       getSingleTargetSnode(publicKey).bind { snode ->
            getRawMessages(snode, publicKey).map { parseRawMessagesResponse(it, snode, publicKey) }
        }
    }

    private fun getNetworkTime(snode: Snode): Promise<Pair<Snode, Long>, Exception> =
        invoke(Snode.Method.Info, snode, emptyMap()).map { rawResponse ->
            val timestamp = rawResponse["timestamp"] as? Long ?: -1
            snode to timestamp
        }

    fun sendMessage(message: SnodeMessage, requiresAuth: Boolean = false, namespace: Int = 0): RawResponsePromise =
        retryIfNeeded(maxRetryCount) {
            val userED25519KeyPair = getUserED25519KeyPair() ?: return@retryIfNeeded Promise.ofFail(Error.NoKeyPair)
            val parameters = message.toJSON().toMutableMap<String, Any>()
            // Construct signature
            if (requiresAuth) {
                val sigTimestamp = nowWithOffset
                val ed25519PublicKey = userED25519KeyPair.publicKey.asHexString
                // assume namespace here is non-zero, as zero namespace doesn't require auth
                val verificationData = "store$namespace$sigTimestamp".toByteArray()
                val signature = try {
                    signAndEncode(verificationData, userED25519KeyPair)
                } catch (exception: Exception) {
                    return@retryIfNeeded Promise.ofFail(Error.SigningFailed)
                }
                parameters["sig_timestamp"] = sigTimestamp
                parameters["pubkey_ed25519"] = ed25519PublicKey
                parameters["signature"] = signature
            }
            // If the namespace is default (0) here it will be implicitly read as 0 on the storage server
            // we only need to specify it explicitly if we want to (in future) or if it is non-zero
            if (namespace != 0) {
                parameters["namespace"] = namespace
            }
            val destination = message.recipient
            getSingleTargetSnode(destination).bind { snode ->
                invoke(Snode.Method.SendMessage, snode, parameters, destination)
            }
        }

    suspend fun deleteMessage(publicKey: String, serverHashes: List<String>) {
        retryWithUniformInterval {
            val userED25519KeyPair =
                getUserED25519KeyPair() ?: throw (Error.NoKeyPair)
            val userPublicKey =
                getUserPublicKey() ?: throw (Error.NoKeyPair)
            val snode = getSingleTargetSnode(publicKey).await()
            val verificationData =
                sequenceOf(Snode.Method.DeleteMessage.rawValue).plus(serverHashes).toByteArray()
            val deleteMessageParams = buildMap {
                this["pubkey"] = userPublicKey
                this["pubkey_ed25519"] = userED25519KeyPair.publicKey.asHexString
                this["messages"] = serverHashes
                this["signature"] = signAndEncode(verificationData, userED25519KeyPair)
            }
            val rawResponse = invoke(
                Snode.Method.DeleteMessage,
                snode,
                deleteMessageParams,
                publicKey
            ).await()

            // thie next step is to verify the nodes on our swarm and check that the message was deleted
            // on at least one of them
            val swarms = rawResponse["swarm"] as? Map<String, Any> ?: throw (Error.Generic)

            val deletedMessages = swarms.mapValuesNotNull { (hexSnodePublicKey, rawJSON) ->
                (rawJSON as? Map<String, Any>)?.let { json ->
                    val isFailed = json["failed"] as? Boolean ?: false
                    val statusCode = json["code"] as? String
                    val reason = json["reason"] as? String

                    if (isFailed) {
                        Log.e(
                            "Loki",
                            "Failed to delete messages from: $hexSnodePublicKey due to error: $reason ($statusCode)."
                        )
                        false
                    } else {
                        // Hashes of deleted messages
                        val hashes = json["deleted"] as List<String>
                        val signature = json["signature"] as String
                        val snodePublicKey = Key.fromHexString(hexSnodePublicKey)
                        // The signature looks like ( PUBKEY_HEX || RMSG[0] || ... || RMSG[N] || DMSG[0] || ... || DMSG[M] )
                        val message = sequenceOf(userPublicKey).plus(serverHashes).plus(hashes)
                            .toByteArray()
                        sodium.cryptoSignVerifyDetached(
                            Base64.decode(signature),
                            message,
                            message.size,
                            snodePublicKey.asBytes
                        )
                    }
                }
            }

            // if all the nodes returned false (the message was not deleted) then we consider this a failed scenario
            if (deletedMessages.entries.all { !it.value }) throw (Error.Generic)
        }
    }

    // Parsing
    private fun parseSnodes(rawResponse: Any): List<Snode> =
        (rawResponse as? Map<*, *>)
            ?.run { get("snodes") as? List<*> }
            ?.asSequence()
            ?.mapNotNull { it as? Map<*, *> }
            ?.mapNotNull {
                createSnode(
                    address = it["ip"] as? String,
                    port = (it["port"] as? String)?.toInt(),
                    ed25519Key = it[KEY_ED25519] as? String,
                    x25519Key = it[KEY_X25519] as? String
                ).apply { if (this == null) Log.d("Loki", "Failed to parse snode from: ${it.prettifiedDescription()}.") }
            }?.toList() ?: listOf<Snode>().also { Log.d("Loki", "Failed to parse snodes from: ${rawResponse.prettifiedDescription()}.") }

    fun deleteAllMessages(): Promise<Map<String,Boolean>, Exception> =
        retryIfNeeded(maxRetryCount) {
            val userED25519KeyPair = getUserED25519KeyPair() ?: return@retryIfNeeded Promise.ofFail(Error.NoKeyPair)
            val userPublicKey = getUserPublicKey() ?: return@retryIfNeeded Promise.ofFail(Error.NoKeyPair)
            getSingleTargetSnode(userPublicKey).bind { snode ->
                retryIfNeeded(maxRetryCount) {
                    getNetworkTime(snode).bind { (_, timestamp) ->
                        val verificationData = sequenceOf(Snode.Method.DeleteAll.rawValue, Namespace.ALL, timestamp.toString()).toByteArray()
                        val deleteMessageParams = buildMap {
                            this["pubkey"] = userPublicKey
                            this["pubkey_ed25519"] = userED25519KeyPair.publicKey.asHexString
                            this["timestamp"] = timestamp
                            this["signature"] = signAndEncode(verificationData, userED25519KeyPair)
                            this["namespace"] = Namespace.ALL
                        }
                        invoke(Snode.Method.DeleteAll, snode, deleteMessageParams, userPublicKey)
                            .map { rawResponse -> parseDeletions(userPublicKey, timestamp, rawResponse) }
                            .fail { e -> Log.e("Loki", "Failed to clear data", e) }
                    }
                }
            }
        }

    fun parseRawMessagesResponse(rawResponse: RawResponse, snode: Snode, publicKey: String, namespace: Int = 0, updateLatestHash: Boolean = true, updateStoredHashes: Boolean = true): List<Pair<SignalServiceProtos.Envelope, String?>> =
        (rawResponse["messages"] as? List<*>)?.let { messages ->
            if (updateLatestHash) updateLastMessageHashValueIfPossible(snode, publicKey, messages, namespace)
            removeDuplicates(publicKey, messages, namespace, updateStoredHashes).let(::parseEnvelopes)
        } ?: listOf()

    fun updateLastMessageHashValueIfPossible(snode: Snode, publicKey: String, rawMessages: List<*>, namespace: Int) {
        val lastMessageAsJSON = rawMessages.lastOrNull() as? Map<*, *>
        val hashValue = lastMessageAsJSON?.get("hash") as? String
        when {
            hashValue != null -> database.setLastMessageHashValue(snode, publicKey, hashValue, namespace)
            rawMessages.isNotEmpty() -> Log.d("Loki", "Failed to update last message hash value from: ${rawMessages.prettifiedDescription()}.")
        }
    }

    /**
     *
     *
     * TODO Use a db transaction, synchronizing is sufficient for now because
     * database#setReceivedMessageHashValues is only called here.
     */
    @Synchronized
    fun removeDuplicates(publicKey: String, rawMessages: List<*>, namespace: Int, updateStoredHashes: Boolean): List<Map<*, *>> {
        val hashValues = database.getReceivedMessageHashValues(publicKey, namespace)?.toMutableSet() ?: mutableSetOf()
        return rawMessages.filterIsInstance<Map<*, *>>().filter { rawMessage ->
            val hash = rawMessage["hash"] as? String
            hash ?: Log.d("Loki", "Missing hash value for message: ${rawMessage.prettifiedDescription()}.")
            hash?.let(hashValues::add) == true
        }.also {
            if (updateStoredHashes && it.isNotEmpty()) {
                database.setReceivedMessageHashValues(publicKey, hashValues, namespace)
            }
        }
    }

    private fun parseEnvelopes(rawMessages: List<Map<*, *>>): List<Pair<SignalServiceProtos.Envelope, String?>> = rawMessages.mapNotNull { rawMessage ->
        val base64EncodedData = rawMessage["data"] as? String
        val data = base64EncodedData?.let(Base64::decode)

        data ?: Log.d("Loki", "Failed to decode data for message: ${rawMessage.prettifiedDescription()}.")

        data?.runCatching { MessageWrapper.unwrap(this) to rawMessage["hash"] as? String }
            ?.onFailure { Log.d("Loki", "Failed to unwrap data for message: ${rawMessage.prettifiedDescription()}.") }
            ?.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDeletions(userPublicKey: String, timestamp: Long, rawResponse: RawResponse): Map<String, Boolean> =
        (rawResponse["swarm"] as? Map<String, Any>)?.mapValuesNotNull { (hexSnodePublicKey, rawJSON) ->
            val json = rawJSON as? Map<String, Any> ?: return@mapValuesNotNull null
            if (json["failed"] as? Boolean == true) {
                val reason = json["reason"] as? String
                val statusCode = json["code"] as? String
                Log.e("Loki", "Failed to delete all messages from: $hexSnodePublicKey due to error: $reason ($statusCode).")
                false
            } else {
                val hashes = (json["deleted"] as Map<String,List<String>>).flatMap { (_, hashes) -> hashes }.sorted() // Hashes of deleted messages
                val signature = json["signature"] as String
                val snodePublicKey = Key.fromHexString(hexSnodePublicKey)
                // The signature looks like ( PUBKEY_HEX || TIMESTAMP || DELETEDHASH[0] || ... || DELETEDHASH[N] )
                val message = sequenceOf(userPublicKey, "$timestamp").plus(hashes).toByteArray()
                sodium.cryptoSignVerifyDetached(Base64.decode(signature), message, message.size, snodePublicKey.asBytes)
            }
        } ?: mapOf()

    // endregion

    // Error Handling
    internal fun handleSnodeError(statusCode: Int, json: Map<*, *>?, snode: Snode, publicKey: String? = null): Throwable? = runCatching {
        fun handleBadSnode() {
            val oldFailureCount = snodeFailureCount[snode] ?: 0
            val newFailureCount = oldFailureCount + 1
            snodeFailureCount[snode] = newFailureCount
            Log.d("Loki", "Couldn't reach snode at $snode; setting failure count to $newFailureCount.")
            if (newFailureCount >= snodeFailureThreshold) {
                Log.d("Loki", "Failure threshold reached for: $snode; dropping it.")
                publicKey?.let { dropSnodeFromSwarmIfNeeded(snode, it) }
                snodePool = (snodePool - snode).also { Log.d("Loki", "Snode pool count: ${it.count()}.") }
                snodeFailureCount -= snode
            }
        }
        when (statusCode) {
            // Usually indicates that the snode isn't up to date
            400, 500, 502, 503 -> handleBadSnode()
            406 -> {
                Log.d("Loki", "The user's clock is out of sync with the service node network.")
                broadcaster.broadcast("clockOutOfSync")
                throw Error.ClockOutOfSync
            }
            421 -> {
                // The snode isn't associated with the given public key anymore
                if (publicKey == null) Log.d("Loki", "Got a 421 without an associated public key.")
                else json?.let(::parseSnodes)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { database.setSwarm(publicKey, it.toSet()) }
                    ?: dropSnodeFromSwarmIfNeeded(snode, publicKey).also { Log.d("Loki", "Invalidating swarm for: $publicKey.") }
            }
            404 -> {
                Log.d("Loki", "404, probably no file found")
                throw Error.Generic
            }
            else -> {
                handleBadSnode()
                Log.d("Loki", "Unhandled response code: ${statusCode}.")
                throw Error.Generic
            }
        }
    }.exceptionOrNull()
}

// Type Aliases
typealias RawResponse = Map<*, *>
typealias MessageListPromise = Promise<List<Pair<SignalServiceProtos.Envelope, String?>>, Exception>
typealias RawResponsePromise = Promise<RawResponse, Exception>
