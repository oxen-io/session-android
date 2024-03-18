package org.thoughtcrime.securesms.database

data class ExpirationInfo(
    val id: Long,
    val timestamp: Long,
    val expiresIn: Long,
    val expireStarted: Long,
    val isMms: Boolean
) {
    private fun isDisappearAfterSend() = timestamp == expireStarted
    fun isDisappearAfterRead() = expiresIn > 0 && !isDisappearAfterSend()
}
