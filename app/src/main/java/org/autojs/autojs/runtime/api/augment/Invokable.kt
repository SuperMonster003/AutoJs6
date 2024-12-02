package org.autojs.autojs.runtime.api.augment

import org.autojs.autojs.util.RhinoUtils.DEFAULT_CALLER

interface Invokable {

    fun invoke(vararg args: Any?): Any? = DEFAULT_CALLER

}
