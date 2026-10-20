package org.autojs.autojs.core.pref

import android.content.SharedPreferences
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import org.autojs.autojs.util.StringUtils.key

/**
 * Rx wrappers for Pref.
 * zh-CN: Pref 的 Rx 包装.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 5, 2026.
 */
object PrefRx {

    /**
     * Observe a declared Long key (docs/settings/ROADMAP.md P2.2): its current value first, then each change, read
     * like `getLong(name, default)`.
     * zh-CN: 监听一个 Long 类型的键声明 (ROADMAP P2.2): 先发送当前值, 之后每次变化时发送, 读法同 `getLong(name, default)`.
     */
    fun observeLong(key: SettingKey<Long>): Flowable<Long> = Flowable.create({ emitter ->
        val subscription = key.settingsStore.listen { changedKey ->
            if (changedKey == key.name && !emitter.isCancelled) {
                emitter.onNext(key.get())
            }
        }
        emitter.onNext(key.get())
        emitter.setCancellable { subscription.close() }
    }, BackpressureStrategy.LATEST).distinctUntilChanged()

    /**
     * Observe a Long preference value.
     * zh-CN: 监听一个 Long 类型的偏好值.
     */
    @Deprecated("Use observeLong(SettingKey) with a declared key (docs/settings/ROADMAP.md P2.2)")
    fun observeLong(keyRes: Int, defaultValue: Long): Flowable<Long> {
        val sp = Pref.get()
        val k = key(keyRes)

        return Flowable.create({ emitter ->
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == k && !emitter.isCancelled) {
                    emitter.onNext(sp.getLong(k, defaultValue))
                }
            }

            // Emit current value immediately.
            // zh-CN: 立即发送当前值.
            emitter.onNext(sp.getLong(k, defaultValue))

            sp.registerOnSharedPreferenceChangeListener(listener)

            // Unregister on dispose.
            // zh-CN: 取消订阅时反注册监听器.
            emitter.setCancellable {
                sp.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }, BackpressureStrategy.LATEST).distinctUntilChanged()
    }
}
