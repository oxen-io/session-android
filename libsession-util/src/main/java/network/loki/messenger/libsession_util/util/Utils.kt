package network.loki.messenger.libsession_util.util

data class ConfigWithSeqNo(val config: ByteArray, val seqNo: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConfigWithSeqNo

        if (!config.contentEquals(other.config)) return false
        if (seqNo != other.seqNo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = config.contentHashCode()
        result = 31 * result + seqNo.hashCode()
        return result
    }

}

data class UserPic(val url: String, val key: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserPic

        if (url != other.url) return false
        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + key.contentHashCode()
        return result
    }
}

data class KeyPair(val pubKey: ByteArray, val secretKey: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyPair

        if (!pubKey.contentEquals(other.pubKey)) return false
        if (!secretKey.contentEquals(other.secretKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pubKey.contentHashCode()
        result = 31 * result + secretKey.contentHashCode()
        return result
    }
}