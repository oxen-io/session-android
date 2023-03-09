package network.loki.messenger.libsession_util.util

sealed class ExpiryMode(val expirySeconds: Long) {
    object NONE: ExpiryMode(0)
    class AfterSend(seconds: Long): ExpiryMode(seconds)
    class AfterRead(seconds: Long): ExpiryMode(seconds)
}