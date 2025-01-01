package org.autojs.autojs.engine

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import org.autojs.autojs.core.ui.ViewExtras
import org.autojs.autojs.engine.module.AssetAndUrlModuleSourceProvider
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.project.ScriptConfig
import org.autojs.autojs.rhino.AndroidContextFactory
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.rhino.RhinoAndroidHelper
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.js_object_assign
import org.autojs.autojs.util.RhinoUtils.js_require
import org.autojs.autojs.util.RhinoUtils.withTimeConsuming
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Script
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.commonjs.module.RequireBuilder
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.net.URI
import java.util.*

/**
 * Created by Stardust on Apr 2, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 */
open class RhinoJavaScriptEngine(private val scriptRuntime: ScriptRuntime, private val androidContext: android.content.Context) : JavaScriptEngine() {

    private val mWrapFactory = WrapFactory()
    val context: Context = enterContext()
    val scriptable: TopLevelScope = createScope(context)

    lateinit var thread: Thread
        private set

    private val mInitScript: Script by lazy {
        try {
            val reader = InputStreamReader(androidContext.assets.open(SOURCE_FILE_INIT))
            context.compileReader(reader, SOURCE_NAME_INIT, 1, null)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
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
            val reader = preprocess(source.nonNullScriptReader)
            val script = context.compileReader(reader, source.fullPath, 1, null)
            return if (hasFeature(ScriptConfig.FEATURE_CONTINUATION)) {
                context.executeScriptWithContinuations(script, scriptable)
            } else {
                script.exec(context, scriptable)
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
    }

    @Synchronized
    override fun destroy() {
        super.destroy()
        Log.d(TAG, "on destroy")
        Context.exit()
    }

    override fun init() {
        thread = Thread.currentThread()
        scriptable.defineProp("__engine__", this)
        initRequireBuilder(context, scriptable)

        runtime.withTimeConsuming("runtime-init-prologue") {
            it.initPrologue()
        }

        mInitScript.withTimeConsuming("script-init") { initScript ->
            runCatching {
                scriptable.defineProp("global", scriptable, PERMANENT)
                context.executeScriptWithContinuations(initScript, scriptable)
            }.getOrElse { e ->
                if (e.message?.contains("Script argument was not a script or was not created by interpreted mode") == true) {
                    initScript.exec(context, scriptable)
                } else throw e
            }.let { export ->
                when (export) {
                    is BaseFunction -> export.call(context, scriptable, scriptable, arrayOf(runtime, scriptable))
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
            is BaseFunction -> exportedRaw.call(context, scriptable, scriptable, arrayOf(runtime, scriptable))
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

    private fun initRequireBuilder(context: Context, scope: Scriptable) {
        val provider = AssetAndUrlModuleSourceProvider(
            androidContext, MODULES_ROOT_PATH, listOf<URI>(File(File.separator).toURI())
        )
        RequireBuilder()
            .setModuleScriptProvider(SoftCachingModuleScriptProvider(provider))
            .setSandboxed(true)
            .createRequire(context, scope)
            .install(scope)
    }

    protected fun createScope(context: Context) = TopLevelScope().apply {
        initStandardObjects(context, false)
    }

    @SuppressLint("VisibleForTests")
    fun enterContext(): Context {
        val rhinoAndroidHelper = RhinoAndroidHelper(androidContext)
        val enterContext = try {
            rhinoAndroidHelper.enterContext()
        } catch (e: SecurityException) {
            rhinoAndroidHelper.contextFactory.enterContext()
        }
        return enterContext.also { setupContext(it) }
    }

    fun setupContext(context: Context) = context.apply {
        if (this is AutoJsContext) {
            rhinoJavaScriptEngine = this@RhinoJavaScriptEngine
        }
        @Suppress("DEPRECATION")
        optimizationLevel = -1
        languageVersion = Context.VERSION_ES6
        locale = Locale.getDefault()
        wrapFactory = mWrapFactory
    }

    private inner class WrapFactory : AndroidContextFactory.WrapFactory() {

        override fun wrapAsJavaObject(cx: Context?, scope: Scriptable, javaObject: Any?, staticType: Class<*>?): Scriptable? {
            return when (javaObject) {
                is View -> ViewExtras.getNativeView(scope, /* view = */ javaObject, staticType, runtime)
                else -> super.wrapAsJavaObject(cx, scope, javaObject, staticType)
            }
        }

    }

    companion object {

        const val SOURCE_FILE_INIT = "init.js"
        const val SOURCE_NAME_INIT = "<init>"
        const val MODULES_ROOT_PATH = "modules"
        const val JS_BEAUTIFY_PATH = "js-beautify"

        @JvmField
        val JS_BEAUTIFY_FILE = PFiles.join(JS_BEAUTIFY_PATH, "beautify.js")

        private val TAG = RhinoJavaScriptEngine::class.java.simpleName

    }

}
