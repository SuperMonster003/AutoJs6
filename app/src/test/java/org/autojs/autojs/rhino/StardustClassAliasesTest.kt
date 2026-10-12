package org.autojs.autojs.rhino

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.Kit
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Guards the alias tables of [StardustClassAliases] (roadmap P7.3, scheme 3). The `com.stardust` copies are gone, so
 * the script-reachable API of every fully covered alias is read from `stardust-alias-snapshot.json` (recorded from
 * the compiled copies before their removal, with `com.stardust` types already mapped to host types) and each host
 * class must still offer those members, otherwise the alias would turn a working call into a silent "method not
 * found". The rules: static members, constructors a script could call and, for constructible or extensible types,
 * the instance members must exist on the host with script-compatible parameters (Java numbers are JS numbers,
 * `Object` accepts anything, a JS function satisfies `Object` and the Rhino value types, a parameter also accepts
 * subtypes); return types are never checked.
 *
 * zh-CN: 守护 [StardustClassAliases] 的别名表 (roadmap P7.3, 方案 3). `com.stardust` 副本已移除, 每个完整覆盖别名的脚本可触及
 * API 从 `stardust-alias-snapshot.json` 读取 (移除前由编译产物记录, `com.stardust` 类型已映射为宿主类型), 宿主类必须仍提供这些
 * 成员. 规则: 静态成员, 脚本可调用的构造器, 可构造或可继承类的实例成员须在宿主上有脚本可见兼容的对应项 (数值互换, Object 接受
 * 任何值, JS 函数满足 Object 与 Rhino 值类型, 参数接受子类型); 不比较返回类型.
 */
class StardustClassAliasesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val loader: ClassLoader = StardustClassAliasesTest::class.java.classLoader!!

    private val snapshot: JsonObject by lazy {
        val stream = StardustClassAliasesTest::class.java.getResourceAsStream("/stardust-alias-snapshot.json")
            ?: throw AssertionError("stardust-alias-snapshot.json is missing from the test resources")
        JsonParser.parseString(stream.bufferedReader().use { it.readText() }).asJsonObject
    }

    @Test
    fun `resolve maps aliased names and their nested classes only`() {
        assertEquals("org.autojs.autojs.pio.PFiles", StardustClassAliases.resolve("com.stardust.pio.PFiles"))
        assertEquals(
            "org.autojs.autojs.runtime.api.Dialogs\$NonUiDialogs",
            StardustClassAliases.resolve("com.stardust.autojs.runtime.api.Dialogs\$NonUiDialogs"),
        )
        assertEquals("org.autojs.autojs.core.automator.UiObject", StardustClassAliases.resolve("com.stardust.automator.UiObject"))
        assertEquals("org.autojs.autojs.util.ClipboardUtils", StardustClassAliases.resolve("com.stardust.util.ClipboardUtil"))
        assertNull(StardustClassAliases.resolve("com.stardust.pio.PFilesX"))
        assertNull(StardustClassAliases.resolve("com.stardust.util.Nath"))
        assertNull(StardustClassAliases.resolve("org.autojs.autojs.pio.PFiles"))
        assertNull(StardustClassAliases.resolve("com.stardust"))
    }

    @Test
    fun `every alias names a loadable host class and the tiers do not overlap`() {
        for ((compat, host) in StardustClassAliases.TABLE + StardustClassAliases.BEST_EFFORT) {
            assertTrue(compat, compat.startsWith("com.stardust."))
            assertTrue(host, host.startsWith("org.autojs.autojs."))
            load(host)
        }
        for ((compat, host) in StardustClassAliases.TABLE) {
            assertEquals("$compat -> $host", compat.substringAfterLast('.'), host.substringAfterLast('.'))
        }
        val overlap = StardustClassAliases.TABLE.keys intersect StardustClassAliases.BEST_EFFORT.keys
        assertTrue("overlapping entries: $overlap", overlap.isEmpty())
    }

    @Test
    fun `the snapshot covers exactly the fully covered tier`() {
        val recorded = snapshot.getAsJsonObject("aliases")
        assertEquals(StardustClassAliases.TABLE.keys, recorded.keySet())
        for ((compat, host) in StardustClassAliases.TABLE) {
            assertEquals(compat, host, recorded.get(compat).asString)
        }
    }

    @Test
    fun `android class loader serves the host class to Rhino lookups of an aliased name`() {
        val classLoader = AndroidClassLoader(loader, temporaryFolder.newFolder("classes"))
        // Kit.classOrNull is what NativeJavaPackage (Packages, importClass, importPackage) calls.
        assertSame(load("org.autojs.autojs.pio.PFile"), Kit.classOrNull(classLoader, "com.stardust.pio.PFile"))
        assertSame(
            load("org.autojs.autojs.runtime.api.Dialogs\$NonUiDialogs"),
            Kit.classOrNull(classLoader, "com.stardust.autojs.runtime.api.Dialogs\$NonUiDialogs"),
        )
        assertSame(load("org.autojs.autojs.core.automator.UiObject"), Kit.classOrNull(classLoader, "com.stardust.automator.UiObject"))
        // Names outside both tables no longer resolve at all: the copies are gone.
        assertNull(Kit.classOrNull(classLoader, "com.stardust.util.Nath"))
    }

    @Test
    fun `android class loader never aliases for callers other than Rhino`() {
        val classLoader = AndroidClassLoader(loader, temporaryFolder.newFolder("classes"))
        // A plain loadClass (what the VM and java.lang.Class.forName do) asks for the class by its JVM name, which
        // no longer exists; the alias must not leak into that path.
        assertThrows(ClassNotFoundException::class.java) { classLoader.loadClass("com.stardust.pio.PFile") }
        assertThrows(ClassNotFoundException::class.java) { Class.forName("com.stardust.pio.PFile", false, classLoader) }
    }

    @Test
    fun `host classes cover every member recorded for the fully covered tier`() {
        val problems = ArrayList<String>()
        val classes = snapshot.getAsJsonObject("classes")
        for ((compat, host) in StardustClassAliases.TABLE) {
            val recorded = classes.getAsJsonObject(compat)
            if (recorded == null) {
                problems += "$compat: not recorded in the snapshot"
                continue
            }
            compareClass(compat, recorded, load(host), problems)
        }
        if (problems.isNotEmpty()) {
            fail(problems.joinToString("\n", prefix = "${problems.size} member(s) reachable through an alias are not covered by the host:\n"))
        }
    }

    private fun load(name: String): Class<*> = Class.forName(name, false, loader)

    private fun compareClass(where: String, recorded: JsonObject, host: Class<*>, problems: MutableList<String>) {
        val constructible = recorded.get("constructible").asBoolean
        val staticOnly = recorded.get("staticOnly").asBoolean
        val hostConstructors = host.constructors.filter { Modifier.isPublic(it.modifiers) }
        val hostMethods = publicMethods(host)
        val hostFields = host.fields.filter { Modifier.isPublic(it.modifiers) }
        for (element in recorded.getAsJsonArray("members")) {
            val member = element.asJsonObject
            val kind = member.get("kind").asString
            val name = member.get("name").asString
            val isStatic = member.get("static").asBoolean
            val params = member.getAsJsonArray("params").map { it.asString }
            // Rhino's PropHolder overloads are engine internals, never called from a script.
            if (params.any { it.startsWith("org.mozilla.javascript.PropHolder") }) continue
            val shown = "$name(${params.joinToString()})"
            when (kind) {
                "ctor" -> {
                    if (hostConstructors.any { compatibleParameters(params, it.parameterTypes.map(::typeName)) }) continue
                    if (staticOnly && params.isEmpty()) continue
                    problems += "$where: constructor (${params.joinToString()}) has no host constructor"
                }
                "method" -> {
                    if (!isStatic && !constructible) continue
                    val match = hostMethods.firstOrNull { it.name == name && compatibleParameters(params, it.parameterTypes.map(::typeName)) }
                    when {
                        match == null -> problems += "$where: $shown is missing on the host"
                        isStatic && !Modifier.isStatic(match.modifiers) -> problems += "$where: $shown is static on the copy but not on the host"
                    }
                }
                "field" -> {
                    if (!isStatic && !constructible) continue
                    val match = hostFields.firstOrNull { it.name == name }
                    when {
                        match == null -> problems += "$where: field $name is missing on the host"
                        isStatic && !Modifier.isStatic(match.modifiers) -> problems += "$where: field $name is static on the copy but not on the host"
                    }
                }
            }
        }
        val nested = recorded.getAsJsonObject("nested")
        for (path in nested.keySet()) {
            // Nested classes of nested classes are recorded with `$`-joined names below the aliased class.
            val segments = path.split('$')
            if (segments.any { it in KOTLIN_SYNTHETIC_NESTED }) continue
            var hostNested: Class<*>? = host
            for (segment in segments) {
                hostNested = hostNested?.declaredClasses?.firstOrNull { it.simpleName == segment }
            }
            if (hostNested == null) {
                problems += "$where: nested class $path is missing on the host"
                continue
            }
            compareClass("$where\$$path", nested.getAsJsonObject(path), hostNested, problems)
        }
    }

    /** Bridge methods stay in: the erased `execute(ScriptSource)` of a generic engine is what a script reaches. */
    private fun publicMethods(type: Class<*>): List<Method> = type.methods.filter {
        Modifier.isPublic(it.modifiers) && it.declaringClass != Any::class.java && '$' !in it.name && it.name !in OBJECT_METHODS
    }

    private fun typeName(type: Class<*>): String = if (type.isArray) typeName(type.componentType) + "[]" else type.name

    private fun compatibleParameters(recorded: List<String>, host: List<String>): Boolean {
        return recorded.size == host.size && recorded.indices.all { compatibleParameter(recorded[it], host[it]) }
    }

    private fun compatibleParameter(recorded: String, host: String): Boolean = when {
        recorded == host || host == "java.lang.Object" -> true
        // A generic type variable of the copy (javap prints it as `T` or `T[]`) matched anything of the same shape.
        TYPE_VARIABLE.matches(recorded) && recorded.count { it == '[' } == host.count { it == '[' } -> true
        recorded in NUMERIC_TYPES && host in NUMERIC_TYPES -> true
        recorded in BOOLEAN_TYPES && host in BOOLEAN_TYPES -> true
        recorded in TEXT_TYPES && host in TEXT_TYPES -> true
        recorded == "java.lang.Object" && host in JS_VALUE_TYPES -> true
        else -> isSubtype(recorded, host)
    }

    /** A host parameter also accepts every subtype of its type (e.g. `core.opencv.Mat` for `org.opencv.core.Mat`). */
    private fun isSubtype(type: String, superType: String): Boolean {
        if (type.endsWith("[]") || superType.endsWith("[]")) return false
        return runCatching { load(superType).isAssignableFrom(load(type)) }.getOrDefault(false)
    }

    companion object {

        private val OBJECT_METHODS = setOf("equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait", "finalize", "clone")

        private val KOTLIN_SYNTHETIC_NESTED = setOf("Companion", "DefaultImpls", "WhenMappings")

        private val TYPE_VARIABLE = Regex("""^[A-Z]\d?(\[\])*$""")

        private val NUMERIC_TYPES = setOf(
            "byte", "short", "int", "long", "float", "double", "char",
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double",
            "java.lang.Character", "java.lang.Number",
        )

        private val BOOLEAN_TYPES = setOf("boolean", "java.lang.Boolean")

        private val TEXT_TYPES = setOf("java.lang.String", "java.lang.CharSequence")

        private val JS_VALUE_TYPES = setOf(
            "org.mozilla.javascript.Callable", "org.mozilla.javascript.Function", "org.mozilla.javascript.BaseFunction",
            "org.mozilla.javascript.NativeFunction", "org.mozilla.javascript.Scriptable", "org.mozilla.javascript.ScriptableObject",
            "org.mozilla.javascript.NativeObject", "org.mozilla.javascript.NativeArray",
        )
    }
}
