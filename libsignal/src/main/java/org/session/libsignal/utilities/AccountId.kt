package org.session.libsignal.utilities

private val VALID_ACCOUNT_ID_PATTERN = Regex("[0-9]{2}[0-9a-fA-F]{64}")

data class AccountId(
    val hexString: String,
): Comparable<AccountId> {
    constructor(prefix: IdPrefix, publicKey: ByteArray):
        this(
            hexString = prefix.value + publicKey.toHexString()
        )

    init {
        check(VALID_ACCOUNT_ID_PATTERN.matches(hexString)) {
            "Invalid account ID: $hexString"
        }
    }

    val prefix: IdPrefix? get() = IdPrefix.fromValue(hexString)

    val pubKeyBytes: ByteArray by lazy {
        Hex.fromStringCondensed(hexString.drop(2))
    }

    override fun compareTo(other: AccountId): Int {
        return hexString.compareTo(other.hexString)
    }
}
