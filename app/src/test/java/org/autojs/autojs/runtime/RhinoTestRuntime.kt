package org.autojs.autojs.runtime

import org.autojs.autojs.rhino.TopLevelScope
import sun.misc.Unsafe

internal object RhinoTestRuntime {

    private val unsafe: Unsafe by lazy {
        Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
    }

    fun create(scope: TopLevelScope): ScriptRuntime {
        return (unsafe.allocateInstance(ScriptRuntime::class.java) as ScriptRuntime).also { runtime ->
            ScriptRuntime::class.java.getDeclaredField("mTopLevelScope").run {
                isAccessible = true
                set(runtime, scope)
            }
            ScriptRuntime::class.java.getDeclaredField("bridges").run {
                isAccessible = true
                set(runtime, ScriptBridges())
            }
        }
    }

}
