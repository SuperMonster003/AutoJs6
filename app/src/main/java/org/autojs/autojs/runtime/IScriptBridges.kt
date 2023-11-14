package org.autojs.autojs.runtime

import org.mozilla.javascript.NativeArray

interface IScriptBridges {

    fun call(func: Any?, target: Any?, args: Array<*>): Any?

    fun toArray(o: Iterable<*>?): NativeArray?

    fun toString(obj: Any?): Any

    fun asArray(uiObjectCollection: Any?): NativeArray?

    fun toPrimitive(obj: Any?): Any

}