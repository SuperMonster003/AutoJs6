package org.autojs.autojs.core.plugin.batchrename

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Case-only renames on removable storage (plugin roadmap P6.6, D40). FAT32 and exFAT ignore case, so a new name that
 * differs only in case names the same entry and the host executor has to go through a temporary name. The test works in
 * the app's own folder on the first removable volume (an emulator SD card image made with `mksdcard`, or a real SD
 * card), checks that the volume really ignores case, then lowers every name with `files.batchRename` and undoes it,
 * reading the exact names back from the directory. Skipped without a removable volume or without the plugin. The file
 * system of the volume goes to logcat under [TAG].
 *
 * zh-CN: 可移动存储上的仅大小写改名 (插件路线图 P6.6, D40). FAT32 与 exFAT 不区分大小写, 仅大小写不同的新名称指向同一条目, 宿主执行器
 * 必须经过临时名称. 测试在第一个可移动卷 (用 `mksdcard` 制作的模拟器 SD 卡镜像或真实 SD 卡) 上的应用自有目录中进行: 先确认该卷确实不区分
 * 大小写, 再用 `files.batchRename` 把全部名称改为小写并撤销, 从目录读回确切的名称. 没有可移动卷或没有插件时跳过. 卷的文件系统写入
 * [TAG] 日志.
 */
class BatchRenameRemovableStorageDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val markerDirectory = File(context.cacheDir, "batch-rename-removable-${System.nanoTime()}")
    private var root: File? = null
    private val journalFiles = ArrayList<File>()

    @After
    fun cleanUp() {
        root?.deleteRecursively()
        markerDirectory.deleteRecursively()
        journalFiles.forEach(File::delete)
    }

    @Test
    fun caseOnlyRenamesAndTheirUndoWorkOnAVolumeThatIgnoresCase() {
        assumeTrue("No Batch Rename plugin is installed", BatchRenamePluginHost.queryServiceCount(context) > 0)
        // The second external files directory, if any, lies on a removable volume. zh-CN: 第二个外部文件目录 (如有) 位于可移动卷上.
        val volumeFiles = context.getExternalFilesDirs(null).drop(1).firstOrNull { it != null }
        assumeTrue("No removable volume is mounted", volumeFiles != null)
        val root = File(checkNotNull(volumeFiles), "batch-rename-case-${System.nanoTime()}").also { this.root = it }
        assertTrue("could not create $root", root.mkdirs())
        File(root, "IMG_1.JPG").writeText("1")
        File(root, "Notes.TXT").writeText("n")
        File(root, "Album").mkdirs()
        File(root, "Album/Cover.PNG").writeText("c")
        Log.i(TAG, "volume ${root.path} fileSystem=${fileSystemOf(root)}")
        assertTrue("the volume of $root distinguishes case, so the test proves nothing", File(root, "img_1.jpg").exists())

        markerDirectory.mkdirs()
        val result = runScript(
            """
            var root = ${JSONObject.quote(root.path)};
            var result = {};
            var plan = files.batchRename.plan(root, { search: "", format: "lower", target: "all" });
            result.planned = plan.items.filter(function (it) { return it.status === "rename"; }).map(function (it) { return it.name + " -> " + it.newName; }).sort();
            var applied = files.batchRename.apply(plan);
            result.batchId = applied.batchId;
            result.applied = applied.counts;
            result.listing = files.listDir(root).sort();
            result.inner = files.listDir(root + "/album").sort();
            var undone = files.batchRename.undo(applied);
            result.undoState = undone.state;
            result.restored = files.listDir(root).sort();
            result.restoredInner = files.listDir(root + "/Album").sort();
            files.write(MARKER, JSON.stringify(result));
            """,
        )
        result.optString("batchId").takeIf { it.isNotEmpty() }?.let { journalFiles += File(context.noBackupFilesDir, "batch-rename/journals/$it.json") }

        assertEquals("""["Album -> album","Cover.PNG -> cover.png","IMG_1.JPG -> img_1.jpg","Notes.TXT -> notes.txt"]""", result.getJSONArray("planned").toString())
        assertEquals(4, result.getJSONObject("applied").getInt("renamed"))
        assertEquals("""["album","img_1.jpg","notes.txt"]""", result.getJSONArray("listing").toString())
        assertEquals("""["cover.png"]""", result.getJSONArray("inner").toString())
        assertEquals("restored", result.getString("undoState"))
        assertEquals("""["Album","IMG_1.JPG","Notes.TXT"]""", result.getJSONArray("restored").toString())
        assertEquals("""["Cover.PNG"]""", result.getJSONArray("restoredInner").toString())
    }

    /** The file system type mounted below [file], from `/proc/mounts`. zh-CN: [file] 所在挂载点的文件系统类型, 取自 `/proc/mounts`. */
    private fun fileSystemOf(file: File): String {
        val uuid = file.path.removePrefix("/storage/").substringBefore('/')
        return runCatching {
            File("/proc/mounts").readLines()
                .map { it.split(' ') }
                .filter { it.size > 2 && it[1].endsWith("/$uuid") }
                .joinToString { "${it[1]}:${it[2]}" }
        }.getOrDefault("unknown").ifEmpty { "unknown" }
    }

    private fun runScript(body: String): JSONObject {
        val marker = File(markerDirectory, "marker.json")
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
            StringScriptSource("batch-rename-removable", source),
            listener,
            ExecutionConfig(workingDirectory = markerDirectory.absolutePath),
        )
        try {
            assertTrue("the script did not finish", completed.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("the script failed", it) }
            return JSONObject(marker.readText()).also { marker.delete() }
        } finally {
            execution.engine?.forceStop()
        }
    }

    private companion object {
        const val TAG = "BatchRenameRemovable"
    }
}
