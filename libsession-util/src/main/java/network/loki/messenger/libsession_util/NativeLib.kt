package network.loki.messenger.libsession_util

class NativeLib {

    /**
     * A native method that is implemented by the 'libsession_util' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'libsession_util' library on application startup.
        init {
            System.loadLibrary("session_util")
        }
    }
}