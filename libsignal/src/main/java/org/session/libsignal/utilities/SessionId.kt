package org.session.libsignal.utilities

class SessionId {

    companion object {
        // needed because JNI doesn't want to detect alternate constructors :shrug:
        @JvmStatic
        fun from(id: String): SessionId = SessionId(id)
    }

    var prefix: IdPrefix?
    var publicKey: String
    var pubKeyBytes: ByteArray

    constructor(id: String) {
        prefix = IdPrefix.fromValue(id)
        publicKey = id.drop(2)
        pubKeyBytes = Hex.fromStringCondensed(publicKey)
    }

    constructor(prefix: IdPrefix, publicKey: ByteArray) {
        this.prefix = prefix
        this.publicKey = publicKey.toHexString()
        this.pubKeyBytes = publicKey
    }

    fun hexString() = prefix?.value + publicKey


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SessionId

        if (prefix != other.prefix) return false
        if (publicKey != other.publicKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = prefix?.hashCode() ?: 0
        result = 31 * result + publicKey.hashCode()
        return result
    }


}