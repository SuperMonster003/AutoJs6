package org.autojs.autojs.runtime

import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray

interface IScriptBridges {

    fun call(func: Callable, target: Any?, args: Array<*>): Any?

    fun toArray(o: Iterable<*>?): NativeArray

    fun asArray(listLike: Any): NativeArray

    fun toString(obj: Any?): String

    fun toPrimitive(obj: Any?): Any?
}
