package org.autojs.autojs.runtime.api.augment.differential

import android.content.ContextWrapper
import android.content.res.Resources
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.WrapFactory
import org.mozilla.javascript.lc.type.TypeInfo
import sun.misc.Unsafe
import java.lang.reflect.Field
import android.content.Context as AndroidContext

/**
 * One Rhino [Context] with two independent top-level scopes: the "js" scope hosts the obsolete `__xxx__.js`
 * modules (see [ObsoleteModuleHost]) and the "kt" scope hosts the Kotlin augmentables, so the two
 * implementations never see each other's globals. Create it in `@Before` and [close] it in `@After`.
 *
 * zh-CN: 一个 Rhino Context 配两个互相独立的顶层作用域: "js" 侧加载旧 `__xxx__.js` 模块, "kt" 侧挂载 Kotlin 模块,
 * 两套实现互不可见. 在 `@Before` 创建, `@After` 调用 [close].
 */
internal class DifferentialRhino : AutoCloseable {

    val context: Context = Context.enter().apply {
        languageVersion = Context.VERSION_ES6
        isInterpretedMode = true
        wrapFactory = EngineWrapFactory()
    }

    val jsScope: TopLevelScope = newScope()
    val ktScope: TopLevelScope = newScope()

    /** A runtime whose top-level scope is [ktScope]; only the fields the augmentables touch are initialized. */
    val ktRuntime: ScriptRuntime = RhinoTestRuntime.create(ktScope)

    private val globalContextField: Field = GlobalAppContext::class.java.getDeclaredField("sApplicationContext").apply {
        isAccessible = true
    }
    private val previousGlobalContext: AndroidContext? = globalContextField.get(null) as? AndroidContext

    init {
        globalContextField.set(null, allocateWithoutConstructor(TestContext::class.java))
    }

    private fun newScope(): TopLevelScope = TopLevelScope().apply {
        initStandardObjects(context, false)
        with(RhinoUtils) { context.registerStandardObjects(this@apply) }
        // The engine registers `android` as a top-level package; a plain scope only knows java / org / com.
        ScriptableObject.putProperty(globalThis, "android", context.evaluateString(this, "Packages.android", "packages", 1, null))
    }

    fun evalJs(source: String): Any? = context.evaluateString(jsScope, source, "js-side", 1, null)

    fun evalKt(source: String): Any? = context.evaluateString(ktScope, source, "kt-side", 1, null)

    /** A rendered result: [key] is what the two sides are compared on, [detail] carries the error message. */
    data class Rendered(val key: String, val detail: String?) {
        override fun toString() = if (detail == null) key else "$key [$detail]"
    }

    /**
     * Evaluates [expression] on one side and renders the result as JSON (or `typeof` for functions and
     * `undefined`); a thrown error renders as `throws <ErrorName>` with the message as detail.
     *
     * The current Kotlin augmentables build their error message through `Context.getString`, which the plain
     * JVM android stubs refuse ("not mocked"), so every Kotlin failure would surface as that stub error. The
     * original exception is only printed to `System.err` by the wrapper, so it is captured here and used
     * instead: `throws <SimpleName>` for real failures, `unavailable(jvm) <SimpleName>` when the failure is a
     * missing Android class or static initializer or an Android method the mockable jar does not implement
     * (`not mocked`, for classes without a JVM fake in `app/src/test/java/android/`), which the differential
     * cases skip.
     */
    fun render(side: Side, expression: String): Rendered {
        val previousErr = System.err
        val captured = java.io.ByteArrayOutputStream()
        System.setErr(java.io.PrintStream(captured, true, "UTF-8"))
        val rendered = try {
            renderWithoutCapture(side, expression)
        } finally {
            System.setErr(previousErr)
        }
        val detail = rendered.detail
        if (detail?.contains("not mocked") != true) return rendered
        val firstLine = captured.toString("UTF-8").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        if (firstLine == null) {
            // Nothing printed an original exception, so the call that was not mocked is the failure itself, unless it is
            // the message lookup of a wrapper that does not print (a callable module): that one hides the real error.
            // zh-CN: 没有打印原始异常时, 未 mock 的调用本身就是失败原因; 但不打印的包装 (可调用模块) 在查消息时失败则掩盖了真实错误.
            return if (detail.contains("android.content.Context")) rendered else Rendered("unavailable(jvm) not mocked", detail)
        }
        val exceptionName = firstLine.substringBefore(':').substringAfterLast('.')
        val message = firstLine.substringAfter(':', "").trim().take(160).ifEmpty { null }
        return when {
            // A resource lookup is only made to build an error message: the call failed, its own message is lost.
            // zh-CN: 只有构造错误消息时才查资源: 调用确实失败了, 只是丢失了原本的消息.
            firstLine.contains("not mocked") && firstLine.contains("android.content.Context") -> Rendered("throws MessageLookup", message)
            firstLine.contains("not mocked") -> Rendered("unavailable(jvm) not mocked", message)
            exceptionName in UNAVAILABLE_EXCEPTIONS -> Rendered("unavailable(jvm) $exceptionName", message)
            else -> Rendered("throws $exceptionName", message)
        }
    }

    private fun renderWithoutCapture(side: Side, expression: String): Rendered {
        val wrapped = """
            (function () {
                try {
                    var v = ($expression);
                    if (v === undefined) return "undefined";
                    if (typeof v === "function") return "function";
                    if (typeof v === "number" && !isFinite(v)) return String(v);
                    return JSON.stringify(v);
                } catch (e) {
                    var name = (e && e.name) || Object.prototype.toString.call(e);
                    if (e && e.javaException) name = "JavaException " + e.javaException.getClass().getSimpleName();
                    return "throws " + name + "\u0000" + String(e && e.message !== undefined ? e.message : e);
                }
            })()
        """.trimIndent()
        return try {
            val text = Context.toString(if (side == Side.JS) evalJs(wrapped) else evalKt(wrapped))
            val separator = text.indexOf('\u0000')
            if (separator < 0) Rendered(text, null) else Rendered(text.substring(0, separator), text.substring(separator + 1).take(160))
        } catch (e: Throwable) {
            Rendered("throws(java) " + e.javaClass.simpleName, e.message?.take(160))
        }
    }

    override fun close() {
        globalContextField.set(null, previousGlobalContext)
        Context.exit()
    }

    enum class Side { JS, KT }

    /**
     * Mirrors the string handling of the engine's `AndroidContextFactory.WrapFactory`: a Java String returned to the
     * script becomes a JS string (the obsolete modules call string methods such as `replace` on `name()` results),
     * while boxed numbers stay Java objects as with the default `javaPrimitiveWrap`.
     */
    private class EngineWrapFactory : WrapFactory() {

        override fun wrap(cx: Context, scope: VarScope, obj: Any?, staticType: TypeInfo): Any? = when (obj) {
            is String -> obj
            else -> super.wrap(cx, scope, obj, staticType)
        }

    }

    /**
     * Stands in for the application context. `Context.getString` is final and delegates to [getResources],
     * so the resources are faked too: every string resource renders as `res:<id>` with its arguments, which is
     * enough for the error messages the augmentables build.
     */
    private class TestContext : ContextWrapper(null) {

        private val resources: Resources = allocateWithoutConstructor(TestResources::class.java)

        override fun getPackageName() = "org.autojs.autojs.test"

        override fun getResources(): Resources = resources

    }

    private class TestResources : Resources(null, null, null) {

        override fun getString(id: Int): String = "res:$id"

        override fun getString(id: Int, vararg formatArgs: Any?): String = "res:$id(${formatArgs.joinToString()})"

        override fun getText(id: Int): CharSequence = "res:$id"

        override fun getQuantityString(id: Int, quantity: Int): String = "res:$id[$quantity]"

        override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String = "res:$id[$quantity](${formatArgs.joinToString()})"

    }

    companion object {

        private val UNAVAILABLE_EXCEPTIONS = setOf("ExceptionInInitializerError", "NoClassDefFoundError", "UnsatisfiedLinkError")

        fun <T> allocateWithoutConstructor(type: Class<T>): T {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null) as Unsafe
            }
            @Suppress("UNCHECKED_CAST")
            return unsafe.allocateInstance(type) as T
        }

    }

}
