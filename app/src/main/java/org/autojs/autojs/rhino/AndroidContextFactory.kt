package org.autojs.autojs.rhino

import android.util.Log
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils.isBackgroundThread
import org.autojs.autojs6.R
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.lc.type.TypeInfo
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Stardust on Apr 5, 2017.
 */
open class AndroidContextFactory(private val cacheDirectory: File) : ContextFactory() {

    private val mContextCount = AtomicInteger()
    private val wrapFactory = WrapFactory()

    init {
        initApplicationClassLoader(createClassLoader(AndroidContextFactory::class.java.classLoader!!))
    }

    /**
     * Create a ClassLoader which is able to deal with bytecode
     *
     * @param parent the parent of the creation classloader
     * @return a new ClassLoader
     */
    final override fun createClassLoader(parent: ClassLoader): AndroidClassLoader {
        return AndroidClassLoader(parent, cacheDirectory)
    }

    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        if (Thread.currentThread().isInterrupted && isBackgroundThread()) {
            throw ScriptInterruptedException()
        }
    }

    override fun makeContext(): Context {
        val cx: Context = AutoJsContext(this)
        setupContext(cx)
        return cx
    }

    private fun setupContext(context: Context) {
        context.apply {
            instructionObserverThreshold = 10000
            isInterpretedMode = true
            languageVersion = Context.VERSION_ES6
            locale = Locale.getDefault()
            wrapFactory = this@AndroidContextFactory.wrapFactory
        }
    }

    override fun onContextCreated(cx: Context) {
        super.onContextCreated(cx)
        val i = mContextCount.incrementAndGet()
        Log.d(TAG, "onContextCreated: count = $i")
    }

    override fun onContextReleased(cx: Context) {
        super.onContextReleased(cx)
        val i = mContextCount.decrementAndGet()
        Log.d(TAG, "onContextReleased: count = $i")
    }

    open class WrapFactory : org.mozilla.javascript.WrapFactory() {

        init {
            isJavaPrimitiveWrap = Pref.getBoolean(R.string.key_rhino_java_primitive_wrap, R.bool.pref_rhino_java_primitive_wrap)
        }

        override fun wrap(cx: Context, scope: Scriptable, obj: Any?, staticType: TypeInfo): Any? = when {
            obj is String -> bridges.toString(obj)
            staticType.asClass() == UiObjectCollection::class.java -> (obj as? UiObjectCollection)?.let { bridges.asArray(it) } ?: UiObjectCollection.EMPTY
            else -> super.wrap(cx, scope, obj, staticType)
        }

    }

    companion object {

        private val TAG: String = AndroidContextFactory::class.java.simpleName
        private val bridges = ScriptBridges()

    }

}