package org.autojs.autojs.rhino

import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeFunction
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.UniqueTag

/**
 * Created by Stardust on 2017/12/6.
 * Modified by SuperMonster003 as of Apr 12, 2023.
 */
class ProxyJavaObject : NativeJavaObject {

    private var mGetter: NativeFunction? = null
    private var mSetter: NativeFunction? = null

    constructor()
    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, staticType: Class<*>, isAdapter: Boolean = false) : super(scope, javaObject, staticType, isAdapter)

    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, staticType: Class<*>, proxyObject: Any, isAdapter: Boolean = false) : super(scope, javaObject, staticType, isAdapter) {
        val proxy = proxyObject as NativeObject
        val getter = proxy["get", scope]
        if (getter is NativeFunction) {
            mGetter = getter
        }
        val setter = proxy["set", scope]
        if (setter is NativeFunction) {
            mSetter = setter
        }
    }

    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, isAdapter: Boolean = false) : this(scope, javaObject, javaObject.javaClass, isAdapter)

    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, proxyObject: Any, isAdapter: Boolean = false) : this(scope, javaObject, javaObject.javaClass, proxyObject, isAdapter)

    override fun put(name: String, start: Scriptable, value: Any) {
        if (name == "__proxy__") {
            val proxy = value as NativeObject
            val getter = proxy["get", start]
            if (getter is NativeFunction) {
                mGetter = getter
            }
            val setter = proxy["set", start]
            if (setter is NativeFunction) {
                mSetter = setter
            }
        } else if (mSetter != null) {
            mSetter!!.call(Context.getCurrentContext(), start, start, arrayOf(name, value))
        } else {
            super.put(name, start, value)
        }
    }

    fun getWithoutProxy(name: String?, start: Scriptable?): Any {
        return super.get(name, start)
    }

    override fun get(name: String, start: Scriptable): Any {
        var value = super.get(name, start)
        if (value != null && value !== UniqueTag.NOT_FOUND) {
            return value
        }
        if (mGetter != null) {
            value = mGetter!!.call(Context.getCurrentContext(), start, start, arrayOf<Any>(name))
        }
        return value
    }

    override fun getDefaultValue(typeHint: Class<*>?) = toString()

}