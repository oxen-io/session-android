package org.session.libsession.snode

import org.session.libsignal.utilities.AccountId

/**
 * An interface that represents the necessary data to sign a message for accounts.
 *
 */
interface SwarmAuth {
    /**
     * Sign the given data and return the signature JSON structure.
     */
    fun sign(data: ByteArray): Map<String, String>

    /**
     * Sign the given data and return the signature JSON structure.
     * This variant is used for push registry requests.
     */
    fun signForPushRegistry(data: ByteArray): Map<String, String>

    val accountId: AccountId
    val ed25519PublicKeyHex: String?
}