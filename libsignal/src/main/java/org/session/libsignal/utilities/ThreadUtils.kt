package org.session.libsignal.utilities

import android.os.Process
import java.util.concurrent.*

object ThreadUtils {

    const val PRIORITY_IMPORTANT_BACKGROUND_THREAD = Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE

    val executorPool: ExecutorService = Executors.newCachedThreadPool()

    @JvmStatic
    fun queue(target: Runnable) {
        try {
            executorPool.execute(target)
        }
        catch (e: Exception) {
            Log.e("ThreadUtils", "Exception returned from queue job: ${e.message}")
        }
    }

    fun queue(target: () -> Unit) {
        try {
            executorPool.execute(target)
        }
        catch (e: Exception) {
            Log.e("ThreadUtils", "Exception returned from queue job: ${e.message}")
        }
    }

    @JvmStatic
    fun newDynamicSingleThreadedExecutor(): ExecutorService {
        val executor = ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, LinkedBlockingQueue())
        executor.allowCoreThreadTimeOut(true)
        return executor
    }
}