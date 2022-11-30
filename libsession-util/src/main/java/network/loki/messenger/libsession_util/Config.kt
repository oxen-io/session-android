package network.loki.messenger.libsession_util


sealed class ConfigBase(protected val /* yucky */ pointer: Long) {

}


class UserProfile(pointer: Long): ConfigBase(pointer) {

    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(): UserProfile
    }

    var lastError: String? = null

    external fun setName(newName: String)


}