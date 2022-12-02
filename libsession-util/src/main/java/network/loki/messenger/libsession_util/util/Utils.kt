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