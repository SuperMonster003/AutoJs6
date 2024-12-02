package org.autojs.autojs.rhino

import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_OBJECT_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_SETTER_KEY
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

/**
 * Created by Stardust on Dec 6, 2017.
 * Modified by SuperMonster003 as of Apr 12, 2023.
 */
class ProxyJavaObject : NativeJavaObject {

    private var mGetter: BaseFunction? = null
    private var mSetter: BaseFunction? = null

    constructor()
    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, staticType: Class<*>, isAdapter: Boolean = false) : super(scope, javaObject, staticType, isAdapter)

    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, staticType: Class<*>, proxyObject: Any, isAdapter: Boolean = false) : super(scope, javaObject, staticType, isAdapter) {
        val proxy = proxyObject as NativeObject
        val getter = proxy[PROXY_GETTER_KEY, scope]
        if (getter is BaseFunction) {
            mGetter = getter
        }
        val setter = proxy[PROXY_SETTER_KEY, scope]
        if (setter is BaseFunction) {
            mSetter = setter
        }
    }

    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, isAdapter: Boolean = false) : this(scope, javaObject, javaObject.javaClass, isAdapter)

    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, proxyObject: Any, isAdapter: Boolean = false) : this(scope, javaObject, javaObject.javaClass, proxyObject, isAdapter)

    override fun put(key: String, start: Scriptable, value: Any) {
        when {
            key == PROXY_OBJECT_KEY -> {
                val proxy = value as NativeObject
                proxy[PROXY_GETTER_KEY, start].let { if (it is BaseFunction) mGetter = it }
                proxy[PROXY_SETTER_KEY, start].let { if (it is BaseFunction) mSetter = it }
            }
            else -> when (val setter = mSetter) {
                null -> super.put(key, start, value)
                else -> callFunction(setter, start, start, arrayOf(key, value))
            }
        }
    }

    override fun get(name: String, start: Scriptable): Any {
        val value = super.get(name, start)
        return if (value != null && value != NOT_FOUND) {
            value
        } else {
            when (val getter = mGetter) {
                null -> value
                else -> callFunction(getter, start, start, arrayOf(name))
            } ?: NOT_FOUND
        }
    }

    override fun getDefaultValue(typeHint: Class<*>?) = toString()

    fun getWithoutProxy(name: String?, start: Scriptable?): Any = super.get(name, start)

}