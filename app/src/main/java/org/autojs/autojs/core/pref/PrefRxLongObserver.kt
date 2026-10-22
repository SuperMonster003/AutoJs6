package org.autojs.autojs.core.pref

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable

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
}
