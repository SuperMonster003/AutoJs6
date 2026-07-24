package org.autojs.autojs.runtime.api.augment

import org.mozilla.javascript.Scriptable

interface SimpleGetterProxy {

    fun get(scope: Scriptable, key: String): Any?

}
