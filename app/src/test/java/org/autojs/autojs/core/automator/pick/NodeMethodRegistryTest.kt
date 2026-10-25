package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.pick.NodeMethodRegistry.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * docs/selector/ROADMAP.md P1.2: the node method registry. The classification of D11 against `UiObject::class.java.methods`
 * (a method added to `UiObject` or `AccessibilityNodeInfoCompat` shows up here until it is classified), the names the
 * interception table of the former `Invoker` listed (each registered or excluded by D11, appendix C Q10), the lookup
 * order (`name`, then the getter `getName`), and the resolution and call of a [NodeMethodTable] on a class of its own
 * (overloads, conversions, NaN, varargs, the exceptions a call yields null for). Reflection only: no `UiObject` is built.
 *
 * zh-CN: P1.2: 节点方法注册表. D11 的分类与 `UiObject::class.java.methods` 对照 (`UiObject` 或 `AccessibilityNodeInfoCompat` 新增的方法在分类前会在此报出),
 * 原 `Invoker` 拦截表列出的名称 (各自在注册表中或被 D11 排除, 附录 C Q10), 查找顺序 (`name`, 再 getter `getName`), 以及 [NodeMethodTable] 在自有类上的
 * 判定与调用 (重载, 转换, NaN, 可变参数, 调用得到 null 的异常). 只用反射, 不构造 `UiObject`.
 */
class NodeMethodRegistryTest {

    private val registry = NodeMethodRegistry.uiObject

    @Test
    fun classificationOfUiObject() {
        val methods = UiObject::class.java.methods.toList()
        val byKind = methods.groupBy { NodeMethodRegistry.kindOf(it) }
        // Every kind occurs, and the registry holds exactly the registered kinds.
        // zh-CN: 每种分类都出现, 注册表恰好包含登记的分类.
        assertEquals(Kind.values().toSet(), byKind.keys)
        assertEquals(methods.filter { NodeMethodRegistry.kindOf(it).registered }.map(::key).toSet(), registry.methods.values.flatten().map(::key).toSet())
        assertTrue("${registry.names.size}", registry.names.size in 100..400)
        // Own methods are declared by the host, getters of the compat class take nothing and return something.
        // zh-CN: 自有方法由宿主声明, compat 类的 getter 无参且有返回值.
        byKind[Kind.OWN]!!.forEach { assertTrue(key(it), it.declaringClass.name.startsWith("org.autojs.")) }
        byKind[Kind.COMPAT_GETTER]!!.forEach { m -> assertTrue(key(m), m.parameterCount == 0 && m.returnType != Void.TYPE && listOf("get", "is", "can", "has").any { m.name.startsWith(it) }) }
        // Appendix C Q10 (2): the readers `canXxx` / `hasXxx` of the compat class are registered as its getters are.
        // zh-CN: 附录 C Q10 (2): compat 类的只读方法 `canXxx` / `hasXxx` 与其 getter 一样登记.
        assertEquals(setOf("canOpenPopup", "hasRequestInitialAccessibilityFocus"), byKind[Kind.COMPAT_GETTER]!!.map { it.name }.filter { it.startsWith("can") || it.startsWith("has") }.toSet())
        // The methods of the compat class the host overrides that D11 leaves out: a new override has to be classified here.
        // zh-CN: 宿主覆盖但 D11 排除的 compat 类方法: 新增的覆盖须在此分类.
        assertEquals(EXCLUDED_OVERRIDES, byKind[Kind.COMPAT]!!.filter { it.declaringClass.name.startsWith("org.autojs.") }.map(NodeMethodTable::signature).toSet())
        // No setter of the compat class is registered: the registered `set*` methods are the actions of the host.
        // zh-CN: compat 类的 setter 都不在注册表中: 注册的 `set*` 方法都是宿主的动作.
        assertEquals(setOf("setProgress", "setSelection", "setText"), registry.names.filter { it.startsWith("set") }.toSet())
        listOf("text", "desc", "bounds", "child", "parent", "compass", "click", "performAction", "getHintText", "isChecked", "getViewIdResourceName", "canOpenPopup", "hasRequestInitialAccessibilityFocus").forEach {
            assertTrue(it, it in registry.names)
        }
        listOf("recycle", "equals", "hashCode", "toString", "getClass", "getChild", "refresh", "unwrap", "setChecked", "findAccessibilityNodeInfosByText").forEach {
            assertTrue(it, it !in registry.names)
        }
        assertSame(Kind.RECYCLE, NodeMethodRegistry.kindOf(UiObject::class.java.getMethod("recycle")))
        assertSame(Kind.OBJECT, NodeMethodRegistry.kindOf(UiObject::class.java.getMethod("hashCode")))
        assertSame(Kind.COMPAT, NodeMethodRegistry.kindOf(UiObject::class.java.getMethod("setChecked", java.lang.Boolean.TYPE)))
    }

    /**
     * Appendix C Q10: the names of the former interception table, registered or excluded by D11 (the compat setters ...).
     * The readers Q10 (2) adds (`canOpenPopup` ...) are not among them: the 49 names keep their classification.
     */
    @Test
    fun formerInterceptedNames() {
        assertEquals(49, INTERCEPTED.size)
        val registered = INTERCEPTED.filter { registry.lookup(it).isNotEmpty() }.toSet()
        assertEquals(setOf("child", "compass", "performAction", "scrollTo", "setProgress", "setSelection", "setText"), registered)
        (INTERCEPTED - registered).forEach { name ->
            val methods = UiObject::class.java.methods.filter { it.name == name }
            assertTrue(name, methods.all { NodeMethodRegistry.kindOf(it) == Kind.COMPAT || NodeMethodRegistry.kindOf(it) == Kind.INTERNAL })
        }
    }

    /** A name finds the methods of that name, then those of its getter, as the former `Invoker` tried them. */
    @Test
    fun lookupOrder() {
        val methods = registry.methods.values.flatten()
        fun tried(name: String) = listOf(name, "get${name.replaceFirstChar { it.uppercase() }}").flatMap { n -> methods.filter { it.name == n } }.map(::key).toSet()
        val names = methods.flatMap { m ->
            val rest = m.name.removePrefix("get")
            listOf(m.name, rest, rest.replaceFirstChar { it.lowercase() }, rest.replaceFirstChar { it.uppercase() }, "get${m.name}", m.name.uppercase(), m.name.drop(1))
        } + listOf("nope", "_", "\$x", "1", "getget") + listOf(0x131, 0x17F, 0xDF, 0xE9, 0xD1).flatMap { listOf(Char(it) + "d", Char(it) + "ize", Char(it) + "ext") }
        val wrong = names.filter { it.isNotEmpty() }.distinct().filter { name -> registry.lookup(name).map(::key).toSet() != tried(name) }
        assertEquals(emptyList<String>(), wrong.take(20))
        listOf("text", "Text", "getText", "child", "hintText", "HintText", "click", "setText").forEach { assertTrue(it, registry.lookup(it).isNotEmpty()) }
        // `Child` found the compat `getChild(int)` through the former `Invoker`; D11 leaves it out (use `child`).
        // zh-CN: 原 `Invoker` 经 `Child` 找到 compat 的 `getChild(int)`; D11 排除了它 (改用 `child`).
        listOf("", "nope", "tEXT", "gettext", "Child").forEach { assertTrue(it, registry.lookup(it).isEmpty()) }
        assertEquals(listOf("text", "getText"), registry.lookup("text").map { it.name }.distinct())
        assertTrue("hintText" in registry.spellings && "viewIdResourceName" in registry.spellings && "getHintText" in registry.spellings)
    }

    /**
     * Appendix C Q16: on API 24-29 ART throws `NoClassDefFoundError` when it reads the return type of `getExtraRenderingInfo`
     * (`AccessibilityNodeInfo.ExtraRenderingInfo`, API 30), and every pickup / detect failed while building the registry.
     * HotSpot resolves the types while it lists the methods, so the readers here fail as ART does: for that return type,
     * and (`TypeNotPresentException`) for a parameter type. Those methods are left out, the rest of the registry is the same.
     * zh-CN: 附录 C Q16: API 24-29 的 ART 读取 `getExtraRenderingInfo` 的返回类型 (API 30 的类) 时抛 `NoClassDefFoundError`, 建注册表失败使全部 pickup / detect 出错.
     * HotSpot 在列出方法时即已解析类型, 因此此处的读取器像 ART 那样失败: 该返回类型, 以及参数类型 (`TypeNotPresentException`). 这些方法被排除, 注册表其余部分不变.
     */
    @Test
    fun signaturesNamingMissingClasses() {
        val keys = registry.methods.values.flatten().map(::key).toSet()
        val lackingReturn = object : NodeMethodRegistry.SignatureReader {
            override fun returnType(method: Method): Class<*> = method.returnType.also { if (it.name == EXTRA_RENDERING_INFO) throw NoClassDefFoundError(it.name.replace('.', '/')) }
        }
        val lost = UiObject::class.java.methods.filter { it.returnType.name == EXTRA_RENDERING_INFO }.map(::key).toSet()
        assertEquals(setOf("AccessibilityNodeInfoCompat.getExtraRenderingInfo():ExtraRenderingInfo"), lost)
        assertTrue(lost.all { it in keys })
        assertEquals(keys - lost, NodeMethodRegistry.tableOf(lackingReturn).methods.values.flatten().map(::key).toSet())
        val lackingParameter = object : NodeMethodRegistry.SignatureReader {
            override fun parameterTypes(method: Method): Array<Class<*>> = method.parameterTypes.also { types ->
                types.firstOrNull { it.name == "android.os.Bundle" }?.let { throw TypeNotPresentException(it.name, null) }
            }
        }
        assertEquals(keys, NodeMethodRegistry.tableOf(lackingParameter).methods.values.flatten().map(::key).toSet())
    }

    @Test
    fun resolutionOnUiObject() {
        assertEquals("child", found("child", 0.0).name)
        assertEquals("getHintText", found("hintText").name)
        assertEquals("performAction", found("performAction", 16.0).name)
        assertEquals("compass", found("compass", "p2c0").name)
        assertSame(NodeMethodResolution.UnknownName, registry.resolve("setTextSelection", listOf(1.0, 2.0)))
        assertSame(NodeMethodResolution.UnknownName, registry.resolve("nope", emptyList()))
        assertEquals(listOf("child(int)"), (registry.resolve("child", listOf("0")) as NodeMethodResolution.NoOverload).signatures)
        assertEquals("Cannot round NaN value.", (registry.resolve("child", listOf(Double.NaN)) as NodeMethodResolution.Rejected).reason)
        assertEquals("Invalid compass: x2", (registry.resolve("compass", listOf("x2")) as NodeMethodResolution.Rejected).reason)
        assertEquals("Invalid rest compass: c", (registry.resolve("compass", listOf("c")) as NodeMethodResolution.Rejected).reason)
        assertEquals("compass", found("compass", "%").name)
    }

    @Test
    fun tableOfItsOwn() {
        val table = NodeMethodTable(Sample::class.java, Sample::class.java.declaredMethods.filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic })
        val sample = Sample()
        fun call(name: String, vararg args: Any?) = (table.resolve(name, args.toList()) as NodeMethodResolution.Found).invoke(sample)
        assertEquals(listOf("a()", "a(int)"), table.lookup("a").map(NodeMethodTable::signature))
        assertEquals("a()", call("a"))
        assertEquals("a(2)", call("a", 2.0))
        assertEquals("a(2)", call("a", 2))
        assertEquals("Cannot round NaN value.", (table.resolve("a", listOf(Double.NaN)) as NodeMethodResolution.Rejected).reason)
        assertEquals(listOf("a()", "a(int)"), (table.resolve("a", listOf(1.0, 2.0)) as NodeMethodResolution.NoOverload).signatures)
        assertEquals("b", call("b"))
        assertEquals("b", call("B"))
        assertEquals("x|y", call("c", "x", "y"))
        assertEquals("", call("c"))
        assertEquals("sb", call("d", StringBuilder("sb")))
        assertEquals(listOf("a", "getB", "c", "d", "npe", "interrupted", "boom").sorted(), table.names.sorted())
        assertTrue("b" in table.spellings && "getB" in table.spellings)
        assertNull(call("npe"))
        assertNull(call("interrupted"))
        try {
            call("boom")
            fail()
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
    }

    private fun found(name: String, vararg args: Any?): Method = (registry.resolve(name, args.toList()) as NodeMethodResolution.Found).method

    private fun key(method: Method) = "${method.declaringClass.simpleName}.${NodeMethodTable.signature(method)}:${method.returnType.simpleName}"

    @Suppress("unused", "FunctionName")
    class Sample {
        fun a(): String = "a()"
        fun a(i: Int): String = "a($i)"
        fun getB(): String = "b"
        fun c(vararg s: String): String = s.joinToString("|")
        fun d(s: String): String = s
        fun npe(): String = throw NullPointerException()
        fun interrupted(): String = throw InterruptedException()
        fun boom(): String = throw IllegalStateException("boom")
    }

    private companion object {

        /** A class of API 30 (appendix C Q16). zh-CN: API 30 的类. */
        const val EXTRA_RENDERING_INFO = "android.view.accessibility.AccessibilityNodeInfo\$ExtraRenderingInfo"

        /**
         * The methods of `AccessibilityNodeInfoCompat` that `UiObject` overrides, left out by D11: those with parameters,
         * and `refresh()` (overridden by P2.1 to drop the cached bounds).
         */
        val EXCLUDED_OVERRIDES = setOf(
            "getChild(int)", "findAccessibilityNodeInfosByText(String)", "findAccessibilityNodeInfosByViewId(String)",
            "performAction(int)", "performAction(int, Bundle)", "refresh()",
        )

        /** The keys of `Invoker.Interceptor.interceptedMap` before P1.2 (by argument types: int, CharSequence, String, boolean, float, int x 2). */
        val INTERCEPTED = setOf(
            "child", "getChild", "performAction", "setDrawingOrder", "setInputType", "setLiveRegion", "setMaxTextLength", "setMovementGranularities",
            "compass",
            "findAccessibilityNodeInfosByText", "findAccessibilityNodeInfosByViewId", "setClassName", "setContentDescription", "setError", "setHintText",
            "setPackageName", "setPaneTitle", "setRoleDescription", "setStateDescription", "setText", "setTooltipText", "setViewIdResourceName",
            "setAccessibilityFocused", "setCanOpenPopup", "setCheckable", "setChecked", "setClickable", "setEditable", "setEnabled", "setFocusable",
            "setFocused", "setHeading", "setImportantForAccessibility", "setLongClickable", "setMultiLine", "setPassword", "setScreenReaderFocusable",
            "setScrollable", "setSelected", "setShowingHintText", "setTextEntryKey", "setVisibleToUser", "setContentInvalid", "setContextClickable",
            "setDismissable",
            "setProgress",
            "setTextSelection", "setSelection", "scrollTo",
        )
    }

}
