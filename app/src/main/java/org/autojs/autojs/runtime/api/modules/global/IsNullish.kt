package org.autojs.autojs.runtime.api.modules.global

import org.autojs.autojs.rhino.ArgumentGuards
import org.mozilla.javascript.Scriptable.NOT_FOUND
import org.mozilla.javascript.Undefined

object IsNullish : ArgumentGuards() {

    fun invoke(vararg args: Any?): Boolean {
        val value = args.getOrNull(0)
        return Undefined.isUndefined(value) || value == null || value == NOT_FOUND
    }

}
