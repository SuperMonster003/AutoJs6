package org.autojs.autojs.runtime.api.augment

import org.autojs.autojs.util.RhinoUtils.DEFAULT_CONSTRUCTOR

interface Constructable {

    fun construct(vararg args: Any?): Any? = DEFAULT_CONSTRUCTOR

}
