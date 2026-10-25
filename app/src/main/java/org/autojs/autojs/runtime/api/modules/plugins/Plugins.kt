package org.autojs.autojs.runtime.api.modules.plugins

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.modules.jsox.Jsox
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.flatten
import org.autojs.autojs.util.RhinoUtils.getDeclarationScope
import org.autojs.autojs.util.RhinoUtils.js_require
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import kotlin.text.RegexOption.IGNORE_CASE

internal class LegacyPluginExtensionRegistry(
    presetModuleNames: List<String> = listOf("Arrayx", "Numberx", "Mathx"),
) {

    internal sealed interface Action {
        data class Preset(val name: String) : Action
        data class Registered(val value: Any?) : Action
    }

    private val modules = linkedMapOf<String, Action>().apply {
        presetModuleNames.forEach { name -> put(name, Action.Preset(name)) }
    }

    private val excludes = linkedSetOf<String>()

    fun select(args: Array<out Any?>): List<Action> {
        return normalizedNames(args).distinct().mapNotNull(modules::get)
    }

    fun selectAll(): List<Action> {
        return modules.filterKeys { it !in excludes }.values.toList()
    }

    fun selectAllBut(args: Array<out Any?>): List<Action> {
        exclude(args)
        return selectAll()
    }

    fun exclude(args: Array<out Any?>) {
        excludes += normalizedNames(args)
    }

    fun register(entries: Iterable<Pair<String, Any?>>) {
        entries.forEach { (name, value) -> modules[name] = Action.Registered(value) }
    }

    private fun normalizedNames(args: Array<out Any?>): List<String> {
        return flatten(args).map(::normalizeModuleName)
    }

    companion object {

        internal fun normalizeModuleName(name: Any?): String {
            val stringName = coerceString(name)
            val normalized = if (stringName.endsWith('x')) stringName else "${stringName}x"
            return normalized.replaceFirstChar { it.uppercaseChar() }
        }

    }

}

@Suppress("unused", "UNUSED_PARAMETER")
class Plugins(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    private val mExtensionRegistry = LegacyPluginExtensionRegistry()

    private val mExtendFunction = newBaseFunction(scriptRuntime.topLevelScope, "extend", { args ->
        mExtensionRegistry.select(args).forEach(::performExtension)
        UNDEFINED
    }, NOT_CONSTRUCTABLE).apply {
        defineProp("exclude", newBaseFunction(scriptRuntime.topLevelScope, "exclude", { args ->
            mExtensionRegistry.exclude(args)
            UNDEFINED
        }, NOT_CONSTRUCTABLE))
        defineProp("registerModule", newBaseFunction(scriptRuntime.topLevelScope, "registerModule", { args ->
            registerModule(args.firstOrNull())
            UNDEFINED
        }, NOT_CONSTRUCTABLE))
    }

    private val mExtendAllFunction = newBaseFunction(scriptRuntime.topLevelScope, "extendAll", {
        mExtensionRegistry.selectAll().forEach(::performExtension)
        UNDEFINED
    }, NOT_CONSTRUCTABLE)

    private val mExtendAllButFunction = newBaseFunction(scriptRuntime.topLevelScope, "extendAllBut", { args ->
        mExtensionRegistry.selectAllBut(args).forEach(::performExtension)
        UNDEFINED
    }, NOT_CONSTRUCTABLE)

    override val selfAssignmentProperties = listOf(
        "extend" to mExtendFunction,
        "extendAll" to mExtendAllFunction,
        "extendAllBut" to mExtendAllButFunction,
    )

    override val selfAssignmentFunctions = listOf(
        ::load.name,
    )

    override fun invoke(vararg args: Any?): Any? = ensureArgumentsOnlyOne(args) { o ->
        load(scriptRuntime, arrayOf(o))
    }

    private fun registerModule(module: Any?) {
        if (module.isJsNullish()) return
        val source = when (module) {
            is Scriptable -> module
            else -> Context.toObject(module, scriptRuntime.topLevelScope)
        }
        mExtensionRegistry.register(source.ids.mapNotNull { id ->
            val name = when (id) {
                is Int -> id.toString()
                else -> runCatching { Context.toString(id) }.getOrNull() ?: return@mapNotNull null
            }
            val value = when (id) {
                is Int -> ScriptableObject.getProperty(source, id)
                else -> ScriptableObject.getProperty(source, name)
            }
            name to value
        })
    }

    private fun performExtension(action: LegacyPluginExtensionRegistry.Action) {
        when (action) {
            is LegacyPluginExtensionRegistry.Action.Preset -> {
                Jsox.extendRhinoWithRuntime(scriptRuntime, action.name)
            }
            is LegacyPluginExtensionRegistry.Action.Registered -> {
                val value = action.value
                require(!value.isJsNullish()) {
                    "Registered plugin extension ${value.jsBrief()} must expose extendJsBuildInObjects()"
                }
                val extension = when (value) {
                    is Scriptable -> value
                    else -> Context.toObject(value, scriptRuntime.topLevelScope)
                }
                callFunction(scriptRuntime, extension, "extendJsBuildInObjects", emptyArray())
            }
        }
    }

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun load(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) { o ->
            val name = coerceString(o)
            val asPackageName = name.contains('.') && !name.matches(Regex(".+\\.js", IGNORE_CASE))
            when {
                asPackageName -> {
                    val plugin = scriptRuntime.plugins.load(name)
                    val moduleExportedFunc = js_require(scriptRuntime, plugin.mainScriptPath) as Callable
                    val global = scriptRuntime.topLevelScope.globalThis
                    val scope = (moduleExportedFunc as? Scriptable)?.let(::getDeclarationScope)
                        ?: getDeclarationScope(global)
                    callFunction(scriptRuntime, moduleExportedFunc, scope, global, arrayOf(plugin.unwrap()))
                }
                scriptRuntime.files.isDir("./plugins") -> {
                    /* As project-level plugins name. */
                    js_require(scriptRuntime, "./plugins/$name")
                }
                else -> listOf(
                    "A directory named \"plugins\" containing module \"$name\"",
                    "must be found in the root directory of current project",
                ).joinToString(" ").let { s -> throw WrappedIllegalArgumentException(s) }
            }
        }

    }

}
