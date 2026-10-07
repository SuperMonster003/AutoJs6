package org.autojs.autojs.theme.intelligent

import android.os.Handler
import android.os.Looper
import org.autojs.autojs.util.ViewUtils
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Whether the app currently shows night mode, with change notifications (roadmap F9).
 * zh-CN: 应用当前是否处于夜间模式, 并提供变化通知 (Roadmap F9).
 *
 * [ViewUtils.isNightModeEnabled] is kept in sync for every mode (on / off / follow system), so it is the
 * single source of truth. [notifyChanged] is called from [ViewUtils.setDefaultNightMode], which every
 * switch goes through; listeners run on the next main loop turn, after the preference has been written.
 * zh-CN: [ViewUtils.isNightModeEnabled] 在各种模式 (开 / 关 / 跟随系统) 下都保持同步, 因此作为唯一依据.
 * [notifyChanged] 由各切换途径都会经过的 [ViewUtils.setDefaultNightMode] 调用; 监听器在下一轮主线程循环中执行, 此时偏好已写入.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object NightModeState {

    fun interface Listener {
        fun onNightModeChanged(isNight: Boolean)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    val isNight: Boolean
        get() = ViewUtils.isNightModeEnabled

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    @JvmStatic
    fun notifyChanged() {
        handler.post {
            val night = isNight
            listeners.forEach { it.onNightModeChanged(night) }
        }
    }

}
