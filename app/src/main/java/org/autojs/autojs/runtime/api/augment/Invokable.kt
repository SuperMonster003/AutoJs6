package org.autojs.autojs.runtime.api.augment

import org.mozilla.javascript.Undefined

interface Invokable {

    fun invoke(vararg args: Any?): Any? = Undefined.instance

}
