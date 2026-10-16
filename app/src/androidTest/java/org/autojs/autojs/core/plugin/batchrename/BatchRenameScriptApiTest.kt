package org.autojs.autojs.core.plugin.batchrename

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The `files.batchRename` script API end to end (plugin roadmap P3.2): scripts run through the production Rhino engine
 * and write what they saw to a marker file. With a Batch Rename plugin installed (a signed release build covers R8 as
 * well) the script plans, excludes an item, applies with progress, undoes, reads the history, hits the error codes and
 * repeats the round trip with the `Async` forms; without one it sees `isAvailable() === false` and
 * `PLUGIN_UNAVAILABLE`. Each test skips itself when the device is in the other state.
 *
 * ```
 * adb shell am instrument -w -e class org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptApiTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * zh-CN: `files.batchRename` 脚本 API 端到端测试 (插件路线图 P3.2): 脚本经正式 Rhino 引擎运行, 把所见写入标记文件. 安装了 Batch Rename
 * 插件时 (签名 release 构建可同时覆盖 R8), 脚本生成方案, 排除一项, 带进度执行, 撤销, 读取历史, 触发各错误码, 并用 `Async` 形态重复
 * 往返; 未安装时应看到 `isAvailable() === false` 与 `PLUGIN_UNAVAILABLE`. 设备处于另一种状态时相应用例自行跳过.
 */
class BatchRenameScriptApiTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val root by lazy { File(context.cacheDir, "batch-rename-script-${System.nanoTime()}").apply { assertTrue(mkdirs()) } }
    private val journalFiles = ArrayList<File>()

    @After
    fun cleanUp() {
        root.deleteRecursively()
        journalFiles.forEach(File::delete)
    }

    @Test
    fun scriptsPlanApplyUndoAndReadTheHistoryThroughThePlugin() {
        assumeTrue("No Batch Rename plugin is installed", BatchRenamePluginHost.queryServiceCount(context) > 0)
        File(root, "album").mkdirs()
        listOf("IMG_1.jpg", "IMG_2.jpg", "IMG_3.jpg", "notes.txt").forEach { File(root, "album/$it").writeText(it) }
        File(root, "a.txt").writeText("a")
        File(root, "b.txt").writeText("b")

        val result = runScript(
            """
            var result = {};
            result.available = files.batchRename.isAvailable();
            result.sameObject = ${'$'}files.batchRename === files.batchRename;

            var plan = files.batchRename.plan("album", { search: /^IMG_(\d+)/, replace: "Photo-$1", includeFolders: false });
            result.rejected = plan.rejected;
            result.planCounts = plan.counts;
            result.planItems = plan.items.map(function (it) { return [it.name, it.newName, it.status, it.kind, it.depth]; });
            result.searchOption = plan.options.search + " " + plan.options.regex + " " + plan.options.caseSensitive;

            plan.items[2].excluded = true;
            var progress = [];
            var applied = files.batchRename.apply(plan, { onProgress: function (done, total, item) { progress.push([done, total, item.status]); } });
            result.appliedCounts = applied.counts;
            result.appliedStatuses = applied.items.map(function (it) { return it.status; });
            result.undoable = applied.undoable;
            result.progress = progress;
            result.listing = files.listDir("album").sort();
            result.batchIds = [applied.batchId];
            try { files.batchRename.apply(plan); } catch (e) { result.staleCode = e.code; result.isBatchRenameError = e instanceof files.batchRename.BatchRenameError; }

            result.historyFirst = files.batchRename.history(1)[0].batchId === applied.batchId;
            var undone = files.batchRename.undo(applied);
            result.undoState = undone.state;
            result.undoCounts = undone.counts;
            result.restoredListing = files.listDir("album").sort();
            try { files.batchRename.undo(applied.batchId); } catch (e) { result.undoAgainCode = e.code; }

            try { files.batchRename(["a.txt"], { search: "a", replace: "b" }); } catch (e) { result.rejectedCode = e.code; }
            try { files.batchRename.plan(".", { search: "a", target: "stem" }); } catch (e) { result.invalidOptionsCode = e.code; }
            try { files.batchRename.plan("missing.txt", { search: "a" }); } catch (e) { result.invalidPathsCode = e.code; }
            try { files.batchRename.plan("a.txt", { search: "(", regex: true }); } catch (e) { result.badRegexCode = e.code; }
            try {
                files.batchRename(["a.txt"], { search: "a", replace: "z", onProgress: function () { throw new Error("from the callback"); } });
            } catch (e) {
                result.callbackError = e.message;
                result.callbackRenamed = files.exists("z.txt");
                result.batchIds.push(files.batchRename.history(1)[0].batchId);
            }

            files.batchRename.planAsync("z.txt", { search: "z", replace: "c" }).then(function (p) {
                return files.batchRename.applyAsync(p, { onProgress: function (done, total) { result.asyncProgress = [done, total]; } });
            }).then(function (r) {
                result.asyncRenamed = r.counts.renamed;
                result.asyncExists = files.exists("c.txt");
                result.batchIds.push(r.batchId);
                return files.batchRename.undoAsync(r.batchId);
            }).then(function (u) {
                result.asyncUndo = u.state;
                return files.batchRename.planAsync("missing.txt", { search: "a" });
            }).catch(function (e) {
                result.asyncRejectedCode = e.code;
            }).then(function () {
                files.write(MARKER, JSON.stringify(result));
            });
            """,
        )
        result.optJSONArray("batchIds")?.let { ids ->
            (0 until ids.length()).forEach { journalFiles += File(context.noBackupFilesDir, "batch-rename/journals/${ids.getString(it)}.json") }
        }

        assertTrue(result.getBoolean("available"))
        assertTrue(result.getBoolean("sameObject"))
        assertFalse(result.getBoolean("rejected"))
        assertEquals("""{"rename":3,"unchanged":1,"conflict":0,"invalid":0,"excluded":1}""", result.getJSONObject("planCounts").toString())
        assertEquals(
            """[["album","album","excluded","dir",0],["IMG_1.jpg","Photo-1.jpg","rename","file",1],["IMG_2.jpg","Photo-2.jpg","rename","file",1],["IMG_3.jpg","Photo-3.jpg","rename","file",1],["notes.txt","notes.txt","unchanged","file",1]]""",
            result.getJSONArray("planItems").toString(),
        )
        assertEquals("^IMG_(\\d+) true true", result.getString("searchOption"))
        assertEquals("""{"renamed":2,"skipped":1,"failed":0,"stale":0,"excluded":2}""", result.getJSONObject("appliedCounts").toString())
        assertEquals("""["excluded","renamed","excluded","renamed","skipped"]""", result.getJSONArray("appliedStatuses").toString())
        assertTrue(result.getBoolean("undoable"))
        assertEquals("""[[1,2,"renamed"],[2,2,"renamed"]]""", result.getJSONArray("progress").toString())
        assertEquals("""["IMG_2.jpg","Photo-1.jpg","Photo-3.jpg","notes.txt"]""", result.getJSONArray("listing").toString())
        assertEquals(BatchRenameScriptCodes.PLAN_STALE, result.getString("staleCode"))
        assertTrue(result.getBoolean("isBatchRenameError"))
        assertTrue(result.getBoolean("historyFirst"))
        assertEquals("restored", result.getString("undoState"))
        assertEquals("""{"restored":2,"stale":0,"failed":0}""", result.getJSONObject("undoCounts").toString())
        assertEquals("""["IMG_1.jpg","IMG_2.jpg","IMG_3.jpg","notes.txt"]""", result.getJSONArray("restoredListing").toString())
        assertEquals(BatchRenameScriptCodes.UNDO_UNAVAILABLE, result.getString("undoAgainCode"))
        assertEquals(BatchRenameScriptCodes.PLAN_REJECTED, result.getString("rejectedCode"))
        assertEquals(BatchRenameScriptCodes.INVALID_OPTIONS, result.getString("invalidOptionsCode"))
        assertEquals(BatchRenameScriptCodes.INVALID_PATHS, result.getString("invalidPathsCode"))
        assertEquals(BatchRenameScriptCodes.INVALID_OPTIONS, result.getString("badRegexCode"))
        // A throwing callback does not stop the batch: the rename happened and was journaled.
        assertTrue(result.getString("callbackError"), result.getString("callbackError").contains("from the callback"))
        assertTrue(result.getBoolean("callbackRenamed"))
        assertEquals("""[1,1]""", result.getJSONArray("asyncProgress").toString())
        assertEquals(1, result.getInt("asyncRenamed"))
        assertTrue(result.getBoolean("asyncExists"))
        assertEquals("restored", result.getString("asyncUndo"))
        assertEquals(BatchRenameScriptCodes.INVALID_PATHS, result.getString("asyncRejectedCode"))
        assertTrue(File(root, "z.txt").exists())
    }

    /** Pinyin options and the `${pinyin}` variable from a script (plugin roadmap P6.1, plugin 1.1.0). zh-CN: 脚本中的拼音选项与变量. */
    @Test
    fun scriptsConvertChineseNamesToPinyin() {
        val features = runBlocking { BatchRenamePluginHost.discover(context) }.flatMap { BatchRenamePluginHost.featuresOf(it.pluginInfo) }
        assumeTrue("No Batch Rename plugin with pinyin is installed", "pinyin" in features)
        File(root, "我的照片.jpg").writeText("a")
        File(root, "重慶.txt").writeText("b")
        val result = runScript(
            """
            var result = {};
            var plan = files.batchRename.plan(["我的照片.jpg", "重慶.txt"], { search: "", pinyin: true, pinyinSeparator: "underscore", pinyinCase: "capitalize" });
            result.newNames = plan.items.map(function (it) { return it.newName; });
            var o = plan.options;
            result.options = [o.pinyin, o.pinyinTone, o.pinyinSeparator, o.pinyinCase, o.pinyinInitials];
            result.variable = files.batchRename.plan("我的照片.jpg", { search: /^/, replace: "${'$'}{pinyin}-", target: "name", pinyinTone: "mark" }).items[0].newName;
            try { files.batchRename.plan("我的照片.jpg", { search: "", pinyinTone: "marks" }); } catch (e) { result.badToneCode = e.code; }
            files.write(MARKER, JSON.stringify(result));
            """,
        )
        assertEquals("""["Wo_De_Zhao_Pian.jpg","Chong_Qing.txt"]""", result.getJSONArray("newNames").toString())
        assertEquals("""[true,"none","underscore","capitalize",false]""", result.getJSONArray("options").toString())
        assertEquals("wǒdezhàopiàn-我的照片.jpg", result.getString("variable"))
        assertEquals(BatchRenameScriptCodes.INVALID_OPTIONS, result.getString("badToneCode"))
    }

    /**
     * Presets from a script (plugin roadmap P6.2): import a document object and its JSON text, list the presets, plan
     * with a preset plus an override, and the error codes of an unknown preset and a foreign document. The test leaves
     * the preset "AutoJs6 test preset" in the plugin, since scripts cannot delete presets.
     * zh-CN: 脚本中的预设 (插件路线图 P6.2): 导入文档对象及其 JSON 文本, 列出预设, 用预设加覆盖选项生成方案, 以及未知预设与非预设文档的
     * 错误码. 脚本无法删除预设, 因此测试会在插件中留下预设 "AutoJs6 test preset".
     */
    @Test
    fun scriptsImportListAndPlanWithPresets() {
        val features = runBlocking { BatchRenamePluginHost.discover(context) }.flatMap { BatchRenamePluginHost.featuresOf(it.pluginInfo) }
        assumeTrue("No Batch Rename plugin with presets is installed", "presets" in features)
        File(root, "IMG_1.jpg").writeText("a")
        val result = runScript(
            """
            var result = {};
            var name = "AutoJs6 test preset";
            var doc = { format: "autojs6-batch-rename-presets", version: 1, presets: [{ name: name, options: { search: "IMG_", replace: "Pic_", target: "name" } }] };
            result.imported = files.batchRename.importPresets(doc, { onConflict: "overwrite" }).imported;
            result.skipped = files.batchRename.importPresets(JSON.stringify(doc)).skipped;
            var listed = files.batchRename.presets().filter(function (p) { return p.name === name; })[0];
            result.listed = [listed.options.search, listed.options.replace, listed.options.target, listed.options.onConflict];
            var plan = files.batchRename.plan("IMG_1.jpg", { preset: name, replace: "Shot_" });
            result.newName = plan.items[0].newName;
            result.planOptions = [plan.options.search, plan.options.replace, plan.options.target, plan.options.preset === undefined];
            try { files.batchRename.plan("IMG_1.jpg", { preset: "no such preset" }); } catch (e) { result.missingCode = e.code; }
            try { files.batchRename.importPresets("[]"); } catch (e) { result.foreignCode = e.code; }
            files.batchRename.importPresetsAsync(doc).then(function (r) {
                result.asyncSkipped = r.skipped;
                return files.batchRename.presetsAsync();
            }).then(function (list) {
                result.asyncListed = list.some(function (p) { return p.name === name; });
            }).catch(function (e) {
                result.asyncError = String(e);
            }).then(function () {
                files.write(MARKER, JSON.stringify(result));
            });
            """,
        )
        assertEquals("""["AutoJs6 test preset"]""", result.getJSONArray("imported").toString())
        assertEquals("""["AutoJs6 test preset"]""", result.getJSONArray("skipped").toString())
        assertEquals("""["IMG_","Pic_","name","fail"]""", result.getJSONArray("listed").toString())
        assertEquals("Shot_1.jpg", result.getString("newName"))
        assertEquals("""["IMG_","Shot_","name",true]""", result.getJSONArray("planOptions").toString())
        assertEquals(BatchRenameScriptCodes.INVALID_OPTIONS, result.getString("missingCode"))
        assertEquals(BatchRenameScriptCodes.INVALID_OPTIONS, result.getString("foreignCode"))
        assertFalse(result.optString("asyncError"), result.has("asyncError"))
        assertEquals("""["AutoJs6 test preset"]""", result.getJSONArray("asyncSkipped").toString())
        assertTrue(result.getBoolean("asyncListed"))
    }

    /**
     * `preview` (plugin roadmap P6.3): names that need not exist, counters in array order, conflicts only within the
     * array, `lastModified` as a `Date`, and the `Async` form; nothing on disk changes.
     * zh-CN: `preview` (插件路线图 P6.3): 名称不必存在, 计数器按数组顺序, 只检查数组内的冲突, `lastModified` 可为 `Date`, 以及 `Async`
     * 形态; 磁盘上的内容不变.
     */
    @Test
    fun scriptsPreviewNamesWithoutFiles() {
        assumeTrue("No Batch Rename plugin is installed", BatchRenamePluginHost.queryServiceCount(context) > 0)
        val result = runScript(
            """
            var result = {};
            var single = files.batchRename.preview("IMG_1.jpg", { search: "IMG_", replace: "Photo_" });
            result.single = [single.newName, single.status, single.reason];
            result.many = files.batchRename.preview(
                ["a.txt", "b.txt", { name: "c", isDirectory: true }],
                { search: /^/, replace: "${'$'}{start=1;padding=2}_", enumerate: true, includeFolders: false }
            ).map(function (r) { return [r.newName, r.status]; });
            result.date = files.batchRename.preview(
                { name: "x.txt", lastModified: new Date(Date.UTC(2020, 10, 2, 12)) },
                { search: "x", replace: "${'$'}YYYY-${'$'}MM-${'$'}DD" }
            ).newName;
            result.duplicate = files.batchRename.preview(["a1.txt", "a2.txt"], { search: /\d/, replace: "" }).map(function (r) { return r.status; });
            try { files.batchRename.preview("a/b", { search: "a" }); } catch (e) { result.badNameCode = e.code; }
            files.batchRename.previewAsync("q.txt", { search: "q", replace: "r" }).then(function (r) {
                result.async = r.newName;
            }).catch(function (e) {
                result.asyncError = String(e);
            }).then(function () {
                files.write(MARKER, JSON.stringify(result));
            });
            """,
        )
        assertEquals("""["Photo_1.jpg","rename",null]""", result.getJSONArray("single").toString())
        assertEquals("""[["01_a.txt","rename"],["02_b.txt","rename"],["c","excluded"]]""", result.getJSONArray("many").toString())
        assertEquals("2020-11-02.txt", result.getString("date"))
        assertEquals("""["conflict","conflict"]""", result.getJSONArray("duplicate").toString())
        assertEquals(BatchRenameScriptCodes.INVALID_OPTIONS, result.getString("badNameCode"))
        assertFalse(result.optString("asyncError"), result.has("asyncError"))
        assertEquals("r.txt", result.getString("async"))
        // runScript deletes the marker after reading it: the preview left the directory empty.
        // zh-CN: runScript 读取后会删除标记文件: 试算没有在目录中留下任何内容.
        assertEquals(emptyList<String>(), root.list()!!.toList())
    }

    /**
     * Metadata variables from a script (plugin roadmap P6.4): the host reads the image's EXIF, the plugin names the item,
     * a file without the value gets an empty one and `metadataMissing`, and `preview` refuses `metadata`.
     * zh-CN: 脚本中的元数据变量 (插件路线图 P6.4): 宿主读取图片 EXIF, 插件为条目命名, 缺少该值的文件得到空值与 `metadataMissing`,
     * `preview` 拒绝 `metadata`.
     */
    @Test
    fun scriptsExpandMetadataVariables() {
        val features = runBlocking { BatchRenamePluginHost.discover(context) }.flatMap { BatchRenamePluginHost.featuresOf(it.pluginInfo) }
        assumeTrue("No Batch Rename plugin with metadata is installed", "metadata" in features)
        BatchRenameSampleImages.write(
            File(root, "IMG_1.jpg"),
            exif = mapOf(
                androidx.exifinterface.media.ExifInterface.TAG_MODEL to "Pixel 9",
                androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL to "2026:10:15 08:30:59",
                androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER to "1.8",
            ),
        )
        File(root, "notes.txt").apply { writeText("n") }.setLastModified(1_700_000_000_000L)
        val result = runScript(
            """
            var result = {};
            var plan = files.batchRename.plan(["IMG_1.jpg", "notes.txt"], { search: /^/, replace: "${'$'}CAMERA_MODEL_${'$'}APERTURE_${'$'}DATE_TAKEN_YYYY_", metadata: true });
            result.items = plan.items.map(function (it) { return [it.newName, it.metadataMissing]; });
            result.metadataOption = plan.options.metadata;
            try { files.batchRename.preview("a.jpg", { search: "a", metadata: true }); } catch (e) { result.previewCode = e.code; }
            files.write(MARKER, JSON.stringify(result));
            """,
        )
        val year = java.time.Instant.ofEpochMilli(1_700_000_000_000L).atZone(java.time.ZoneId.systemDefault()).year
        assertEquals("""[["Pixel 9_f1.8_2026_IMG_1.jpg",false],["__${year}_notes.txt",true]]""", result.getJSONArray("items").toString())
        assertTrue(result.getBoolean("metadataOption"))
        assertEquals(BatchRenameScriptCodes.INVALID_OPTIONS, result.getString("previewCode"))
    }

    /**
     * A plugin before 1.1.0 does not advertise pinyin, presets or metadata: scripts that use them fail, the others still plan.
     * zh-CN: 1.1.0 之前的插件未声明拼音: 用到拼音的脚本失败, 其余脚本照常生成方案.
     */
    @Test
    fun olderPluginsRefuseScriptsThatUsePinyin() {
        assumeTrue("No Batch Rename plugin is installed", BatchRenamePluginHost.queryServiceCount(context) > 0)
        val features = runBlocking { BatchRenamePluginHost.discover(context) }.flatMap { BatchRenamePluginHost.featuresOf(it.pluginInfo) }
        assumeFalse("The installed plugin converts to pinyin", "pinyin" in features)
        File(root, "照片.jpg").writeText("a")
        val result = runScript(
            """
            var result = {};
            try { files.batchRename.plan("照片.jpg", { search: "", pinyin: true }); } catch (e) { result.optionCode = e.code; }
            try { files.batchRename.plan("照片.jpg", { search: "照片", replace: "${'$'}{pinyin}" }); } catch (e) { result.variableCode = e.code; }
            result.plain = files.batchRename.plan("照片.jpg", { search: "照片", replace: "${'$'}${'$'}{pinyin}", pinyinTone: "mark" }).items[0].newName;
            try { files.batchRename.presets(); } catch (e) { result.presetsCode = e.code; }
            try { files.batchRename.plan("照片.jpg", { preset: "any" }); } catch (e) { result.presetCode = e.code; }
            try { files.batchRename.plan("照片.jpg", { search: "照片", metadata: true }); } catch (e) { result.metadataCode = e.code; }
            files.write(MARKER, JSON.stringify(result));
            """,
        )
        assertEquals(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE, result.getString("optionCode"))
        assertEquals(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE, result.getString("variableCode"))
        assertEquals("${'$'}{pinyin}.jpg", result.getString("plain"))
        assertEquals(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE, result.getString("presetsCode"))
        assertEquals(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE, result.getString("presetCode"))
        assertEquals(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE, result.getString("metadataCode"))
    }

    @Test
    fun withoutThePluginScriptsSeePluginUnavailable() {
        assumeFalse("A Batch Rename plugin is installed", BatchRenamePluginHost.queryServiceCount(context) > 0)
        File(root, "a.txt").writeText("a")
        val result = runScript(
            """
            var result = { available: files.batchRename.isAvailable() };
            try { files.batchRename.plan("a.txt", { search: "a" }); } catch (e) {
                result.code = e.code;
                result.name = e.name;
                result.message = e.message;
                result.isBatchRenameError = e instanceof files.batchRename.BatchRenameError;
            }
            files.batchRename.planAsync("a.txt", { search: "a" }).catch(function (e) {
                result.asyncCode = e.code;
                files.write(MARKER, JSON.stringify(result));
            });
            """,
        )
        assertFalse(result.getBoolean("available"))
        assertEquals(BatchRenameScriptCodes.PLUGIN_UNAVAILABLE, result.getString("code"))
        assertEquals("BatchRenameError", result.getString("name"))
        assertTrue(result.getString("message").isNotBlank())
        assertTrue(result.getBoolean("isBatchRenameError"))
        assertEquals(BatchRenameScriptCodes.PLUGIN_UNAVAILABLE, result.getString("asyncCode"))
        assertTrue(File(root, "a.txt").exists())
    }

    /**
     * `deleteHistory` and `clearHistory` (plugin roadmap D39) on batches of both sources; they need no plugin. The journal
     * of the device is set aside meanwhile and put back afterwards.
     * zh-CN: 对两种来源的批次调用 `deleteHistory` 与 `clearHistory` (插件路线图 D39); 两者不需要插件. 期间设备原有的日志被移开, 结束后放回.
     */
    @Test
    fun scriptsDeleteAndClearTheHistoryOfEverySource() {
        val journal = File(context.noBackupFilesDir, "batch-rename/journals")
        val aside = File(context.cacheDir, "batch-rename-journal-aside-${System.nanoTime()}").apply { mkdirs() }
        journal.listFiles().orEmpty().forEach { file -> check(file.renameTo(File(aside, file.name))) { "could not set ${file.name} aside" } }
        try {
            val executor = BatchRenameHostServices.executor(context)
            fun rename(name: String, source: BatchRenameSource): String {
                val file = File(root, name).apply { writeText(name) }
                return executor.execute(
                    listOf(BatchRenameOperation(file, "renamed-$name", false, file.lastModified(), file.length(), false)),
                    source,
                    pluginUid = android.os.Process.myUid().takeIf { source == BatchRenameSource.EXPLORER },
                ).batchId
            }
            val first = rename("a.txt", BatchRenameSource.SCRIPT)
            rename("b.txt", BatchRenameSource.SCRIPT)
            rename("c.txt", BatchRenameSource.EXPLORER)
            val result = runScript(
                """
                var result = {};
                result.all = files.batchRename.history().length;
                result.deleted = files.batchRename.deleteHistory("$first");
                result.deletedAgain = files.batchRename.deleteHistory("$first");
                result.deletedNewest = files.batchRename.deleteHistory(files.batchRename.history(1)[0]);
                try { files.batchRename.deleteHistory("b_1"); } catch (e) { result.badIdCode = e.code; }
                try { files.batchRename.clearHistory({ source: "plugin" }); } catch (e) { result.badSourceCode = e.code; }
                result.clearedExplorer = files.batchRename.clearHistory({ source: "explorer" });
                result.left = files.batchRename.history().map(function (it) { return it.source; });
                result.clearedAll = files.batchRename.clearHistory();
                result.after = files.batchRename.history().length;
                files.write(MARKER, JSON.stringify(result));
                """,
            )
            assertEquals(3, result.getInt("all"))
            assertTrue(result.getBoolean("deleted"))
            assertFalse(result.getBoolean("deletedAgain"))
            // The newest batch is the file manager's. zh-CN: 最新的批次来自文件管理器.
            assertTrue(result.getBoolean("deletedNewest"))
            assertEquals(BatchRenameScriptCodes.INVALID_OPTIONS, result.getString("badIdCode"))
            assertEquals(BatchRenameScriptCodes.INVALID_OPTIONS, result.getString("badSourceCode"))
            assertEquals(0, result.getInt("clearedExplorer"))
            assertEquals("""["script"]""", result.getJSONArray("left").toString())
            assertEquals(1, result.getInt("clearedAll"))
            assertEquals(0, result.getInt("after"))
            // Forgetting the history leaves the files as they are. zh-CN: 删除历史不影响文件.
            assertEquals(listOf("renamed-a.txt", "renamed-b.txt", "renamed-c.txt"), root.list().orEmpty().sorted())
        } finally {
            journal.mkdirs()
            journal.listFiles().orEmpty().forEach(File::delete)
            aside.listFiles().orEmpty().forEach { file -> file.renameTo(File(journal, file.name)) }
            aside.deleteRecursively()
        }
    }

    private fun runScript(body: String): JSONObject {
        val marker = File(root, "marker.json")
        val source = "var MARKER = " + JSONObject.quote(marker.path) + ";\n" + body.trimIndent()
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(
            StringScriptSource("batch-rename-script-api", source),
            listener,
            ExecutionConfig(workingDirectory = root.absolutePath),
        )
        try {
            assertTrue("the script did not finish", completed.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("the script failed", it) }
            assertTrue("the script wrote no result", marker.isFile)
            return JSONObject(marker.readText()).also { marker.delete() }
        } finally {
            execution.engine?.forceStop()
        }
    }

    @Suppress("unused")
    private fun JSONArray.strings() = (0 until length()).map(::getString)
}
