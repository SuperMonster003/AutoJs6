package org.autojs.autojs.rhino

import android.util.Log
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.pref.keys.ExtensibilityKeys
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils.isBackgroundThread
import org.autojs.autojs6.R
import org.mozilla.javascript.Context
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.lc.type.TypeInfo
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Stardust on Apr 5, 2017.
 */
open class AndroidContextFactory(private val cacheDirectory: File) : EventLoopContextFactory() {

    private val mContextCount = AtomicInteger()

    /** The WrapFactory of a new Context (setupContext); an engine installs its own while it is bound (AutoJsContext). */
    internal val wrapFactory = WrapFactory()

    /**
     * Rhino contexts entered through this factory and not yet released; read only, for the memory diagnostics
     * (docs/memory/ROADMAP.md T1 e, P2.1).
     * zh-CN: 经本工厂进入且尚未释放的 Rhino Context 数; 只读, 供内存诊断使用 (docs/memory/ROADMAP.md T1 e, P2.1).
     */
    val activeContextCount: Int
        get() = mContextCount.get()

    init {
        // The application class loader is created once per process and clears what earlier processes left in the cache directory.
        // zh-CN: 应用类加载器每个进程创建一次, 清理之前进程留在缓存目录中的文件.
        initApplicationClassLoader(AndroidClassLoader(AndroidContextFactory::class.java.classLoader!!, cacheDirectory))
    }

    /**
     * Create a ClassLoader which is able to deal with bytecode. Rhino creates one for every generated class (JavaAdapter);
     * it shares the cache directory and must not clear it (docs/memory/ROADMAP.md H26).
     * zh-CN: 创建能处理字节码的类加载器. Rhino 为每个生成类 (JavaAdapter) 创建一个; 它与其他加载共用缓存目录, 不能清空该目录 (docs/memory/ROADMAP.md H26).
     *
     * @param parent the parent of the creation classloader
     * @return a new ClassLoader
     */
    final override fun createClassLoader(parent: ClassLoader): AndroidClassLoader {
        return AndroidClassLoader(parent, cacheDirectory, clearCacheOnInit = false)
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
            isJavaPrimitiveWrap = ExtensibilityKeys.RHINO_JAVA_PRIMITIVE_WRAP.get()
        }

        override fun wrap(cx: Context, scope: VarScope, obj: Any?, staticType: TypeInfo): Any? = when {
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
