package network.loki.messenger.libsession_util

data class Config(private val /* yucky */ pointer: Long) {

    companion object {
        external fun newInstance(): Config
    }

    var lastError: String? = null

    external fun setName(newName: String)


}