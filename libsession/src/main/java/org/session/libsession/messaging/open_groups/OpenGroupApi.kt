package org.session.libsession.messaging.open_groups

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.type.TypeFactory
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.GenericHash
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupPoller.Companion.maxInactivityPeriod
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.OnionResponse
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Base64.decode
import org.session.libsignal.utilities.Base64.encodeBytes
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.utilities.HTTP.Verb.DELETE
import org.session.libsignal.utilities.HTTP.Verb.GET
import org.session.libsignal.utilities.HTTP.Verb.POST
import org.session.libsignal.utilities.HTTP.Verb.PUT
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.removing05PrefixIfNeeded
import org.whispersystems.curve25519.Curve25519
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

object OpenGroupApi {
    private val moderators: HashMap<String, Set<String>> =
        hashMapOf() // Server URL to (channel ID to set of moderator IDs)
    private val curve = Curve25519.getInstance(Curve25519.BEST)
    val defaultRooms = MutableSharedFlow<List<DefaultGroup>>(replay = 1)
    private val hasPerformedInitialPoll = mutableMapOf<String, Boolean>()
    private var hasUpdatedLastOpenDate = false
    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }
    private val timeSinceLastOpen by lazy {
        val context = MessagingModuleConfiguration.shared.context
        val lastOpenDate = TextSecurePreferences.getLastOpenTimeDate(context)
        val now = System.currentTimeMillis()
        now - lastOpenDate
    }

    private const val defaultServerPublicKey =
        "a03c383cf63c3c4efe67acc52112a6dd734b3a946b9545f488aaa93da7991238"
    const val defaultServer = "http://116.203.70.33"

    sealed class Error(message: String) : Exception(message) {
        object Generic : Error("An error occurred.")
        object ParsingFailed : Error("Invalid response.")
        object DecryptionFailed : Error("Couldn't decrypt response.")
        object SigningFailed : Error("Couldn't sign message.")
        object InvalidURL : Error("Invalid URL.")
        object NoPublicKey : Error("Couldn't find server public key.")
        object NoEd25519KeyPair : Error("Couldn't find ed25519 key pair.")
    }

    data class DefaultGroup(val id: String, val name: String, val image: ByteArray?) {

        val joinURL: String get() = "$defaultServer/$id?public_key=$defaultServerPublicKey"
    }

    data class Info(val id: String, val name: String, val imageID: Int?)

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class BatchRequest(
        val roomID: String,
        val fromDeletionServerID: Long?,
        val fromMessageServerID: Long?
    )

    data class BatchResult(
        val messages: List<OpenGroupMessageV2>,
        val deletions: List<MessageDeletion>,
        val moderators: List<String>
    )

    data class MessageDeletion(
        @JsonProperty("id")
        val id: Long = 0,
        @JsonProperty("deleted_message_id")
        val deletedMessageServerID: Long = 0
    ) {

        companion object {
            val empty = MessageDeletion()
        }
    }

    data class Request(
        val verb: HTTP.Verb,
        val room: String?,
        val server: String,
        val endpoint: String,
        val queryParameters: Map<String, String> = mapOf(),
        val parameters: Any? = null,
        val headers: Map<String, String> = mapOf(),
        /**
         * Always `true` under normal circumstances. You might want to disable
         * this when running over Lokinet.
         */
        val useOnionRouting: Boolean = true,
        val isBlinded: Boolean = true
    )

    private fun createBody(parameters: Any?): RequestBody? {
        if (parameters == null) return null
        val parametersAsJSON = JsonUtil.toJson(parameters)
        return RequestBody.create(MediaType.get("application/json"), parametersAsJSON)
    }

    private fun getResponseBodyJson(request: Request): Promise<Map<*, *>, Exception> {
        return send(request).map {
            JsonUtil.fromJson(it.body, Map::class.java)
        }
    }

    private fun send(request: Request): Promise<OnionResponse, Exception> {
        val url = HttpUrl.parse(request.server) ?: return Promise.ofFail(Error.InvalidURL)
        val urlBuilder = HttpUrl.Builder()
            .scheme(url.scheme())
            .host(url.host())
            .port(url.port())
            .addPathSegments(request.endpoint)
        if (request.verb == GET) {
            for ((key, value) in request.queryParameters) {
                urlBuilder.addQueryParameter(key, value)
            }
        }
        fun execute(): Promise<OnionResponse, Exception> {
            val publicKey =
                MessagingModuleConfiguration.shared.storage.getOpenGroupPublicKey(request.server)
                    ?: return Promise.ofFail(Error.NoPublicKey)
            val ed25519KeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair()
                ?: return Promise.ofFail(Error.NoEd25519KeyPair)
            val urlRequest = urlBuilder.build()
            val headers = request.headers.toMutableMap()
            val nonce = sodium.nonce(16)
            val timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
            var pubKey = ""
            var signature = ByteArray(0)
            if (request.isBlinded) {
                SodiumUtilities.blindedKeyPair(publicKey, ed25519KeyPair)?.let { keyPair ->
                    pubKey = SodiumUtilities.SessionId(
                        SodiumUtilities.IdPrefix.BLINDED,
                        keyPair.publicKey.asBytes
                    ).hexString
                    var bodyHash = ByteArray(0)
                    if (request.parameters != null) {
                        val parameterBytes = JsonUtil.toJson(request.parameters).toByteArray()
                        val parameterHash = ByteArray(GenericHash.BYTES_MAX)
                        if (sodium.cryptoGenericHash(
                            parameterHash,
                            parameterHash.size,
                            parameterBytes,
                            parameterBytes.size.toLong()
                        )) {
                            bodyHash = parameterHash
                        }
                    }
                    val messageBytes = Hex.fromStringCondensed(publicKey)
                        .plus(nonce)
                        .plus("$timestamp".toByteArray(Charsets.US_ASCII))
                        .plus(request.verb.rawValue.toByteArray())
                        .plus(urlRequest.encodedPath().toByteArray())
                        .plus(bodyHash)
                    signature = SodiumUtilities.sogsSignature(
                        messageBytes,
                        ed25519KeyPair.secretKey.asBytes,
                        keyPair.secretKey.asBytes,
                        keyPair.publicKey.asBytes
                    ) ?: return Promise.ofFail(Error.SigningFailed)
                } ?: return Promise.ofFail(Error.SigningFailed)
            } else {
                pubKey = SodiumUtilities.SessionId(
                    SodiumUtilities.IdPrefix.UN_BLINDED,
                    ed25519KeyPair.publicKey.asBytes
                ).hexString
                signature = ByteArray(0)
            }
            headers["X-SOGS-Nonce"] = encodeBytes(nonce)
            headers["X-SOGS-Timestamp"] = "$timestamp"
            headers["X-SOGS-Pubkey"] = pubKey
            headers["X-SOGS-Signature"] = encodeBytes(signature)

            val requestBuilder = okhttp3.Request.Builder()
                .url(urlRequest)
                .headers(Headers.of(headers))
            when (request.verb) {
                GET -> requestBuilder.get()
                PUT -> requestBuilder.put(createBody(request.parameters)!!)
                POST -> requestBuilder.post(createBody(request.parameters)!!)
                DELETE -> requestBuilder.delete(createBody(request.parameters))
            }
            if (!request.room.isNullOrEmpty()) {
                requestBuilder.header("Room", request.room)
            }
            return if (request.useOnionRouting) {
                OnionRequestAPI.sendOnionRequest(requestBuilder.build(), request.server, publicKey)
            } else {
                Promise.ofFail(IllegalStateException("It's currently not allowed to send non onion routed requests."))
            }
        }
        return execute()
    }

    fun downloadOpenGroupProfilePicture(
        server: String,
        roomID: String,
        imageId: Int?
    ): Promise<ByteArray, Exception> {
        val request = Request(
            verb = GET,
            room = roomID,
            server = server,
            endpoint = "room/$roomID/file/$imageId"
        )
        return send(request).map { it.body ?: throw Error.ParsingFailed }
    }

    // region Upload/Download
    fun upload(file: ByteArray, room: String, server: String): Promise<Long, Exception> {
        val base64EncodedFile = encodeBytes(file)
        val parameters = mapOf("file" to base64EncodedFile)
        val request = Request(
            verb = POST,
            room = room,
            server = server,
            endpoint = "files",
            parameters = parameters
        )
        return getResponseBodyJson(request).map { json ->
            (json["result"] as? Number)?.toLong() ?: throw Error.ParsingFailed
        }
    }

    fun download(file: Long, room: String, server: String): Promise<ByteArray, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "file/$file")
        return send(request).map { it.body ?: throw Error.ParsingFailed }
    }
    // endregion

    // region Sending
    fun send(
        message: OpenGroupMessageV2,
        room: String,
        server: String
    ): Promise<OpenGroupMessageV2, Exception> {
        val signedMessage = message.sign() ?: return Promise.ofFail(Error.SigningFailed)
        val jsonMessage = signedMessage.toJSON()
        val request = Request(
            verb = POST,
            room = room,
            server = server,
            endpoint = "messages",
            parameters = jsonMessage
        )
        return getResponseBodyJson(request).map { json ->
            @Suppress("UNCHECKED_CAST") val rawMessage = json["message"] as? Map<String, Any>
                ?: throw Error.ParsingFailed
            val result = OpenGroupMessageV2.fromJSON(rawMessage) ?: throw Error.ParsingFailed
            val storage = MessagingModuleConfiguration.shared.storage
            storage.addReceivedMessageTimestamp(result.sentTimestamp)
            result
        }
    }
    // endregion

    // region Messages
    fun getMessages(room: String, server: String): Promise<List<OpenGroupMessageV2>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val queryParameters = mutableMapOf<String, String>()
        storage.getLastMessageServerID(room, server)?.let { lastId ->
            queryParameters += "from_server_id" to lastId.toString()
        }
        val request = Request(
            verb = GET,
            room = room,
            server = server,
            endpoint = "messages",
            queryParameters = queryParameters
        )
        return getResponseBodyJson(request).map { json ->
            @Suppress("UNCHECKED_CAST") val rawMessages =
                json["messages"] as? List<Map<String, Any>>
                    ?: throw Error.ParsingFailed
            parseMessages(room, server, rawMessages)
        }
    }

    private fun parseMessages(
        room: String,
        server: String,
        rawMessages: List<Map<*, *>>
    ): List<OpenGroupMessageV2> {
        val messages = rawMessages.mapNotNull { json ->
            json as Map<String, Any>
            try {
                val message = OpenGroupMessageV2.fromJSON(json) ?: return@mapNotNull null
                if (message.serverID == null || message.sender.isNullOrEmpty()) return@mapNotNull null
                val sender = message.sender
                val data = decode(message.base64EncodedData)
                val signature = decode(message.base64EncodedSignature)
                val publicKey = Hex.fromStringCondensed(sender.removing05PrefixIfNeeded())
                val isValid = curve.verifySignature(publicKey, data, signature)
                if (!isValid) {
                    Log.d("Loki", "Ignoring message with invalid signature.")
                    return@mapNotNull null
                }
                message
            } catch (e: Exception) {
                null
            }
        }
        return messages
    }
    // endregion

    // region Message Deletion
    @JvmStatic
    fun deleteMessage(serverID: Long, room: String, server: String): Promise<Unit, Exception> {
        val request =
            Request(verb = DELETE, room = room, server = server, endpoint = "messages/$serverID")
        return send(request).map {
            Log.d("Loki", "Message deletion successful.")
        }
    }

    fun getDeletedMessages(
        room: String,
        server: String
    ): Promise<List<MessageDeletion>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val queryParameters = mutableMapOf<String, String>()
        storage.getLastDeletionServerID(room, server)?.let { last ->
            queryParameters["from_server_id"] = last.toString()
        }
        val request = Request(
            verb = GET,
            room = room,
            server = server,
            endpoint = "deleted_messages",
            queryParameters = queryParameters
        )
        return send(request).map { response ->
            val json = JsonUtil.fromJson(response.body, Map::class.java)
            val type = TypeFactory.defaultInstance()
                .constructCollectionType(List::class.java, MessageDeletion::class.java)
            val idsAsString = JsonUtil.toJson(json["ids"])
            val serverIDs = JsonUtil.fromJson<List<MessageDeletion>>(idsAsString, type)
                ?: throw Error.ParsingFailed
            val lastMessageServerId = storage.getLastDeletionServerID(room, server) ?: 0
            val serverID = serverIDs.maxByOrNull { it.id } ?: MessageDeletion.empty
            if (serverID.id > lastMessageServerId) {
                storage.setLastDeletionServerID(room, server, serverID.id)
            }
            serverIDs
        }
    }
    // endregion

    // region Moderation
    private fun handleModerators(serverRoomId: String, moderatorList: List<String>) {
        moderators[serverRoomId] = moderatorList.toMutableSet()
    }

    fun getModerators(room: String, server: String): Promise<List<String>, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "moderators")
        return getResponseBodyJson(request).map { json ->
            @Suppress("UNCHECKED_CAST") val moderatorsJson = json["moderators"] as? List<String>
                ?: throw Error.ParsingFailed
            val id = "$server.$room"
            handleModerators(id, moderatorsJson)
            moderatorsJson
        }
    }

    @JvmStatic
    fun ban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val parameters = mapOf("public_key" to publicKey)
        val request = Request(
            verb = POST,
            room = room,
            server = server,
            endpoint = "block_list",
            parameters = parameters
        )
        return send(request).map {
            Log.d("Loki", "Banned user: $publicKey from: $server.$room.")
        }
    }

    fun banAndDeleteAll(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val parameters = mapOf("public_key" to publicKey)
        val request = Request(
            verb = POST,
            room = room,
            server = server,
            endpoint = "ban_and_delete_all",
            parameters = parameters
        )
        return send(request).map {
            Log.d("Loki", "Banned user: $publicKey from: $server.$room.")
        }
    }

    fun unban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val request =
            Request(verb = DELETE, room = room, server = server, endpoint = "block_list/$publicKey")
        return send(request).map {
            Log.d("Loki", "Unbanned user: $publicKey from: $server.$room")
        }
    }

    @JvmStatic
    fun isUserModerator(publicKey: String, room: String, server: String): Boolean =
        moderators["$server.$room"]?.contains(publicKey) ?: false
    // endregion

    // region General
    @Suppress("UNCHECKED_CAST")
    fun poll(
        rooms: List<String>,
        server: String
    ): Promise<Map<String, BatchResult>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val context = MessagingModuleConfiguration.shared.context
        val timeSinceLastOpen = this.timeSinceLastOpen
        val useMessageLimit = (hasPerformedInitialPoll[server] != true
                && timeSinceLastOpen > maxInactivityPeriod)
        hasPerformedInitialPoll[server] = true
        if (!hasUpdatedLastOpenDate) {
            hasUpdatedLastOpenDate = true
            TextSecurePreferences.setLastOpenDate(context)
        }
        val requests = rooms.map { room ->
            BatchRequest(
                roomID = room,
                fromDeletionServerID = if (useMessageLimit) null else storage.getLastDeletionServerID(
                    room,
                    server
                ),
                fromMessageServerID = if (useMessageLimit) null else storage.getLastMessageServerID(
                    room,
                    server
                )
            )
        }
        val request = Request(
            verb = POST,
            room = null,
            server = server,
            endpoint = "batch",
            parameters = mapOf("requests" to requests)
        )
        return getResponseBodyJson(request = request).map { json ->
            val results = json["results"] as? List<*> ?: throw Error.ParsingFailed
            results.mapNotNull { json ->
                if (json !is Map<*, *>) return@mapNotNull null
                val roomID = json["room_id"] as? String ?: return@mapNotNull null
                // Moderators
                val moderators = json["moderators"] as? List<String> ?: return@mapNotNull null
                handleModerators("$server.$roomID", moderators)
                // Deletions
                val type = TypeFactory.defaultInstance()
                    .constructCollectionType(List::class.java, MessageDeletion::class.java)
                val idsAsString = JsonUtil.toJson(json["deletions"])
                val deletions = JsonUtil.fromJson<List<MessageDeletion>>(idsAsString, type)
                    ?: throw Error.ParsingFailed
                // Messages
                val rawMessages =
                    json["messages"] as? List<Map<String, Any>> ?: return@mapNotNull null
                val messages = parseMessages(roomID, server, rawMessages)
                roomID to BatchResult(
                    messages = messages,
                    deletions = deletions,
                    moderators = moderators
                )
            }.toMap()
        }
    }

    fun getDefaultRoomsIfNeeded(): Promise<List<DefaultGroup>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        storage.setOpenGroupPublicKey(defaultServer, defaultServerPublicKey)
        return getAllRooms(defaultServer).map { groups ->
            val earlyGroups = groups.map { group ->
                DefaultGroup(group.id, group.name, null)
            }
            // See if we have any cached rooms, and if they already have images don't overwrite them with early non-image results
            defaultRooms.replayCache.firstOrNull()?.let { replayed ->
                if (replayed.none { it.image?.isNotEmpty() == true }) {
                    defaultRooms.tryEmit(earlyGroups)
                }
            }
            val images = groups.associate { group ->
                group.id to downloadOpenGroupProfilePicture(defaultServer, group.id, group.imageID)
            }
            groups.map { group ->
                val image = try {
                    images[group.id]!!.get()
                } catch (e: Exception) {
                    // No image or image failed to download
                    null
                }
                DefaultGroup(group.id, group.name, image)
            }
        }.success { new ->
            defaultRooms.tryEmit(new)
        }
    }

    fun getInfo(roomToken: String, server: String): Promise<Info, Exception> {
        val request = Request(
            verb = GET,
            room = null,
            server = server,
            endpoint = "room/$roomToken"
        )
        return getResponseBodyJson(request).map { json ->
            val rawRoom = json["room"] as? Map<*, *> ?: throw Error.ParsingFailed
            val id = rawRoom["id"] as? String ?: throw Error.ParsingFailed
            val name = rawRoom["name"] as? String ?: throw Error.ParsingFailed
            val imageID = rawRoom["image_id"] as? Int
            Info(id = id, name = name, imageID = imageID)
        }
    }

    private fun getAllRooms(server: String): Promise<List<Info>, Exception> {
        val request = Request(
            verb = GET,
            room = null,
            server = server,
            endpoint = "rooms",
            isBlinded = true
        )
        return send(request).map { response ->
            val rawRooms = JsonUtil.fromJson(response.body, List::class.java) ?: throw Error.ParsingFailed
            rawRooms.mapNotNull {
                val roomJson = it as? Map<*, *> ?: return@mapNotNull null
                val id = roomJson["token"] as? String ?: return@mapNotNull null
                val name = roomJson["name"] as? String ?: return@mapNotNull null
                val imageID = roomJson["image_id"] as? Int
                Info(id, name, imageID)
            }
        }
    }

    fun getMemberCount(room: String, server: String): Promise<Int, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "active_users")
        return getResponseBodyJson(request).map { json ->
            val activeUserCount = json["active_users"] as? Int ?: throw Error.ParsingFailed
            val storage = MessagingModuleConfiguration.shared.storage
            storage.setUserCount(room, server, activeUserCount)
            activeUserCount
        }
    }

    fun getCapabilities(room: String, server: String): Promise<List<String>, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "capabilities")
        return getResponseBodyJson(request).map { json ->
            json["capabilities"] as? List<String> ?: throw Error.ParsingFailed
        }
    }
    // endregion
}