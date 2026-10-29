package org.autojs.autojs.core.plugin.scriptmodule

import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Installed
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.NotInstalled
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Reason
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModuleUsageScanner.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Roadmap P7.1 (design `docs/augment/P7-1-packaged-app-libraries-design.md` sections 2 / 4): which libraries of the
 * Bundled Libraries plugin a packaged app takes along, with the libraries they require, and why the others are left out.
 * The plugin files are stand-ins with the `require` calls of the 1.0.0 release.
 * zh-CN: ROADMAP P7.1 (设计 P7-1 第 2 / 4 节): 打包应用带上 Bundled Libraries 插件的哪些库 (含它们 require 的库), 以及其余的库为何没带.
 * 插件文件是替身, 保留 1.0.0 版本中的 `require` 调用.
 */
class PackagedScriptModulesTest {

    private val release = requireNotNull(ScriptModulePluginCatalog.parse(File(ScriptModulePluginStateTest.CATALOG).readText()))

    private val all = release.libraries.map { it.id }

    private val sources = linkedMapOf(
        "axios.js" to "require('axios/utils/index.js');",
        "axios/utils/index.js" to "require('modules/axios/browser-libs/utils.js');",
        "axios/browser-libs/utils.js" to "",
        "axios/browser-libs/XMLHttpRequest.js" to "require('stream');",
        "cheerio.js" to "require('parse5');",
        "dayjs.js" to "require('dayjs/dayjs.min.js');",
        "dayjs/dayjs.min.js" to "",
        "dayjs/../../evil.js" to "",
        "i18n.js" to "require('banana-i18n');",
        "banana-i18n.js" to "",
        "pangu.js" to "require('pangu.min.js');",
        "pangu.min.js" to "",
        "pangu.LICENSE.txt" to "require('lodash')",
        "lodash.js" to "",
        "buffer.js" to "",
        "events.js" to "",
        "fs.js" to "const Buffer = require('buffer').Buffer; const path = require('path');",
        "os.js" to "",
        "path.js" to "",
        "process.js" to "",
        "nodejs.js" to "const process = require('process'); const { Buffer } = require('buffer');",
        "promise.js" to "",
    )

    private val files = object : PackagedScriptModules.Files {
        override val paths = sources.keys
        override fun read(path: String) = sources[path]?.toByteArray()
    }

    private fun installed(authorized: Boolean = true, pluginEnabled: Boolean = true, enabled: Set<String> = all.toSet(), catalog: ScriptModulePluginCatalog? = release) =
        Installed(versionCode = 1, lastUpdateTime = 2, authorized = authorized, pluginEnabled = pluginEnabled, catalog = catalog, enabledLibraries = enabled)

    private fun modules(vararg ids: String) = Usage(ids.toSet(), emptySet())

    @Test
    fun copiesTheLibrariesTheScriptsUseWithTheLibrariesTheyRequire() {
        val plan = PackagedScriptModules.plan(installed(), Usage(setOf("node:fs", "dayjs", "aj6-nope-pkg"), setOf("pangu")), includeAll = false, files)
        assertEquals(listOf("dayjs", "pangu", "buffer", "fs", "path"), plan.copied)
        assertEquals(
            listOf("dayjs.js", "dayjs/dayjs.min.js", "pangu.LICENSE.txt", "pangu.js", "pangu.min.js", "buffer.js", "fs.js", "path.js"),
            plan.files.keys.toList(),
        )
        assertEquals(sources["fs.js"], plan.files.getValue("fs.js").decodeToString())
        assertEquals(emptyMap<String, Reason>(), plan.missing)
    }

    @Test
    fun followsInternalFilesAndLeavesUnknownNamesOut() {
        val axios = PackagedScriptModules.plan(installed(), modules("modules/axios/browser-libs/utils.js"), includeAll = false, files)
        assertEquals(listOf("axios"), axios.copied)
        assertEquals(listOf("axios.js", "axios/browser-libs/XMLHttpRequest.js", "axios/browser-libs/utils.js", "axios/utils/index.js"), axios.files.keys.toList())
        assertEquals(listOf("i18n"), PackagedScriptModules.plan(installed(), modules("banana-i18n"), includeAll = false, files).copied)
        assertEquals(listOf("cheerio"), PackagedScriptModules.plan(installed(), modules("cheerio"), includeAll = false, files).copied)
        assertEquals(listOf("buffer", "process", "nodejs"), PackagedScriptModules.plan(installed(), modules("nodejs"), includeAll = false, files).copied)
        assertEquals(PackagedScriptModules.Plan.EMPTY, PackagedScriptModules.plan(installed(), modules("promise", "./dayjs", "/sdcard/lodash.js"), includeAll = false, files))
    }

    @Test
    fun includesEveryLibraryWhenBundledLibrariesIsSelected() {
        val plan = PackagedScriptModules.plan(installed(), Usage.EMPTY, includeAll = true, files)
        assertEquals(all, plan.copied)
        assertEquals(sources.keys - setOf("dayjs/../../evil.js", "promise.js"), plan.files.keys)
        assertEquals(emptyMap<String, Reason>(), plan.missing)
    }

    @Test
    fun reportsTheLibrariesItCannotCopy() {
        val usage = Usage(setOf("lodash", "fs"), setOf("dayjs"))
        val libraryDisabled = PackagedScriptModules.plan(installed(enabled = all.toSet() - setOf("lodash", "path")), usage, includeAll = false, files)
        assertEquals(listOf("dayjs", "buffer", "fs"), libraryDisabled.copied)
        assertEquals(mapOf("lodash" to Reason.LIBRARY_DISABLED, "path" to Reason.LIBRARY_DISABLED), libraryDisabled.missing)
        listOf(
            installed(pluginEnabled = false) to Reason.PLUGIN_DISABLED,
            installed(authorized = false, pluginEnabled = false) to Reason.UNAUTHORIZED,
            installed(catalog = null) to Reason.INCOMPATIBLE,
            NotInstalled to Reason.NOT_INSTALLED,
        ).forEach { (state, reason) ->
            val plan = PackagedScriptModules.plan(state, usage, includeAll = false, if (state is Installed) files else null)
            assertEquals(reason.code, emptyList<String>(), plan.copied)
            assertEquals(reason.code, listOf("dayjs", "lodash", "fs").associateWith { reason }, plan.missing)
        }
        // The plugin APK cannot be read (it changed since the state was taken). zh-CN: 插件 APK 读不到 (状态读取后插件有变).
        assertEquals(mapOf("dayjs" to Reason.INCOMPATIBLE), PackagedScriptModules.plan(installed(), modules("dayjs"), includeAll = false, null).missing)
    }

    @Test
    fun namesTheLibrariesWithoutACatalog() {
        assertEquals(
            listOf("axios", "path", "i18n", "dayjs"),
            PackagedScriptModules.libraries(Usage(setOf("modules/axios/browser-libs/utils.js", "node:path", "./local", "banana-i18n", "promise"), setOf("dayjs")), null),
        )
        assertEquals(listOf("axios", "path", "i18n"), PackagedScriptModules.libraries(modules("modules/axios/x.js", "node:path", "banana-i18n", "promise"), release))
    }

    @Test
    fun unavailableReasonFollowsTheOrderOfLookup() {
        assertEquals(Reason.NOT_INSTALLED, NotInstalled.unavailableReason("dayjs"))
        assertEquals(Reason.INCOMPATIBLE, installed(catalog = null, authorized = false).unavailableReason("dayjs"))
        assertEquals(Reason.NOT_INSTALLED, installed(authorized = false).unavailableReason("promise"))
        assertEquals(Reason.UNAUTHORIZED, installed(authorized = false, pluginEnabled = false).unavailableReason("dayjs"))
        assertEquals(Reason.PLUGIN_DISABLED, installed(pluginEnabled = false, enabled = emptySet()).unavailableReason("dayjs"))
        assertEquals(Reason.LIBRARY_DISABLED, installed(enabled = setOf("lodash")).unavailableReason("dayjs"))
        assertNull(installed(enabled = setOf("dayjs")).unavailableReason("dayjs"))
    }

}
