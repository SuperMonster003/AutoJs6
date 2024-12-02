package org.autojs.autojs.runtime.api

import android.widget.Toast
import org.autojs.autojs.runtime.ScriptRuntime
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by SuperMonster003 on Aug 3, 2023.
 * Modified by SuperMonster003 as of Oct 24, 2023.
 * Transformed by SuperMonster003 on Oct 24, 2023.
 */
object ScriptToast {

    @JvmField
    val pool = ConcurrentHashMap<Toast, WeakReference<ScriptRuntime>>()

    @JvmStatic
    fun add(t: Toast, scriptRuntime: ScriptRuntime) {
        pool[t] = WeakReference(scriptRuntime)
    }

    @JvmStatic
    fun dismissAll(scriptRuntime: ScriptRuntime) {
        pool.entries.forEach { entry ->
            if (entry.value.get() == scriptRuntime) {
                pool.remove(entry.key)
                entry.key.cancel()
            }
        }
    }

    @JvmStatic
    fun clear(scriptRuntime: ScriptRuntime) {
        pool.entries
            .filter { it.value.get() == scriptRuntime }
            .forEach { pool.remove(it.key) }
    }

}
