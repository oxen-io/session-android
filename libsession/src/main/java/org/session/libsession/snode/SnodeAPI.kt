@file:Suppress("NAME_SHADOWING")

package org.session.libsession.snode

import android.os.SystemClock
import com.fasterxml.jackson.databind.JsonNode
import com.goterl.lazysodium.exceptions.SodiumException
import com.goterl.lazysodium.interfaces.GenericHash
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.utils.Key
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import com.goterl.lazysodium.utils.KeyPair
import kotlinx.coroutines.coroutineScope
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.unwrap
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.messaging.utilities.SodiumUtilities.sodium
import org.session.libsession.snode.model.BatchResponse
import org.session.libsession.snode.model.StoreMessageResponse
import org.session.libsession.snode.utilities.asyncPromise
import org.session.libsession.snode.utilities.await
import org.session.libsession.snode.utilities.retrySuspendAsPromise
import org.session.libsession.utilities.mapValuesNotNull
import org.session.libsession.utilities.toByteArray
import org.session.libsignal.crypto.secureRandom
import org.session.libsignal.crypto.shuffledRandom
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Broadcaster
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
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

    @Deprecated("Use a dependency injected SnodeClock.currentTimeMills() instead")
    @JvmStatic
    val nowWithOffset
        get() = MessagingModuleConfiguration.shared.clock.currentTimeMills()

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        val namespace: Int?,
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

        else -> GlobalScope.asyncPromise {
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

    private suspend fun<Res> invokeSuspend(
        method: Snode.Method,
        snode: Snode,
        parameters: Map<String, Any>,
        responseClass: Class<Res>,
        publicKey: String? = null,
        version: Version = Version.V3
    ): Res = when {
        useOnionRequests -> {
            val resp = OnionRequestAPI.sendOnionRequest(method, parameters, snode, version, publicKey).await()
            JsonUtil.fromJson(resp.body ?: throw Error.Generic, responseClass)
        }

        else -> HTTP.execute(
            HTTP.Verb.POST,
            url = "${snode.address}:${snode.port}/storage_rpc/v1",
            parameters = buildMap {
                this["method"] = method.rawValue
                this["params"] = parameters
            }
        ).toString().let {
            JsonUtil.fromJson(it, responseClass)
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
        snodePool.takeIf { it.size >= minimumSnodePoolCount }?.secureRandom()?.let { Promise.of(it) } ?: GlobalScope.asyncPromise {
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

    fun getSingleTargetSnode(publicKey: String): Promise<Snode, Exception> {
        // SecureRandom should be cryptographically secure
        return getSwarm(publicKey).map { it.shuffledRandom().random() }
    }

    // Public API
    fun getAccountID(onsName: String): Promise<String, Exception> = GlobalScope.asyncPromise {
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

    /**
     * Fetch swarm nodes for the specific public key.
     *
     * Note: this differs from [getSwarm] in that it doesn't store the swarm nodes in the database.
     * This always fetches from network.
     */
    suspend fun fetchSwarmNodes(publicKey: String): List<Snode> {
        val randomNode = getRandomSnode().await()
        val response = invoke(
            method = Snode.Method.GetSwarm,
            snode = randomNode, parameters = buildMap { this["pubKey"] = publicKey },
            publicKey = publicKey
        ).await()

        return parseSnodes(response)
    }


    /**
     * Build parameters required to call authenticated storage API.
     *
     * @param auth The authentication data required to sign the request
     * @param namespace The namespace of the messages you want to retrieve. Null if not relevant.
     * @param verificationData A function that returns the data to be signed. The function takes the namespace text and timestamp as arguments.
     * @param timestamp The timestamp to be used in the request. Default is the current time.
     * @param builder A lambda that allows the user to add additional parameters to the request.
     */
    private fun buildAuthenticatedParameters(
        auth: SwarmAuth,
        namespace: Int?,
        verificationData: ((namespaceText: String, timestamp: Long) -> Any)? = null,
        timestamp: Long = nowWithOffset,
        builder: MutableMap<String, Any>.() -> Unit = {}
    ): Map<String, Any> {
        return buildMap {
            // Build user provided parameter first
            this.builder()

            if (verificationData != null) {
                // Namespace shouldn't be in the verification data if it's null or 0.
                val namespaceText = when (namespace) {
                    null, 0 -> ""
                    else -> namespace.toString()
                }

                val verifyData = when (val verify = verificationData(namespaceText, timestamp)) {
                    is String -> verify.toByteArray()
                    is ByteArray -> verify
                    else -> throw IllegalArgumentException("verificationData must return a String or ByteArray")
                }

                putAll(auth.sign(verifyData))
                put("timestamp", timestamp)
            }

            put("pubkey", auth.accountId.hexString)
            if (namespace != null && namespace != 0) {
                put("namespace", namespace)
            }

            auth.ed25519PublicKeyHex?.let { put("pubkey_ed25519", it) }
        }
    }

    /**
     * Retrieve messages from the swarm.
     *
     * @param snode The swarm service where you want to retrieve messages from. It can be a swarm for a specific user or a group. Call [getSingleTargetSnode] to get a swarm node.
     * @param auth The authentication data required to retrieve messages. This can be a user or group authentication data.
     * @param namespace The namespace of the messages you want to retrieve. Default is 0.
     */
    fun getRawMessages(
        snode: Snode,
        auth: SwarmAuth,
        namespace: Int = 0
    ): RawResponsePromise {
        val parameters = buildAuthenticatedParameters(
            namespace = namespace,
            auth = auth,
            verificationData = { ns, t -> "${Snode.Method.Retrieve.rawValue}$ns$t" }
        ) {
            put(
                "last_hash",
                database.getLastMessageHashValue(snode, auth.accountId.hexString, namespace).orEmpty()
            )
        }

        // Make the request
        return invoke(Snode.Method.Retrieve, snode, parameters, auth.accountId.hexString)
    }

    fun getUnauthenticatedRawMessages(
        snode: Snode,
        publicKey: String,
        namespace: Int = 0
    ): RawResponsePromise {
        val parameters = buildMap {
            put("last_hash", database.getLastMessageHashValue(snode, publicKey, namespace).orEmpty())
            put("pubkey", publicKey)
            if (namespace != 0) {
                put("namespace", namespace)
            }
        }

        return invoke(Snode.Method.Retrieve, snode, parameters, publicKey)
    }

    fun buildAuthenticatedStoreBatchInfo(
        namespace: Int,
        message: SnodeMessage,
        auth: SwarmAuth,
    ): SnodeBatchRequestInfo {
        check(message.recipient == auth.accountId.hexString) {
            "Message sent to ${message.recipient} but authenticated with ${auth.accountId.hexString}"
        }

        val params = buildAuthenticatedParameters(
            namespace = namespace,
            auth = auth,
            verificationData = { ns, t -> "${Snode.Method.SendMessage.rawValue}$ns$t" },
        ) {
            putAll(message.toJSON())
        }

        return SnodeBatchRequestInfo(
            Snode.Method.SendMessage.rawValue,
            params,
            namespace
        )
    }

    fun buildAuthenticatedUnrevokeSubKeyBatchRequest(
        groupAdminAuth: OwnedSwarmAuth,
        subAccountTokens: List<ByteArray>,
    ): SnodeBatchRequestInfo {
        val params = buildAuthenticatedParameters(
            namespace = null,
            auth = groupAdminAuth,
            verificationData = { _, t ->
                subAccountTokens.fold(
                    "${Snode.Method.UnrevokeSubAccount.rawValue}$t".toByteArray()
                ) { acc, subAccount -> acc + subAccount }
            }
        ) {
            put("unrevoke", subAccountTokens.map(Base64::encodeBytes))
        }

        return SnodeBatchRequestInfo(
            Snode.Method.UnrevokeSubAccount.rawValue,
            params,
            null
        )
    }

    fun buildAuthenticatedRevokeSubKeyBatchRequest(
        groupAdminAuth: OwnedSwarmAuth,
        subAccountTokens: List<ByteArray>,
    ): SnodeBatchRequestInfo {
        val params = buildAuthenticatedParameters(
            namespace = null,
            auth = groupAdminAuth,
            verificationData = { _, t ->
                subAccountTokens.fold(
                    "${Snode.Method.RevokeSubAccount.rawValue}$t".toByteArray()
                ) { acc, subAccount -> acc + subAccount }
            }
        ) {
            put("revoke", subAccountTokens.map(Base64::encodeBytes))
        }

        return SnodeBatchRequestInfo(
            Snode.Method.RevokeSubAccount.rawValue,
            params,
            null
        )
    }

    /**
     * Message hashes can be shared across multiple namespaces (for a single public key destination)
     * @param publicKey the destination's identity public key to delete from (05...)
     * @param ed25519PubKey the destination's ed25519 public key to delete from. Only required for user messages.
     * @param messageHashes a list of stored message hashes to delete from all namespaces on the server
     * @param required indicates that *at least one* message in the list is deleted from the server, otherwise it will return 404
     */
    fun buildAuthenticatedDeleteBatchInfo(
        auth: SwarmAuth,
        messageHashes: List<String>,
        required: Boolean = false
    ): SnodeBatchRequestInfo {
        val params = buildAuthenticatedParameters(
            namespace = null,
            auth = auth,
            verificationData = { _, _ ->
                buildString {
                    append(Snode.Method.DeleteMessage.rawValue)
                    messageHashes.forEach(this::append)
                }
            }
        ) {
            put("messages", messageHashes)
            put("required", required)
        }

        return SnodeBatchRequestInfo(
            Snode.Method.DeleteMessage.rawValue,
            params,
            null
        )
    }

    fun buildAuthenticatedRetrieveBatchRequest(
        auth: SwarmAuth,
        lastHash: String?,
        namespace: Int = 0,
        maxSize: Int? = null
    ): SnodeBatchRequestInfo {
        val params = buildAuthenticatedParameters(
            namespace = namespace,
            auth = auth,
            verificationData = { ns, t -> "${Snode.Method.Retrieve.rawValue}$ns$t" },
        ) {
            put("last_hash", lastHash.orEmpty())
            if (maxSize != null) {
                put("max_size", maxSize)
            }
        }

        return SnodeBatchRequestInfo(
            Snode.Method.Retrieve.rawValue,
            params,
            namespace
        )
    }

    fun buildAuthenticatedAlterTtlBatchRequest(
        auth: SwarmAuth,
        messageHashes: List<String>,
        newExpiry: Long,
        shorten: Boolean = false,
        extend: Boolean = false
    ): SnodeBatchRequestInfo {
        val params =
            buildAlterTtlParams(auth, messageHashes, newExpiry, extend, shorten)
        return SnodeBatchRequestInfo(
            Snode.Method.Expire.rawValue,
            params,
            null
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun getRawBatchResponse(
        snode: Snode,
        publicKey: String,
        requests: List<SnodeBatchRequestInfo>,
        sequence: Boolean = false
    ): RawResponsePromise {
        val parameters = buildMap { this["requests"] = requests }
        return invoke(
            if (sequence) Snode.Method.Sequence else Snode.Method.Batch,
            snode,
            parameters,
            publicKey
        ).success { rawResponses ->
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

    private data class RequestInfo(
        val snode: Snode,
        val publicKey: String,
        val request: SnodeBatchRequestInfo,
        val responseType: Class<*>,
        val callback: SendChannel<Result<Any>>,
        val requestTime: Long = SystemClock.uptimeMillis(),
    )

    private val batchedRequestsSender: SendChannel<RequestInfo>

    init {
        val batchRequests = Channel<RequestInfo>()
        batchedRequestsSender = batchRequests

        val batchWindowMills = 100L

        data class BatchKey(val snodeAddress: String, val publicKey: String)

        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch {
            val batches = hashMapOf<BatchKey, MutableList<RequestInfo>>()

            while (true) {
                val batch = select<List<RequestInfo>?> {
                    // If we receive a request, add it to the batch
                    batchRequests.onReceive {
                        batches.getOrPut(BatchKey(it.snode.address, it.publicKey)) { mutableListOf() }.add(it)
                        null
                    }

                    // If we have anything in the batch, look for the one that is about to expire
                    // and wait for it to expire, remove it from the batches and send it for
                    // processing.
                    if (batches.isNotEmpty()) {
                        val earliestBatch = batches.minBy { it.value.first().requestTime }
                        val deadline = earliestBatch.value.first().requestTime + batchWindowMills
                        onTimeout(
                            timeMillis = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)
                        ) {
                            batches.remove(earliestBatch.key)
                        }
                    }
                }

                if (batch != null) {
                    launch batch@{
                        val snode = batch.first().snode
                        val responses = try {
                            getBatchResponse(
                                snode = snode,
                                publicKey = batch.first().publicKey,
                                requests = batch.mapNotNull { info ->
                                    info.request.takeIf { !info.callback.isClosedForSend }
                                },
                                sequence = false
                            )
                        } catch (e: Exception) {
                            for (req in batch) {
                                runCatching {
                                    req.callback.send(Result.failure(e))
                                }
                            }
                            return@batch
                        }

                        // For each response, parse the result, match it with the request then send
                        // back through the request's callback.
                        for ((req, resp) in batch.zip(responses.results)) {
                            val result = runCatching {
                                check(resp.code == 200) {
                                    "Error calling \"${req.request.method}\" with code = ${resp.code}, msg = ${resp.body}"
                                }

                                JsonUtil.fromJson(resp.body, req.responseType)
                            }

                            runCatching{
                                req.callback.send(result)
                            }
                        }

                        // Close all channels in the requests just in case we don't have paired up
                        // responses.
                        for (req in batch) {
                            req.callback.close()
                        }
                    }
                }
            }
        }
    }

    suspend fun <T> sendBatchRequest(
        snode: Snode,
        publicKey: String,
        request: SnodeBatchRequestInfo,
        responseType: Class<T>,
    ): T {
        val callback = Channel<Result<T>>()
        @Suppress("UNCHECKED_CAST")
        batchedRequestsSender.send(RequestInfo(snode, publicKey, request, responseType, callback as SendChannel<Any>))
        try {
            return callback.receive().getOrThrow()
        } catch (e: CancellationException) {
            // Close the channel if the coroutine is cancelled, so the batch processing won't
            // handle this one (best effort only)
            callback.close()
            throw e
        }
    }

    suspend fun sendBatchRequest(
        snode: Snode,
        publicKey: String,
        request: SnodeBatchRequestInfo,
    ): JsonNode {
        return sendBatchRequest(snode, publicKey, request, JsonNode::class.java)
    }

    suspend fun getBatchResponse(
        snode: Snode,
        publicKey: String,
        requests: List<SnodeBatchRequestInfo>,
        sequence: Boolean = false
    ): BatchResponse {
        return invokeSuspend(
            method = if (sequence) Snode.Method.Sequence else Snode.Method.Batch,
            snode = snode,
            parameters = mapOf("requests" to requests),
            responseClass = BatchResponse::class.java,
            publicKey = publicKey
        ).also { resp ->
            // If there's a unsuccessful response, go through specific logic to handle
            // potential snode errors.
            val firstError = resp.results.firstOrNull { !it.isSuccessful }
            if (firstError != null) {
                handleSnodeError(
                    statusCode = firstError.code,
                    json = if (firstError.body.isObject) {
                        JsonUtil.fromJson(firstError.body, Map::class.java)
                    } else {
                        null
                    },
                    snode = snode,
                    publicKey = publicKey
                )
            }
        }
    }

    fun getExpiries(
        messageHashes: List<String>,
        auth: SwarmAuth,
    ): RawResponsePromise {
        val hashes = messageHashes.takeIf { it.size != 1 }
            ?: (messageHashes + "///////////////////////////////////////////") // TODO remove this when bug is fixed on nodes.
        return scope.retrySuspendAsPromise(maxRetryCount) {
            val params = buildAuthenticatedParameters(
                auth = auth,
                namespace = null,
                verificationData = { _, t -> buildString {
                    append(Snode.Method.GetExpiries.rawValue)
                    append(t)
                    hashes.forEach(this::append)
                } },
            ) {
                this["messages"] = hashes
            }

            val snode = getSingleTargetSnode(auth.accountId.hexString).await()
            invoke(Snode.Method.GetExpiries, snode, params, auth.accountId.hexString).await()
        }
    }

    fun alterTtl(
        auth: SwarmAuth,
        messageHashes: List<String>,
        newExpiry: Long,
        extend: Boolean = false,
        shorten: Boolean = false
    ): RawResponsePromise = scope.retrySuspendAsPromise(maxRetryCount) {
        val params = buildAlterTtlParams(auth, messageHashes, newExpiry, extend, shorten)
        val snode = getSingleTargetSnode(auth.accountId.hexString).await()
        invoke(Snode.Method.Expire, snode, params, auth.accountId.hexString).await()
    }

    private fun buildAlterTtlParams(
        auth: SwarmAuth,
        messageHashes: List<String>,
        newExpiry: Long,
        extend: Boolean = false,
        shorten: Boolean = false
    ): Map<String, Any> {
        val shortenOrExtend = if (extend) "extend" else if (shorten) "shorten" else ""

        return buildAuthenticatedParameters(
            namespace = null,
            auth = auth,
            verificationData = { _, _ ->
                buildString {
                    append("expire")
                    append(shortenOrExtend)
                    append(newExpiry.toString())
                    messageHashes.forEach(this::append)
                }
            }
        ) {
            this["expiry"] = newExpiry
            this["messages"] = messageHashes
            when {
                extend -> this["extend"] = true
                shorten -> this["shorten"] = true
            }
        }
    }

    fun getMessages(auth: SwarmAuth): MessageListPromise = scope.retrySuspendAsPromise(maxRetryCount) {
        val snode = getSingleTargetSnode(auth.accountId.hexString).await()
        val resp = getRawMessages(snode, auth).await()
        parseRawMessagesResponse(resp, snode, auth.accountId.hexString)
    }

    fun getNetworkTime(snode: Snode): Promise<Pair<Snode, Long>, Exception> =
        invoke(Snode.Method.Info, snode, emptyMap()).map { rawResponse ->
            val timestamp = rawResponse["timestamp"] as? Long ?: -1
            snode to timestamp
        }

    /**
     * Note: After this method returns, [auth] will not be used by any of async calls and it's afe
     * for the caller to clean up the associated resources if needed.
     */
    suspend fun sendMessage(
        message: SnodeMessage,
        auth: SwarmAuth?,
        namespace: Int = 0
    ): StoreMessageResponse {
        return retryWithUniformInterval(maxRetryCount = maxRetryCount) {
            val params = if (auth != null) {
                check(auth.accountId.hexString == message.recipient) {
                    "Message sent to ${message.recipient} but authenticated with ${auth.accountId.hexString}"
                }

                val timestamp = nowWithOffset

                buildAuthenticatedParameters(
                    auth = auth,
                    namespace = namespace,
                    verificationData = { ns, t -> "${Snode.Method.SendMessage.rawValue}$ns$t" },
                    timestamp = timestamp
                ) {
                    put("sig_timestamp", timestamp)
                    putAll(message.toJSON())
                }
            } else {
                buildMap {
                    putAll(message.toJSON())
                    if (namespace != 0) {
                        put("namespace", namespace)
                    }
                }
            }

            sendBatchRequest(
                snode = getSingleTargetSnode(message.recipient).await(),
                publicKey = message.recipient,
                request = SnodeBatchRequestInfo(
                    method = Snode.Method.SendMessage.rawValue,
                    params = params,
                    namespace = namespace
                ),
                responseType = StoreMessageResponse::class.java
            )
        }
    }
    
    suspend fun deleteMessage(publicKey: String, swarmAuth: SwarmAuth, serverHashes: List<String>) {
        retryWithUniformInterval {
            val snode = getSingleTargetSnode(publicKey).await()
            val params = buildAuthenticatedParameters(
                auth = swarmAuth,
                namespace = null,
                verificationData = { _, _ ->
                    buildString {
                        append(Snode.Method.DeleteMessage.rawValue)
                        serverHashes.forEach(this::append)
                    }
                }
            ) {
                this["messages"] = serverHashes
            }
            val rawResponse = invoke(
                Snode.Method.DeleteMessage,
                snode,
                params,
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
                        val message = sequenceOf(swarmAuth.accountId.hexString)
                            .plus(serverHashes)
                            .plus(hashes)
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
                ).apply {
                    if (this == null) Log.d(
                        "Loki",
                        "Failed to parse snode from: ${it.prettifiedDescription()}."
                    )
                }
            }?.toList() ?: listOf<Snode>().also {
            Log.d(
                "Loki",
                "Failed to parse snodes from: ${rawResponse.prettifiedDescription()}."
            )
        }

    fun deleteAllMessages(auth: SwarmAuth): Promise<Map<String, Boolean>, Exception> =
        scope.retrySuspendAsPromise(maxRetryCount) {
            val snode = getSingleTargetSnode(auth.accountId.hexString).await()
            val timestamp = MessagingModuleConfiguration.shared.clock.waitForNetworkAdjustedTime()

            val params = buildAuthenticatedParameters(
                auth = auth,
                namespace = null,
                verificationData = { _, t -> "${Snode.Method.DeleteAll.rawValue}all$t" },
                timestamp = timestamp
            ) {
                put("namespace", "all")
            }

            val rawResponse = invoke(Snode.Method.DeleteAll, snode, params, auth.accountId.hexString).await()
            parseDeletions(
                auth.accountId.hexString,
                timestamp,
                rawResponse
            )
        }

    fun parseRawMessagesResponse(rawResponse: RawResponse, snode: Snode, publicKey: String, namespace: Int = 0, updateLatestHash: Boolean = true, updateStoredHashes: Boolean = true, decrypt: ((ByteArray) -> Pair<ByteArray, AccountId>?)? = null): List<Pair<SignalServiceProtos.Envelope, String?>> =
        (rawResponse["messages"] as? List<*>)?.let { messages ->
            if (updateLatestHash) updateLastMessageHashValueIfPossible(snode, publicKey, messages, namespace)
            parseEnvelopes(removeDuplicates(publicKey, messages, namespace, updateStoredHashes), decrypt)
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

    private fun parseEnvelopes(rawMessages: List<*>, decrypt: ((ByteArray)->Pair<ByteArray, AccountId>?)?): List<Pair<SignalServiceProtos.Envelope, String?>> {
        return rawMessages.mapNotNull { rawMessage ->
            val rawMessageAsJSON = rawMessage as? Map<*, *>
            val base64EncodedData = rawMessageAsJSON?.get("data") as? String
            val data = base64EncodedData?.let { Base64.decode(it) }
            if (data != null) {
                try {
                    if (decrypt != null) {
                        val (decrypted, sender) = decrypt(data)!!
                        val envelope = SignalServiceProtos.Envelope.parseFrom(decrypted).toBuilder()
                        envelope.source = sender.hexString
                        Pair(envelope.build(), rawMessageAsJSON["hash"] as? String)
                    }
                    else Pair(MessageWrapper.unwrap(data), rawMessageAsJSON["hash"] as? String)
                } catch (e: Exception) {
                    Log.d("Loki", "Failed to unwrap data for message: ${rawMessage.prettifiedDescription()}.", e)
                    null
                }
            } else {
                Log.d("Loki", "Failed to decode data for message: ${rawMessage?.prettifiedDescription()}.")
                null
            }
        }
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
