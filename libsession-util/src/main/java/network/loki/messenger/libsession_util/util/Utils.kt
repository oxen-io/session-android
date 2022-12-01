package network.loki.messenger.libsession_util.util

data class StringWithLen(private val bytes: ByteArray, private val len: Long) { // We might not need this class, could be helpful though

    override fun toString(): String = bytes.decodeToString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StringWithLen

        if (!bytes.contentEquals(other.bytes)) return false
        if (len != other.len) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + len.hashCode()
        return result
    }

}


data class ConfigWithSeqNo(val config: String, val seqNo: Long)