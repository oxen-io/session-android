package org.session.libsignal.utilities

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object ThreadUtils {

    const val TAG = "ThreadUtils"

    const val PRIORITY_IMPORTANT_BACKGROUND_THREAD = Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE

    // Paraphrased from: https://www.baeldung.com/kotlin/create-thread-pool
    // "A cached thread pool such as one created via:
    // `val executorPool: ExecutorService = Executors.newCachedThreadPool()`
    // will utilize resources according to the requirements of submitted tasks. It will try to reuse
    // existing threads for submitted tasks but will create as many threads as it needs if new tasks
    // keep pouring in (with a memory usage of at least 1MB per created thread). These threads will
    // live for up to 60 seconds of idle time before terminating by default. As such, it presents a
    // very sharp tool that doesn't include any backpressure mechanism - and a sudden peak in load
    // can bring the system down with an OutOfMemory error. We can achieve a similar effect but with
    // better control by creating a ThreadPoolExecutor manually."

    private val corePoolSize      = Runtime.getRuntime().availableProcessors() // Default thread pool size is our CPU core count
    private val maxPoolSize       = corePoolSize * 4                           // Allow a maximum pool size of up to 4 threads per core
    private val keepAliveTimeSecs = 100L                                       // How long to keep idle threads in the pool before they are terminated
    private val workQueue         = SynchronousQueue<Runnable>()
    val executorPool: ExecutorService = ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTimeSecs, TimeUnit.SECONDS, workQueue)

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