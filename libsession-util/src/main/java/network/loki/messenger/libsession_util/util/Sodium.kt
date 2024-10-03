package network.loki.messenger.libsession_util.util

import java.util.regex.Pattern

object Sodium {

    const val KICKED_DOMAIN = "SessionGroupKickedMessage"
    val KICKED_REGEX: Pattern = Pattern.compile("^(05[a-zA-Z0-9]{64})(\\d+)$")

    init {
        System.loadLibrary("session_util")
    }
    external fun ed25519KeyPair(seed: ByteArray): KeyPair
    external fun ed25519PkToCurve25519(pk: ByteArray): ByteArray

    external fun encryptForMultipleSimple(
        messages: Array<ByteArray>,
        recipients: Array<ByteArray>,
        ed25519SecretKey: ByteArray,
        domain: String
    ): ByteArray

    external fun decryptForMultipleSimple(
        encoded: ByteArray,
        ed25519SecretKey: ByteArray,
        senderPubKey: ByteArray,
        domain: String,
    ): ByteArray?
}