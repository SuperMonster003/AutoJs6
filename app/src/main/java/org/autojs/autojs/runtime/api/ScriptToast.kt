package org.autojs.autojs.runtime.api

import android.widget.Toast
import org.autojs.autojs.runtime.ScriptRuntime
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by SuperMonster003 on Aug 3, 2023.
 * Modified by SuperMonster003 as of Oct 24, 2023.
 * Transformed by SuperMonster003 on Oct 24, 2023.
 */
object ScriptToast {

    @JvmField
    val pool = ConcurrentHashMap<Toast, ScriptRuntime>()

    @JvmStatic
    fun add(t: Any?, scriptRuntime: ScriptRuntime) {
        if (t is Toast) {
            pool[t] = scriptRuntime
        }
    }

    @JvmStatic
    fun dismissAll(scriptRuntime: ScriptRuntime) {
        for (entry in pool.entries) {
            if (entry.value === scriptRuntime) {
                entry.key.cancel()
                pool.remove(entry.key, scriptRuntime)
            }
        }
    }

    @JvmStatic
    fun clear(scriptRuntime: ScriptRuntime) {
        for (entry in pool.entries) {
            if (entry.value === scriptRuntime) {
                pool.remove(entry.key, scriptRuntime)
            }
        }
    }

}
