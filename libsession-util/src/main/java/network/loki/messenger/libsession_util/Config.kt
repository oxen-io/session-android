package network.loki.messenger.libsession_util

import network.loki.messenger.libsession_util.util.ConfigWithSeqNo


sealed class ConfigBase(protected val /* yucky */ pointer: Long) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
    }
    external fun dirty(): Boolean
    external fun needsPush(): Boolean
    external fun needsDump(): Boolean
    external fun push(): ConfigWithSeqNo
}

class UserProfile(pointer: Long): ConfigBase(pointer) {
    companion object {
        init {
            System.loadLibrary("session_util")
        }
        external fun newInstance(): UserProfile
    }
    external fun setName(newName: String)
    external fun getName(): String?
    external fun free()
}