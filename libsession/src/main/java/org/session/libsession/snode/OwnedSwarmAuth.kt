package org.session.libsession.snode

import com.goterl.lazysodium.interfaces.Sign
import org.session.libsession.messaging.utilities.SodiumUtilities.sodium
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Base64

/**
 * A [SwarmAuth] that signs message using a single ED25519 private key.
 *
 * This should be used for the owner of an account, like a user or a group admin.
 */
class OwnedSwarmAuth(
    override val accountId: AccountId,
    override val ed25519PublicKeyHex: String?,
    val ed25519PrivateKey: ByteArray,
) : SwarmAuth {
    init {
        check(ed25519PrivateKey.size == Sign.SECRETKEYBYTES) {
            "Invalid secret key size, expecting ${Sign.SECRETKEYBYTES} but got ${ed25519PrivateKey.size}"
        }
    }

    override fun sign(data: ByteArray): Map<String, String> {
        val signature = Base64.encodeBytes(ByteArray(Sign.BYTES).also {
            check(sodium.cryptoSignDetached(it, data, data.size.toLong(), ed25519PrivateKey)) {
                "Failed to sign data"
            }
        })

        return buildMap {
            put("signature", signature)
        }
    }

    override fun signForPushRegistry(data: ByteArray): Map<String, String> {
        return sign(data)
    }

    companion object {
        fun ofClosedGroup(groupAccountId: AccountId, adminKey: ByteArray): OwnedSwarmAuth {
            return OwnedSwarmAuth(
                accountId = groupAccountId,
                ed25519PublicKeyHex = null,
                ed25519PrivateKey = adminKey
            )
        }
    }
}