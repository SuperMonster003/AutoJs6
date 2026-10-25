package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.util.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mozilla.javascript.regexp.NativeRegExp
import java.io.File
import java.lang.reflect.Modifier

/**
 * Guards the generated builders: the committed `UiSelectorBuilders.kt` must be what the generator
 * renders from the registry today, and the compiled `UiSelector` must expose exactly one public
 * method per registry overload with the declared parameter types.
 * zh-CN: 守护生成的 builder: 提交的 `UiSelectorBuilders.kt` 必须与生成器今天由注册表渲染的一致, 且编译后的 `UiSelector`
 * 必须为注册表的每个重载暴露恰好一个参数类型相符的 public 方法.
 */
class UiSelectorBuildersTest {

    @Test
    fun committedBuildersMatchTheRegistry() {
        val expected = SelectorBuilderGenerator.generate()
        val committed = File(moduleDir(), SelectorBuilderGenerator.RELATIVE_PATH)
        val actual = committed.takeIf { it.isFile }?.readText()?.replace("\r\n", "\n")
        if (actual != expected) {
            val rendered = File(moduleDir(), "build/generated-selector-builders/UiSelectorBuilders.kt")
            checkNotNull(rendered.parentFile).mkdirs()
            rendered.writeText(expected)
            fail(
                "${committed.path} does not match SelectorRegistry; copy the fresh rendering over it: ${rendered.path}",
            )
        }
    }

    @Test
    fun everyRegistryOverloadIsAPublicMethodOfUiSelector() {
        val methods = UiSelector::class.java.methods.filter { Modifier.isPublic(it.modifiers) && !it.isBridge && !it.isSynthetic }
        for (entry in SelectorRegistry.entries) {
            for (overload in entry.overloads) {
                val parameterTypes = overload.params.map { jvmType(it.type) }.toTypedArray()
                val matches = methods.filter { it.name == entry.name && it.parameterTypes.contentEquals(parameterTypes) }
                assertEquals("${entry.name}(${parameterTypes.joinToString { it.simpleName }})", 1, matches.size)
                assertEquals(entry.name, UiSelector::class.java, matches.single().returnType)
                assertEquals(entry.name, overload.isVararg, matches.single().isVarArgs)
            }
        }
    }

    @Test
    fun registryNamesAreUniqueAndOverloadsDistinct() {
        val names = SelectorRegistry.names
        assertEquals(names.size, names.toSet().size)
        for (entry in SelectorRegistry.entries) {
            val shapes = entry.overloads.map { overload -> overload.params.map { it.type } }
            assertEquals(entry.name, shapes.size, shapes.toSet().size)
            assertTrue(entry.name, entry.deprecatedFor == null || entry.deprecatedFor in SelectorRegistry)
        }
    }

    private fun jvmType(type: SelectorArgType): Class<*> = when (type) {
        SelectorArgType.STRING -> String::class.java
        SelectorArgType.REGEX -> NativeRegExp::class.java
        SelectorArgType.BOOLEAN -> Boolean::class.java
        SelectorArgType.INT -> Int::class.java
        SelectorArgType.DOUBLE -> Double::class.java
        SelectorArgType.APP -> App::class.java
        SelectorArgType.FILTER -> BooleanFilter.BooleanSupplier::class.java
        SelectorArgType.ACTIONS -> Array<Any>::class.java
        SelectorArgType.OPTIONS -> Map::class.java
        SelectorArgType.KEYS -> Array<String>::class.java
    }

    private fun moduleDir(): File {
        // Gradle runs unit tests with the module directory as the working directory; an IDE may use the project root.
        val cwd = File("").absoluteFile
        return if (File(cwd, "src/main/java").isDirectory) cwd else File(cwd, "app")
    }

}
