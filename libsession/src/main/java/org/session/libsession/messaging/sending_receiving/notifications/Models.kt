package org.session.libsession.messaging.sending_receiving.notifications

import com.goterl.lazysodium.utils.Key
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * N.B. all of these variable names will be named the same as the actual JSON utf-8 request/responses expected from the server.
 * Changing the variable names will break how data is serialized/deserialized.
 * If it's less than ideally named we can use [SerialName], such as for the push metadata which uses
 * single-letter keys to be as compact as possible.
 */

@Serializable
data class SubscriptionRequest(
    /** the 33-byte account being subscribed to; typically a session ID */
    val pubkey: String,
    /** when the pubkey starts with 05 (i.e. a session ID) this is the ed25519 32-byte pubkey associated with the session ID */
    val session_ed25519: String?,
    /** 32-byte swarm authentication subkey; omitted (or null) when not using subkey auth (new closed groups) */
    val subkey_tag: String? = null,
    /** array of integer namespaces to subscribe to, **must be sorted in ascending order** */
    val namespaces: List<Int>,
    /** if provided and true then notifications will include the body of the message (as long as it isn't too large) */
    val data: Boolean,
    /** the signature unix timestamp in seconds, not ms */
    val sig_ts: Long,
    /** the 64-byte ed25519 signature */
    val signature: String,
    /** the string identifying the notification service, "firebase" for android (currently) */
    val service: String,
    /** dict of service-specific data, currently just "token" field with device-specific token but different services might have other requirements */
    val service_info: Map<String, String>,
    /** 32-byte encryption key; notification payloads sent to the device will be encrypted with XChaCha20-Poly1305 via libsodium using this key.
     * persist it on device */
    val enc_key: String
)

@Serializable
data class UnsubscriptionRequest(
    /** the 33-byte account being subscribed to; typically a session ID */
    val pubkey: String,
    /** when the pubkey starts with 05 (i.e. a session ID) this is the ed25519 32-byte pubkey associated with the session ID */
    val session_ed25519: String?,
    /** 32-byte swarm authentication subkey; omitted (or null) when not using subkey auth (new closed groups) */
    val subkey_tag: String? = null,
    /** the signature unix timestamp in seconds, not ms */
    val sig_ts: Long,
    /** the 64-byte ed25519 signature */
    val signature: String,
    /** the string identifying the notification service, "firebase" for android (currently) */
    val service: String,
    /** dict of service-specific data, currently just "token" field with device-specific token but different services might have other requirements */
    val service_info: Map<String, String>,
)

/** invalid values, missing reuqired arguments etc, details in message */
private const val UNPARSEABLE_ERROR = 1
/** the "service" value is not active / valid */
private const val SERVICE_NOT_AVAILABLE = 2
/** something getting wrong internally talking to the backend */
private const val SERVICE_TIMEOUT = 3
/** other error processing the subscription (details in the message) */
private const val GENERIC_ERROR = 4

@Serializable
data class SubscriptionResponse(
    override val error: Int? = null,
    override val message: String? = null,
    override val success: Boolean? = null,
    val added: Boolean? = null,
    val updated: Boolean? = null,
): Response

@Serializable
data class UnsubscribeResponse(
    override val error: Int? = null,
    override val message: String? = null,
    override val success: Boolean? = null,
    val removed: Boolean? = null,
): Response

interface Response {
    val error: Int?
    val message: String?
    val success: Boolean?
    fun isSuccess() = success == true && error == null
    fun isFailure() = !isSuccess()
}

@Serializable
data class PushNotificationMetadata(
        /** Account ID (such as Session ID or closed group ID) where the message arrived **/
        @SerialName("@")
        val account: String,

        /** The hash of the message in the swarm. */
        @SerialName("#")
        val msg_hash: String,

        /** The swarm namespace in which this message arrived. */
        @SerialName("n")
        val namespace: Int,

        /** The length of the message data.  This is always included, even if the message content
         * itself was too large to fit into the push notification. */
        @SerialName("l")
        val data_len: Int,

        /** This will be true if the data was omitted because it was too long to fit in a push
         * notification (around 2.5kB of raw data), in which case the push notification includes
         * only this metadata but not the message content itself. */
        @SerialName("B")
        val data_too_long : Boolean = false
)

@Serializable
data class PushNotificationServerObject(
    val enc_payload: String,
    val spns: Int,
) {
    fun decryptPayload(key: Key): Any {

        TODO()
    }
}