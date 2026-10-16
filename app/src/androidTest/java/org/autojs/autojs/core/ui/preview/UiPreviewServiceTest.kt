package org.autojs.autojs.core.ui.preview

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.ui.JsViewHelper
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.plugin.ui.preview.IUiPreviewCallback
import org.autojs.plugin.ui.preview.IUiPreviewService
import org.autojs.plugin.ui.preview.UiPreviewContract
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Binds the ui-mode preview service from the instrumentation (same signature and process as the host) and
 * exercises contract v1: info, snapshot sessions with pixels and bounds, embedded sessions on API 30+,
 * failure positions, lenient bindings, the per-client limit, request limits, host-side stop and release; and
 * contract v2 (3-Block Compose roadmap P10.6, API 33+): a script run in the embedded root with its console,
 * clicks, replacement, stop, errors, `exit()` and the release of a running session; and contract v3 (host roadmap
 * `docs/dev/android-xml-attributes-roadmap.md` R3): the resources of a project in layouts and a project run.
 *
 * zh-CN: 以 instrumentation (与宿主同签名同进程) 绑定 ui 模式预览服务并验证契约 v1: 信息, 快照会话的像素与边界, API 30+ 的嵌入会话,
 * 失败定位, 宽松绑定, 每客户端上限, 请求上限, 宿主侧停止与释放; 以及契约 v2 (3-Block Compose 路线图 P10.6, API 33+): 嵌入根中的脚本运行及其控制台,
 * 点击, 替换, 停止, 错误, `exit()` 与运行中会话的释放.
 */
@RunWith(AndroidJUnit4::class)
class UiPreviewServiceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var connection: ServiceConnection
    private lateinit var service: IUiPreviewService
    private val sessions = ArrayList<String>()
    private val files = ArrayList<File>()

    private class Callback : IUiPreviewCallback.Stub() {
        val bounds = LinkedBlockingQueue<Bundle>()
        val failures = LinkedBlockingQueue<Bundle>()
        val lost = LinkedBlockingQueue<Bundle>()
        val taps = LinkedBlockingQueue<Bundle>()
        val console = LinkedBlockingQueue<Bundle>()
        val scriptErrors = LinkedBlockingQueue<Bundle>()
        val exits = LinkedBlockingQueue<Bundle>()
        override fun onBounds(sessionId: String, bounds: Bundle) { this.bounds.add(bounds) }
        override fun onLayoutFailed(sessionId: String, failure: Bundle) { failures.add(failure) }
        override fun onSessionLost(sessionId: String, reason: Bundle) { lost.add(reason) }
        override fun onTap(sessionId: String, tap: Bundle) { taps.add(tap) }
        // Contract v2 (3-Block Compose roadmap P10.6): the running script's console, errors and end.
        override fun onConsole(sessionId: String, entry: Bundle) { console.add(entry) }
        override fun onScriptError(sessionId: String, error: Bundle) { scriptErrors.add(error) }
        override fun onScriptExit(sessionId: String, exit: Bundle) { exits.add(exit) }
    }

    @Before
    fun bind() {
        val bound = CountDownLatch(1)
        var binder: IBinder? = null
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) { binder = service; bound.countDown() }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        val intent = Intent(UiPreviewContract.ACTION).setPackage(context.packageName)
        assertTrue("the preview service must bind", context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        assertTrue("the preview service did not connect", bound.await(10, TimeUnit.SECONDS))
        service = IUiPreviewService.Stub.asInterface(binder)
    }

    @After
    fun unbind() {
        sessions.forEach { runCatching { service.release(it) } }
        sessions.clear()
        runCatching { context.unbindService(connection) }
        files.forEach { it.delete() }
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private fun request(mode: String, width: Int, height: Int, label: String = "preview-test", contractVersion: Int = UiPreviewContract.VERSION) = Bundle().apply {
        putInt(UiPreviewContract.KEY_CONTRACT_VERSION, contractVersion)
        putString(UiPreviewContract.KEY_MODE, mode)
        putInt(UiPreviewContract.KEY_WIDTH_PX, width)
        putInt(UiPreviewContract.KEY_HEIGHT_PX, height)
        putString(UiPreviewContract.KEY_LABEL, label)
        putLong(UiPreviewContract.KEY_CLIENT_VERSION_CODE, 1L)
    }

    private fun layout(xml: String, bindingsJson: String? = null) = Bundle().apply {
        putString(UiPreviewContract.KEY_XML, xml)
        bindingsJson?.let { putString(UiPreviewContract.KEY_BINDINGS_JSON, it) }
    }

    private fun create(request: Bundle, callback: Callback): Bundle {
        val result = service.createSession(request, callback)
        result.getString(UiPreviewContract.KEY_SESSION_ID)?.let { sessions.add(it) }
        return result
    }

    private fun sessionId(result: Bundle): String {
        assertNull("createSession failed: ${result.getString(UiPreviewContract.KEY_ERROR_MESSAGE)}", result.getString(UiPreviewContract.KEY_ERROR_CODE))
        return assertNotNull(result.getString(UiPreviewContract.KEY_SESSION_ID)).let { result.getString(UiPreviewContract.KEY_SESSION_ID)!! }
    }

    private fun snapshotFile(): Pair<File, ParcelFileDescriptor> {
        val file = File(context.cacheDir, "ui-preview-${UUID.randomUUID()}.argb").also { files.add(it) }
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
        return file to fd
    }

    /**
     * The next bounds report of a layout. An embedded session also reports the empty hierarchy after its first
     * traversal; on a slow device that report can arrive after the test applied its layout, so it is skipped.
     * zh-CN: 下一份布局的边界报告. 嵌入会话首次遍历时也会报告空层级, 设备较慢时它可能晚于测试应用布局到达, 故跳过.
     */
    private fun awaitBounds(callback: Callback): JSONArray {
        while (true) {
            val bounds = callback.bounds.poll(15, TimeUnit.SECONDS) ?: throw AssertionError("no bounds arrived")
            if (bounds.getInt(UiPreviewContract.KEY_ELEMENT_COUNT) == 0) continue
            return JSONArray(bounds.getString(UiPreviewContract.KEY_BOUNDS_JSON))
        }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun outcome(result: Bundle) = result.getString(UiPreviewContract.KEY_OUTCOME)

    /** Kept in a separate class so no method of the test class names an API 30 type. */
    private object Embedded {
        @androidx.annotation.RequiresApi(30)
        fun attach(surfaceView: SurfaceView, surfacePackage: android.os.Parcelable) {
            surfaceView.setChildSurfacePackage(surfacePackage as android.view.SurfaceControlViewHost.SurfacePackage)
        }
    }

    private fun attach(surfaceView: SurfaceView, surfacePackage: android.os.Parcelable) {
        if (Build.VERSION.SDK_INT >= 30) Embedded.attach(surfaceView, surfacePackage)
    }

    private fun error(result: Bundle) = result.getString(UiPreviewContract.KEY_ERROR_CODE)

    // --------------------------------------------------------------------------------------------------- tests

    @Test
    fun previewInfoReportsContractModesAndLimits() {
        // The Binder getters keep answering for the frozen v1 base (a 1.6.0 client compares them strictly); the
        // preview info keeps the frozen v2 identity in its two keys (a 1.7.0 client compares them strictly) and
        // lists every served version with its hash, and whether scripts can run (API 33+).
        assertEquals(UiPreviewContract.BASE_VERSION, service.contractVersion)
        assertEquals(UiPreviewContract.HASH_V1, service.contractHash)
        val info = service.previewInfo
        assertEquals(UiPreviewContract.STATUS_READY, info.getString(UiPreviewContract.KEY_STATUS))
        assertEquals(UiPreviewContract.VERSION_V2, info.getInt(UiPreviewContract.KEY_CONTRACT_VERSION))
        assertEquals(UiPreviewContract.HASH_V2, info.getString(UiPreviewContract.KEY_CONTRACT_HASH))
        val versions = info.getIntArray(UiPreviewContract.KEY_SUPPORTED_CONTRACT_VERSIONS)!!.toList()
        val hashes = info.getStringArray(UiPreviewContract.KEY_CONTRACT_HASHES)!!.toList()
        assertEquals(versions.size, hashes.size)
        assertEquals(listOf(UiPreviewContract.BASE_VERSION, UiPreviewContract.VERSION_V2), versions.take(2))
        assertEquals(listOf(UiPreviewContract.HASH_V1, UiPreviewContract.HASH_V2), hashes.take(2))
        assertEquals(versions.sorted(), versions)
        assertEquals(Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE, info.getBoolean(UiPreviewContract.KEY_SCRIPT_RUN_SUPPORTED))
        assertTrue(info.getLong(UiPreviewContract.KEY_HOST_VERSION_CODE) > 0)
        val modes = info.getStringArray(UiPreviewContract.KEY_MODES)!!.toList()
        assertTrue(UiPreviewContract.MODE_SNAPSHOT in modes)
        assertEquals(Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_EMBEDDED, UiPreviewContract.MODE_EMBEDDED in modes)
        assertEquals(Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE, info.getBoolean(UiPreviewContract.KEY_INTERACTIVE_POINTER_SUPPORTED))
        assertEquals(UiPreviewContract.MAX_SESSIONS_PER_CLIENT, info.getInt(UiPreviewContract.KEY_MAX_SESSIONS_PER_CLIENT))
        assertEquals(UiPreviewContract.LAYOUT_TIMEOUT_MS, info.getLong(UiPreviewContract.KEY_LAYOUT_TIMEOUT_MS))
    }

    @Test
    fun snapshotSessionReturnsPixelsAndBoundsAndAppliesBindings() {
        val callback = Callback()
        val id = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 320, 480), callback))
        val (file, fd) = snapshotFile()
        val widthDp = 120
        val result = service.setLayout(
            id,
            layout(
                "<vertical id=\"root\" padding=\"8\"><text id=\"title\" text=\"{{msg}}\" textSize=\"20sp\"/><button id=\"ok\" w=\"{{w}}\" text=\"OK\"/></vertical>",
                "{\"msg\":\"hello\",\"w\":$widthDp}",
            ),
            fd,
        )
        assertEquals(result.getString(UiPreviewContract.KEY_ERROR_MESSAGE), UiPreviewContract.OUTCOME_APPLIED, outcome(result))
        assertEquals(3, result.getInt(UiPreviewContract.KEY_ELEMENT_COUNT))
        assertEquals("[]", result.getString(UiPreviewContract.KEY_DIAGNOSTICS_JSON))
        assertEquals(UiPreviewContract.SNAPSHOT_FORMAT_ARGB8888_ROW_MAJOR, result.getString(UiPreviewContract.KEY_SNAPSHOT_FORMAT))
        assertEquals(320L * 480L * UiPreviewContract.SNAPSHOT_BYTES_PER_PIXEL, result.getLong(UiPreviewContract.KEY_SNAPSHOT_BYTE_LENGTH))
        val bytes = file.readBytes()
        assertEquals(320 * 480 * 4, bytes.size)
        assertEquals(result.getString(UiPreviewContract.KEY_SNAPSHOT_SHA256), sha256(bytes))

        val bounds = awaitBounds(callback)
        assertEquals(3, bounds.length())
        val ids = (0 until bounds.length()).map { bounds.getJSONObject(it) }
        assertEquals(listOf(0, 1, 2), ids.map { it.getInt(UiPreviewContract.Bounds.ELEMENT_INDEX) })
        assertEquals(listOf("root", "title", "ok"), ids.map { it.getString(UiPreviewContract.Bounds.ID) })
        val root = ids[0]
        assertEquals(0, root.getInt(UiPreviewContract.Bounds.LEFT))
        assertEquals(320, root.getInt(UiPreviewContract.Bounds.RIGHT))
        assertEquals(480, root.getInt(UiPreviewContract.Bounds.BOTTOM))
        val button = ids[2]
        val expectedWidth = (widthDp * context.resources.displayMetrics.density).roundToInt()
        val actualWidth = button.getInt(UiPreviewContract.Bounds.RIGHT) - button.getInt(UiPreviewContract.Bounds.LEFT)
        assertTrue("w=\"{{w}}\" must bind to $widthDp dp (= $expectedWidth px), got $actualWidth px", abs(actualWidth - expectedWidth) <= 1)
        assertTrue(ids[1].getInt(UiPreviewContract.Bounds.TOP) < button.getInt(UiPreviewContract.Bounds.TOP))
    }

    @Test
    fun invalidXmlReportsThePosition() {
        val callback = Callback()
        val id = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 200, 200), callback))
        val (_, fd) = snapshotFile()
        val result = service.setLayout(id, layout("<vertical><text text=\"a\"></vertical>"), fd)
        assertEquals(UiPreviewContract.OUTCOME_FAILED, outcome(result))
        assertEquals(UiPreviewContract.ERROR_XML_INVALID, error(result))
        assertTrue(result.getInt(UiPreviewContract.KEY_LINE) >= 1)
        assertEquals(1, result.getInt(UiPreviewContract.KEY_ELEMENT_INDEX))
        assertTrue(result.getString(UiPreviewContract.KEY_ERROR_MESSAGE)!!.isNotBlank())
    }

    @Test
    fun inflateFailureNamesTheElement() {
        val callback = Callback()
        val id = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 200, 200), callback))
        val (_, fd) = snapshotFile()
        val result = service.setLayout(id, layout("<vertical><text text=\"a\"/><nope/></vertical>"), fd)
        assertEquals(UiPreviewContract.OUTCOME_FAILED, outcome(result))
        assertEquals(UiPreviewContract.ERROR_INFLATE_FAILED, error(result))
        assertEquals(2, result.getInt(UiPreviewContract.KEY_ELEMENT_INDEX))
        assertEquals(3, result.getInt(UiPreviewContract.KEY_ELEMENT_COUNT))
        // The session survives a failed frame: the next layout applies.
        val (_, next) = snapshotFile()
        assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.setLayout(id, layout("<frame><text text=\"b\"/></frame>"), next)))
    }

    @Test
    fun bindingFailureIsReportedWithoutStoppingTheFrame() {
        val callback = Callback()
        val id = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 200, 200), callback))
        val (file, fd) = snapshotFile()
        val result = service.setLayout(id, layout("<vertical><text id=\"t\" text=\"{{missing}}\"/><text text=\"ok\"/></vertical>", "{}"), fd)
        assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(result))
        val diagnostics = JSONArray(result.getString(UiPreviewContract.KEY_DIAGNOSTICS_JSON))
        assertEquals(1, diagnostics.length())
        val diagnostic = diagnostics.getJSONObject(0)
        assertEquals(UiPreviewContract.ERROR_BINDING_FAILED, diagnostic.getString(UiPreviewContract.Diagnostic.CODE))
        assertEquals(1, diagnostic.getInt(UiPreviewContract.Diagnostic.ELEMENT_INDEX))
        assertEquals("text", diagnostic.getString(UiPreviewContract.Diagnostic.ATTR_NAME))
        assertTrue(diagnostic.getString(UiPreviewContract.Diagnostic.MESSAGE).contains("missing"))
        assertEquals(200 * 200 * 4, file.readBytes().size)
        assertEquals(3, awaitBounds(callback).length())
    }

    @Test
    fun ignoredAttributesOfNativeXmlAreDiagnosedWithoutStoppingTheFrame() {
        val callback = Callback()
        val id = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 200, 200), callback))
        val xml = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" xmlns:tools=\"http://schemas.android.com/tools\" " +
            "android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" tools:ignore=\"x\">" +
            "<TextView android:text=\"a\" android:onClick=\"go\" android:noSuchAttribute=\"1\" " +
            // Read when the view is created: no diagnostic.
            "style=\"@android:style/Widget.Material.TextView\" android:theme=\"@android:style/Theme.Material.Light\"/></LinearLayout>"
        val result = service.setLayout(id, layout(xml), snapshotFile().second)
        assertEquals(result.getString(UiPreviewContract.KEY_ERROR_MESSAGE), UiPreviewContract.OUTCOME_APPLIED, outcome(result))
        val diagnostics = JSONArray(result.getString(UiPreviewContract.KEY_DIAGNOSTICS_JSON))
        val byAttr = (0 until diagnostics.length()).map { diagnostics.getJSONObject(it) }.associateBy { it.getString(UiPreviewContract.Diagnostic.ATTR_NAME) }
        assertEquals(setOf("android:onClick", "android:noSuchAttribute"), byAttr.keys)
        byAttr.values.forEach {
            assertEquals(UiPreviewContract.ERROR_ATTRIBUTE_IGNORED, it.getString(UiPreviewContract.Diagnostic.CODE))
            assertEquals(1, it.getInt(UiPreviewContract.Diagnostic.ELEMENT_INDEX))
        }
        assertEquals(UiPreviewContract.ATTRIBUTE_OUTCOME_UNSUPPORTED, byAttr.getValue("android:onClick").getString(UiPreviewContract.Diagnostic.OUTCOME))
        assertEquals(UiPreviewContract.ATTRIBUTE_OUTCOME_UNKNOWN, byAttr.getValue("android:noSuchAttribute").getString(UiPreviewContract.Diagnostic.OUTCOME))
        // AutoJs6 XML keeps its rules: an unknown attribute is silently ignored, a declared unsupported one fails.
        val autojs = service.setLayout(id, layout("<vertical><text text=\"a\" noSuchAttribute=\"1\"/></vertical>"), snapshotFile().second)
        assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(autojs))
        assertEquals("[]", autojs.getString(UiPreviewContract.KEY_DIAGNOSTICS_JSON))
        assertEquals(UiPreviewContract.OUTCOME_FAILED, outcome(service.setLayout(id, layout("<vertical><text text=\"a\" onClick=\"go\"/></vertical>"), snapshotFile().second)))
    }

    @Test
    fun requestsBeyondTheLimitsAreRejected() {
        val callback = Callback()
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(create(request(UiPreviewContract.MODE_SNAPSHOT, 5000, 10), callback)))
        assertEquals(UiPreviewContract.ERROR_CONTRACT_MISMATCH, error(create(request(UiPreviewContract.MODE_SNAPSHOT, 10, 10, contractVersion = UiPreviewContract.VERSION + 1), callback)))
        sessions.add(sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 10, 10, "v1", contractVersion = UiPreviewContract.BASE_VERSION), Callback())).also { service.release(it) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(create(request("window", 10, 10), callback)))
        val id = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 100, 100), callback))
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(service.setLayout(id, layout("x".repeat(UiPreviewContract.MAX_XML_CHARS + 1)), null)))
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(service.setLayout(id, layout("<frame/>"), null)))
        val (_, fd) = snapshotFile()
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(service.setLayout(id, layout("<frame/>", "[1,2]"), fd)))
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(service.resize(id, 0, 10)))
        assertEquals(UiPreviewContract.ERROR_SESSION_NOT_FOUND, error(service.setLayout("missing", layout("<frame/>"), null)))
        if (Build.VERSION.SDK_INT < UiPreviewContract.MIN_API_INTERACTIVE) {
            assertEquals(UiPreviewContract.ERROR_UNSUPPORTED_MODE, error(service.setPointerMode(id, UiPreviewContract.POINTER_MODE_INTERACTIVE)))
        }
        // Script runs (v2) need an embedded session: a snapshot session refuses them on every API level.
        assertEquals(UiPreviewContract.ERROR_UNSUPPORTED_MODE, error(service.runScript(id, script("\"ui\";\nui.layout(<frame/>);\n", "snapshot-run"))))
        assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.stopScript(id)))
        assertEquals(UiPreviewContract.ERROR_SESSION_NOT_FOUND, error(service.runScript("missing", script("\"ui\";", "missing"))))
        assertEquals(UiPreviewContract.ERROR_SESSION_NOT_FOUND, error(service.stopScript("missing")))
        if (Build.VERSION.SDK_INT < UiPreviewContract.MIN_API_EMBEDDED) {
            assertEquals(UiPreviewContract.ERROR_UNSUPPORTED_MODE, error(create(request(UiPreviewContract.MODE_EMBEDDED, 10, 10), callback)))
        }
    }

    @Test
    fun twoSessionsAreIndependentAndAThirdIsRefused() {
        val first = Callback()
        val second = Callback()
        val a = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 300, 200, "a"), first))
        val b = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 200, 300, "b"), second))
        assertEquals(UiPreviewContract.ERROR_SESSION_LIMIT, error(create(request(UiPreviewContract.MODE_SNAPSHOT, 10, 10, "c"), Callback())))
        val (fileA, fdA) = snapshotFile()
        val (fileB, fdB) = snapshotFile()
        val resultA = service.setLayout(a, layout("<vertical><text text=\"a\"/></vertical>"), fdA)
        val resultB = service.setLayout(b, layout("<vertical><text text=\"b\"/><text text=\"c\"/></vertical>"), fdB)
        assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(resultA))
        assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(resultB))
        assertEquals(300 * 200 * 4, fileA.readBytes().size)
        assertEquals(200 * 300 * 4, fileB.readBytes().size)
        assertEquals(2, awaitBounds(first).length())
        assertEquals(3, awaitBounds(second).length())
        val labels = AutoJs.instance.scriptEngineService.scriptExecutions.filter { !it.isFinished }.map { it.source.name }
        assertTrue("preview runtimes are listed as executions: $labels", "a" in labels && "b" in labels)

        service.release(a)
        instrumentation.waitForIdleSync()
        assertEquals(UiPreviewContract.ERROR_SESSION_NOT_FOUND, error(service.resize(a, 10, 10)))
        assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.resize(b, 100, 100)))
        assertTrue(first.lost.isEmpty())
    }

    @Test
    fun stoppingTheRuntimeFromTheHostLosesTheSession() {
        val callback = Callback()
        val label = "stop-${UUID.randomUUID()}"
        val id = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 100, 100, label), callback))
        val execution = AutoJs.instance.scriptEngineService.scriptExecutions.first { it.source.name == label }
        assertFalse(execution.isFinished)
        instrumentation.runOnMainSync { execution.engine!!.forceStop() }
        val lost = callback.lost.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("onSessionLost did not arrive")
        assertEquals(UiPreviewContract.REASON_STOPPED, lost.getString(UiPreviewContract.KEY_REASON))
        assertEquals(UiPreviewContract.ERROR_SESSION_NOT_FOUND, error(service.setLayout(id, layout("<frame/>"), null)))
        instrumentation.waitForIdleSync()
        assertTrue(AutoJs.instance.scriptEngineService.scriptExecutions.none { it.source.name == label && !it.isFinished })
    }

    @Test
    fun embeddedSessionReturnsASurfacePackageAndBounds() {
        assumeTrue("the embedded mode needs API 30", Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_EMBEDDED)
        val activity = instrumentation.startActivitySync(Intent(context, AboutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            lateinit var surfaceView: SurfaceView
            val surfaceCreated = CountDownLatch(1)
            instrumentation.runOnMainSync {
                surfaceView = SurfaceView(activity).apply {
                    setZOrderOnTop(true)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) { surfaceCreated.countDown() }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) {}
                    })
                }
                activity.addContentView(surfaceView, ViewGroup.LayoutParams(400, 600))
            }
            assertTrue("the SurfaceView did not create its surface", surfaceCreated.await(10, TimeUnit.SECONDS))
            val request = request(UiPreviewContract.MODE_EMBEDDED, 400, 600)
            instrumentation.runOnMainSync {
                @Suppress("DEPRECATION")
                request.putBinder(UiPreviewContract.KEY_HOST_TOKEN, surfaceView.hostToken)
                if (Build.VERSION.SDK_INT >= 35) {
                    surfaceView.rootSurfaceControl?.inputTransferToken?.let { request.putParcelable(UiPreviewContract.KEY_INPUT_TRANSFER_TOKEN, it) }
                }
                request.putInt(UiPreviewContract.KEY_DISPLAY_ID, surfaceView.display.displayId)
            }
            val callback = Callback()
            val created = create(request, callback)
            val id = sessionId(created)
            // Typed as Parcelable: a SurfacePackage in a method signature of this class would make it unloadable below API 30.
            // zh-CN: 以 Parcelable 类型持有: 本类方法签名中若出现 SurfacePackage, API 30 以下将无法加载该类.
            @Suppress("DEPRECATION")
            val surfacePackage: android.os.Parcelable? = created.getParcelable(UiPreviewContract.KEY_SURFACE_PACKAGE)
            assertNotNull("an embedded session returns its SurfacePackage", surfacePackage)
            assertEquals("SurfacePackage", surfacePackage!!.javaClass.simpleName)
            instrumentation.runOnMainSync { attach(surfaceView, surfacePackage) }

            val result = service.setLayout(id, layout("<vertical><text id=\"t\" text=\"embedded\"/><button text=\"OK\"/></vertical>"), null)
            assertEquals(result.getString(UiPreviewContract.KEY_ERROR_MESSAGE), UiPreviewContract.OUTCOME_APPLIED, outcome(result))
            assertEquals(3, result.getInt(UiPreviewContract.KEY_ELEMENT_COUNT))
            val bounds = awaitBounds(callback)
            assertEquals(3, bounds.length())
            assertEquals(400, bounds.getJSONObject(0).getInt(UiPreviewContract.Bounds.RIGHT))

            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.setHighlight(id, 1)))
            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.resize(id, 300, 500)))
            callback.bounds.clear()
            val resized = awaitBounds(callback)
            assertEquals(300, resized.getJSONObject(0).getInt(UiPreviewContract.Bounds.RIGHT))
            @Suppress("DEPRECATION")
            val reattached: android.os.Parcelable? = service.reattach(id).getParcelable(UiPreviewContract.KEY_SURFACE_PACKAGE)
            assertNotNull("reattach returns a package for the same hierarchy", reattached)
            assertEquals("SurfacePackage", reattached!!.javaClass.simpleName)
            if (Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE) {
                assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.setPointerMode(id, UiPreviewContract.POINTER_MODE_INTERACTIVE)))
            }
            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.setPointerMode(id, UiPreviewContract.POINTER_MODE_EDIT)))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    // ------------------------------------------------------------------------------------- script runs (v2)

    private fun script(text: String, name: String) = Bundle().apply {
        putString(UiPreviewContract.KEY_SCRIPT_TEXT, text)
        putString(UiPreviewContract.KEY_SCRIPT_NAME, name)
    }

    /** An embedded session attached to a SurfaceView of a throwaway activity; [block] gets its id and callback. */
    private fun withEmbeddedSession(block: (String, Callback) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(context, AboutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            lateinit var surfaceView: SurfaceView
            val surfaceCreated = CountDownLatch(1)
            instrumentation.runOnMainSync {
                surfaceView = SurfaceView(activity).apply {
                    setZOrderOnTop(true)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) { surfaceCreated.countDown() }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) {}
                    })
                }
                activity.addContentView(surfaceView, ViewGroup.LayoutParams(400, 600))
            }
            assertTrue("the SurfaceView did not create its surface", surfaceCreated.await(10, TimeUnit.SECONDS))
            val request = request(UiPreviewContract.MODE_EMBEDDED, 400, 600)
            instrumentation.runOnMainSync {
                @Suppress("DEPRECATION")
                request.putBinder(UiPreviewContract.KEY_HOST_TOKEN, surfaceView.hostToken)
                if (Build.VERSION.SDK_INT >= 35) {
                    surfaceView.rootSurfaceControl?.inputTransferToken?.let { request.putParcelable(UiPreviewContract.KEY_INPUT_TRANSFER_TOKEN, it) }
                }
                request.putInt(UiPreviewContract.KEY_DISPLAY_ID, surfaceView.display.displayId)
            }
            val callback = Callback()
            val created = create(request, callback)
            val id = sessionId(created)
            @Suppress("DEPRECATION")
            val surfacePackage: android.os.Parcelable? = created.getParcelable(UiPreviewContract.KEY_SURFACE_PACKAGE)
            instrumentation.runOnMainSync { attach(surfaceView, surfacePackage!!) }
            block(id, callback)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun awaitConsole(callback: Callback, text: String): Bundle {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (true) {
            val entry = callback.console.poll((deadline - SystemClock.uptimeMillis()).coerceAtLeast(1), TimeUnit.MILLISECONDS)
                ?: throw AssertionError("no console entry containing '$text' arrived")
            if (entry.getString(UiPreviewContract.KEY_TEXT).orEmpty().contains(text)) return entry
        }
    }

    private fun awaitExit(callback: Callback, runId: String): String {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (true) {
            val exit = callback.exits.poll((deadline - SystemClock.uptimeMillis()).coerceAtLeast(1), TimeUnit.MILLISECONDS)
                ?: throw AssertionError("onScriptExit for $runId did not arrive")
            if (exit.getString(UiPreviewContract.KEY_RUN_ID) == runId) return exit.getString(UiPreviewContract.KEY_REASON)!!
        }
    }

    private fun runId(result: Bundle): String {
        assertEquals(result.getString(UiPreviewContract.KEY_ERROR_MESSAGE), UiPreviewContract.OUTCOME_APPLIED, outcome(result))
        return result.getString(UiPreviewContract.KEY_RUN_ID)!!
    }

    /** The content root of the live run named [name]: the run is a normal execution whose runtime carries the host tag. */
    private fun runRoot(name: String): ViewGroup {
        val execution = AutoJs.instance.scriptEngineService.scriptExecutions.first { it.source.name == name && !it.isFinished } as UiPreviewScriptExecution
        val runtime = execution.runtime ?: throw AssertionError("the run $name has no runtime")
        return UiContentHost.attached(runtime)?.contentRoot ?: throw AssertionError("the run $name has no content root")
    }

    private fun textOf(root: ViewGroup, id: String): String {
        lateinit var text: String
        instrumentation.runOnMainSync { text = (JsViewHelper.findViewByStringId(root, id) as TextView).text.toString() }
        return text
    }

    @Test
    fun runScriptRunsTheScriptInTheRootWithConsoleClicksReplacementAndStop() {
        assumeTrue("script runs need API 33", Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE)
        withEmbeddedSession { id, callback ->
            val name = "run-${UUID.randomUUID()}"
            val counter = "\"ui\";\nui.layout(<vertical><button id=\"b\" text=\"count 0\"/></vertical>);\nvar n = 0;\nconsole.log(\"started\");\n" +
                "ui.b.on(\"click\", () => {\n    n++;\n    ui.b.setText(\"count \" + n);\n    console.log(\"clicked \" + n);\n});\n"
            val first = runId(service.runScript(id, script(counter, name)))
            assertEquals(first, awaitConsole(callback, "started").getString(UiPreviewContract.KEY_RUN_ID))
            // The run is a normal execution under the script's name; its ui.layout went into the session's root.
            val root = runRoot(name)
            assertEquals("count 0", textOf(root, "b"))
            instrumentation.runOnMainSync { JsViewHelper.findViewByStringId(root, "b")!!.performClick() }
            awaitConsole(callback, "clicked 1")
            assertEquals("count 1", textOf(root, "b"))
            // A second run replaces the first (its exit says so) and the root shows the new content.
            val second = runId(service.runScript(id, script("\"ui\";\nui.layout(<frame><text id=\"t\" text=\"second\"/></frame>);\nconsole.log(\"second started\");\n", name)))
            assertEquals(UiPreviewContract.EXIT_REPLACED, awaitExit(callback, first))
            awaitConsole(callback, "second started")
            assertEquals("second", textOf(runRoot(name), "t"))
            // stopScript ends the run; the session and its layout preview go on, and stopping again is harmless.
            val stopped = service.stopScript(id)
            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(stopped))
            assertEquals(second, stopped.getString(UiPreviewContract.KEY_RUN_ID))
            assertEquals(UiPreviewContract.EXIT_STOPPED, awaitExit(callback, second))
            instrumentation.waitForIdleSync()
            assertTrue(AutoJs.instance.scriptEngineService.scriptExecutions.none { it.source.name == name && !it.isFinished })
            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.stopScript(id)))
            callback.bounds.clear()
            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.setLayout(id, layout("<vertical><text text=\"after\"/></vertical>"), null)))
            assertEquals(2, awaitBounds(callback).length())
            assertTrue(callback.scriptErrors.isEmpty())
        }
    }

    @Test
    fun scriptErrorsExitAndReleaseAreReportedAndRequestsAreChecked() {
        assumeTrue("script runs need API 33", Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE)
        withEmbeddedSession { id, callback ->
            // An uncaught error: reported with its line, then the run ends with the error reason.
            val failing = runId(service.runScript(id, script("\"ui\";\nui.layout(<frame/>);\nthrow new Error(\"boom\");\n", "error-run")))
            val error = callback.scriptErrors.poll(15, TimeUnit.SECONDS) ?: throw AssertionError("onScriptError did not arrive")
            assertEquals(failing, error.getString(UiPreviewContract.KEY_RUN_ID))
            assertTrue(error.getString(UiPreviewContract.KEY_ERROR_MESSAGE)!!.contains("boom"))
            assertEquals(3, error.getInt(UiPreviewContract.KEY_LINE))
            assertEquals(UiPreviewContract.EXIT_ERROR, awaitExit(callback, failing))
            // exit() ends the run with the script reason; the session survives.
            val exiting = runId(service.runScript(id, script("\"ui\";\nui.layout(<frame/>);\nconsole.log(\"bye\");\nexit();\n", "exit-run")))
            awaitConsole(callback, "bye")
            assertEquals(UiPreviewContract.EXIT_SCRIPT, awaitExit(callback, exiting))
            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.resize(id, 300, 500)))
            // Requests: not a ui script, too long, missing text.
            assertEquals(UiPreviewContract.ERROR_SCRIPT_FAILED, error(service.runScript(id, script("console.log(1);", "plain"))))
            assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(service.runScript(id, script("x".repeat(UiPreviewContract.MAX_SCRIPT_CHARS + 1), "long"))))
            assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(service.runScript(id, Bundle())))
            // Releasing the session ends a running script with the release reason.
            val running = runId(service.runScript(id, script("\"ui\";\nui.layout(<frame/>);\nconsole.log(\"running\");\nsetInterval(() => {}, 1000);\n", "release-run")))
            awaitConsole(callback, "running")
            sessions.remove(id)
            service.release(id)
            assertEquals(UiPreviewContract.EXIT_RELEASED, awaitExit(callback, running))
            instrumentation.waitForIdleSync()
            assertTrue(AutoJs.instance.scriptEngineService.scriptExecutions.none { it.source.name == "release-run" && !it.isFinished })
        }
    }

    // ----------------------------------------------------------------------- project resources and projects (v3)

    /** The host's native layout sample: res/ with colors, drawables, five layouts (four of them included) and values. */
    private val focusProject = "sample/\u5e03\u5c40/Android \u539f\u751f\u5e03\u5c40/\u4e13\u6ce8\u65f6\u5149"

    /** The files under the asset directory [dir], as paths relative to it. */
    private fun assetFiles(dir: String, relative: String = ""): List<String> {
        val children = context.assets.list(if (relative.isEmpty()) dir else "$dir/$relative").orEmpty()
        if (children.isEmpty()) return listOf(relative)
        return children.flatMap { assetFiles(dir, if (relative.isEmpty()) it else "$relative/$it") }
    }

    private fun zipOf(entries: List<Pair<String, ByteArray>>): Pair<File, String> {
        val file = File(context.cacheDir, "ui-preview-${UUID.randomUUID()}.zip").also { files.add(it) }
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file to sha256(file.readBytes())
    }

    /** A zip of the asset directory [dir] with the files [include] accepts, and its digest. */
    private fun zipAssets(dir: String, include: (String) -> Boolean = { true }): Pair<File, String> =
        zipOf(assetFiles(dir).filter(include).map { path -> path to context.assets.open("$dir/$path").use { it.readBytes() } })

    private fun assetText(path: String) = context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun withResources(bundle: Bundle, zip: Pair<File, String>, layoutPath: String?, androidLayout: Boolean? = null) = bundle.apply {
        putParcelable(UiPreviewContract.KEY_RESOURCES_FD, ParcelFileDescriptor.open(zip.first, ParcelFileDescriptor.MODE_READ_ONLY))
        putString(UiPreviewContract.KEY_RESOURCES_SHA256, zip.second)
        layoutPath?.let { putString(UiPreviewContract.KEY_LAYOUT_PATH, it) }
        androidLayout?.let { putBoolean(UiPreviewContract.KEY_ANDROID_LAYOUT, it) }
    }

    @Test
    fun projectResourcesResolveInLayoutsAndTheUnpackedCopyFollowsTheDigest() {
        val info = service.previewInfo
        assertTrue(info.getBoolean(UiPreviewContract.KEY_RESOURCES_SUPPORTED))
        assertEquals(UiPreviewContract.VERSION, info.getIntArray(UiPreviewContract.KEY_SUPPORTED_CONTRACT_VERSIONS)!!.last())
        assertEquals(UiPreviewContract.HASH, info.getStringArray(UiPreviewContract.KEY_CONTRACT_HASHES)!!.last())

        val callback = Callback()
        val id = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 1080, 2200), callback))
        val layoutPath = "res/layout/activity_main.xml"
        val xml = assetText("$focusProject/$layoutPath")
        // Without the project's resources its @drawable / @dimen / @layout references cannot resolve.
        val bare = service.setLayout(id, layout(xml), snapshotFile().second)
        assertEquals(bare.getString(UiPreviewContract.KEY_ERROR_MESSAGE), UiPreviewContract.OUTCOME_FAILED, outcome(bare))
        // With them the layout applies the way ui.layoutFile applies it in a run of the project.
        val resources = zipAssets(focusProject) { it.startsWith("res/") }
        val result = service.setLayout(id, withResources(layout(xml), resources, layoutPath, androidLayout = true), snapshotFile().second)
        assertEquals(result.getString(UiPreviewContract.KEY_ERROR_MESSAGE), UiPreviewContract.OUTCOME_APPLIED, outcome(result))
        // Host roadmap A5: every attribute the sample writes takes effect; those its theme overlay and styles add are not reported.
        assertEquals("[]", result.getString(UiPreviewContract.KEY_DIAGNOSTICS_JSON))
        val bounds = awaitBounds(callback)
        val ids = (0 until bounds.length()).map { bounds.getJSONObject(it).optString(UiPreviewContract.Bounds.ID) }
        assertTrue(ids.toString(), "root" in ids)
        // The same layout without layoutPath resolves from the zip root as the working directory.
        val fromRoot = service.setLayout(id, withResources(layout(xml), resources, null, androidLayout = true), snapshotFile().second)
        assertEquals(fromRoot.getString(UiPreviewContract.KEY_ERROR_MESSAGE), UiPreviewContract.OUTCOME_APPLIED, outcome(fromRoot))
        // The unpacked copy is reused while the digest stays the same, and replaced when it changes.
        val archives = File(context.cacheDir, "ui-preview/$id/archives")
        fun copies() = archives.list().orEmpty().filter { it.startsWith("resources-") }.sorted()
        val first = copies()
        assertEquals(first.toString(), 1, first.size)
        val changed = zipAssets(focusProject) { it.startsWith("res/") && !it.endsWith("strings.xml") }
        assertEquals(UiPreviewContract.OUTCOME_FAILED, outcome(service.setLayout(id, withResources(layout(xml), changed, layoutPath, true), snapshotFile().second)))
        val second = copies()
        assertEquals(1, second.size)
        assertFalse(second == first)
        assertTrue(archives.list().orEmpty().none { it.startsWith("incoming") || it.endsWith(".partial") })
        // A v2 request afterwards goes back to the session's own working directory: the references fail again.
        assertEquals(UiPreviewContract.OUTCOME_FAILED, outcome(service.setLayout(id, layout(xml), snapshotFile().second)))
    }

    @Test
    fun invalidResourceZipsAreRejectedAndTheSessionStays() {
        val callback = Callback()
        val id = sessionId(create(request(UiPreviewContract.MODE_SNAPSHOT, 200, 200), callback))
        val xml = "<vertical><text text=\"ok\"/></vertical>"
        val good = zipOf(listOf("res/values/strings.xml" to "<resources/>".toByteArray()))
        val wrongDigest = good.first to UiPreviewRequests.sha256Hex("not the zip")
        assertEquals(UiPreviewContract.ERROR_ARCHIVE_INVALID, error(service.setLayout(id, withResources(layout(xml), wrongDigest, null), snapshotFile().second)))
        val escaping = zipOf(listOf("../escaped.xml" to "<resources/>".toByteArray()))
        assertEquals(UiPreviewContract.ERROR_ARCHIVE_INVALID, error(service.setLayout(id, withResources(layout(xml), escaping, null), snapshotFile().second)))
        assertFalse(File(context.cacheDir, "ui-preview/$id/escaped.xml").exists())
        assertFalse(File(context.cacheDir, "ui-preview/escaped.xml").exists())
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(service.setLayout(id, withResources(layout(xml), good, "../main.xml"), snapshotFile().second)))
        val noZip = layout(xml).apply { putString(UiPreviewContract.KEY_LAYOUT_PATH, "res/layout/main.xml") }
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(service.setLayout(id, noZip, snapshotFile().second)))
        assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.setLayout(id, withResources(layout(xml), good, null), snapshotFile().second)))
    }

    @Test
    fun aProjectRunWorksInItsUnpackedProject() {
        assumeTrue("script runs need API 33", Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE)
        withEmbeddedSession { id, callback ->
            val project = zipAssets(focusProject)
            val text = "\"ui\";\nui.useAndroidLayout();\nui.layoutFile('res/layout/activity_main.xml');\n" +
                "console.log('cwd ' + files.cwd());\nconsole.log('laid out ' + ui.root.getChildCount());\n"
            val request = script(text, "project-run").apply {
                putParcelable(UiPreviewContract.KEY_PROJECT_FD, ParcelFileDescriptor.open(project.first, ParcelFileDescriptor.MODE_READ_ONLY))
                putString(UiPreviewContract.KEY_PROJECT_SHA256, project.second)
                putString(UiPreviewContract.KEY_SCRIPT_PATH, "main.js")
            }
            val first = runId(service.runScript(id, request))
            val cwd = awaitConsole(callback, "cwd ").getString(UiPreviewContract.KEY_TEXT)!!
            assertTrue(cwd, cwd.contains("ui-preview/$id/archives/project-"))
            assertEquals(first, awaitConsole(callback, "laid out 1").getString(UiPreviewContract.KEY_RUN_ID))
            val copy = File(cwd.removePrefix("cwd ").trim())
            assertTrue(File(copy, "res/layout/view_timer.xml").isFile)
            // A plain v2 run replaces it and its copy goes with it.
            val second = runId(service.runScript(id, script("\"ui\";\nconsole.log('plain ' + files.cwd());\n", "plain-run")))
            assertEquals(UiPreviewContract.EXIT_REPLACED, awaitExit(callback, first))
            val plain = awaitConsole(callback, "plain ").getString(UiPreviewContract.KEY_TEXT)!!
            assertFalse(plain, plain.contains("archives"))
            assertFalse(copy.exists())
            service.stopScript(id)
            awaitExit(callback, second)
        }
    }
}
