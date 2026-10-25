package org.autojs.autojs.runtime.api.modules

import com.google.gson.JsonParser
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.autojs.autojs.runtime.api.modules.generated.TestScriptModules
import org.autojs.autojs.runtime.api.modules.sample.FakeEmitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Roadmap P1.4 / appendix C Q8: [ScriptModuleLayer.ORDER] is the mounting order of every module of the old
 * `ScriptRuntime.augment()`. It must list each augment call once, in the order of the registration inventory
 * (`tools/augment/baseline/registrations.json`, extracted from `augment()` by `tools/api-coverage/extract_exports.py`),
 * its top-level names must keep the order of `Object.keys(global)` recorded on the device
 * (`surface-XQ-AT72-5586.txt`), and `ScriptRuntime.augmentLegacy` must dispatch exactly its top-level names. The
 * mounter binds sub-modules in this order too.
 */
class ScriptModuleOrderTest {

    private val order = ScriptModuleLayer.ORDER
    private val topLevel = ScriptModuleLayer.TOP_LEVEL_ORDER

    @Test
    fun everyAugmentCallIsListedOnceWithItsSubModulesAfterIt() {
        assertEquals(93, order.size)
        assertEquals("duplicates in ORDER", order.size, order.toSet().size)
        assertEquals(order.filter { '.' !in it }, topLevel)
        assertEquals(76, topLevel.size)
        var current = ""
        order.forEach { name ->
            if ('.' in name) assertEquals("$name must follow its parent", current, name.substringBefore('.')) else current = name
        }
    }

    @Test
    fun orderMatchesTheRegistrationInventory() {
        val modules = JsonParser.parseString(File(REGISTRATIONS).readText()).asJsonObject.getAsJsonArray("modules").map { it.asJsonObject }
        // global.Global and global.Legacy assign their members to the global object and have no module name.
        val names = modules.map { m ->
            m.get("module").asString.ifEmpty { m.get("class").asString.substringAfterLast('.').replaceFirstChar { it.lowercaseChar() } }
        }
        assertEquals(names, order)
    }

    @Test
    fun topLevelNamesKeepTheGlobalKeyOrderOfTheDeviceBaseline() {
        val keys = File(SURFACE).useLines { lines -> lines.first { it.startsWith(KEYS_ORDER) }.removePrefix(KEYS_ORDER).trim().split(' ') }
        assertTrue("baseline keys order is missing", keys.size >= 500)
        // Only "legacy" has no key of its own: it assigns functions to the global object. Modules added after the
        // baseline are not on the device list and must come last, in the order they were added.
        assertEquals(listOf("legacy") + ADDED_AFTER_BASELINE, topLevel.filter { it !in keys })
        assertEquals(ADDED_AFTER_BASELINE, topLevel.takeLast(ADDED_AFTER_BASELINE.size))
        assertEquals(keys.filter { it in topLevel }, topLevel.filter { it in keys })
    }

    @Test
    fun augmentLegacyDispatchesTheTopLevelNamesInOrder() {
        val source = File(SCRIPT_RUNTIME).readText()
        val body = source.substringAfter("private fun augmentLegacy(").substringBefore("else -> throw IllegalStateException(")
        val labels = Regex("^ {12}\"([^\"]+)\" ->", RegexOption.MULTILINE).findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(topLevel, labels)
        assertTrue("augment() must walk ScriptModuleLayer.ORDER", source.contains("ScriptModuleLayer.mountInOrder(modules) { name -> augmentLegacy(target, name) }"))
        assertTrue("the built-in objects must be extended through the layer", source.contains("ScriptModuleLayer.extendBuiltInsOr(scriptModules) { Jsox.extendAllRhinoWithRuntime(this) }"))
        assertTrue("initEpilogue() must use extendJsBuiltIns()", source.contains("if (ExtensibilityKeys.EXTENDING_JS_BUILD_IN_OBJECTS.get()) extendJsBuiltIns()"))
        val engine = File(JAVASCRIPT_ENGINE).readText()
        assertTrue("the execution mode jsox must use extendJsBuiltIns()", engine.contains("mRuntime.extendJsBuiltIns();") && !engine.contains("Jsox."))
    }

    @Test
    fun migratedModulesAreListed() {
        assertTrue(ScriptModuleLayer.MIGRATED.all { it in topLevel })
    }

    /**
     * P2.1: MIGRATED is the set of top-level modules of the host registry, and every registered module (sub-modules by
     * their path `parent.name`) has its place in ORDER, so a new @ScriptModule class is mounted where its old augment call
     * was. A migrated top-level module must bring all the sub-modules its old augment call mounted.
     */
    @Test
    fun migratedModulesComeFromTheHostRegistry() {
        val names = ScriptModules.bindings.associateBy { it.spec.className }
        fun path(binding: ScriptModuleBinding<*>): String = binding.spec.parent?.let { "${path(names.getValue(it))}.${binding.spec.name}" } ?: binding.spec.name
        assertEquals(ScriptModules.moduleNames.toSet(), ScriptModuleLayer.MIGRATED)
        val paths = ScriptModules.bindings.map(::path)
        assertEquals("registered modules missing from ORDER", emptyList<String>(), paths.filter { it !in order })
        val missingChildren = ScriptModuleLayer.MIGRATED.flatMap { top -> order.filter { it.startsWith("$top.") && it !in paths } }
        assertEquals("sub-modules of migrated modules that are not migrated", emptyList<String>(), missingChildren)
        assertTrue("base64" in ScriptModuleLayer.MIGRATED)
    }

    /** Sub-modules follow the order list; without one (or for paths it does not list) the registry order is kept. */
    @Test
    fun subModulesAreBoundInMountOrder() {
        fun childKeys(order: List<String>): String = DifferentialRhino().use { rhino ->
            val emitters = ScriptModuleMounter.EmitterFactory { _, scope -> ScriptModuleMounter.EmitterFactory.emitterObject(FakeEmitter(), scope) }
            ScriptModuleMounter(TestScriptModules, rhino.ktRuntime, rhino.ktScope, rhino.ktScope.globalThis, emitters, order = order).install("probe")
            rhino.evalKt("Object.keys(probe).filter(k => k === 'child' || k === 'sibling').join()").toString()
        }
        assertEquals("child,sibling", childKeys(emptyList()))
        assertEquals("sibling,child", childKeys(listOf("probe", "probe.sibling", "probe.child")))
        assertEquals("sibling,child", childKeys(listOf("probe", "probe.sibling")))
    }

    companion object {
        /** Unit tests run in the app module directory. */
        private const val REGISTRATIONS = "../tools/augment/baseline/registrations.json"
        private const val SURFACE = "../tools/augment/baseline/surface-XQ-AT72-5586.txt"
        private const val SCRIPT_RUNTIME = "src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt"
        private const val JAVASCRIPT_ENGINE = "src/main/java/org/autojs/autojs/engine/JavaScriptEngine.java"
        private const val KEYS_ORDER = "# keys order:"

        /** Top-level modules added after the device baseline 5586: `memory` (docs/memory/ROADMAP.md P5.1). */
        private val ADDED_AFTER_BASELINE = listOf("memory")
    }

}
