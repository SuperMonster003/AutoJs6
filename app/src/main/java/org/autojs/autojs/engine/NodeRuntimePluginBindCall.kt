package org.autojs.autojs.engine

/**
 * One Context.bindService call of [NodeJsRuntimePluginHost], shared with its JVM regression test.
 *
 * The context registers the connection (LoadedApk.mServices) before it asks the system, so a bind that returns false
 * or throws stays registered until unbindService. Every attempt therefore counts as registered, and a failed one is
 * released at once instead of being kept: on ColorOS, which refuses binds of sleeping plugins, each retry used to leave
 * one more connection behind (docs/memory/ROADMAP.md H28 / appendix C Q46).
 *
 * zh-CN: [NodeJsRuntimePluginHost] 的一次 Context.bindService 调用, 与其 JVM 回归测试共用.
 * Context 在请求系统之前就登记了连接 (LoadedApk.mServices), 因此返回 false 或抛出异常的绑定在 unbindService 之前一直处于登记状态.
 * 所以每次尝试都视为已登记, 失败的尝试立即释放, 不保留: ColorOS 拒绝绑定休眠的插件, 此前每次重试都多留下一个连接
 * (docs/memory/ROADMAP.md H28 / 附录 C Q46).
 */
internal object NodeRuntimePluginBindCall {

    sealed interface Outcome {

        /** The system accepted the bind: the caller keeps the registration until it unbinds. */
        data object Bound : Outcome

        /** bindService returned false: the registration was already released. */
        data object Refused : Outcome

        /** bindService threw [error]: the registration was already released. */
        class Denied(val error: SecurityException) : Outcome
    }

    /**
     * Runs [bind] and calls [release] exactly once when it does not return true, also for an unexpected
     * RuntimeException, which is thrown again after the release.
     *
     * zh-CN: 执行 [bind], 未返回 true 时恰好调用一次 [release]; 意外的 RuntimeException 也是如此, 释放后重新抛出.
     */
    fun attempt(bind: () -> Boolean, release: () -> Unit): Outcome {
        val bound = try {
            bind()
        } catch (error: SecurityException) {
            release()
            return Outcome.Denied(error)
        } catch (error: RuntimeException) {
            release()
            throw error
        }
        if (bound) return Outcome.Bound
        release()
        return Outcome.Refused
    }
}
