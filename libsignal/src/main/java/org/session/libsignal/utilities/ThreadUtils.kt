package org.session.libsignal.utilities

import android.os.Process
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object ThreadUtils {

    const val PRIORITY_IMPORTANT_BACKGROUND_THREAD = Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE

    // From: https://www.baeldung.com/kotlin/create-thread-pool
    // "A cached thread pool will utilize resources according to the requirements of submitted
    // tasks. It will try to reuse existing threads for submitted tasks but will create up to
    // Integer.MAX_VALUE threads if needed. These threads will live for 60 seconds before
    // terminating. As such, it presents a very sharp tool that doesn’t include any backpressure
    // mechanism - and a sudden peak in load can bring the system down with an OutOfMemoryError.
    // We can achieve a similar effect but with better control by creating a ThreadPoolExecutor
    // manually."
    private val corePoolSize      = getCPUCoreCount()  // Get the CPU core count
    private val maximumPoolSize   = corePoolSize * 4   // Allow up to 4 threads per core
    private val keepAliveTimeSecs = 100L               // Which may execute for up to 100 seconds
    private val workQueue         = SynchronousQueue<Runnable>()
    val executorPool: ExecutorService = ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTimeSecs, TimeUnit.SECONDS, workQueue)

    @JvmStatic
    fun queue(target: Runnable) {
        executorPool.execute(target)
    }

    fun queue(target: () -> Unit) {
        executorPool.execute(target)
    }

    // Thread executor used by the audio recorder only
    @JvmStatic
    fun newDynamicSingleThreadedExecutor(): ExecutorService {
        val executor = ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, LinkedBlockingQueue())
        executor.allowCoreThreadTimeOut(true)
        return executor
    }

    private fun getCPUCoreCount(): Int {
        val pattern = Pattern.compile("cpu[0-9]+")
        return Math.max(
            File("/sys/devices/system/cpu/")
                .walk()
                .maxDepth(1)
                .count { pattern.matcher(it.name).matches() },
            Runtime.getRuntime().availableProcessors()
        )
    }
}