package org.autojs.autojs.storage.history

import java.lang.ref.WeakReference
import java.util.UUID

/**
 * Hands editor callbacks to the revision list page, which runs in its own activity and therefore
 * cannot receive lambdas through an Intent. The editor keeps a strong reference to its
 * [Callbacks]; the registry only holds weak ones, so a closed editor never leaks through here.
 *
 * zh-CN: 把编辑器回调交给版本列表页. 该页是独立 Activity, 无法经 Intent 传递 lambda.
 * 编辑器自己强引用 [Callbacks], 注册表只持弱引用, 编辑器关闭后不会经此泄漏.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object VersionHistoryEditorBridge {

    class Callbacks(
        /** Receives the decoded revision text; the editor replaces its content with it. */
        val onRestoreToEditor: (restoredText: String) -> Unit,
        /** Runs after the text was applied so the editor can refresh its UI state. */
        val onRestoredUi: () -> Unit,
    )

    private val registry = HashMap<String, WeakReference<Callbacks>>()

    @Synchronized
    fun register(callbacks: Callbacks): String {
        pruneLocked()
        val token = UUID.randomUUID().toString()
        registry[token] = WeakReference(callbacks)
        return token
    }

    @Synchronized
    fun get(token: String?): Callbacks? {
        token ?: return null
        return registry[token]?.get()
    }

    @Synchronized
    fun unregister(token: String?) {
        token ?: return
        registry.remove(token)
    }

    private fun pruneLocked() {
        val stale = registry.filterValues { it.get() == null }.keys
        stale.forEach { registry.remove(it) }
    }
}
