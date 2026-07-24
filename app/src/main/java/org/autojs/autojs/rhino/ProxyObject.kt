package org.autojs.autojs.rhino

import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.getDeclarationScope
import org.autojs.autojs.util.RhinoUtils.initNativeObjectPrototype
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * Created by Stardust on May 17, 2017.
 * Modified by SuperMonster003 as of Apr 12, 2023.
 */
open class ProxyObject() : NativeObject() {

    private var mGetter: Callable? = null
    private var mSetter: Callable? = null

    init {
        initNativeObjectPrototype(this)
    }

    constructor(scope: Scriptable, proxyObject: NativeObject) : this() {
        initNativeObjectPrototype(getDeclarationScope(scope), this)
        val getter = proxyObject[PROXY_GETTER_KEY, scope]
        if (getter is Callable) {
            mGetter = getter
        }
        val setter = proxyObject[PROXY_SETTER_KEY, scope]
        if (setter is Callable) {
            mSetter = setter
        }
    }

    constructor(scope: VarScope, proxyObject: NativeObject) : this(ScriptableObject.getTopLevelScope(scope).globalThis, proxyObject)

    constructor(
        scope: Scriptable,
        getter: ((Array<out Any?>) -> Any?)? = null,
        setter: ((Array<out Any?>) -> Undefined)? = null,
    ) : this(scope, createProxyObject(getDeclarationScope(scope), getter, setter))

    constructor(
        scope: VarScope,
        getter: ((Array<out Any?>) -> Any?)? = null,
        setter: ((Array<out Any?>) -> Undefined)? = null,
    ) : this(scope, createProxyObject(scope, getter, setter))

    override fun put(key: String, start: Scriptable, value: Any?) {
        when {
            key == PROXY_OBJECT_KEY -> {
                val proxy = value as NativeObject
                proxy[PROXY_GETTER_KEY, start].let { if (it is Callable) mGetter = it }
                proxy[PROXY_SETTER_KEY, start].let { if (it is Callable) mSetter = it }
            }
            else -> when (val setter = mSetter) {
                null -> super.put(key, start, value)
                else -> callFunction(setter, start, start, arrayOf(key, value))
            }
        }
    }

    override fun get(key: String, start: Scriptable): Any? {
        return when (val value = super.get(key, start)) {
            NOT_FOUND -> when (val getter = mGetter) {
                null -> value
                else -> callFunction(getter, start, start, arrayOf(key))
            }
            else -> value
        }
    }

    fun getWithoutProxy(name: String?, start: Scriptable?): Any? = super.get(name, start)

    @Suppress("MayBeConstant")
    companion object {

        @JvmField
        val PROXY_OBJECT_KEY = "__proxy__"

        @JvmField
        val AUGMENTED_OBJECT_KEY = "__augmented__"

        @JvmField
        val AUGMENTED_CUSTOM_TO_STRING_KEY = "__augmented_custom_to_string__"

        @JvmField
        val PROXY_SETTER_KEY = "set"

        @JvmField
        val PROXY_GETTER_KEY = "get"

        private fun createProxyObject(
            scope: VarScope,
            getter: ((Array<out Any?>) -> Any?)?,
            setter: ((Array<out Any?>) -> Undefined)?,
        ) = newNativeObject(scope).also { o ->
            o.defineProp(PROXY_GETTER_KEY, newBaseFunction(scope, PROXY_GETTER_KEY, getter, NOT_CONSTRUCTABLE))
            o.defineProp(PROXY_SETTER_KEY, newBaseFunction(scope, PROXY_SETTER_KEY, setter, NOT_CONSTRUCTABLE))
        }

    }

}
