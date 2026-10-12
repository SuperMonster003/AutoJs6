package org.autojs.autojs.rhino

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.Kit
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Guards the alias table of [StardustClassAliases] (roadmap P7.3). For every aliased `com.stardust` class the host
 * class must still offer each member a script could reach through the name, otherwise the alias would turn a
 * working call into a silent "method not found". The rules mirror `tools/stardust/compare_alias_signatures.py`:
 *
 * - an alias only changes what the name resolves to, so the reachable members are the public static methods and
 *   fields, the public constructors a script could call (no parameter of the compat engine stack,
 *   `com.stardust.autojs.*`) and, for classes a script can construct or extend (abstract types), the public
 *   instance members;
 * - `com.stardust` parameter types are mapped to the host class at the analogous path (or the alias target);
 * - Rhino scripts cannot tell Java numbers, `Object` versus a JS function or `String` versus `CharSequence` apart,
 *   so such parameter differences count as compatible; return types are never checked.
 *
 * zh-CN: 守护 [StardustClassAliases] 的别名表 (roadmap P7.3). 每个别名类的宿主类必须仍提供脚本能通过类名触及的成员,
 * 否则别名会把原本可用的调用变成静默的 "方法不存在". 规则与 `tools/stardust/compare_alias_signatures.py` 一致: 只核对
 * public 静态成员, 脚本可调用的构造器 (参数不含 `com.stardust.autojs.*` 引擎类型) 以及可构造或可继承类的实例成员;
 * `com.stardust` 参数类型按类比路径或别名表映射到宿主类; 数值类型, Object 与 JS 函数, String 与 CharSequence 的差别对
 * Rhino 脚本不可见, 视为兼容; 不比较返回类型.
 */
class StardustClassAliasesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val loader: ClassLoader = StardustClassAliasesTest::class.java.classLoader!!

    @Test
    fun `resolve maps aliased names and their nested classes only`() {
        assertEquals("org.autojs.autojs.pio.PFiles", StardustClassAliases.resolve("com.stardust.pio.PFiles"))
        assertEquals(
            "org.autojs.autojs.runtime.api.Dialogs\$NonUiDialogs",
            StardustClassAliases.resolve("com.stardust.autojs.runtime.api.Dialogs\$NonUiDialogs"),
        )
        assertNull(StardustClassAliases.resolve("com.stardust.pio.PFilesX"))
        assertNull(StardustClassAliases.resolve("com.stardust.autojs.runtime.api.Device"))
        assertNull(StardustClassAliases.resolve("org.autojs.autojs.pio.PFiles"))
        assertNull(StardustClassAliases.resolve("com.stardust"))
    }

    @Test
    fun `every alias pairs a compat class with a host class of the same simple name`() {
        for ((compat, host) in StardustClassAliases.TABLE) {
            assertTrue(compat, compat.startsWith("com.stardust."))
            assertTrue(host, host.startsWith("org.autojs.autojs."))
            assertEquals("$compat -> $host", compat.substringAfterLast('.'), host.substringAfterLast('.'))
            load(compat)
            load(host)
        }
    }

    @Test
    fun `alias table matches the survey script`() {
        val script = File("../tools/stardust/compare_alias_signatures.py")
        assertTrue(script.path, script.isFile)
        val block = script.readText().substringAfter("ALIASES = {").substringBefore("\n}")
        val surveyed = Regex(""""([\w.]+)":\s*"([\w.]+)"""").findAll(block).associate { it.groupValues[1] to it.groupValues[2] }
        for ((compat, host) in StardustClassAliases.TABLE) {
            assertEquals("$compat is not surveyed by compare_alias_signatures.py", host, surveyed[compat])
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
        // Classes outside the table keep their copies (Device: the maintainer declined its static width / height).
        assertSame(load("com.stardust.autojs.runtime.api.Device"), Kit.classOrNull(classLoader, "com.stardust.autojs.runtime.api.Device"))
        assertSame(load("com.stardust.util.ScreenMetrics"), Kit.classOrNull(classLoader, "com.stardust.util.ScreenMetrics"))
    }

    @Test
    fun `android class loader keeps the copy for callers other than Rhino`() {
        val classLoader = AndroidClassLoader(loader, temporaryFolder.newFolder("classes"))
        // A plain loadClass (what the VM and java.lang.Class.forName do) must return the class that was asked for.
        assertSame(load("com.stardust.pio.PFile"), classLoader.loadClass("com.stardust.pio.PFile"))
        assertSame(load("com.stardust.pio.PFile"), Class.forName("com.stardust.pio.PFile", false, classLoader))
    }

    @Test
    fun `host classes cover every member a script could reach through an alias`() {
        val problems = ArrayList<String>()
        for ((compat, host) in StardustClassAliases.TABLE) {
            compareClasses(load(compat), load(host), problems)
        }
        if (problems.isNotEmpty()) {
            fail(problems.joinToString("\n", prefix = "${problems.size} member(s) reachable through an alias are not covered by the host:\n"))
        }
    }

    private fun load(name: String): Class<*> = Class.forName(name, false, loader)

    private fun exists(name: String): Boolean = runCatching { load(name) }.isSuccess

    private fun compareClasses(compat: Class<*>, host: Class<*>, problems: MutableList<String>) {
        val where = compat.name
        val compatConstructors = compat.constructors.filter { Modifier.isPublic(it.modifiers) }
        val scriptConstructors = compatConstructors.filter { ctor -> ctor.parameterTypes.none { it.name.startsWith(ENGINE_INTERNAL_PREFIX) } }
        val extensible = compat.isInterface || Modifier.isAbstract(compat.modifiers)
        val constructible = scriptConstructors.isNotEmpty() || extensible
        val compatMethods = publicMethods(compat)
        val compatFields = compat.fields.filter { Modifier.isPublic(it.modifiers) }
        val staticOnly = compatMethods.all { Modifier.isStatic(it.modifiers) } && compatFields.all { Modifier.isStatic(it.modifiers) }
        val hostConstructors = host.constructors.filter { Modifier.isPublic(it.modifiers) }
        val hostMethods = publicMethods(host)
        val hostFields = host.fields.filter { Modifier.isPublic(it.modifiers) }
        val companionMethods = host.declaredClasses.firstOrNull { it.simpleName == "Companion" }?.let(::publicMethods).orEmpty()

        for (constructor in compatConstructors) {
            val mapped = constructor.parameterTypes.map(::mapType)
            if (hostConstructors.any { compatibleParameters(mapped, it.parameterTypes.map(::typeName)) }) continue
            if (constructor.parameterTypes.any { it.name.startsWith(ENGINE_INTERNAL_PREFIX) }) continue
            if (staticOnly && mapped.isEmpty()) continue
            problems += "$where: constructor (${mapped.joinToString()}) has no host constructor"
        }
        for (method in compatMethods) {
            val isStatic = Modifier.isStatic(method.modifiers)
            if (!isStatic && !constructible) continue
            val mapped = method.parameterTypes.map(::mapType)
            val match = hostMethods.firstOrNull { it.name == method.name && compatibleParameters(mapped, it.parameterTypes.map(::typeName)) }
            when {
                match == null -> {
                    val companion = companionMethods.any { it.name == method.name && compatibleParameters(mapped, it.parameterTypes.map(::typeName)) }
                    problems += "$where: ${method.name}(${mapped.joinToString()}) is " +
                        if (companion) "only on the host companion (add @JvmStatic)" else "missing on the host"
                }
                isStatic && !Modifier.isStatic(match.modifiers) -> {
                    problems += "$where: ${method.name}(${mapped.joinToString()}) is static on the copy but not on the host"
                }
            }
        }
        for (field in compatFields) {
            val isStatic = Modifier.isStatic(field.modifiers)
            if (!isStatic && !constructible) continue
            val match = hostFields.firstOrNull { it.name == field.name }
            when {
                match == null -> problems += "$where: field ${field.name} is missing on the host"
                isStatic && !Modifier.isStatic(match.modifiers) -> problems += "$where: field ${field.name} is static on the copy but not on the host"
            }
        }
        for (nested in compat.declaredClasses.filter { Modifier.isPublic(it.modifiers) && !it.isAnonymousClass && !it.isSynthetic }) {
            val hostNested = host.declaredClasses.firstOrNull { it.simpleName == nested.simpleName }
            if (hostNested == null) {
                problems += "$where: nested class ${nested.simpleName} is missing on the host"
                continue
            }
            compareClasses(nested, hostNested, problems)
        }
    }

    private fun publicMethods(type: Class<*>): List<Method> = type.methods.filter {
        Modifier.isPublic(it.modifiers) && it.declaringClass != Any::class.java && !it.isSynthetic && !it.isBridge &&
            '$' !in it.name && it.name !in OBJECT_METHODS
    }

    private fun typeName(type: Class<*>): String = if (type.isArray) typeName(type.componentType) + "[]" else type.name

    /** Maps a `com.stardust` type to the host type a script would hold (alias target, analogous path or known move). */
    private fun mapType(type: Class<*>): String {
        if (type.isArray) return mapType(type.componentType) + "[]"
        val name = type.name
        if (!name.startsWith("com.stardust.")) return name
        val outer = name.substringBefore('$')
        val rest = name.substring(outer.length)
        val target = StardustClassAliases.TABLE[outer] ?: analogous(outer) ?: KNOWN_MOVES[outer] ?: return name
        return target + rest
    }

    private fun analogous(outer: String): String? {
        val suffix = when {
            outer.startsWith("com.stardust.autojs.") -> outer.removePrefix("com.stardust.autojs.")
            else -> outer.removePrefix("com.stardust.")
        }
        val candidate = "org.autojs.autojs.$suffix"
        return candidate.takeIf(::exists)
    }

    private fun compatibleParameters(compat: List<String>, host: List<String>): Boolean {
        return compat.size == host.size && compat.indices.all { compatibleParameter(compat[it], host[it]) }
    }

    private fun compatibleParameter(compat: String, host: String): Boolean = when {
        compat == host || host == "java.lang.Object" -> true
        compat in NUMERIC_TYPES && host in NUMERIC_TYPES -> true
        compat in BOOLEAN_TYPES && host in BOOLEAN_TYPES -> true
        compat in TEXT_TYPES && host in TEXT_TYPES -> true
        compat == "java.lang.Object" && host in JS_VALUE_TYPES -> true
        else -> false
    }

    companion object {

        private const val ENGINE_INTERNAL_PREFIX = "com.stardust.autojs."

        private val OBJECT_METHODS = setOf("equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait", "finalize", "clone")

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

        /** Compat types whose host counterpart lives at a different path than the analogous one. */
        private val KNOWN_MOVES = mapOf(
            "com.stardust.autojs.ScriptEngineService" to "org.autojs.autojs.engine.ScriptEngineService",
            "com.stardust.autojs.core.boardcast.BroadcastEmitter" to "org.autojs.autojs.core.broadcast.BroadcastEmitter",
            "com.stardust.autojs.core.util.ScriptPromiseAdapter" to "org.autojs.autojs.runtime.api.ScriptPromiseAdapter",
            "com.stardust.notification.Notification" to "org.autojs.autojs.core.notification.Notification",
            "com.stardust.util.Func1" to "org.autojs.autojs.tool.Func1",
            "com.stardust.util.ScreenMetrics" to "org.autojs.autojs.runtime.api.ScreenMetrics",
            "com.stardust.view.accessibility.AccessibilityNotificationObserver" to "org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver",
        )
    }
}
