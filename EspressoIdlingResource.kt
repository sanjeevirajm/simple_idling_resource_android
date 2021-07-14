import org.apache.commons.lang3.ThreadUtils
import java.lang.Thread.State.RUNNABLE

class EspressoIdlingResource: IdlingResource
{
    private val timeOutListeners = mutableListOf<TimeOutListener>()
    private var threadList = arrayListOf<ThreadInfo>()

    private var isBackgroundThreadAlive = false
    private var isBackgroundThreadAliveOldValue = true

    private val threadsCanBeIgnored = mutableSetOf<Thread>()

    // written from main thread, read from any thread.
    @Volatile
    private var resourceCallback: IdlingResource.ResourceCallback? = null

    override fun getName(): String
    {
        return IDLING_RESOURCE
    }

    override fun isIdleNow(): Boolean
    {
        log(IDLING_RESOURCE, "isIdleNow : ${!isBackgroundThreadAlive}")
        threadList.forEach { threadInfo ->
            val remainingTime = System.currentTimeMillis() - threadInfo.time
            log(IDLING_RESOURCE, "isIdleNow: " + threadInfo(threadInfo.thread, remainingTime/1000))
        }
        log(IDLING_RESOURCE, "isIdleNow threadsCanBeIgnored: $threadsCanBeIgnored")

        return !isBackgroundThreadAlive
    }

    private var idlingMonitor: Thread? = null

    override fun registerIdleTransitionCallback(resourceCallback: IdlingResource.ResourceCallback)
    {
        KotlinUtils.log(IDLING_RESOURCE, "registerIdleTransitionCallback")
        this.resourceCallback = resourceCallback
        log = "registerIdleTransitionCallback currentThread: ${Thread.currentThread()}, idlingMonitor: $idlingMonitor"
        idlingMonitor?.interrupt()
        isBackgroundThreadAlive = false
        isBackgroundThreadAliveOldValue = true

        idlingMonitor = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(20)
                } catch (e: InterruptedException) {
                    break
                }
                //                log = "all threads: "+ThreadUtils.getAllThreads().filter {
                //                    it.threadGroup?.name != "main" &&
                //                    it.isAlive && !it.isDaemon
                //                }.map {
                //                    it.toString() + " ${it.state}"
                //                }

                val aliveThreads =
                        ThreadUtils.getAllThreads().filter {
                            it.isAlive &&
                                    !it.isDaemon &&
                                    it.state == RUNNABLE &&
                                    !threadsCanBeIgnored.contains(it) &&
                                    !threadNamesCanBeIgnored.contains(it.name) &&
                                    !it.name.startsWithAny(
                                        "Binder"
                                    )
                        }

                //                log = "alive threads: "+ aliveThreads.map {
                //                    it.toString() + " ${it.state}"
                //                }

                isBackgroundThreadAlive = aliveThreads.isNotEmpty()

                //                log = "oldValue: $oldValue, isBackgroundThreadAlive: $isBackgroundThreadAlive"

                if (isBackgroundThreadAliveOldValue && isBackgroundThreadAlive) {
                    val itemsToBeRemoved = arrayListOf<ThreadInfo>()

                    threadList.forEach { threadInfo ->
                        if (!aliveThreads.contains(threadInfo.thread)) {
                            itemsToBeRemoved.add(threadInfo)
                        }
                    }

                    threadList.removeAll(itemsToBeRemoved)

                    aliveThreads.forEach { eachThread ->
                        val existingThreadInfo = threadList.firstOrNull {
                            eachThread == it.thread
                        }

                        if (existingThreadInfo != null) {
                            val remainingTime = System.currentTimeMillis() - existingThreadInfo.time
                            if (remainingTime > THREAD_ERROR_TIMEOUT_MILLISECONDS) {
                                threadList.remove(existingThreadInfo)
                                warn("Ignored Thread: "+threadInfo(existingThreadInfo.thread, remainingTime / 1000))
                                threadsCanBeIgnored.add(existingThreadInfo.thread)
//                                throwError(threadInfo(existingThreadInfo.thread, remainingTime / 1000))
                            } else if (remainingTime > THREAD_WARNING_TIMEOUT_MILLISECONDS && !existingThreadInfo.hasWarned) {
                                existingThreadInfo.hasWarned = true
                                warn(threadInfo(existingThreadInfo.thread, remainingTime / 1000))
                            }
                        } else {
                            threadList.add(ThreadInfo(eachThread, System.currentTimeMillis(), false))
                        }
                    }
                }

                if (!isBackgroundThreadAlive && isBackgroundThreadAlive != isBackgroundThreadAliveOldValue) {
                    log = "resourceCallback.onTransitionToIdle()"
                    resourceCallback.onTransitionToIdle()
                }

                isBackgroundThreadAliveOldValue = isBackgroundThreadAlive
            }
        }.also {
            it.name = "idlingMonitor"
            it.priority = Thread.MIN_PRIORITY
            it.start()
        }
    }

    private fun threadInfo(thread: Thread, timeTakenInSeconds: Long): String {
        val stackTracesString = thread.stackTrace.map { stackTraceElement ->
            stackTraceElement.stackTraceAsString
        }
        return "$thread, timeTakenInSeconds: $timeTakenInSeconds, stackTraces: $stackTracesString"
    }

    fun warn(info: String) {
        val message = "TimeOutWarning info: $info"
        timeOutListeners.forEach {
            it.timeoutWarning(message)
        }
    }

    fun throwError(info: String) {
        val message = "TimeOutError info: $info"
        timeOutListeners.forEach {
            it.timeoutError(message)
        }
    }

    companion object {
        @JvmStatic
        val idlingResource = EspressoIdlingResource()

        @JvmStatic
        fun addTimeOutListener(timeOutListener: TimeOutListener) {
            if (!idlingResource.timeOutListeners.contains(timeOutListener)) {
                idlingResource.timeOutListeners.add(timeOutListener)
            }
        }

        @JvmStatic
        fun removeTimeOutListener(timeOutListener: TimeOutListener) {
            idlingResource.timeOutListeners.remove(timeOutListener)
        }

        const val IDLING_RESOURCE = "IDLING_RESOURCE"
        const val THREAD_WARNING_TIMEOUT_MILLISECONDS = 15_000L
        const val THREAD_ERROR_TIMEOUT_MILLISECONDS = 20_000L

        const val API_WARNING_TIMEOUT_MILLISECONDS = 8_000L
        const val API_ERROR_TIMEOUT_MILLISECONDS = 12_000L

        private val threadNamesCanBeIgnored = mutableSetOf(
            "main",
            "queued-work-looper",
            "ConnectivityThread",
            "idlingMonitor",
            "InstrumentationConnectionThread",
            "Chrome_ProcessLauncherThread",
            "GoogleApiHandler",
            "AppUpdateService",
            "Instr: androidx.test.runner.AndroidJUnitRunner",
            "TcmReceiver",
            "UiAutomation",
            "magnifier pixel copy result handler",
            "Chrome_IOThread",
            "ThreadPoolForeg",
            "PlatformServiceBridgeHandlerThread",
            "MemoryInfra",
            "ConnectivityManager",
            "WifiConnectivityManager",
            "AndroidCellularSignalStrength",
            "CookieMonsterCl", // cookie clearing thread, ignore waiting for it
            "AudioThread",
            "Chrome_InProcGpuThread",
            "NativeCrypto",
            "CameraX-scheduler",
            "hwuiTask1",
            "VizCompositorThread",
            "hwuiTask2"
        )

        fun awaitUntilIdle() {
            while (!idlingResource.isIdleNow) {
                try {
                    Thread.sleep(10)
                } catch (e: InterruptedException) {
                    e.printStackTraceIfLogsEnabled()
                    log(IDLING_RESOURCE, "InterruptedException: ${Thread.currentThread()}")
                    //ignore
                }
            }
        }
    }
}

interface TimeOutListener {
    fun timeoutWarning(message: String)
    fun timeoutError(message: String)
}

data class ThreadInfo(val thread: Thread, val time: Long, var hasWarned: Boolean)
