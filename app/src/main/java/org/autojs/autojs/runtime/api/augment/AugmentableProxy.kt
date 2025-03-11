package org.autojs.autojs.runtime.api.augment

import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.autojs.autojs.util.StringUtils.uppercaseFirstChar
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Scriptable.NOT_FOUND
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

open class AugmentableProxy(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    open fun set(augmented: ScriptableObject, key: String, value: Any?): Undefined {
        if (key == "constructor") return UNDEFINED

        val setterResult = listOf(
            when {
                Regex("^set[A-Z].*").containsMatchIn(key) -> key
                else -> "set${uppercaseFirstChar(key)}"
            },
            when {
                Regex("^is[A-Z].*").containsMatchIn(key) -> key
                else -> "is${uppercaseFirstChar(key)}"
            },
        ).any { setCore(augmented, it, value) }

        require(setterResult) {
            throw WrappedIllegalArgumentException("Unknown key \"$key\" for augmentable proxy setter")
        }

        return UNDEFINED
    }

    open fun setCore(augmented: ScriptableObject, key: String, value: Any?): Boolean {
        return when (val o = augmented.prop(key)) {
            is BaseFunction -> when (value) {
                is NativeArray -> {
                    callFunction(scriptRuntime, o, augmented, augmented, value.toTypedArray())
                }
                else -> {
                    callFunction(scriptRuntime, o, augmented, augmented, arrayOf(value))
                }
            }
            else -> null
        }.let { it != null }
    }

    open fun get(augmented: ScriptableObject, key: String): Any? {
        listOf(
            key,
            "get${uppercaseFirstChar(key)}",
            "is${uppercaseFirstChar(key)}",
        ).forEach {
            getCore(augmented, it).let { result ->
                if (result != NOT_FOUND) return result
            }
        }

        return NOT_FOUND
    }

    open fun getCore(augmented: ScriptableObject, key: String): Any? {
        return when (val value = augmented.prop(key)) {
            is BaseFunction -> when {
                // @Hint by SuperMonster003 on Nov 6, 2024.
                //  ! Indicated that `key` is not defined in `augmented`
                //  ! but defined in a certain object in its prototype chain.
                //  ! zh-CN: 表示 `key` 未定义在 `augmented` 上, 但定义在其原型链对象上.
                !augmented.has(key) && ScriptableObject.hasProperty(augmented, key) -> {
                    withRhinoContext { cx -> BoundFunction(cx, augmented, value, augmented, arrayOf()) }
                }
                else -> value
            }
            else -> value
        }
    }

    fun proxying(target: Scriptable, proto: Any, withDollarPrefix: Boolean): ProxyObject {
        val augmented = augment(newNativeObject(), proto, false)

        val proxyObject = ProxyObject(
            scriptRuntime.topLevelScope,
            fun(args: Array<out Any?>): Any? {
                val (keyArg) = args
                return get(augmented, coerceString(keyArg))
            },
            fun(args: Array<out Any?>): Undefined {
                val (keyArg, value) = args
                return set(augmented, coerceString(keyArg), value)
            },
        )

        val keys = mutableListOf(key)
        if (withDollarPrefix) keys += "\$$key"
        keys.forEach { target.defineProp(it, proxyObject) }

        return proxyObject
    }

}
