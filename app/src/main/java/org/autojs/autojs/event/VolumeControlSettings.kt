package org.autojs.autojs.event

import org.autojs.autojs.core.pref.keys.RecordKeys
import org.autojs.autojs.core.pref.keys.RunningKeys
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The two "Volume control" settings (while running scripts and while recording) that decide whether [GlobalKeyObserver]
 * exists and whether its shell fallback runs (docs/shizuku-oom/ROADMAP.md P1.5 / P3.1).
 *
 * zh-CN: 两项 "音量键控制" 设置 (脚本运行时与录制时), 决定 [GlobalKeyObserver] 是否存在以及其 shell 兜底监听是否运行
 * (docs/shizuku-oom/ROADMAP.md P1.5 / P3.1).
 */
object VolumeControlSettings {

    private val keys = listOf(RunningKeys.USE_VOLUME_CONTROL_RUNNING, RecordKeys.USE_VOLUME_CONTROL_RECORD)

    @JvmStatic
    fun isEnabled(): Boolean = keys.any { it.get() }

    /**
     * Calls [onChanged] after either setting changes (or its store is cleared), on the thread of the store's listener;
     * closing the result unregisters it.
     * zh-CN: 任一设置变化 (或其存储被清空) 后在存储的回调线程上调用 [onChanged]; 关闭返回值即注销.
     */
    @JvmStatic
    fun listen(onChanged: Runnable): AutoCloseable {
        val names = keys.map { it.name }.toSet()
        val subscriptions = keys.map { it.settingsStore }.distinct().map { store ->
            store.listen { key -> if (key == null || key in names) onChanged.run() }
        }
        return AutoCloseable { subscriptions.forEach { it.close() } }
    }

    /**
     * Calls [onEnabled] once when either setting is on: at once (on the calling thread) when one already is, otherwise
     * on the thread of the store's listener after one is turned on. The subscription is closed after the call; closing
     * the result earlier cancels it.
     * zh-CN: 任一设置开启时调用 [onEnabled] 一次: 已开启则在调用线程上立即调用, 否则在某项被打开后于存储的回调线程上调用.
     * 调用后订阅即关闭; 提前关闭返回值即取消.
     */
    @JvmStatic
    fun whenEnabled(onEnabled: Runnable): AutoCloseable = OneShot(onEnabled).also(OneShot::start)

    private class OneShot(private val onEnabled: Runnable) : AutoCloseable {

        private val done = AtomicBoolean()

        @Volatile
        private var subscription: AutoCloseable? = null

        fun start() {
            // Subscribe before reading, so a change between the two is not missed.
            // zh-CN: 先订阅后读取, 两者之间的变化不会漏掉.
            subscription = listen(::check)
            check()
            // The listener may have fired before the subscription was stored.
            // zh-CN: 回调可能在订阅保存之前已触发.
            if (done.get()) subscription?.close()
        }

        private fun check() {
            if (isEnabled() && done.compareAndSet(false, true)) {
                subscription?.close()
                onEnabled.run()
            }
        }

        override fun close() {
            done.set(true)
            subscription?.close()
        }
    }
}
