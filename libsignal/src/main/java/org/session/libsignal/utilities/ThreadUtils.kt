package org.session.libsignal.utilities

import android.os.Process
import java.util.concurrent.*

object ThreadUtils {

    const val PRIORITY_IMPORTANT_BACKGROUND_THREAD = Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE

    val executorPool: ExecutorService = Executors.newCachedThreadPool()

    @JvmStatic
    fun queue(target: Runnable) {
        executorPool.execute(target)
    }

    fun queue(target: () -> Unit) {
        executorPool.execute(target)
    }

    @JvmStatic
    fun newDynamicSingleThreadedExecutor(): ExecutorService {
        val executor = ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, LinkedBlockingQueue())
        executor.allowCoreThreadTimeOut(true)
        return executor
    }

    // Helper method to just print the current stack trace so you can see where things are being
    // called from without throwing then catching an exception and printing its details. For example
    // if there are multiple ways to get to a given method and you don't know which code path was
    // taken you can call `ThreadUtils.logCurrentThreadStackTrace` from inside the method being
    // called to see how you got there.
    @JvmStatic
    fun logCurrentThreadStackTrace(tag: String = "ThreadUtils") {
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            Log.d(tag, element.className + "." + element.methodName + "(" + element.fileName + ":" + element.lineNumber + ")")
        }
    }
}