package org.autojs.autojs.core.plugin.scriptmodule

import org.autojs.autojs.core.plugin.scriptmodule.ScriptModuleUsageScanner.Usage
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Roadmap P7.1 (design `docs/augment/P7-1-packaged-app-libraries-design.md` section 3): the module names and global
 * getters the APK builder finds in the scripts of a project.
 * zh-CN: ROADMAP P7.1 (设计 P7-1 第 3 节): 打包时在项目脚本中找到的模块名与全局 getter.
 */
class ScriptModuleUsageScannerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun findsTheStringLiteralsOfRequireAndImport() {
        val text = """
            const dayjs = require('dayjs');
            const _ = require ( "lodash" );
            const pangu = require(`pangu`);
            const fs = require('node:fs');
            import axios from 'axios';
            import { EventEmitter } from "events";
            import 'process';
            export * from 'path';
            const os = await import('os');
            const utils = require('modules/axios/browser-libs/utils.js');
        """.trimIndent()
        assertEquals(
            setOf("dayjs", "lodash", "pangu", "node:fs", "axios", "events", "process", "path", "os", "modules/axios/browser-libs/utils.js"),
            ScriptModuleUsageScanner.scan(text, globals = false).moduleIds,
        )
    }

    @Test
    fun skipsComputedNamesAndOtherCalls() {
        val text = """
            const a = require(name);
            const b = require('cheer' + 'io');
            const c = myrequire('dayjs');
            const d = require(`${'$'}{lib}`);
            const e = obj.import('lodash');
        """.trimIndent()
        assertEquals(Usage.EMPTY, ScriptModuleUsageScanner.scan(text, globals = false))
    }

    @Test
    fun findsTheGlobalGettersAsIdentifiers() {
        val text = """
            log(dayjs().format());
            let s = pangu.spacing('a');
            obj.axios = 1;
            let mycheerio = 2, i18n_ = 3, ${'$'}i18n = 4;
        """.trimIndent()
        assertEquals(setOf("dayjs", "pangu"), ScriptModuleUsageScanner.scan(text).globals)
        assertEquals(emptySet<String>(), ScriptModuleUsageScanner.scan(text, globals = false).globals)
    }

    @Test
    fun scansTheScriptsOfAProjectLikeTheArchive() {
        val root = temporaryFolder.newFolder("project")
        File(root, "main.js").writeText("require('dayjs');")
        File(root, "lib/util.ts").apply { parentFile!!.mkdirs() }.writeText("import _ from 'lodash';\ncheerio.load('');")
        File(root, "notes.txt").writeText("require('axios');")
        File(root, "excluded/skip.js").apply { parentFile!!.mkdirs() }.writeText("require('pangu');")
        File(root, "big.js").writeBytes(ByteArray((ScriptModuleUsageScanner.MAX_FILE_BYTES + 1).toInt()) { ' '.code.toByte() }.also {
            "require('os');".toByteArray().copyInto(it)
        })
        File(root, "encrypted.js").outputStream().use { stream ->
            EncryptedScriptFileHeader.writeHeader(stream)
            stream.write("require('path');".toByteArray())
        }
        val usage = ScriptModuleUsageScanner.scan(root, listOf(File(root, "excluded").canonicalFile))
        // A global name in a string counts too: the scan may take a library more, never one less.
        // zh-CN: 字符串中的全局名也计入: 扫描只会多带库, 不会少带.
        assertEquals(Usage(setOf("dayjs", "lodash"), setOf("dayjs", "cheerio")), usage)
        assertEquals(Usage(setOf("pangu"), setOf("pangu")), ScriptModuleUsageScanner.scan(File(root, "excluded/skip.js")))
    }

}
