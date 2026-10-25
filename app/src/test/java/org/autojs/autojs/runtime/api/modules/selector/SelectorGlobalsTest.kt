package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Freezes the set of `UiSelector` methods exported as global functions in
 * `src/test/resources/selector-global-functions.txt`: a change there is a change of the script
 * surface and has to be deliberate. Also checks the list against the compiled class both ways.
 * zh-CN: 把作为全局函数导出的 `UiSelector` 方法集合固化在 `src/test/resources/selector-global-functions.txt` 中: 那里的
 * 变化就是脚本表面的变化, 必须是有意为之. 同时双向核对该列表与编译后的类.
 */
class SelectorGlobalsTest {

    private val snapshot = "selector-global-functions.txt"

    @Test
    fun exportedNamesMatchTheSnapshot() {
        val resource = SelectorGlobalsTest::class.java.classLoader!!.getResource(snapshot) ?: run {
            fail("Missing test resource $snapshot")
            return
        }
        val expected: List<String> = resource.readText().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val actual = SelectorGlobals.names.sorted()
        if (actual != expected) {
            val rendered = File("build/selector-global-functions.txt").absoluteFile
            checkNotNull(rendered.parentFile).mkdirs()
            rendered.writeText(actual.joinToString("\n", postfix = "\n"))
            val added = actual - expected.toSet()
            val removed = expected - actual.toSet()
            fail("Exported selector globals changed (added $added, removed $removed); update src/test/resources/$snapshot from ${rendered.path} if intended")
        }
    }

    @Test
    fun everyExportedNameIsAPublicInstanceMethodOfUiSelector() {
        val methods = publicInstanceMethodNames()
        val missing = SelectorGlobals.names.filter { it !in methods }
        assertEquals(emptyList<String>(), missing)
        assertEquals(SelectorGlobals.names.size, SelectorGlobals.names.toSet().size)
    }

    @Test
    fun everyPublicInstanceMethodIsEitherExportedOrExcludedOnPurpose() {
        val exported = SelectorGlobals.names.toSet()
        val unaccounted = publicInstanceMethodNames().filter { it !in exported && it !in SelectorGlobals.excluded }
        assertEquals(emptyList<String>(), unaccounted)
        assertTrue(SelectorGlobals.excluded.none { it in exported })
    }

    /**
     * Public, non-static, non-mangled methods of [UiSelector] not inherited from [Any]. Bridges stay in:
     * Kotlin implements the [org.autojs.autojs.core.automator.UiObjectActions] defaults as bridge stubs
     * (`click()` etc.), and those are the very methods scripts call.
     * zh-CN: [UiSelector] 中 public, 非静态, 未混淆且不来自 [Any] 的方法. 桥接方法保留: Kotlin 以桥接存根实现
     * [org.autojs.autojs.core.automator.UiObjectActions] 的默认方法 (`click()` 等), 它们正是脚本调用的方法.
     */
    private fun publicInstanceMethodNames(): Set<String> = UiSelector::class.java.methods
        .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) }
        .filter { it.declaringClass != Any::class.java && !it.isSynthetic }
        .map { it.name }
        .filter { '$' !in it && it != "toString" && it != "equals" && it != "hashCode" }
        .toSet()

}
