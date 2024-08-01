package org.session.libsignal.utilities

data class AccountId(
    val prefix: IdPrefix?,
    val hexString: String,
    val publicKey: String
) {
    constructor(id: String): this(
        prefix = IdPrefix.fromValue(id),
        publicKey = id.drop(2),
        hexString = id,
    )

    constructor(prefix: IdPrefix, publicKey: ByteArray):
        this(
            prefix = prefix,
            publicKey = publicKey.toHexString(),
            hexString = prefix.value + publicKey.toHexString()
        )

    val pubKeyBytes by lazy {
        Hex.fromStringCondensed(publicKey)
    }
}
