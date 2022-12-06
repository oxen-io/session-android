package network.loki.messenger.libsession_util.util

object Sodium {
    external fun ed25519KeyPair(seed: ByteArray): KeyPair
}