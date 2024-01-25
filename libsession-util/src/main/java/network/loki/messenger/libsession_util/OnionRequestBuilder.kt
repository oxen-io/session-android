package network.loki.messenger.libsession_util

import java.io.Closeable

// Note: The order must match 'EncryptType' in 'session/onionreq/builder.hpp'
enum class OnionRequestEncryptionType {
    AES_GCM, X_CHA_CHA_20
}

class OnionRequestBuilder(protected val pointer: Long): Closeable {
    companion object {
        init {
            System.loadLibrary("session_util")
        }

        external fun newInstance(encType: OnionRequestEncryptionType): OnionRequestBuilder
    }

    external fun free()
    override fun close() {
        free()
    }

    external fun setServerDestination(host: String, target: String, scheme: String, port: Int, x25519PubKeyHex: String)
    external fun setSnodeDestination(ed25519PubKeyHex: String, x25519PubKeyHex: String)
    external fun addHop(ed25519PubKeyHex: String, x25519PubKeyHex: String)
    external fun build(payload: ByteArray): ByteArray

    external fun decrypt(ciphertext: ByteArray): ByteArray
}