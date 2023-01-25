package network.loki.messenger.libsession_util.util

sealed class ExpiryMode(val expiryMinutes: Long) {
    object NONE: ExpiryMode(0)
    class AfterSend(minutes: Long): ExpiryMode(minutes)
    class AfterRead(minutes: Long): ExpiryMode(minutes)
}