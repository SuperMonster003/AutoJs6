package org.autojs.autojs.runtime.api.augment.compose

import com.google.gson.JsonParser
import org.autojs.autojs.engine.NodeBridgeSampleCatalog
import org.autojs.plugin.compose.api.ComposeUiIds
import org.junit.Assert.*
import org.junit.Test
import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.Context
import org.mozilla.javascript.Parser
import java.io.File

/** The public sample browser and manifest must expose the same executable Rhino sources. */
class ComposeExampleCatalogTest {
    private val app = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { if (it.name == "app") it else File(it, "app") }
        .first { File(it, "src/main/assets-app/indices/compose-examples.json").isFile }
    private val assets = File(app, "src/main/assets-app")
    private val manifest = JsonParser.parseString(File(assets, "indices/compose-examples.json").readText()).asJsonObject
    private val entries = manifest.getAsJsonArray("examples").map { it.asJsonObject }
    private val catalog = NodeBridgeSampleCatalog(
        lister = { path -> File(assets, path).list() },
        opener = { path -> File(assets, path).takeIf { it.isFile }?.inputStream() },
    )

    @Test fun theExistingSampleCatalogFindsEveryIndexedExampleWithoutAStaticCategoryRegistry() {
        assertEquals(1, manifest["schemaVersion"].asInt)
        assertEquals(ComposeUiIds.PLUGIN_PACKAGE_NAME, manifest["pluginPackage"].asString)
        assertEquals("sample/Compose UI", manifest["hostSamplePath"].asString)
        assertEquals("Compose UI", manifest["category"].asString)
        assertEquals(5, entries.size)
        val files = entries.map { it["file"].asString }
        assertEquals(files.size, files.toSet().size)
        assertEquals(entries.size, entries.map { it["id"].asString }.toSet().size)
        assertTrue(catalog.list().entries.any { it.name == "Compose UI" && it.isDirectory })
        val samples = catalog.list("Compose UI").entries
        assertEquals(files.sorted(), samples.map { it.name })
        assertTrue(samples.none { it.isDirectory })
        assertTrue("Keep the catalog manifest out of the executable sample directory", samples.all { it.name.endsWith(".js") })
    }

    @Test fun everyListedSourceParsesAsRhinoAndUsesItsDeclaredExecutionMode() {
        val uiDirective = Regex("^\\s*(['\"])ui\\1\\s*;")
        entries.forEach { entry ->
            val file = entry["file"].asString
            assertTrue("$file must be a simple relative JS name", Regex("[a-z][a-z0-9-]*\\.js").matches(file))
            val sample = catalog.read("Compose UI/$file")
            assertFalse("The browser must read the entire runnable example", sample.truncated)
            Parser(CompilerEnvirons().apply { languageVersion = Context.VERSION_ES6 }).parse(sample.content, file, 1)
            val mode = entry["mode"].asString
            assertTrue(mode in setOf("ui", "normal"))
            assertEquals("$file mode and leading directive", mode == "ui", uiDirective.containsMatchIn(sample.content))
            assertEquals("Only the normal floating HUD needs overlay permission", mode == "normal", entry["requiresOverlay"].asBoolean)
            assertTrue("$file states its plugin prerequisite", sample.content.contains("Compose UI 1.0.0 installed and enabled"))
            assertTrue("$file states its host prerequisite", sample.content.contains("versionCode >= ${manifest["minHostVersionCode"].asInt}"))
        }
    }
}
