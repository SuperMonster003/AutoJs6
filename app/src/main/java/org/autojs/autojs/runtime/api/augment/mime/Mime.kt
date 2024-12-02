package org.autojs.autojs.runtime.api.augment.mime

import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils.coerceString

class Mime(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override fun invoke(vararg args: Any?): JsMime = ensureArgumentsOnlyOne(args) { mimeStr ->
        // require(mimeStr is String) { "Argument mimeStr ${mimeStr.jsBrief()} for function mime must be a string" }
        JsMime(coerceString(mimeStr))
    }

}
