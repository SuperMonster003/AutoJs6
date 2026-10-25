package org.autojs.autojs.runtime.api.modules.installer

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.api.installer.InstallerScriptArguments
import org.autojs.autojs.runtime.api.installer.InstallerScriptOptions
import org.autojs.autojs.runtime.api.installer.InstallerScriptValues
import org.autojs.autojs.runtime.api.installer.InstallerService
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import java.util.function.Supplier

internal typealias InstallerWork = suspend () -> Any?

/** The installer / $installer façade. All argument snapshots are made before launching a worker. */
@Suppress("unused", "UNUSED_PARAMETER")
class Installer(
    private val runtime: ScriptRuntime,
    private val service: InstallerService,
) : Augmentable(runtime) {
    override val keys = listOf(AugmentableKey("installer"))

    override val selfAssignmentFunctions = listOf(
        ::install.name, ::installAsync.name, ::session.name,
        ::uninstall.name, ::uninstallAsync.name, ::inspect.name, ::inspectAsync.name,
        ::isDefault.name, ::setDefault.name, ::setDefaultAsync.name,
        ::users.name, ::usersAsync.name, ::isAvailable.name,
    )

    override val scriptErrorPassThroughFunctions: Set<String> get() = selfAssignmentFunctions.toSet()

    override val selfAssignmentGetters = listOf<Any>(
        "status" to Supplier<Any?> { syncOf { { service.status() } } },
    )

    override val selfAssignmentProperties: List<Pair<Any, Any?>> by lazy {
        listOf(InstallerJsErrors.NAME to InstallerJsErrors.constructor(runtime), "authorizer" to authorizerObject())
    }

    @RhinoRuntimeFunctionInterface
    fun install(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = syncOf { installWork(args) }

    @RhinoRuntimeFunctionInterface
    fun installAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = asyncOf { installWork(args) }

    @RhinoRuntimeFunctionInterface
    fun session(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        InstallerJsArguments.count("installer.session", args, 1..2)
        val plan = sources(args[0])
        val options = InstallerScriptOptions.install(InstallerJsArguments.optional(args, 1))
        if (!InstallerPromises.sync(runtime) { service.isAvailable() }) {
            throw InstallerJsErrors.failure(InstallerErrorCodes.PLUGIN_UNAVAILABLE, "installer plugin is unavailable")
        }
        InstallSessionNativeObject(runtime, service, plan, options)
    }

    @RhinoRuntimeFunctionInterface
    fun uninstall(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = syncOf { uninstallWork(args) }

    @RhinoRuntimeFunctionInterface
    fun uninstallAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = asyncOf { uninstallWork(args) }

    @RhinoRuntimeFunctionInterface
    fun inspect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = syncOf(inspect = true) { inspectWork(args) }

    @RhinoRuntimeFunctionInterface
    fun inspectAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = asyncOf(inspect = true) { inspectWork(args) }

    @RhinoRuntimeFunctionInterface
    fun isDefault(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = syncOf {
        InstallerJsArguments.count("installer.isDefault", args, 0..0)
        val work: InstallerWork = { service.isDefault() }
        work
    }

    @RhinoRuntimeFunctionInterface
    fun setDefault(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = syncOf { setDefaultWork(args) }

    @RhinoRuntimeFunctionInterface
    fun setDefaultAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = asyncOf { setDefaultWork(args) }

    @RhinoRuntimeFunctionInterface
    fun users(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = syncOf { usersWork(args) }

    @RhinoRuntimeFunctionInterface
    fun usersAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = asyncOf { usersWork(args) }

    @RhinoRuntimeFunctionInterface
    fun isAvailable(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = syncOf {
        InstallerJsArguments.count("installer.isAvailable", args, 0..0)
        val work: InstallerWork = { service.isAvailable() }
        work
    }

    private fun installWork(args: Array<out Any?>): InstallerWork {
        InstallerJsArguments.count("installer.install", args, 1..2)
        val plan = sources(args[0])
        val options = InstallerScriptOptions.install(InstallerJsArguments.optional(args, 1))
        return { InstallerScriptValues.completion(service.install(plan, options), plan.isBatch) }
    }

    private fun uninstallWork(args: Array<out Any?>): InstallerWork {
        InstallerJsArguments.count("installer.uninstall", args, 1..2)
        val packageName = InstallerScriptArguments.packageName(InstallerJsArguments.source(args[0]))
        val options = InstallerScriptOptions.uninstall(InstallerJsArguments.optional(args, 1))
        return { InstallerScriptValues.uninstallResult(service.uninstall(packageName, options)) }
    }

    private fun inspectWork(args: Array<out Any?>): InstallerWork {
        InstallerJsArguments.count("installer.inspect", args, 1..1)
        val source = InstallerScriptArguments.inspect(InstallerJsArguments.source(args[0]), runtime.files.cwd())
        return { InstallerScriptValues.json(service.inspect(source)) }
    }

    private fun setDefaultWork(args: Array<out Any?>): InstallerWork {
        InstallerJsArguments.count("installer.setDefault", args, 0..2)
        val enabled = InstallerScriptOptions.enabled(InstallerJsArguments.optional(args, 0))
        val options = InstallerScriptOptions.setDefault(InstallerJsArguments.optional(args, 1))
        return { service.setDefault(enabled, options.authorizer, options.mode) }
    }

    private fun usersWork(args: Array<out Any?>): InstallerWork {
        InstallerJsArguments.count("installer.users", args, 0..0)
        return { InstallerScriptValues.users(service.users()) }
    }

    private fun sources(raw: Any?) = InstallerScriptArguments.sources(InstallerJsArguments.source(raw), runtime.files.cwd())

    private fun authorizerObject(): NativeObject = RhinoUtils.newNativeObject(runtime.topLevelScope).apply {
        defineProperty("available", { syncOf { { service.availableAuthorizers() } } }, null, ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        defineProperty("state", newBaseFunction(runtime.topLevelScope, "state", { args -> syncOf {
            InstallerJsArguments.count("installer.authorizer.state", args, 0..1)
            val name = InstallerScriptOptions.authorizer(InstallerJsArguments.optional(args, 0))
            val work: InstallerWork = { service.authorizerState(name) }
            work
        } }, NOT_CONSTRUCTABLE), ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        for (async in listOf(false, true)) {
            val name = if (async) "requestAsync" else "request"
            defineProperty(name, newBaseFunction(runtime.topLevelScope, name, { args ->
                val build = {
                    InstallerJsArguments.count("installer.authorizer.$name", args, 1..2)
                    val rawAuthorizer = InstallerJsArguments.json(args[0])
                        ?: throw InstallerJsErrors.failure(InstallerErrorCodes.INVALID_ARGUMENT, "authorizer request requires an authorizer name")
                    val authorizer = InstallerScriptOptions.authorizer(rawAuthorizer)
                    val timeout = InstallerScriptOptions.timeout(InstallerJsArguments.optional(args, 1), InstallerContract.DEFAULT_USER_ACTION_TIMEOUT_MILLIS)
                    val work: InstallerWork = { service.requestAuthorizer(authorizer, timeout) }
                    work
                }
                if (async) asyncOf(build = build) else syncOf(build = build)
            }, NOT_CONSTRUCTABLE), ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        }
    }

    private fun syncOf(inspect: Boolean = false, build: () -> InstallerWork): Any? = guarded {
        val work = build()
        InstallerJsResults.toJs(runtime, InstallerPromises.sync(runtime, work), inspect)
    }

    private fun asyncOf(inspect: Boolean = false, build: () -> InstallerWork): Any = try {
        val work = build()
        InstallerPromises.launch(runtime, inspect, work)
    } catch (failure: Throwable) {
        InstallerPromises.rejected(runtime, failure)
    }

    private inline fun <T> guarded(block: () -> T): T = try { block() } catch (failure: Throwable) {
        InstallerJsErrors.throwInto(runtime, failure)
    }
}
