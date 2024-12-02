package org.autojs.autojs.runtime.api.augment.jsox

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.jsSpecies
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.flatten
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.StringUtils.lowercaseFirstChar
import org.mozilla.javascript.Undefined

class Jsox(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val key = lowercaseFirstChar(javaClass.simpleName)

    override val selfAssignmentFunctions = listOf(
        ::extend.name,
        ::extendAll.name,
    )

    override fun invoke(vararg args: Any?): Undefined = extendRhinoWithRuntime(scriptRuntime, *args)

    companion object : FlexibleArray() {

        private val presetModules = listOf("Mathx", "Numberx", "Arrayx")

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun extend(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = unwrapArguments(args) {
            extendRhinoWithRuntime(scriptRuntime, *it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun extendRhinoWithRuntime(scriptRuntime: ScriptRuntime, vararg args: Any?): Undefined {
            val modules = flatten(args).map { coerceString(it) }.distinct()
            if (modules.isEmpty()) return extendAllRhinoWithRuntime(scriptRuntime)
            modules.forEach { moduleName ->
                val niceName = normalizeModuleName(moduleName)
                try {
                    val clazz = Class.forName(Jsox::class.java.name.replace(Jsox::class.java.simpleName, niceName))
                    val extensible = clazz.getConstructor(ScriptRuntime::class.java).newInstance(scriptRuntime)
                    require(extensible is JsBuildInObjectExtensible) {
                        val moduleNameSuffix = if (moduleName != niceName) " (or ${niceName})" else ""
                        listOf(
                            "Module name $moduleName$moduleNameSuffix",
                            "which is ${extensible.jsSpecies()}",
                            "cannot be taken as a extensible JavaScript build-in object",
                        ).joinToString(" ")
                    }
                    extensible.extendBuildInObject()
                } catch (_: ClassNotFoundException) {
                    throw WrappedIllegalArgumentException("Module name $moduleName cannot be taken as a extensible JavaScript build-in object")
                }
            }
            return UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun extendAll(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            extendAllRhinoWithRuntime(scriptRuntime)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun extendAllRhinoWithRuntime(scriptRuntime: ScriptRuntime): Undefined = extendRhinoWithRuntime(scriptRuntime, presetModules)

        private fun normalizeModuleName(name: String): String {
            return StringUtils.uppercaseFirstChar(if (!name.endsWith('x')) "${name}x" else name)
        }

    }

}
