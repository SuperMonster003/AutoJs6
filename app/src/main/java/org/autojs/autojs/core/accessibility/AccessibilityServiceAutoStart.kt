package org.autojs.autojs.core.accessibility

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Restores the accessibility service without making Activity creation wait for root or
 * secure-settings I/O.
 *
 * Every [org.autojs.autojs.ui.BaseActivity] requests this check while it is being created.
 * A process-wide single-flight guard prevents adjacent Activity launches from running the
 * same shell/settings sequence concurrently.
 *
 * zh-CN: 在不阻塞 Activity 创建的前提下自动恢复无障碍服务.
 *
 * 每个 [org.autojs.autojs.ui.BaseActivity] 创建时都会请求一次检查. 进程级单飞门控可避免
 * 相邻 Activity 启动时并发执行相同的 shell/settings 操作.
 */
internal object AccessibilityServiceAutoStart {

    private val coordinator = AccessibilityServiceAutoStartCoordinator(Dispatchers.IO.asExecutor())

    fun request(context: Context) {
        val applicationContext = context.applicationContext
        coordinator.submit {
            try {
                AccessibilityTool(applicationContext).apply {
                    if (!isRunning()) startService(false)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Unable to restore the accessibility service automatically.", error)
            }
        }
    }

    private const val TAG = "AccessibilityAutoStart"
}

/** Serializes adjacent automatic-start requests without dedicating a permanent thread. */
internal class AccessibilityServiceAutoStartCoordinator(
    private val executor: Executor,
) {

    private val taskInFlight = AtomicBoolean(false)

    fun submit(task: () -> Unit): Boolean {
        if (!taskInFlight.compareAndSet(false, true)) return false

        try {
            executor.execute {
                try {
                    task()
                } finally {
                    taskInFlight.set(false)
                }
            }
        } catch (error: RuntimeException) {
            taskInFlight.set(false)
            throw error
        }
        return true
    }
}
