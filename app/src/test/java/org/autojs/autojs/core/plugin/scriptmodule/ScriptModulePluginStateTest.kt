package org.autojs.autojs.core.plugin.scriptmodule

import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Installed
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Lookup
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.NotInstalled
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Roadmap P4.3 (design `docs/augment/P4-3-bundled-libraries-design.md` (a) / (c) / (d)): the catalog of the Bundled
 * Libraries plugin (the copy of its 1.0.0 `assets/script-modules.json` in the test resources pins the contract) and the
 * answer for a module id in the four states.
 * zh-CN: ROADMAP P4.3 (设计 P4-3 (a) / (c) / (d)): Bundled Libraries 插件的清单 (测试资源中的 1.0.0 `assets/script-modules.json` 副本固定契约), 以及四态下对模块 id 的回答.
 */
class ScriptModulePluginStateTest {

    private val release = requireNotNull(ScriptModulePluginCatalog.parse(File(CATALOG).readText()))

    private fun installed(
        authorized: Boolean = true,
        pluginEnabled: Boolean = true,
        enabled: Set<String> = release.libraries.map { it.id }.toSet(),
        catalog: ScriptModulePluginCatalog? = release,
    ) = Installed(versionCode = 1, lastUpdateTime = 2, authorized = authorized, pluginEnabled = pluginEnabled, catalog = catalog, enabledLibraries = enabled)

    @Test
    fun catalogOfTheFirstRelease() {
        assertEquals("modules", release.moduleRoot)
        assertEquals(
            listOf("axios", "cheerio", "dayjs", "i18n", "pangu", "lodash", "buffer", "events", "fs", "os", "path", "process", "nodejs"),
            release.libraries.map { it.id },
        )
        assertEquals(setOf("libraries", "node-shims"), release.libraries.map { it.group }.toSet())
        assertEquals("i18n", release.ownerOf("banana-i18n.js")?.id)
        assertEquals("axios", release.ownerOf("axios/browser-libs/utils.js")?.id)
        assertEquals("pangu", release.ownerOf("pangu.min.js")?.id)
        assertNull(release.ownerOf("promise.js"))
        assertEquals("modules/axios/utils/index.js", release.assetPath("axios/utils/index.js"))
    }

    /** The guidance names without a catalog are the module names of the first release, and the host still has their files (until P5.2). */
    @Test
    fun knownModulesMatchTheCatalogAndTheAssets() {
        val names = release.libraries.flatMap { library -> library.modules.keys.map { it to library.id } }.toMap()
        assertEquals(names, ScriptModulePluginState.KNOWN_MODULES)
        // P5.2 removes these files from the APK; drop this check then. zh-CN: P5.2 从 APK 删除这些文件, 届时删去此项检查.
        release.libraries.flatMap { it.files }.forEach { assertTrue(it, File(ASSETS, it.removeSuffix("/")).exists()) }
    }

    @Test
    fun normalizesIdsLikeTheAssetProvider() {
        assertEquals("axios.js", ScriptModulePluginCatalog.normalizeId("axios"))
        assertEquals("pangu.min.js", ScriptModulePluginCatalog.normalizeId("pangu.min.js"))
        assertEquals("axios/browser-libs/utils.js", ScriptModulePluginCatalog.normalizeId("modules/axios/browser-libs/utils.js"))
        assertEquals("axios/utils/index.js", ScriptModulePluginCatalog.normalizeId("axios\\utils\\index"))
        listOf("", "./axios", "../axios", "/axios", "\\axios", "https://x/axios", "file:///axios", "a/../axios", "a//b", ".").forEach {
            assertFalse(it, ScriptModulePluginCatalog.isNamedId(it))
        }
        assertTrue(ScriptModulePluginCatalog.isNamedId("node_modules_like/x"))
    }

    @Test
    fun rejectsCatalogsItDoesNotUnderstand() {
        listOf(
            """{"schemaVersion": 2, "moduleRoot": "modules", "libraries": []}""",
            """{"schemaVersion": 1, "moduleRoot": "../x", "libraries": []}""",
            """{"schemaVersion": 1, "moduleRoot": "modules"}""",
            """{"schemaVersion": 1, "moduleRoot": "modules", "libraries": [{"id": "a", "group": "g", "modules": {"a": "../a.js"}, "files": ["a.js"]}]}""",
            """{"schemaVersion": 1, "moduleRoot": "modules", "libraries": [{"id": "a", "group": "g", "modules": {"a": "a.js"}, "files": ["/a.js"]}]}""",
            """{"schemaVersion": 1, "moduleRoot": "modules", "libraries": [{"id": 1, "group": "g", "modules": {}, "files": []}]}""",
            "{bad",
        ).forEach { assertNull(it, ScriptModulePluginCatalog.parse(it)) }
    }

    @Test
    fun notInstalled() {
        assertEquals(Lookup.Unavailable("axios", Reason.NOT_INSTALLED), NotInstalled.lookup("axios"))
        assertEquals(Lookup.Unavailable("i18n", Reason.NOT_INSTALLED), NotInstalled.lookup("banana-i18n"))
        assertEquals(Lookup.Unavailable("path", Reason.NOT_INSTALLED), NotInstalled.lookup("path.js"))
        // Internal files and unknown names are left to the next provider. zh-CN: 内部文件与未知名称交给下一个 provider.
        listOf("modules/axios/browser-libs/utils.js", "aj6-nope-pkg", "./axios", "/sdcard/axios.js", "http://x/axios").forEach {
            assertEquals(it, Lookup.Unknown, NotInstalled.lookup(it))
        }
    }

    @Test
    fun installedAndEnabled() {
        val state = installed()
        listOf(
            "axios" to ("axios" to "axios.js"),
            "modules/axios/browser-libs/utils.js" to ("axios" to "axios/browser-libs/utils.js"),
            "pangu.min.js" to ("pangu" to "pangu.min.js"),
            "banana-i18n" to ("i18n" to "banana-i18n.js"),
            "path.js" to ("path" to "path.js"),
            "nodejs" to ("nodejs" to "nodejs.js"),
        ).forEach { (id, expected) ->
            val lookup = state.lookup(id) as? Lookup.Available
            assertNotNull(id, lookup)
            assertEquals(id, expected, lookup!!.libraryId to lookup.path)
            assertTrue(lookup.plugin === state)
        }
        assertEquals(Lookup.Unknown, state.lookup("aj6-nope-pkg"))
        assertEquals(Lookup.Unknown, state.lookup("promise"))
    }

    @Test
    fun disabledInThePluginCenterOrNotAuthorized() {
        assertEquals(Lookup.Unavailable("axios", Reason.PLUGIN_DISABLED), installed(pluginEnabled = false).lookup("axios"))
        assertEquals(Lookup.Unavailable("axios", Reason.PLUGIN_DISABLED), installed(pluginEnabled = false).lookup("modules/axios/utils/index.js"))
        assertEquals(Lookup.Unavailable("axios", Reason.UNAUTHORIZED), installed(authorized = false, pluginEnabled = false).lookup("axios"))
        assertEquals(Lookup.Unknown, installed(pluginEnabled = false).lookup("aj6-nope-pkg"))
    }

    @Test
    fun libraryDisabledOnThePluginPage() {
        val state = installed(enabled = release.libraries.map { it.id }.toSet() - setOf("axios", "buffer"))
        assertEquals(Lookup.Unavailable("axios", Reason.LIBRARY_DISABLED), state.lookup("axios"))
        assertEquals(Lookup.Unavailable("axios", Reason.LIBRARY_DISABLED), state.lookup("modules/axios/browser-libs/index.js"))
        assertEquals(Lookup.Unavailable("buffer", Reason.LIBRARY_DISABLED), state.lookup("buffer.js"))
        assertTrue(state.lookup("fs") is Lookup.Available)
        assertTrue(state.lookup("dayjs") is Lookup.Available)
    }

    @Test
    fun installedWithoutAReadableCatalog() {
        assertEquals(Lookup.Unavailable("dayjs", Reason.INCOMPATIBLE), installed(catalog = null).lookup("dayjs"))
        assertEquals(Lookup.Unknown, installed(catalog = null).lookup("aj6-nope-pkg"))
    }

    companion object {
        const val CATALOG = "src/test/resources/script-modules/bundled-libraries-1.0.0.json"
        const val ASSETS = "src/main/assets/modules"
    }

}
