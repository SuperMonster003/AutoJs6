package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.PluginSettingsProvider
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.archive.ImportPlan.SkipReason
import org.autojs.autojs.core.pref.archive.SettingsArchiveException.Reason
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest.PluginEntry
import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * docs/settings/ROADMAP.md P13.1: the plugin directories of a settings archive (D3): the manifest's `plugins` and
 * `plugins/<packageName>/settings.json` written and read back, their checks, the empty [PluginSettingsProvider] registry
 * of the first phase, and the import plan of plugin directories without a provider (`TARGET_MISSING`).
 *
 * zh-CN: ROADMAP P13.1: 设置归档的插件目录 (D3): manifest 的 `plugins` 与 `plugins/<包名>/settings.json` 的写读往返与校验,
 * 第一期为空的提供方注册表, 以及没有提供方的插件目录的导入计划 (`TARGET_MISSING`).
 */
class SettingsArchivePluginsTest : DomainKeysTestBase() {

    private val host = SettingsArchiveManifest.Host("org.autojs.autojs6", 5781, "6.8.0")

    private val device = SettingsArchiveManifest.Device("XQ-DQ72", 33)

    private val snapshot = SettingsSnapshot(listOf(SettingsSnapshot.Category("general", emptyMap())))

    private val sample = """{"format":"autojs6-plugin-settings","formatVersion":1,"settings":{"a":1}}"""

    private fun write(plugins: List<SettingsArchiveWriter.PluginFile>, data: List<SettingsArchiveWriter.DataFile> = emptyList()): ByteArray =
        ByteArrayOutputStream().also { SettingsArchiveWriter.write(it, snapshot, "t", host, device, data, plugins = plugins) }.toByteArray()

    private fun names(zip: ByteArray): List<String> = ArrayList<String>().apply {
        ZipInputStream(ByteArrayInputStream(zip)).use { input -> while (true) add((input.nextEntry ?: break).name) }
    }

    @Test
    fun pluginFilesReadBackWhatWasWritten() {
        val zip = write(
            listOf(SettingsArchiveWriter.PluginFile(SAMPLE, 3, sample), SettingsArchiveWriter.PluginFile(OTHER, 0, "{}")),
            listOf(SettingsArchiveWriter.DataFile("data_shortcuts", 1, "shortcuts.zip", byteArrayOf(1))),
        )
        // After the manifest, the categories and the data files, one directory per plugin.
        assertEquals(
            listOf("manifest.json", "categories/general.json", "data/shortcuts.zip", "plugins/$SAMPLE/settings.json", "plugins/$OTHER/settings.json"),
            names(zip),
        )
        val reader = SettingsArchiveReader({ ByteArrayInputStream(zip) })
        val manifest = reader.manifest()
        assertEquals(listOf(PluginEntry(SAMPLE, 3), PluginEntry(OTHER, 0)), manifest.plugins)
        assertEquals("plugins/$SAMPLE/settings.json", manifest.plugins[0].file)
        assertEquals(sample, reader.plugin(SAMPLE))
        assertEquals("{}", reader.plugin(OTHER))
        val root = SettingsJson.parse(manifest.toText()) as Map<*, *>
        assertEquals(listOf(mapOf("id" to SAMPLE, "entries" to 3), mapOf("id" to OTHER, "entries" to 0)), root["plugins"])
        assertEquals(manifest, SettingsArchiveManifest.fromText(manifest.toText()))

        // The first phase: an empty array and no plugins/ directory (appendix B).
        val empty = write(emptyList())
        assertTrue(names(empty).none { it.startsWith(SettingsArchive.PLUGINS_DIR) })
        assertEquals(emptyList<PluginEntry>(), SettingsArchiveReader({ ByteArrayInputStream(empty) }).manifest().plugins)
        assertEquals(Reason.MISSING_FILE, assertThrows(SettingsArchiveException::class.java) { SettingsArchiveReader({ ByteArrayInputStream(empty) }).plugin(SAMPLE) }.reason)
    }

    @Test
    fun pluginEntriesAndFilesAreChecked() {
        val valid = SettingsJson.parse(SettingsArchiveManifest("t", host, device, emptyList()).toText()) as Map<*, *>
        listOf<Any>(
            listOf(mapOf("id" to "../escape", "entries" to 1)),
            listOf(mapOf("id" to "sample", "entries" to 1)),
            listOf(mapOf("id" to "org.autojs/plugin", "entries" to 1)),
            listOf(mapOf("id" to SAMPLE)),
            listOf(mapOf("id" to SAMPLE, "entries" to -1)),
            listOf(mapOf("entries" to 1)),
            listOf(mapOf("id" to SAMPLE, "entries" to 1), mapOf("id" to SAMPLE, "entries" to 2)),
            mapOf("id" to SAMPLE, "entries" to 1),
        ).forEachIndexed { index, plugins ->
            val text = SettingsJson.stringify(LinkedHashMap<Any?, Any?>(valid).also { it["plugins"] = plugins })
            val e = assertThrows("$index: $text", SettingsArchiveException::class.java) { SettingsArchiveManifest.fromText(text) }
            assertEquals(text, Reason.INVALID_MANIFEST, e.reason)
        }
        // A manifest without plugins (an archive of P6.1 to P10) has none.
        val text = SettingsJson.stringify(LinkedHashMap<Any?, Any?>(valid).also { it.remove("plugins") })
        assertEquals(emptyList<PluginEntry>(), SettingsArchiveManifest.fromText(text).plugins)

        assertThrows(IllegalArgumentException::class.java) { SettingsArchive.pluginFile("../escape") }
        assertThrows(IllegalArgumentException::class.java) { SettingsArchiveReader({ ByteArrayInputStream(write(emptyList())) }).plugin("plugins") }
        assertThrows(IllegalArgumentException::class.java) { SettingsArchiveWriter.PluginFile("sample", 1, "{}") }
        assertThrows(IllegalArgumentException::class.java) { SettingsArchiveWriter.PluginFile(SAMPLE, -1, "{}") }
        assertThrows(IllegalArgumentException::class.java) {
            write(listOf(SettingsArchiveWriter.PluginFile(SAMPLE, 1, "{}"), SettingsArchiveWriter.PluginFile(SAMPLE, 2, "{}")))
        }
    }

    @Test
    fun theFirstPhaseRegistersNoPluginSettingsProvider() {
        assertEquals(emptyList<PluginSettingsProvider>(), SettingsRegistry.pluginSettingsProviders)
        assertNull(SettingsRegistry.pluginSettingsProvider(SAMPLE))
        // So every plugin directory is TARGET_MISSING, installed or not.
        val plan = SettingsImportPlanner.plan(
            emptyList(), emptyList(), ImportEnvironment(installedPlugins = setOf(SAMPLE)),
            plugins = listOf(PluginEntry(SAMPLE, 3), PluginEntry(OTHER, 1)),
        )
        assertEquals(emptyList<String>(), plan.plugins)
        assertEquals(
            listOf(
                ImportPlan.Skip("plugins/", SAMPLE, null, SkipReason.TARGET_MISSING, "no settings provider"),
                ImportPlan.Skip("plugins/", OTHER, null, SkipReason.TARGET_MISSING, "not installed"),
            ),
            plan.skips,
        )
        assertEquals(2, plan.skipCounts[SkipReason.TARGET_MISSING])
        assertEquals(emptyList<String>(), plan.categories)
    }

    @Test
    fun pluginDirectoriesWithoutAnInstalledProviderAreTargetMissing() {
        val provider = object : PluginSettingsProvider {
            override val packageName = SAMPLE
            override fun exportSettings() = PluginSettingsProvider.Exported("{}", 0)
            override fun importSettings(text: String) = PluginSettingsProvider.Imported(0, 0)
        }
        val absent = object : PluginSettingsProvider by provider {
            override val packageName = "org.autojs.plugin.absent"
        }
        val plan = SettingsImportPlanner.plan(
            emptyList(), emptyList(), ImportEnvironment(installedPlugins = setOf(SAMPLE, OTHER)),
            plugins = listOf(PluginEntry(SAMPLE, 3), PluginEntry(OTHER, 1), PluginEntry("org.autojs.plugin.absent", 2), PluginEntry(SAMPLE, 3)),
            providers = listOf(provider, absent),
        )
        // The installed plugin with a provider is handed its directory once; the others are skipped whole.
        assertEquals(listOf(SAMPLE), plan.plugins)
        assertEquals(
            listOf(
                ImportPlan.Skip(ImportPlan.PLUGINS, OTHER, null, SkipReason.TARGET_MISSING, "no settings provider"),
                ImportPlan.Skip(ImportPlan.PLUGINS, "org.autojs.plugin.absent", null, SkipReason.TARGET_MISSING, "not installed"),
            ),
            plan.skips,
        )
        assertEquals(emptyList<ImportPlan.Put>(), plan.puts)
        assertEquals(emptyList<ImportPlan.Remove>(), plan.removes)
        // A plan of settings categories only (P7) has no plugins.
        assertEquals(emptyList<String>(), ImportPlan(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()).plugins)
    }

    companion object {

        private const val SAMPLE = "org.autojs.plugin.sample"

        private const val OTHER = "org.autojs.plugin.other"
    }
}
