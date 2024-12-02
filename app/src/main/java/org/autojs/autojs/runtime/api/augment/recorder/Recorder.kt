package org.autojs.autojs.runtime.api.augment.recorder

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable

class Recorder(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override fun invoke(vararg args: Any?): Double = ensureArgumentsAtMost(args, 2) {
        val (key, timestamp) = it
        scriptRuntime.recorder.shortcut(key, timestamp)
    }

}