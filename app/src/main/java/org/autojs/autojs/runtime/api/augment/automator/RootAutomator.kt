package org.autojs.autojs.runtime.api.augment.automator

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Constructable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RootUtils
import org.mozilla.javascript.Scriptable

@Suppress("unused")
class RootAutomator(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Constructable {

    init {
        originateKeyName()
    }

    override fun construct(vararg args: Any?): Scriptable = ensureArgumentsAtMost(args, 1) {
        if (!RootUtils.isRootAvailable()) {
            throw RuntimeException("$key must be instantiated with root access")
        }
        when (it.size) {
            0 -> RootAutomatorNativeObject(scriptRuntime)
            1 -> RootAutomatorNativeObject(scriptRuntime, it[0])
            else -> throw WrappedIllegalArgumentException("Invalid arguments length ${it.size} for $key constructor")
        }
    }

}
