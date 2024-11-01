package org.session.libsession.database

import org.session.libsession.snode.OwnedSwarmAuth
import org.session.libsignal.utilities.AccountId

val StorageProtocol.userAuth: OwnedSwarmAuth?
    get() = getUserPublicKey()?.let { accountId ->
        getUserED25519KeyPair()?.let { keyPair ->
            OwnedSwarmAuth(
                accountId = AccountId(hexString = accountId),
                ed25519PublicKeyHex = keyPair.publicKey.asHexString,
                ed25519PrivateKey = keyPair.secretKey.asBytes
            )
        }
    }
