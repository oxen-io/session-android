package network.loki.messenger.libsession_util.util

data class BaseCommunityInfo(val baseUrl: String, val room: String, val pubKeyHex: String) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun parseFullUrl(fullUrl: String): Triple<String, String, ByteArray>?
    }
    external fun fullUrl(): String
}