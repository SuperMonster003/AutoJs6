package org.autojs.autojs.runtime.api.augment.jsox

import org.autojs.autojs.AutoJs
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Augmentable.Companion.refineAttributes
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.PERMANENT

interface JsBuildInObjectExtensible {

    fun extendBuildInObject()

}

const val AS_JSOX_PROTO = 0x10000
const val AS_JSOX_STATIC = 0x20000
const val AS_JSOX_GLOBAL = 0x40000

internal fun extendBuildInObjectInternal(scriptRuntime: ScriptRuntime, augmentable: Augmentable, buildInObjectName: String, extensibleProtoClass: Class<*>? = null) {
    extendBuildInObjectInternal(scriptRuntime, augmentable, scriptRuntime.topLevelScope.prop(buildInObjectName) as ScriptableObject, extensibleProtoClass)
}

internal fun extendBuildInObjectInternal(scriptRuntime: ScriptRuntime, augmentable: Augmentable, buildInObject: ScriptableObject, extensibleProtoClass: Class<*>? = null) {
    augmentable.selfAssignmentProperties.forEach { pair ->
        val (first, second) = pair
        val name: String
        val value: Any?
        val flag: Int
        when (first) {
            is Pair<*, *> -> {
                name = first.first as String
                value = first.second
                flag = second as Int
            }
            else -> {
                name = first as String
                value = second
                flag = -1
            }
        }
        if (flag and AS_JSOX_STATIC != 0) {
            buildInObject.defineProperty(name, value, PERMANENT or refineAttributes(flag))
        }
        if (flag and AS_JSOX_PROTO != 0) {
            (buildInObject.prop("prototype") as ScriptableObject).defineProperty(name, value, PERMANENT or refineAttributes(flag))
        }
        if (flag and AS_JSOX_GLOBAL != 0) {
            scriptRuntime.topLevelScope.defineProperty(name, value, PERMANENT or refineAttributes(flag))
        }
    }

    val staticList = mutableListOf<Pair<Pair<String, String>, Int>>()
    val protoList = mutableListOf<Pair<String, Int>>()
    val globalList = mutableListOf<Pair<Pair<String, String>, Int>>()

    augmentable.selfAssignmentFunctions.forEach { o ->
        if (o !is Pair<*, *>) return@forEach

        val (first, second) = o
        val flag = if (second is Int) second else 0

        if (flag and AS_JSOX_STATIC != 0) addFuncNameIntoList(first, staticList, flag)
        if (flag and AS_JSOX_PROTO != 0) when (first) {
            is String -> protoList += first to (PERMANENT or refineAttributes(flag))
            is Pair<*, *> -> throw IllegalArgumentException("A proto name must be specified without alias")
        }
        if (flag and AS_JSOX_GLOBAL != 0) addFuncNameIntoList(first, globalList, flag)
    }

    staticList.forEach { pair ->
        val (funcNamePair, attributes) = pair
        val (name, alias) = funcNamePair
        augmentable.augmentFunctionsBy(buildInObject, mutableListOf(name to alias to attributes))
    }
    protoList.forEach { pair ->
        val (funcName, attributes) = pair
        require(extensibleProtoClass != null) { "A proto class must be specified for build-in object prototype extension" }

        val prototypeObject = buildInObject.prop("prototype") as ScriptableObject

        val f = object : BaseFunction() {

            override fun getFunctionName() = funcName

            override fun call(cx: Context?, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>) = runCatching {
                extensibleProtoClass
                    .getMethod(funcName, ScriptRuntime::class.java, Scriptable::class.java, Array<Any>::class.java)
                    .invoke(null, scriptRuntime, thisObj, args)
            }.getOrElse { e ->
                val message = "Failed to extend build-in object ${extensibleProtoClass.name}#$funcName"
                AutoJs.instance.globalConsole.warn(message)
                scriptRuntime.exit(e)
            }

            override fun construct(cx: Context?, scope: Scriptable, args: Array<out Any?>): Scriptable {
                throw RuntimeException("Function $funcName is unable to be invoked with \"new\" operator")
            }
        }.apply { RhinoUtils.initNewBaseFunction(this) }

        prototypeObject.defineProperty(funcName, f, attributes)
    }
    globalList.forEach { pair ->
        val (funcNamePair, attributes) = pair
        val (name, alias) = funcNamePair
        augmentable.augmentFunctionsBy(scriptRuntime.topLevelScope, mutableListOf(name to alias to attributes))
    }
}

private fun addFuncNameIntoList(funcName: Any?, list: MutableList<Pair<Pair<String, String>, Int>>, flag: Int) {
    when (funcName) {
        is String -> list += funcName to funcName to (PERMANENT or refineAttributes(flag))
        is Pair<*, *> -> {
            val (name, alias) = funcName
            when (alias) {
                is List<*> -> alias.forEach { list += coerceString(name) to coerceString(it) to (PERMANENT or refineAttributes(flag)) }
                else -> list += coerceString(name) to coerceString(alias) to (PERMANENT or refineAttributes(flag))
            }
        }
    }
}
