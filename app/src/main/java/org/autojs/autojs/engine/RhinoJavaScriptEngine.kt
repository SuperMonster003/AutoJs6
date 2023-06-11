package org.autojs.autojs.engine

import android.util.Log
import android.view.View
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.ui.ViewExtras
import org.autojs.autojs.engine.module.AssetAndUrlModuleSourceProvider
import org.autojs.autojs.engine.module.ScopeRequire
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

    lateinit var thread: Thread
        private set

    private val mScriptable: TopLevelScope
    private val mInitScript: Script
        get() {
            return sInitScript ?: try {
                val reader = InputStreamReader(mAndroidContext.assets.open(SOURCE_FILE_INIT))
                context.compileReader(reader, SOURCE_NAME_INIT, 1, null).also {
                    sInitScript = it
                }
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
        try {
            val reader = preprocess(source.nonNullScriptReader)
            val script = context.compileReader(reader, source.fullPath, 1, null)
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
        return (getTag(ExecutionConfig.tag) as ExecutionConfig? ?: return false).scriptConfig.hasFeature(feature)
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
        sContextEngineMap.remove(context)
        Context.exit()
    }

    override fun init() {
        thread = Thread.currentThread()
        ScriptableObject.putProperty(mScriptable, "__engine__", this)
        initRequireBuilder(context, mScriptable)
        try {
            context.executeScriptWithContinuations(mInitScript, mScriptable)
        } catch (e: IllegalArgumentException) {
            if (e.message?.contains("Script argument was not a script or was not created by interpreted mode") == true) {
                mInitScript.exec(context, mScriptable)
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
        ScopeRequire(context, scope,SoftCachingModuleScriptProvider(provider)).install(scope)
    }

    protected fun createScope(context: Context): TopLevelScope {
        return TopLevelScope().apply {
            initStandardObjects(context, false)
        }
    }

    fun enterContext(): Context {
        return RhinoAndroidHelper(mAndroidContext).enterContext().also {
            it.apply {
                optimizationLevel = -1
                languageVersion = Context.VERSION_ES6
                locale = Locale.getDefault()
                wrapFactory = WrapFactory()
            }
            sContextEngineMap[it] = this
        }
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
                is View -> ViewExtras.getNativeView(scope, /* view = */ javaObject, staticType, runtime)
                else -> super.wrapAsJavaObject(cx, scope, javaObject, staticType)
            }
        }

    }

    companion object {

        const val SOURCE_FILE_INIT = "init.js"
        const val SOURCE_NAME_INIT = "<init>"

        private const val MODULES_PATH = "modules"

        private var sInitScript: Script? = null
        private val sContextEngineMap = ConcurrentHashMap<Context, RhinoJavaScriptEngine>()
        private val TAG = RhinoJavaScriptEngine::class.java.simpleName

        fun getEngineOfContext(context: Context) = sContextEngineMap[context]

    }

}
