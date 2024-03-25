package network.loki.messenger.libsession_util.util

object Sodium {
    init {
        System.loadLibrary("session_util")
    }
    external fun ed25519KeyPair(seed: ByteArray): KeyPair
    external fun ed25519PkToCurve25519(pk: ByteArray): ByteArray
}