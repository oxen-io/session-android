package org.session.libsignal.utilities

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object ThreadUtils {

    const val TAG = "ThreadUtils"

    const val PRIORITY_IMPORTANT_BACKGROUND_THREAD = Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE

    val executorPool: ExecutorService = Executors.newCachedThreadPool()

    // Note: To see how many threads are running in our app at any given time we can use:
    // val threadCount = getAllStackTraces().size

    @JvmStatic
    fun queue(target: Runnable) {
        executorPool.execute {
            try {
                target.run()
            } catch (e: Exception) {
                Log.e(TAG, e)
            }
        }
    }

    fun queue(target: () -> Unit) {
        executorPool.execute {
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