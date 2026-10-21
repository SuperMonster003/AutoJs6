package org.autojs.autojs.engine

import android.annotation.SuppressLint
import android.graphics.Paint
import android.os.Build
import android.util.Log
import android.view.View
import org.autojs.autojs.core.plugin.typescript.CompiledTypeScriptSource
import org.autojs.autojs.core.plugin.typescript.TypeScriptExecutionCompiler
import org.autojs.autojs.core.plugin.typescript.TypeScriptMaterializedRhinoProject
import org.autojs.autojs.core.plugin.typescript.TypeScriptRhinoCommonJsRequire
import org.autojs.autojs.core.plugin.typescript.TypeScriptSourceMapBindings
import org.autojs.autojs.core.ui.ViewExtras
import org.autojs.autojs.engine.module.AndroidRhinoAssetLoader
import org.autojs.autojs.engine.module.AssetAndUrlModuleSourceProvider
import org.autojs.autojs.engine.module.ProcessWideAssetModuleScriptProvider
import org.autojs.autojs.engine.module.ProcessWideRhinoAssetCache
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.project.ScriptConfig
import org.autojs.autojs.rhino.AndroidContextFactory
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.rhino.RhinoAndroidHelper
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.proxy.PaintProxyObject
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.js_object_assign
import org.autojs.autojs.util.RhinoUtils.js_require
import org.autojs.autojs.util.RhinoUtils.registerStandardObjects
import org.autojs.autojs.util.RhinoUtils.withTimeConsuming
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Script
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.commonjs.module.Require
import org.mozilla.javascript.commonjs.module.provider.MultiModuleScriptProvider
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider
import org.mozilla.javascript.lc.type.TypeInfo
import java.io.File
import java.io.IOException
import java.io.Reader
import java.net.URI
import java.util.Locale

/**
 * Created by Stardust on Apr 2, 2017.
 * Modified by SuperMonster003 as of Jan 16, 2026.
 */
open class RhinoJavaScriptEngine(private val androidContext: android.content.Context) : JavaScriptEngine() {

    private val mWrapFactory = WrapFactory()
    private val mAssetLoader = AndroidRhinoAssetLoader(androidContext)
    private lateinit var mRequire: Require
    private var mMaterializedTypeScriptProject: TypeScriptMaterializedRhinoProject? = null
    val context: Context = enterContext()
    val scriptable: TopLevelScope = createScope(context)

    lateinit var thread: Thread
        private set

    /** Whether the current thread is the one the script runs on (set by [init]; the main thread in UI mode). */
    fun isOnScriptThread() = ::thread.isInitialized && thread === Thread.currentThread()

    private val mInitScript: Script
        get() = try {
            ProcessWideRhinoAssetCache.getBootstrapScript(
                context,
                mAssetLoader,
                SOURCE_FILE_INIT,
                SOURCE_NAME_INIT,
            )
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }

    override fun put(name: String, value: Any?) {
        scriptable.defineProp(name, Context.javaToJS(value, scriptable))
    }

    override fun setRuntime(runtime: ScriptRuntime) {
        super.setRuntime(runtime)
        runtime.bridges.setup(this)
        runtime.topLevelScope = scriptable
    }

    public override fun doExecution(source: JavaScriptSource): Any? {
        try {
            mMaterializedTypeScriptProject?.close()
            mMaterializedTypeScriptProject = null
            val compiledSource = source as? CompiledTypeScriptSource
            val executable = compiledSource?.executable
            executable?.let { compiled ->
                val bindings: TypeScriptSourceMapBindings
                val generatedSourceName: String
                if (compiled.isProjectCompilation) {
                    val project = TypeScriptMaterializedRhinoProject.create(
                        androidContext,
                        compiled,
                        compiledSource.preExecutePrefix,
                    )
                    mMaterializedTypeScriptProject = project
                    bindings = compiled.sourceMapBindings(
                        generatedSourceNames = project.generatedSourceNames,
                        entryGeneratedLineOffset = compiledSource.entryGeneratedLineOffset,
                    )
                    generatedSourceName = project.entryFile.canonicalPath
                } else {
                    bindings = compiled.sourceMapBindings(
                        generatedSourceNames = mapOf(
                            compiled.entryModulePath to compiled.generatedSourceName,
                        ),
                        entryGeneratedLineOffset = compiledSource.entryGeneratedLineOffset,
                    )
                    generatedSourceName = compiled.generatedSourceName
                }
                setTag(
                    TypeScriptExecutionCompiler.SOURCE_MAP_ENGINE_TAG,
                    bindings,
                )
                setTag(
                    TypeScriptExecutionCompiler.GENERATED_SOURCE_ENGINE_TAG,
                    generatedSourceName,
                )
                compiled.diagnostics.forEach { diagnostic ->
                    runtime.console.warn(
                        TypeScriptExecutionCompiler.formatDiagnostic(
                            compiled.sourceName,
                            diagnostic,
                        ),
                    )
                }
            }
            mMaterializedTypeScriptProject?.let { project ->
                return mRequire.requireMain(context, project.entryFile.canonicalPath)
            }
            val reader = preprocess(source.nonNullScriptReader)
            val sourceName = executable?.generatedSourceName ?: source.fullPath
            val script = context.compileReader(reader, sourceName, 1, null)
            return if (hasFeature(ScriptConfig.FEATURE_CONTINUATION)) {
                context.executeScriptWithContinuations(script, scriptable)
            } else {
                script.exec(context, scriptable, scriptable.globalThis)
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @Throws(IOException::class)
    protected fun preprocess(script: Reader) = script

    override fun forceStop() {
        Log.d(TAG, "forceStop: interrupt Thread: $thread")
        thread.interrupt()
        runtime.cancelScriptJobs()
    }

    @Synchronized
    override fun destroy() {
        mMaterializedTypeScriptProject?.close()
        mMaterializedTypeScriptProject = null
        super.destroy()
        Log.d(TAG, "on destroy")
        Context.exit()
    }

    override fun init() {
        thread = Thread.currentThread()

        initRequireBuilder(context, scriptable)

        runtime.withTimeConsuming("runtime-init-prologue") {
            it.initPrologue()
        }

        scriptable.defineProp("global", scriptable.globalThis, PERMANENT)
        scriptable.defineProp("__engine__", this, READONLY or DONTENUM or PERMANENT)
        scriptable.defineProp("Promise", runtime.js_Promise, READONLY or PERMANENT)
        scriptable.defineProp("ResultAdapter", runtime.js_ResultAdapter, READONLY or PERMANENT)

        this.withTimeConsuming("script-init") {
            // Resolve the cached script inside the timed block so a cold asset read/compile is
            // attributed to script-init instead of being hidden by receiver evaluation.
            val initScript = mInitScript
            runCatching {
                context.executeScriptWithContinuations(initScript, scriptable)
            }.getOrElse { e ->
                if (e.message?.contains("Script argument was not a script or was not created by interpreted mode") == true) {
                    initScript.exec(context, scriptable, scriptable.globalThis)
                } else throw e
            }.let { export ->
                when (export) {
                    is Callable -> callFunction(
                        runtime,
                        export,
                        scriptable,
                        scriptable.globalThis,
                        arrayOf<Any?>(runtime, scriptable),
                    )
                    is NativeArray -> export.map { coerceString(it) }.distinct().forEach { bindModule(it) }
                    is NativeObject -> export.entries.forEach { entry -> bindModule(coerceString(entry.key), coerceString(entry.value)) }
                    is String -> when {
                        export.contains("|") -> export.split("|").forEach { bindModule(it) }
                        else -> bindModule(export)
                    }
                    is Number -> bindModule(coerceString(export))
                    else -> require(export.isJsNullish()) { "Invalid init script evaluated value ${export.jsBrief()}" }
                }
            }
        }

        runtime.withTimeConsuming("runtime-init-epilogue") {
            it.initEpilogue()
        }
    }

    private fun bindModule(fileName: String) = bindModule(fileName, fileName)

    private fun bindModule(moduleName: String, fileName: String) {
        val niceModuleName = moduleName.trim()
        val niceFileName = fileName.trim()
        val exportedRaw = kotlin.runCatching {
            js_require(runtime, "__${niceFileName}__")
        }.getOrElse { js_require(runtime, niceFileName) }
        val exported: Any? = when (exportedRaw) {
            is Callable -> callFunction(
                runtime,
                exportedRaw,
                scriptable,
                scriptable.globalThis,
                arrayOf<Any?>(runtime, scriptable),
            )
            else -> exportedRaw
        }
        if (exported.isJsNullish()) {
            return
        }
        val globalObject = scriptable.prop(niceModuleName)
        if (globalObject.isJsNullish()) {
            scriptable.defineProp(niceModuleName, exported)
            return
        }
        require(globalObject is Scriptable) {
            "Global value global.$niceModuleName is ${globalObject.jsBrief()}, which must be a Scriptable to be assigned to"
        }
        require(exported is Scriptable) {
            "Module $niceModuleName exported value ${exported.jsBrief()} must be a Scriptable to be assigned to the global object"
        }
        js_object_assign(globalObject, exported)
    }

    private fun initRequireBuilder(context: Context, scope: TopLevelScope) {
        val provider = AssetAndUrlModuleSourceProvider(
            androidContext, MODULES_ROOT_PATH, listOf<URI>(File(File.separator).toURI())
        )
        val moduleScriptProvider = MultiModuleScriptProvider(
            listOf(
                ProcessWideAssetModuleScriptProvider(androidContext, MODULES_ROOT_PATH),
                SoftCachingModuleScriptProvider(provider),
            )
        )
        mRequire = TypeScriptRhinoCommonJsRequire(
            context = context,
            nativeScope = scope,
            moduleScriptProvider = moduleScriptProvider,
            runtimeProject = { mMaterializedTypeScriptProject },
        )
        mRequire.install(scope)
    }

    protected fun createScope(context: Context) = TopLevelScope().apply {
        initStandardObjects(context, false)
        context.registerStandardObjects(this)
    }

    @SuppressLint("VisibleForTests")
    fun enterContext(): Context {
        val rhinoAndroidHelper = RhinoAndroidHelper(androidContext)
        val enterContext = try {
            rhinoAndroidHelper.enterContext()
        } catch (_: SecurityException) {
            rhinoAndroidHelper.contextFactory.enterContext()
        }
        return enterContext.also { setupContext(it) }
    }

    fun setupContext(context: Context) = context.apply {
        if (this is AutoJsContext) {
            rhinoJavaScriptEngine = this@RhinoJavaScriptEngine
        }
        isInterpretedMode = true
        languageVersion = Context.VERSION_ES6
        locale = Locale.getDefault()
        wrapFactory = mWrapFactory
    }

    private inner class WrapFactory : AndroidContextFactory.WrapFactory() {

        override fun wrapAsJavaObject(cx: Context?, scope: VarScope, javaObject: Any?, staticType: TypeInfo): Scriptable? {
            val globalThis = ScriptableObject.getTopLevelScope(scope).globalThis
            return when {
                javaObject is View -> ViewExtras.getNativeView(globalThis, /* view = */ javaObject, staticType.asClass(), runtime)
                javaObject is Paint && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // Use composition-based proxy to preserve Paint identity and intercept setColor only.
                    // zh-CN: 使用组合式代理以保持 Paint 身份一致性, 且仅拦截 setColor.
                    val base = super.wrapAsJavaObject(cx, scope, javaObject, staticType) ?: return null
                    PaintProxyObject(globalThis, base, javaObject)
                }
                else -> super.wrapAsJavaObject(cx, scope, javaObject, staticType)
            }
        }
    }

    companion object {

        private const val TAG = "RhinoJavaScriptEngine"

        const val SOURCE_FILE_INIT = "init.js"
        const val SOURCE_NAME_INIT = "<init>"
        const val MODULES_ROOT_PATH = "modules"
        const val JS_BEAUTIFY_PATH = "js-beautify"

        @JvmField
        val JS_BEAUTIFY_FILE = PFiles.join(JS_BEAUTIFY_PATH, "beautify.js")
    }
}
