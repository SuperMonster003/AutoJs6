package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.util.RhinoUtils.callFunction

internal fun ScriptPromiseAdapter.toJsPromise(scriptRuntime: ScriptRuntime): Any {
    return requireNotNull(
        callFunction(
            scriptRuntime,
            scriptRuntime.js_ResultAdapter,
            "promise",
            arrayOf(this),
        ),
    )
}
