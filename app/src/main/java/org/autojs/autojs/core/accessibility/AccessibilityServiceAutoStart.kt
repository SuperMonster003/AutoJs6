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
 * The policy itself lives in [org.autojs.autojs.core.accessibility.control.A11yServiceController.restoreIfMalfunctioning]:
 * only a malfunctioning service is restored, i.e. one that is enabled in system settings but has
 * no bound instance in this process (typically after the app was updated or its process was killed).
 * A service that is not enabled is left alone: the user may have stopped it deliberately, and it is
 * started on demand by scripts, the layout inspector and the other explicit entry points.
 *
 * Every [org.autojs.autojs.ui.BaseActivity] requests this check while it is being created.
 * A process-wide single-flight guard prevents adjacent Activity launches from running the
 * same shell/settings sequence concurrently.
 *
 * zh-CN: 在不阻塞 Activity 创建的前提下自动恢复无障碍服务.
 *
 * 策略本身位于 [org.autojs.autojs.core.accessibility.control.A11yServiceController.restoreIfMalfunctioning]:
 * 仅恢复 "故障" 状态的服务, 即系统设置中已启用但进程内没有绑定实例 (通常出现在应用更新或进程被杀之后).
 * 未启用的服务不做处理: 用户可能是有意关闭的, 脚本, 布局分析器等显式入口会在需要时按需启动.
 *
 * 每个 [org.autojs.autojs.ui.BaseActivity] 创建时都会请求一次检查. 进程级单飞门控可避免
 * 相邻 Activity 启动时并发执行相同的 shell/settings 操作.
 */
internal object AccessibilityServiceAutoStart {

    private val coordinator = AccessibilityServiceAutoStartCoordinator(Dispatchers.IO.asExecutor())

    @Suppress("UNUSED_PARAMETER")
    fun request(context: Context) {
        coordinator.submit {
            try {
                AccessibilityTool.controller.restoreIfMalfunctioning()
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
