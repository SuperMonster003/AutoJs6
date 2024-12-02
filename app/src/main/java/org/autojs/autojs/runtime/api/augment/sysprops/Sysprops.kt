package org.autojs.autojs.runtime.api.augment.sysprops

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.ArrayExtensions.toNativeObject
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.AndroidUtils.SystemProperties
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.StringUtils.convertRegex
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.regexp.NativeRegExp

@Suppress("UNUSED_PARAMETER")
class SysProps(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val key = super.key.lowercase()

    override val selfAssignmentFunctions = listOf(
        ::get.name,
        ::getInt.name,
        ::getBoolean.name,
        ::getAll.name,
    )

    companion object {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun get(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String? = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (propName, defaultValue) = argList
            SystemProperties.get(coerceString(propName), if (defaultValue.isJsNullish()) null else coerceString(defaultValue))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getInt(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (propName, defaultValue) = argList
            SystemProperties.getInt(coerceString(propName), if (defaultValue.isJsNullish()) null else coerceIntNumber(defaultValue))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getBoolean(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (propName, defaultValue) = argList
            SystemProperties.getBoolean(coerceString(propName), if (defaultValue.isJsNullish()) null else coerceBoolean(defaultValue))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getAll(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsAtMost(args, 2) { argList ->
            val all = SystemProperties.getAll()
            when (argList.size) {
                2 -> {
                    val (keyFilter, valueFilter) = argList
                    all.filter {
                        matchFilter(it.key, keyFilter) && matchFilter(it.value, valueFilter)
                    }.toNativeObject()
                }
                1 -> {
                    val (arg) = argList
                    val keyFilter: Any?
                    val valueFilter: Any?
                    when (arg) {
                        is NativeObject -> {
                            keyFilter = arg.prop("key") ?: arg.prop("keys")
                            valueFilter = arg.prop("value") ?: arg.prop("values")
                        }
                        else -> {
                            keyFilter = arg
                            valueFilter = null
                        }
                    }
                    getAll(scriptRuntime, arrayOf(keyFilter, valueFilter))
                }
                0 -> all.toNativeObject()
                else -> throw ShouldNeverHappenException()
            }
        }

        private fun matchFilter(input: String, filter: Any?) = when {
            filter.isJsNullish() -> true
            filter is NativeRegExp -> Regex(convertRegex(filter.toString())).containsMatchIn(input)
            else -> input.contains(coerceString(filter))
        }

    }

}
