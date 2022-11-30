package network.loki.messenger.libsession_util


sealed class ConfigBase(protected val /* yucky */ pointer: Long) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
    }
    external fun dirty(): Boolean
}

class UserProfile(pointer: Long): ConfigBase(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(): UserProfile
    }
    external fun setName(newName: String)
    external fun getName(): String
    external fun free()
}