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
            } catch (e) { result.callbackError = e.message; result.callbackRenamed = files.exists("z.txt"); }

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
     * A plugin before 1.1.0 does not advertise pinyin: scripts that use it fail, the others still plan.
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
            files.write(MARKER, JSON.stringify(result));
            """,
        )
        assertEquals(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE, result.getString("optionCode"))
        assertEquals(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE, result.getString("variableCode"))
        assertEquals("${'$'}{pinyin}.jpg", result.getString("plain"))
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
