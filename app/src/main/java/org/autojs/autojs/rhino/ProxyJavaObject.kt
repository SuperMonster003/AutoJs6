package org.autojs.autojs.rhino

import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_OBJECT_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_SETTER_KEY
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope

/**
 * Created by Stardust on Dec 6, 2017.
 * Modified by SuperMonster003 as of Apr 12, 2023.
 */
class ProxyJavaObject : NativeJavaObject {

    private var mGetter: Callable? = null
    private var mSetter: Callable? = null

    constructor()
    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, staticType: Class<*>, isAdapter: Boolean = false) : super(scope, javaObject, staticType, isAdapter)

    @JvmOverloads
    constructor(scope: VarScope, javaObject: Any, staticType: Class<*>, isAdapter: Boolean = false) : super(scope, javaObject, staticType, isAdapter)

    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, staticType: Class<*>, proxyObject: Any, isAdapter: Boolean = false) : super(scope, javaObject, staticType, isAdapter) {
        installProxy(proxyObject)
    }

    @JvmOverloads
    constructor(scope: VarScope, javaObject: Any, staticType: Class<*>, proxyObject: Any, isAdapter: Boolean = false) : super(scope, javaObject, staticType, isAdapter) {
        installProxy(proxyObject)
    }

    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, isAdapter: Boolean = false) : this(scope, javaObject, javaObject.javaClass, isAdapter)

    @JvmOverloads
    constructor(scope: VarScope, javaObject: Any, isAdapter: Boolean = false) : this(scope, javaObject, javaObject.javaClass, isAdapter)

    @JvmOverloads
    constructor(scope: Scriptable, javaObject: Any, proxyObject: Any, isAdapter: Boolean = false) : this(scope, javaObject, javaObject.javaClass, proxyObject, isAdapter)

    @JvmOverloads
    constructor(scope: VarScope, javaObject: Any, proxyObject: Any, isAdapter: Boolean = false) : this(scope, javaObject, javaObject.javaClass, proxyObject, isAdapter)

    override fun put(key: String, start: Scriptable, value: Any?) {
        when {
            key == PROXY_OBJECT_KEY -> {
                installProxy(value)
            }
            else -> when (val setter = mSetter) {
                null -> super.put(key, start, value)
                else -> callFunction(setter, start, start, arrayOf(key, value))
            }
        }
    }

    override fun get(name: String, start: Scriptable): Any? {
        val value = super.get(name, start)
        return if (value !== NOT_FOUND) {
            value
        } else {
            when (val getter = mGetter) {
                null -> value
                else -> callFunction(getter, start, start, arrayOf(name))
            }
        }
    }

    fun getWithoutProxy(name: String?, start: Scriptable?): Any? = super.get(name, start)

    fun putWithoutProxy(name: String, start: Scriptable, value: Any?) = super.put(name, start, value)

    private fun installProxy(proxyObject: Any?) {
        val proxy = proxyObject as NativeObject
        mGetter = proxy[PROXY_GETTER_KEY, proxy] as? Callable
        mSetter = proxy[PROXY_SETTER_KEY, proxy] as? Callable
    }

}
