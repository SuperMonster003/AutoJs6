package org.autojs.autojs.core.automator.pick

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.JUnitCore

/**
 * Appendix C Q128 (docs/augment/ROADMAP.md): when Gradle ran `ScriptModuleArityTest` before `PickResultTypeTest` in one JVM
 * (it runs the classes that failed last time first), the arity samples had initialized `PickResultType` from one of its
 * objects and `everySpellingMapsToItsType` failed. The test classes run here in a fixed order on a fresh copy of the host
 * classes, so the outcome does not depend on what this JVM loaded before.
 * zh-CN: 附录 C Q128: Gradle 在同一 JVM 中先跑 ScriptModuleArityTest 再跑 PickResultTypeTest 时 (上次失败的类先跑), arity 样例已从某个对象
 * 初始化了 PickResultType, everySpellingMapsToItsType 失败. 此处在宿主类的新副本上按固定顺序运行测试类, 结果与本 JVM 先前加载过什么无关.
 */
class PickResultTypeInitOrderTest {

    @Test
    fun pickResultTypeTestPassesAfterScriptModuleArityTest() = assertPassInOrder(ARITY_TEST, PICK_RESULT_TYPE_TEST)

    @Test
    fun pickResultTypeTestPassesAfterAnObjectIsTouchedFirst() = SYMBOLIC_TYPES.forEach { first ->
        val loader = FreshHostLoader()
        Class.forName("${PickResultType::class.java.name}$$first", true, loader)
        assertPassInOrder(PICK_RESULT_TYPE_TEST, loader = loader, label = "$first first")
    }

    private fun assertPassInOrder(vararg names: String, loader: ClassLoader = FreshHostLoader(), label: String = names.joinToString(" -> ")) {
        val result = JUnitCore().run(*names.map { Class.forName(it, false, loader) }.toTypedArray())
        assertTrue("$label: " + result.failures.joinToString("; ") { "${it.testHeader}: ${it.message}" }, result.wasSuccessful())
    }

    /** Defines the classes of the host and its tests afresh; everything else comes from the JVM's loader. zh-CN: 重新定义宿主及其测试的类, 其余来自 JVM 的加载器. */
    private class FreshHostLoader : ClassLoader(PickResultTypeInitOrderTest::class.java.classLoader) {

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith(HOST_PACKAGE)) return super.loadClass(name, resolve)
            synchronized(this) {
                findLoadedClass(name)?.let { return it }
                val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")?.use { it.readBytes() } ?: throw ClassNotFoundException(name)
                return defineClass(name, bytes, 0, bytes.size)
            }
        }

    }

    private companion object {

        const val HOST_PACKAGE = "org.autojs.autojs."

        const val ARITY_TEST = "org.autojs.autojs.runtime.api.modules.ScriptModuleArityTest"

        val PICK_RESULT_TYPE_TEST: String = PickResultTypeTest::class.java.name

        val SYMBOLIC_TYPES = listOf("Widget", "WidgetCollection", "Widgets", "Content", "Contents", "Point", "Points", "Selector", "Existence")

    }

}
