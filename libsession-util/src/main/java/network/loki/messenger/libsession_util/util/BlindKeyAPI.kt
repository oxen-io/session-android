package network.loki.messenger.libsession_util.util

object BlindKeyAPI {
    private val loadLibrary by lazy {
        System.loadLibrary("session_util")
    }

    init {
        // Ensure the library is loaded at initialization
        loadLibrary
    }

    external fun blindVersionKeyPair(ed25519SecretKey: ByteArray): KeyPair
    external fun blindVersionSign(ed25519SecretKey: ByteArray, timestamp: Long): ByteArray
}