package org.autojs.autojs.runtime

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.NativeArray

interface IScriptBridges {

    fun call(func: BaseFunction, target: Any?, args: Array<*>): Any?

    fun toArray(o: Iterable<*>?): NativeArray

    fun asArray(list: Iterable<*>): NativeArray

    fun toString(obj: Any?): String

    fun toPrimitive(obj: Any?): Any
}