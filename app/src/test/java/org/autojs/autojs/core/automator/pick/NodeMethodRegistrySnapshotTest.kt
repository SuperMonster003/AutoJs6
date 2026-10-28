package org.autojs.autojs.core.automator.pick

import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Method

/**
 * docs/selector/ROADMAP.md P4.2 / P4.3: freezes the node method registry ([NodeMethodRegistry.uiObject]) in
 * `src/test/resources/node-method-registry.txt`, one line per method: its kind (D11: `own` / `compat_getter`), name and
 * parameter types. `tools/selector/registry_export.py` renders from that file the method names the documentation lists
 * (`dataTypes.md`, `UiObjectInvokable`) and the result type unions of the declarations (`aj6-int-selector.d.ts`), so a
 * change of the registry fails here first; when it is intended, copy `build/node-method-registry.txt` over the resource
 * and run the script. The JVM sees the registry of the newest API: on API 24-29 the methods whose signature names a later
 * class are left out (appendix C Q16, `getExtraRenderingInfo`).
 *
 * zh-CN: P4.2 / P4.3: 把节点方法注册表固化在 `src/test/resources/node-method-registry.txt` 中, 每个方法一行: 分类 (D11: `own` / `compat_getter`),
 * 名称与参数类型. `tools/selector/registry_export.py` 由该文件生成文档列出的方法名 (`dataTypes.md` 的 `UiObjectInvokable`) 与声明中的结果类型联合
 * (`aj6-int-selector.d.ts`), 因此注册表的变化首先在此报出; 确属有意时, 以 `build/node-method-registry.txt` 覆盖该资源后运行脚本.
 * JVM 看到的是最新 API 的注册表: API 24-29 上签名含更高 API 类的方法不登记 (附录 C Q16, `getExtraRenderingInfo`).
 */
class NodeMethodRegistrySnapshotTest {

    private val snapshot = "node-method-registry.txt"

    @Test
    fun registryMatchesTheSnapshot() {
        val resource = NodeMethodRegistrySnapshotTest::class.java.classLoader!!.getResource(snapshot) ?: run {
            fail("Missing test resource $snapshot")
            return
        }
        val expected = resource.readText().lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        val actual = render()
        if (actual != expected) {
            val rendered = File("build/$snapshot").absoluteFile
            checkNotNull(rendered.parentFile).mkdirs()
            rendered.writeText((HEADER + actual).joinToString("\n", postfix = "\n"))
            fail("The node method registry changed (added ${actual - expected.toSet()}, removed ${expected - actual.toSet()}); if intended, update src/test/resources/$snapshot from ${rendered.path} and run tools/selector/registry_export.py")
        }
    }

    private companion object {

        val HEADER = listOf(
            "# NodeMethodRegistry.uiObject (docs/selector/ROADMAP.md D11, P4.2): <kind> <name>(<parameter types>), sorted.",
            "# Kept by NodeMethodRegistrySnapshotTest; read by tools/selector/registry_export.py.",
        )

        fun render(): List<String> = NodeMethodRegistry.uiObject.methods.values.flatten()
            .map { "${NodeMethodRegistry.kindOf(it).name.lowercase()} ${it.name}(${parameters(it)})" }
            .sorted()

        /** The parameter types by their Java names, the last one of a varargs method as `type...`. */
        fun parameters(method: Method): String = method.parameterTypes.mapIndexed { i, type ->
            if (method.isVarArgs && i == method.parameterCount - 1) "${type.componentType!!.typeName}..." else type.typeName
        }.joinToString(", ")

    }

}
