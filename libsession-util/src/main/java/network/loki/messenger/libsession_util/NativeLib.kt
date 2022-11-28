package network.loki.messenger.libsession_util

class NativeLib {

    companion object {
        // Used to load the 'libsession_util' library on application startup.
        init {
            System.loadLibrary("session_util")
        }
    }
}