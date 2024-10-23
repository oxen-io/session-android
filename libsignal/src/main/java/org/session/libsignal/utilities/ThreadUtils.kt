package org.session.libsignal.utilities

import android.os.Process
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext

object ThreadUtils {

    const val TAG = "ThreadUtils"

    const val PRIORITY_IMPORTANT_BACKGROUND_THREAD = Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE

    @Deprecated("Use a proper coroutine context/dispatcher instead, so it's clearer what priority you want the work to be done")
    @JvmStatic
    fun queue(target: () -> Unit) {
        Dispatchers.Default.dispatch(EmptyCoroutineContext) {
            try {
                target()
            } catch (e: Exception) {
                Log.e(TAG, e)
            }
        }
    }

    // Thread executor used by the audio recorder only
    @JvmStatic
    fun newDynamicSingleThreadedExecutor(): ExecutorService {
        val executor = ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, LinkedBlockingQueue())
        executor.allowCoreThreadTimeOut(true)
        return executor
    }
}