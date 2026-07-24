package org.autojs.autojs.runtime.api.augment.global

import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.mozilla.javascript.Scriptable.NOT_FOUND
import org.mozilla.javascript.Undefined

object IsNullish : Augmentable(), Invokable {

    override fun invoke(vararg args: Any?): Boolean {
        val value = args.getOrNull(0)
        return Undefined.isUndefined(value) || value == null || value == NOT_FOUND
    }

}
