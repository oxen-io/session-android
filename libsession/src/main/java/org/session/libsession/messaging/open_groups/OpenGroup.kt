package org.session.libsession.messaging.open_groups

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import java.util.Locale

data class OpenGroup(
    val server: String,
    val room: String,
    val id: String,
    val name: String,
    val publicKey: String,
    val imageId: String?,
    val infoUpdates: Int,
    val canWrite: Boolean,
) {
    constructor(server: String, room: String, publicKey: String, name: String, imageId: String?, canWrite: Boolean, infoUpdates: Int) : this(
        server = server,
        room = room,
        id = "$server.$room",
        name = name,
        publicKey = publicKey,
        imageId = imageId,
        infoUpdates = infoUpdates,
        canWrite = canWrite
    )

    companion object {

        fun fromJSON(jsonAsString: String): OpenGroup? {
            return try {
                val json = JsonUtil.fromJson(jsonAsString)
                if (!json.has("room")) return null
                val room = json.get("room").asText().lowercase(Locale.US)
                val server = json.get("server").asText().lowercase(Locale.US)
                val displayName = json.get("displayName").asText()
                val publicKey = json.get("publicKey").asText()
                val imageId = if (json.hasNonNull("imageId")) { json.get("imageId")?.asText() } else { null }
                val canWrite = json.get("canWrite")?.asText()?.toBoolean() ?: true
                val infoUpdates = json.get("infoUpdates")?.asText()?.toIntOrNull() ?: 0
                OpenGroup(server = server, room = room, name = displayName, publicKey = publicKey, imageId = imageId, canWrite = canWrite, infoUpdates = infoUpdates)
            } catch (e: Exception) {
                Log.w("Loki", "Couldn't parse open group from JSON: $jsonAsString.", e);
                null
            }
        }

        fun getServer(urlAsString: String): HttpUrl? {
            val url = urlAsString.toHttpUrlOrNull() ?: return null
            val builder = HttpUrl.Builder().scheme(url.scheme).host(url.host)
            if (url.port != 80 || url.port != 443) {
                // Non-standard port; add to server
                builder.port(url.port)
            }
            return builder.build()
        }
    }

    fun toJson(): Map<String,String?> = mapOf(
        "room" to room,
        "server" to server,
        "publicKey" to publicKey,
        "displayName" to name,
        "imageId" to imageId,
        "infoUpdates" to infoUpdates.toString(),
        "canWrite" to canWrite.toString()
    )

    val joinURL: String get() = "$server/$room?public_key=$publicKey"

    val groupId: String get() = "$server.$room"
}