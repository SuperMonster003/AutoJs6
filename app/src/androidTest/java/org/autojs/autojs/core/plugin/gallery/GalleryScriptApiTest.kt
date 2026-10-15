package org.autojs.autojs.core.plugin.gallery

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.autojs.plugin.gallery.api.GalleryErrorCodes
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
 * The `gallery` script API end to end (3-Maple Image roadmap M6, 10.4): scripts run through the production Rhino
 * engine against an installed 3-Maple Image (3.4.0 or later) and write what they saw to a marker file. The converter
 * runs headless; the viewer is opened, followed and closed by the script; the picker and the editor are left with
 * the back key and answer null. Without the plugin the script sees `isAvailable() === false` and
 * `PLUGIN_UNAVAILABLE`. Each test skips itself when the device is in the other state.
 *
 * ```
 * adb shell am instrument -w -e class org.autojs.autojs.core.plugin.gallery.GalleryScriptApiTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * zh-CN: `gallery` 脚本 API 端到端测试 (3-Maple Image 路线图 M6, 10.4): 脚本经正式 Rhino 引擎针对已安装的 3-Maple Image (3.4.0 及以上)
 * 运行, 把所见写入标记文件. 转换器无界面运行; 查看器由脚本打开, 跟随并关闭; 选图与编辑器用返回键离开并应答 null. 未安装插件时脚本应看到
 * `isAvailable() === false` 与 `PLUGIN_UNAVAILABLE`. 设备处于另一种状态时相应用例自行跳过.
 */
class GalleryScriptApiTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val root by lazy { File(context.cacheDir, "gallery-script-${System.nanoTime()}").apply { assertTrue(mkdirs()) } }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun installed(): Boolean = GalleryPluginHost.queryServiceCount(context) > 0

    private fun png(name: String, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0x80336699.toInt()) }
        File(root, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun scriptsConvertWithTheHostOwningEveryFile() {
        assumeTrue("No gallery plugin is installed", installed())
        png("src.png", 400, 300)
        File(root, "taken.webp").writeText("old")
        File(root, "notes.txt").writeText("not an image")

        val result = runScript(
            """
            var result = {};
            result.available = gallery.isAvailable();
            var first = gallery.convert("src.png", { format: "jpeg", quality: 80, percent: 50, background: "#00ff00" });
            result.first = [first.name, first.mimeType, first.width, first.height, first.size > 0];
            result.firstExists = files.exists("src_converted.jpg");
            var second = gallery.convert("src.png", { format: "jpg", longEdge: 100 });
            result.second = [second.name, second.width, second.height];
            try { gallery.convert("src.png", { output: "taken.webp", format: "webp" }); } catch (e) { result.existsCode = e.code; result.isGalleryError = e instanceof gallery.GalleryError; }
            var replaced = gallery.convert("src.png", { output: "taken.webp", format: "webp", overwrite: true, width: 40 });
            result.replaced = [replaced.name, replaced.mimeType, replaced.width, replaced.height];
            try { gallery.convert("missing.png"); } catch (e) { result.missingCode = e.code; }
            try { gallery.convert("src.png", { sizeMode: "fit" }); } catch (e) { result.invalidCode = e.code; }
            try { gallery.convert("notes.txt"); } catch (e) { result.decodeCode = e.code; }
            try { gallery.view([]); } catch (e) { result.emptyViewCode = e.code; }
            gallery.convertAsync("src.png", { format: "png", sizeMode: "custom", width: 50, height: 50, keepAspectRatio: false }).then(function (w) {
                result.async = [w.name, w.width, w.height];
                return gallery.convertAsync("missing.png");
            }).catch(function (e) {
                result.asyncCode = e.code;
            }).then(function () {
                result.listing = files.listDir(".").sort();
                files.write(MARKER, JSON.stringify(result));
            });
            """,
        )

        assertTrue(result.getBoolean("available"))
        assertEquals("""["src_converted.jpg","image/jpeg",200,150,true]""", result.getJSONArray("first").plain())
        assertTrue(result.getBoolean("firstExists"))
        assertEquals("""["src_converted (1).jpg",100,75]""", result.getJSONArray("second").toString())
        assertEquals(GalleryErrorCodes.OUTPUT_EXISTS, result.getString("existsCode"))
        assertTrue(result.getBoolean("isGalleryError"))
        assertEquals("""["taken.webp","image/webp",40,30]""", result.getJSONArray("replaced").plain())
        assertEquals(GalleryErrorCodes.FILE_NOT_FOUND, result.getString("missingCode"))
        assertEquals(GalleryErrorCodes.INVALID_ARGUMENT, result.getString("invalidCode"))
        assertEquals(GalleryErrorCodes.DECODE_FAILED, result.getString("decodeCode"))
        assertEquals(GalleryErrorCodes.INVALID_ARGUMENT, result.getString("emptyViewCode"))
        assertEquals("""["src_converted.png",50,50]""", result.getJSONArray("async").toString())
        assertEquals(GalleryErrorCodes.FILE_NOT_FOUND, result.getString("asyncCode"))
        assertEquals(
            """["notes.txt","src.png","src_converted (1).jpg","src_converted.jpg","src_converted.png","taken.webp"]""",
            result.getJSONArray("listing").toString(),
        )
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(File(root, "taken.webp").path, it) }
        assertEquals("image/webp", bounds.outMimeType)
        assertFalse("no staging file is left behind", root.list()!!.any { it.startsWith(".gallery-") })
    }

    @Test
    fun scriptsFollowAndCloseTheViewer() {
        assumeTrue("No gallery plugin is installed", installed())
        png("one.png", 40, 30)
        png("two.png", 30, 40)

        val result = runScript(
            """
            var result = { events: [] };
            gallery.viewAsync(["one.png", "two.png"], { index: 1 }).then(function (session) {
                result.openState = session.state;
                result.count = session.count;
                result.paths = session.paths.map(function (p) { return files.getName(p); });
                session.on("page", function (page) { result.events.push("page:" + page.index); });
                session.on("close", function (event) {
                    result.events.push("close:" + event.reason);
                    result.closedState = session.state;
                    result.index = session.index;
                    files.write(MARKER, JSON.stringify(result));
                });
                setTimeout(function () { session.close(); }, 1500);
            });
            """,
        )

        assertEquals("open", result.getString("openState"))
        assertEquals(2, result.getInt("count"))
        assertEquals("""["one.png","two.png"]""", result.getJSONArray("paths").toString())
        assertEquals("close:host", result.getJSONArray("events").let { it.getString(it.length() - 1) })
        assertEquals("closed", result.getString("closedState"))
        assertEquals(1, result.getInt("index"))
    }

    @Test
    fun aPickerOrEditorLeftWithBackAnswersNull() {
        assumeTrue("No gallery plugin is installed", installed())
        png("photo.png", 64, 48)
        val back = Thread {
            repeat(2) {
                Thread.sleep(4_000)
                InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
        }
        back.start()
        val result = runScript(
            """
            var result = {};
            gallery.pickAsync({ multiple: true, maxCount: 3 }).then(function (picked) {
                result.picked = picked;
                return gallery.editAsync("photo.png");
            }).then(function (edited) {
                result.edited = edited;
                result.listing = files.listDir(".").sort();
            }).catch(function (e) {
                result.error = e.code + " " + e.message;
            }).then(function () {
                files.write(MARKER, JSON.stringify(result));
            });
            """,
            timeoutSeconds = 90,
        )
        back.join()
        assertFalse(result.optString("error"), result.has("error"))
        assertTrue(result.isNull("picked"))
        assertTrue(result.isNull("edited"))
        assertEquals("""["photo.png"]""", result.getJSONArray("listing").toString())
    }

    @Test
    fun scriptsSeeAMissingPlugin() {
        assumeFalse("A gallery plugin is installed", installed())
        png("src.png", 10, 10)
        val result = runScript(
            """
            var result = { available: gallery.isAvailable() };
            try { gallery.convert("src.png"); } catch (e) { result.code = e.code; result.name = e.name; result.message = e.message; result.isGalleryError = e instanceof gallery.GalleryError; }
            gallery.pickAsync().catch(function (e) {
                result.asyncCode = e.code;
                files.write(MARKER, JSON.stringify(result));
            });
            """,
        )
        assertFalse(result.getBoolean("available"))
        assertEquals(GalleryErrorCodes.PLUGIN_UNAVAILABLE, result.getString("code"))
        assertEquals("GalleryError", result.getString("name"))
        assertTrue(result.getString("message").isNotBlank())
        assertTrue(result.getBoolean("isGalleryError"))
        assertEquals(GalleryErrorCodes.PLUGIN_UNAVAILABLE, result.getString("asyncCode"))
    }

    /** `JSONArray.toString` escapes `/`; the scripts wrote plain strings. */
    private fun org.json.JSONArray.plain(): String = toString().replace("\\/", "/")

    private fun runScript(body: String, timeoutSeconds: Long = 60): JSONObject {
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
            StringScriptSource("gallery-script-api", source),
            listener,
            ExecutionConfig(workingDirectory = root.absolutePath),
        )
        try {
            assertTrue("the script did not finish", completed.await(timeoutSeconds, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("the script failed", it) }
            assertTrue("the script wrote no result", marker.isFile)
            return JSONObject(marker.readText())
        } finally {
            execution.engine?.forceStop()
        }
    }
}
