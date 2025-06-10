package org.autojs.autojs.runtime.api.augment.mime

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils.coerceString

class Mime(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override fun invoke(vararg args: Any?): JsMime = ensureArgumentsOnlyOne(args) { mimeStr ->
        JsMime(coerceString(mimeStr))
    }

}
