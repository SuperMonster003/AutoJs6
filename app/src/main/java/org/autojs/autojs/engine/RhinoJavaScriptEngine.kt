package org.autojs.autojs.engine

import android.util.Log
import android.view.View
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.ui.ViewExtras
import org.autojs.autojs.engine.module.AssetAndUrlModuleSourceProvider
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.project.ScriptConfig
import org.autojs.autojs.rhino.RhinoAndroidHelper
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.util.KotlinUtils.KotlinBoxed
import org.mozilla.javascript.Context
import org.mozilla.javascript.Script
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.commonjs.module.RequireBuilder
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Stardust on 2017/4/2.
 */
open class RhinoJavaScriptEngine(private val mAndroidContext: android.content.Context) : JavaScriptEngine() {

    val context: Context
    private val mScriptable: TopLevelScope
    lateinit var thread: Thread
        private set

    private val initScript: Script
        get() {
            return sInitScript ?: try {
                val reader = InputStreamReader(mAndroidContext.assets.open("init.js"))
                val script = context.compileReader(reader, SOURCE_NAME_INIT, 1, null)
                sInitScript = script
                script
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }

    val scriptable: Scriptable
        get() = mScriptable

    init {
        this.context = enterContext()
        mScriptable = createScope(this.context)
    }

    override fun put(name: String, value: Any?) {
        ScriptableObject.putProperty(mScriptable, name, Context.javaToJS(value, mScriptable))
    }

    override fun setRuntime(runtime: ScriptRuntime) {
        super.setRuntime(runtime)
        runtime.topLevelScope = mScriptable
    }

    public override fun doExecution(source: JavaScriptSource): Any? {
        var reader = source.nonNullScriptReader
        try {
            reader = preprocess(reader)
            val script = context.compileReader(reader, source.toString(), 1, null)
            return if (hasFeature(ScriptConfig.FEATURE_CONTINUATION)) {
                context.executeScriptWithContinuations(script, mScriptable)
            } else {
                script.exec(context, mScriptable)
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }

    }

    fun hasFeature(feature: String): Boolean {
        val config = getTag(ExecutionConfig.tag) as ExecutionConfig?
        return config != null && config.scriptConfig.hasFeature(feature)
    }

    @Throws(IOException::class)
    protected fun preprocess(script: Reader): Reader {
        return script
    }

    override fun forceStop() {
        Log.d(LOG_TAG, "forceStop: interrupt Thread: $thread")
        thread.interrupt()
    }

    @Synchronized
    override fun destroy() {
        super.destroy()
        Log.d(LOG_TAG, "on destroy")
        sContextEngineMap.remove(context)
        Context.exit()
    }

    override fun init() {
        thread = Thread.currentThread()
        ScriptableObject.putProperty(mScriptable, "__engine__", this)
        initRequireBuilder(context, mScriptable)
        try {
            context.executeScriptWithContinuations(initScript, mScriptable)
        } catch (e: IllegalArgumentException) {
            if ("Script argument was not a script or was not created by interpreted mode " == e.message) {
                initScript.exec(context, mScriptable)
            } else {
                throw e
            }
        }
    }

    private fun initRequireBuilder(context: Context, scope: Scriptable) {
        val provider = AssetAndUrlModuleSourceProvider(
            mAndroidContext, MODULES_PATH,
            listOf<URI>(File("/").toURI())
        )
        RequireBuilder()
            .setModuleScriptProvider(SoftCachingModuleScriptProvider(provider))
            .setSandboxed(true)
            .createRequire(context, scope)
            .install(scope)

    }

    protected fun createScope(context: Context): TopLevelScope {
        val topLevelScope = TopLevelScope()
        topLevelScope.initStandardObjects(context, false)
        return topLevelScope
    }

    fun enterContext(): Context {
        val context = RhinoAndroidHelper(mAndroidContext).enterContext()
        setupContext(context)
        sContextEngineMap[context] = this
        return context
    }

    protected fun setupContext(context: Context) {
        context.optimizationLevel = -1
        context.languageVersion = Context.VERSION_ES6
        context.locale = Locale.getDefault()
        context.wrapFactory = WrapFactory()
    }

    private inner class WrapFactory : org.mozilla.javascript.WrapFactory() {

        override fun wrap(cx: Context, scope: Scriptable, obj: Any?, staticType: Class<*>?): Any? = when {
            obj is KotlinBoxed -> runtime.bridges.toPrimitive(obj.get())
            obj is String -> runtime.bridges.toString(obj.toString())
            staticType == UiObjectCollection::class.java -> runtime.bridges.asArray(obj)
            else -> super.wrap(cx, scope, obj, staticType)
        }

        override fun wrapAsJavaObject(cx: Context?, scope: Scriptable, javaObject: Any?, staticType: Class<*>?): Scriptable? {
            return when (javaObject) {
                is View -> ViewExtras.getNativeView(scope, javaObject, staticType, runtime)
                else -> super.wrapAsJavaObject(cx, scope, javaObject, staticType)
            }
        }

    }

    companion object {

        const val SOURCE_NAME_INIT = "<init>"

        private const val LOG_TAG = "RhinoJavaScriptEngine"

        private const val MODULES_PATH = "modules"
        private var sInitScript: Script? = null
        private val sContextEngineMap = ConcurrentHashMap<Context, RhinoJavaScriptEngine>()

        fun getEngineOfContext(context: Context): RhinoJavaScriptEngine? {
            return sContextEngineMap[context]
        }

    }

}
