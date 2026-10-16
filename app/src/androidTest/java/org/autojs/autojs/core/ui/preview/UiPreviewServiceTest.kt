package org.autojs.autojs.core.ui.preview

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.TypedValue
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
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
 * clicks, replacement, stop, errors, `exit()` and the release of a running session; contract v3 (host roadmap
 * `docs/dev/android-xml-attributes-roadmap.md` R3): the resources of a project in layouts and a project run; and
 * contract v4 (3-Block Compose roadmap P11.5, API 33+): dialogs inside the root, lifecycle events on `ui.emitter`
 * with the back key, window colors as strips and background, and the busy-wait limit.
 *
 * zh-CN: 以 instrumentation (与宿主同签名同进程) 绑定 ui 模式预览服务并验证契约 v1: 信息, 快照会话的像素与边界, API 30+ 的嵌入会话,
 * 失败定位, 宽松绑定, 每客户端上限, 请求上限, 宿主侧停止与释放; 以及契约 v2 (3-Block Compose 路线图 P10.6, API 33+): 嵌入根中的脚本运行及其控制台,
 * 点击, 替换, 停止, 错误, `exit()` 与运行中会话的释放; 契约 v3: 布局中的工程资源与工程运行; 契约 v4 (3-Block Compose 路线图 P11.5, API 33+):
 * 根容器内的对话框, `ui.emitter` 上的生命周期事件与返回键, 窗口颜色的色条与背景, 忙等上限.
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
        val handled = LinkedBlockingQueue<Bundle>()
        override fun onBounds(sessionId: String, bounds: Bundle) { this.bounds.add(bounds) }
        override fun onLayoutFailed(sessionId: String, failure: Bundle) { failures.add(failure) }
        override fun onSessionLost(sessionId: String, reason: Bundle) { lost.add(reason) }
        override fun onTap(sessionId: String, tap: Bundle) { taps.add(tap) }
        // Contract v2 (3-Block Compose roadmap P10.6): the running script's console, errors and end.
        override fun onConsole(sessionId: String, entry: Bundle) { console.add(entry) }
        override fun onScriptError(sessionId: String, error: Bundle) { scriptErrors.add(error) }
        override fun onScriptExit(sessionId: String, exit: Bundle) { exits.add(exit) }
        // Contract v4 (3-Block Compose roadmap P11.5): a lifecycle event reached the run.
        override fun onLifecycleHandled(sessionId: String, result: Bundle) { handled.add(result) }
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
        // v3 (3-Block Compose 1.8.0), v4 and v5 follow with their frozen hashes; v4 says what runs get beyond v3.
        assertEquals(
            listOf(UiPreviewContract.BASE_VERSION, UiPreviewContract.VERSION_V2, UiPreviewContract.VERSION_V3, UiPreviewContract.VERSION_V4, UiPreviewContract.VERSION),
            versions,
        )
        assertEquals(listOf(UiPreviewContract.HASH_V1, UiPreviewContract.HASH_V2, UiPreviewContract.HASH_V3, UiPreviewContract.HASH_V4, UiPreviewContract.HASH), hashes)
        val runs = Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE
        for (key in listOf(
            UiPreviewContract.KEY_DIALOGS_IN_CANVAS, UiPreviewContract.KEY_LIFECYCLE_EVENTS,
            UiPreviewContract.KEY_BUSY_WAIT_LIMIT_SUPPORTED, UiPreviewContract.KEY_WINDOW_FUNCTIONS_VISIBLE,
        )) {
            assertEquals(key, runs, info.getBoolean(key))
        }
        // v5: TSX runs need runs and TypeScript Engine, which this device may not have.
        assertTrue(info.containsKey(UiPreviewContract.KEY_TSX_RUN_SUPPORTED))
        if (!runs) assertFalse(info.getBoolean(UiPreviewContract.KEY_TSX_RUN_SUPPORTED))
        Log.i(TAG, "tsxRunSupported: ${info.getBoolean(UiPreviewContract.KEY_TSX_RUN_SUPPORTED)}")
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

    // ------------------------------------------------------------------------------------------------ TSX runs (v5)

    private fun tsx(text: String, name: String) = script(text, name).apply { putString(UiPreviewContract.KEY_SCRIPT_DIALECT, UiPreviewContract.SCRIPT_DIALECT_TSX) }

    /** The next `onScriptError` of [runId]; a TSX run may first spend a few seconds in the compiler. */
    private fun awaitError(callback: Callback, runId: String): Bundle {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (true) {
            val error = callback.scriptErrors.poll((deadline - SystemClock.uptimeMillis()).coerceAtLeast(1), TimeUnit.MILLISECONDS)
                ?: throw AssertionError("onScriptError for $runId did not arrive")
            if (error.getString(UiPreviewContract.KEY_RUN_ID) == runId) return error
        }
    }

    @Test
    fun tsxRunsAreCompiledFirstAndReportTheirErrorsOnTsxLines() {
        assumeTrue("script runs need API 33", Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE)
        assumeTrue("TSX runs need TypeScript Engine", service.previewInfo.getBoolean(UiPreviewContract.KEY_TSX_RUN_SUPPORTED))
        withEmbeddedSession { id, callback ->
            // The TypeScript Engine template's way: an inline onClick, and the ui module through `as any` for ids.
            val name = "tsx-${UUID.randomUUID()}"
            val counter = "\"ui\";\n\nlet n: number = 0;\nui.layout(\n    <vertical>\n" +
                "        <button id='b' text='count 0' onClick={() => {\n            n++;\n            (ui as any).b.setText('count ' + n);\n" +
                "            console.log('clicked ' + n);\n        }} />\n    </vertical>\n);\nconsole.log('tsx started');\n"
            val first = runId(service.runScript(id, tsx(counter, name)))
            val started = SystemClock.uptimeMillis()
            assertEquals(first, awaitConsole(callback, "tsx started").getString(UiPreviewContract.KEY_RUN_ID))
            Log.i(TAG, "TSX run started after ${SystemClock.uptimeMillis() - started} ms")
            // The run is an execution under the script's name, with the compiled layout in the session's root.
            val root = runRoot(name)
            assertEquals("count 0", textOf(root, "b"))
            instrumentation.runOnMainSync { JsViewHelper.findViewByStringId(root, "b")!!.performClick() }
            awaitConsole(callback, "clicked 1")
            assertEquals("count 1", textOf(root, "b"))
            // A text that does not compile: every error on the console, the first one with its TSX line and column,
            // the error exit, and nothing of the script ran.
            val wrong = "\"ui\";\nui.layout(<frame />);\nconst x: number = 'one';\nconsole.log('not compiled ' + x);\n"
            val failing = runId(service.runScript(id, tsx(wrong, "tsx-wrong")))
            assertEquals(UiPreviewContract.EXIT_REPLACED, awaitExit(callback, first))
            val error = awaitError(callback, failing)
            assertEquals(UiPreviewContract.ERROR_COMPILE_FAILED, error.getString(UiPreviewContract.KEY_ERROR_CODE))
            assertTrue(error.getString(UiPreviewContract.KEY_ERROR_MESSAGE)!!, error.getString(UiPreviewContract.KEY_ERROR_MESSAGE)!!.startsWith("TS2322: "))
            assertEquals(3, error.getInt(UiPreviewContract.KEY_LINE))
            assertEquals(7, error.getInt(UiPreviewContract.KEY_COLUMN))
            val logged = awaitConsole(callback, "TS2322")
            assertEquals(failing, logged.getString(UiPreviewContract.KEY_RUN_ID))
            assertEquals(Log.ERROR, logged.getInt(UiPreviewContract.KEY_LEVEL))
            assertEquals(UiPreviewContract.EXIT_ERROR, awaitExit(callback, failing))
            assertTrue(callback.console.none { it.getString(UiPreviewContract.KEY_TEXT).orEmpty().contains("not compiled") })
            // An uncaught error of a TSX run names its TSX line: the interface the compiler erased does not shift it.
            val throwing = "\"ui\";\n\ninterface Point {\n    x: number;\n}\n\nconst p: Point = { x: 1 };\nui.layout(<frame />);\n" +
                "if (p.x > 0) {\n    throw new Error('tsx boom');\n}\n"
            val thrown = runId(service.runScript(id, tsx(throwing, "tsx-throw")))
            val runtimeError = awaitError(callback, thrown)
            assertEquals(null, runtimeError.getString(UiPreviewContract.KEY_ERROR_CODE))
            assertTrue(runtimeError.getString(UiPreviewContract.KEY_ERROR_MESSAGE)!!.contains("tsx boom"))
            assertEquals(10, runtimeError.getInt(UiPreviewContract.KEY_LINE))
            assertEquals(UiPreviewContract.EXIT_ERROR, awaitExit(callback, thrown))
            // A stop right after runScript ends the run whether or not it is still compiling, exactly once.
            val stoppedEarly = runId(service.runScript(id, tsx(counter, "tsx-stopped")))
            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.stopScript(id)))
            assertEquals(UiPreviewContract.EXIT_STOPPED, awaitExit(callback, stoppedEarly))
            SystemClock.sleep(3_000)
            assertTrue(callback.exits.none { it.getString(UiPreviewContract.KEY_RUN_ID) == stoppedEarly })
            assertTrue(callback.console.none { it.getString(UiPreviewContract.KEY_RUN_ID) == stoppedEarly })
            // Requests: an unknown dialect is refused.
            assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(service.runScript(id, script(counter, name).apply { putString(UiPreviewContract.KEY_SCRIPT_DIALECT, "ts") })))
            instrumentation.waitForIdleSync()
            assertTrue(AutoJs.instance.scriptEngineService.scriptExecutions.none { it.source.name.startsWith("tsx-") && !it.isFinished })
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

    /**
     * Host roadmap A7: qualified resource directories follow the session (night mode, locale, and its size as the
     * screen width); a locale match comes before width, and width before night mode, as in Android.
     */
    @Test
    fun qualifiedResourcesFollowTheSessionNightModeLocaleAndSize() {
        fun dimen(px: Int) = "<resources><dimen name=\"box\">${px}px</dimen></resources>".toByteArray()
        val layoutPath = "res/layout/main.xml"
        val xml = "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">" +
            "<View android:id=\"@+id/box\" android:layout_width=\"@dimen/box\" android:layout_height=\"10px\"/></FrameLayout>"
        val resources = zipOf(
            listOf(
                layoutPath to xml.toByteArray(),
                "res/values/dimens.xml" to dimen(10),
                "res/values-night/dimens.xml" to dimen(20),
                "res/values-fr/dimens.xml" to dimen(30),
                "res/values-w300dp/dimens.xml" to dimen(40),
            ),
        )

        fun boxWidth(id: String, callback: Callback): Int {
            val result = service.setLayout(id, withResources(layout(xml), resources, layoutPath, androidLayout = true), snapshotFile().second)
            assertEquals(result.getString(UiPreviewContract.KEY_ERROR_MESSAGE), UiPreviewContract.OUTCOME_APPLIED, outcome(result))
            val bounds = awaitBounds(callback)
            val box = (0 until bounds.length()).map { bounds.getJSONObject(it) }.first { it.optString(UiPreviewContract.Bounds.ID) == "box" }
            return box.getInt(UiPreviewContract.Bounds.RIGHT) - box.getInt(UiPreviewContract.Bounds.LEFT)
        }

        fun session(nightMode: Int, localeTag: String?, width: Int): Pair<String, Callback> {
            val callback = Callback()
            val request = request(UiPreviewContract.MODE_SNAPSHOT, width, 200).apply {
                putInt(UiPreviewContract.KEY_DENSITY_DPI, 160)
                putInt(UiPreviewContract.KEY_NIGHT_MODE, nightMode)
                localeTag?.let { putString(UiPreviewContract.KEY_LOCALE_TAG, it) }
            }
            return sessionId(create(request, callback)) to callback
        }

        // A client has at most two sessions at a time.
        val (day, dayCallback) = session(UiPreviewContract.NIGHT_MODE_NO, "en-US", 200)
        assertEquals(10, boxWidth(day, dayCallback))
        service.release(day)
        val (french, frenchCallback) = session(UiPreviewContract.NIGHT_MODE_YES, "fr-FR", 200)
        assertEquals(30, boxWidth(french, frenchCallback))
        service.release(french)
        val (night, nightCallback) = session(UiPreviewContract.NIGHT_MODE_YES, "en-US", 200)
        assertEquals(20, boxWidth(night, nightCallback))
        // 400 px at 160 dpi is 400 dp wide: values-w300dp applies, before night mode; a resize is followed by the next layout.
        assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.resize(night, 400, 200)))
        nightCallback.bounds.clear()
        assertEquals(40, boxWidth(night, nightCallback))
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

    // ------------------------------------------------------- dialogs, lifecycle, window colors, busy wait (v4)

    private fun lifecycle(event: String) = Bundle().apply { putString(UiPreviewContract.KEY_LIFECYCLE_EVENT, event) }

    private fun awaitHandled(callback: Callback, event: String): Bundle {
        val handled = callback.handled.poll(15, TimeUnit.SECONDS) ?: throw AssertionError("onLifecycleHandled for $event did not arrive")
        assertEquals(event, handled.getString(UiPreviewContract.KEY_LIFECYCLE_EVENT))
        return handled
    }

    /** The session root around the content root of the run [name]. */
    private fun sessionRoot(name: String): UiPreviewRootView = runRoot(name).parent as UiPreviewRootView

    /** Waits until the root shows [count] dialogs and returns the top one (null for none). */
    private fun awaitDialogs(root: UiPreviewRootView, count: Int): MaterialDialog? {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var dialogs: List<MaterialDialog> = emptyList()
            instrumentation.runOnMainSync { dialogs = root.dialogLayer.dialogs }
            if (dialogs.size == count) return dialogs.lastOrNull()
            SystemClock.sleep(50)
        }
        throw AssertionError("the preview root did not come to show $count dialog(s)")
    }

    private fun awaitDialog(root: UiPreviewRootView): MaterialDialog = awaitDialogs(root, 1)!!

    private fun isInside(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun pixels(view: View): Bitmap {
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    @Test
    fun dialogsOfARunShowInsideTheRootAndTakeTheBackKey() {
        assumeTrue("script runs need API 33", Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE)
        withEmbeddedSession { id, callback ->
            val name = "dialog-run-${UUID.randomUUID()}"
            val alert = "\"ui\";\nui.layout(<frame><text text=\"main\"/></frame>);\n" +
                "dialogs.alert(\"Alert title\", \"Alert body\", () => console.log(\"alert closed\"));\nconsole.log(\"alert shown\");\n"
            val first = runId(service.runScript(id, script(alert, name)))
            awaitConsole(callback, "alert shown")
            val root = sessionRoot(name)
            val dialog = awaitDialog(root)
            // The dialog's view is part of the embedded root, and its own window was never added.
            instrumentation.runOnMainSync {
                assertTrue(dialog.isShowing)
                assertTrue(isInside(dialog.view, root))
                assertTrue(dialog.view.isAttachedToWindow)
                assertFalse(dialog.window?.peekDecorView()?.isAttachedToWindow == true)
                assertTrue(dialog.view.width > 0 && dialog.view.width <= root.width)
            }
            // Its button works like in a window: the script's callback runs and the dialog leaves the root.
            instrumentation.runOnMainSync { dialog.getActionButton(DialogAction.POSITIVE).performClick() }
            awaitConsole(callback, "alert closed")
            instrumentation.runOnMainSync {
                assertTrue(root.dialogLayer.dialogs.isEmpty())
                assertFalse(dialog.isShowing)
                assertFalse(dialog.view.isAttachedToWindow)
            }
            // A cancelable dialog takes the back key: it is cancelled, the key counts as consumed and the run goes on.
            val confirm = "\"ui\";\nui.layout(<frame/>);\n" +
                "dialogs.confirm(\"Confirm title\", \"Confirm body\", (ok) => console.log(\"confirmed \" + ok));\nconsole.log(\"confirm shown\");\n"
            val second = runId(service.runScript(id, script(confirm, name)))
            assertEquals(UiPreviewContract.EXIT_REPLACED, awaitExit(callback, first))
            awaitConsole(callback, "confirm shown")
            awaitDialog(sessionRoot(name))
            assertEquals(second, runId(service.sendLifecycle(id, lifecycle(UiPreviewContract.LIFECYCLE_BACK_PRESSED))))
            val back = awaitHandled(callback, UiPreviewContract.LIFECYCLE_BACK_PRESSED)
            assertEquals(second, back.getString(UiPreviewContract.KEY_RUN_ID))
            assertTrue(back.getBoolean(UiPreviewContract.KEY_CONSUMED))
            awaitConsole(callback, "confirmed false")
            awaitDialogs(sessionRoot(name), 0)
            assertNull(callback.exits.poll(500, TimeUnit.MILLISECONDS))
            // A dialog left open goes with its run.
            val third = runId(service.runScript(id, script(alert, name)))
            assertEquals(UiPreviewContract.EXIT_REPLACED, awaitExit(callback, second))
            awaitDialog(sessionRoot(name))
            service.stopScript(id)
            assertEquals(UiPreviewContract.EXIT_STOPPED, awaitExit(callback, third))
            instrumentation.runOnMainSync { assertTrue(root.dialogLayer.dialogs.isEmpty()) }
            assertTrue(callback.scriptErrors.isEmpty())
        }
    }

    @Test
    fun lifecycleEventsReachUiEmitterAndAnUnconsumedBackKeyExits() {
        assumeTrue("script runs need API 33", Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE)
        withEmbeddedSession { id, callback ->
            // Without a run there is nothing to deliver to; unknown events are refused.
            assertEquals(UiPreviewContract.ERROR_NOT_RUNNING, error(service.sendLifecycle(id, lifecycle(UiPreviewContract.LIFECYCLE_PAUSE))))
            val text = "\"ui\";\nui.layout(<frame/>);\nlet backs = 0;\n" +
                "ui.emitter.on(\"pause\", () => console.log(\"paused\"));\n" +
                "ui.emitter.on(\"resume\", () => console.log(\"resumed\"));\n" +
                "ui.emitter.on(\"back_pressed\", (e) => {\n    backs++;\n    console.log(\"back \" + backs);\n    if (backs === 1) e.consumed = true;\n});\n" +
                "console.log(\"ready \" + (ui.emitter !== null));\n"
            val run = runId(service.runScript(id, script(text, "lifecycle-run")))
            awaitConsole(callback, "ready true")
            assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(service.sendLifecycle(id, lifecycle("create"))))
            assertEquals(run, runId(service.sendLifecycle(id, lifecycle(UiPreviewContract.LIFECYCLE_PAUSE))))
            awaitConsole(callback, "paused")
            assertFalse(awaitHandled(callback, UiPreviewContract.LIFECYCLE_PAUSE).getBoolean(UiPreviewContract.KEY_CONSUMED))
            runId(service.sendLifecycle(id, lifecycle(UiPreviewContract.LIFECYCLE_RESUME)))
            awaitConsole(callback, "resumed")
            awaitHandled(callback, UiPreviewContract.LIFECYCLE_RESUME)
            // The first back key is consumed by the handler, so the run goes on.
            runId(service.sendLifecycle(id, lifecycle(UiPreviewContract.LIFECYCLE_BACK_PRESSED)))
            awaitConsole(callback, "back 1")
            assertTrue(awaitHandled(callback, UiPreviewContract.LIFECYCLE_BACK_PRESSED).getBoolean(UiPreviewContract.KEY_CONSUMED))
            assertNull(callback.exits.poll(500, TimeUnit.MILLISECONDS))
            // The second is not: the run ends with exit, as an Activity would finish.
            runId(service.sendLifecycle(id, lifecycle(UiPreviewContract.LIFECYCLE_BACK_PRESSED)))
            awaitConsole(callback, "back 2")
            assertFalse(awaitHandled(callback, UiPreviewContract.LIFECYCLE_BACK_PRESSED).getBoolean(UiPreviewContract.KEY_CONSUMED))
            assertEquals(UiPreviewContract.EXIT_SCRIPT, awaitExit(callback, run))
            assertEquals(UiPreviewContract.ERROR_NOT_RUNNING, error(service.sendLifecycle(id, lifecycle(UiPreviewContract.LIFECYCLE_RESUME))))
            assertTrue(callback.scriptErrors.isEmpty())
        }
    }

    @Test
    fun windowColorsShowAsStripsAndBackgroundAndOtherFunctionsWarnInTheRunConsole() {
        assumeTrue("script runs need API 33", Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE)
        withEmbeddedSession { id, callback ->
            val name = "colors-run-${UUID.randomUUID()}"
            val text = "\"ui\";\nui.layout(<frame><text text=\"x\"/></frame>);\n" +
                "ui.statusBarColor(\"#ff0000\");\nui.navigationBarColor(\"#0000ff\");\nui.backgroundColor(\"#00ff00\");\n" +
                "ui.keepScreenOn();\nui.keepScreenOn();\nconsole.log(\"colored\");\n"
            val run = runId(service.runScript(id, script(text, name)))
            // The ignored function warns once, in the run's own console that the client sees.
            awaitConsole(callback, "keepScreenOn has no effect")
            awaitConsole(callback, "colored")
            assertTrue(callback.console.none { it.getString(UiPreviewContract.KEY_TEXT).orEmpty().contains("keepScreenOn") })
            instrumentation.waitForIdleSync()
            val root = sessionRoot(name)
            val content = runRoot(name)
            val density = root.resources.displayMetrics
            val status = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, UiPreviewContract.STATUS_BAR_STRIP_DP.toFloat(), density).toInt()
            val navigation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, UiPreviewContract.NAVIGATION_BAR_STRIP_DP.toFloat(), density).toInt()
            assertEquals(status, content.top)
            assertEquals(navigation, root.height - content.bottom)
            val bitmap = pixels(root)
            assertEquals(Color.RED, bitmap.getPixel(root.width / 2, status / 2))
            assertEquals(Color.BLUE, bitmap.getPixel(root.width / 2, root.height - navigation / 2))
            assertEquals(Color.GREEN, bitmap.getPixel(root.width - 2, root.height / 2))
            // Once the run is over, a layout shows on the plain root again.
            service.stopScript(id)
            assertEquals(UiPreviewContract.EXIT_STOPPED, awaitExit(callback, run))
            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.setLayout(id, layout("<frame><text text=\"plain\"/></frame>"), null)))
            instrumentation.waitForIdleSync()
            assertEquals(0, root.contentFrame.top)
            assertTrue(pixels(root).getPixel(root.width / 2, status / 2) != Color.RED)
        }
    }

    @Test
    fun aBusyMainThreadEndsTheRunWithinItsLimitAndAStopReachesABusyHandler() {
        assumeTrue("script runs need API 33", Build.VERSION.SDK_INT >= UiPreviewContract.MIN_API_INTERACTIVE)
        withEmbeddedSession { id, callback ->
            // Limits outside the contract are refused.
            assertEquals(
                UiPreviewContract.ERROR_INVALID_REQUEST,
                error(service.runScript(id, script("\"ui\";\n", "bad-limit").apply { putLong(UiPreviewContract.KEY_BUSY_WAIT_LIMIT_MS, 500L) })),
            )
            // The body never returns to the main thread, and a catch block cannot keep it going.
            val spin = "\"ui\";\nui.layout(<frame/>);\nconsole.log(\"spinning\");\nwhile (true) {\n    try {\n        while (true) {}\n" +
                "    } catch (e) {\n        console.log(\"caught \" + e);\n    }\n}\n"
            val startedAt = SystemClock.uptimeMillis()
            val busy = runId(service.runScript(id, script(spin, "busy-run").apply { putLong(UiPreviewContract.KEY_BUSY_WAIT_LIMIT_MS, 1_000L) }))
            awaitConsole(callback, "spinning")
            val error = callback.scriptErrors.poll(15, TimeUnit.SECONDS) ?: throw AssertionError("onScriptError did not arrive")
            assertEquals(busy, error.getString(UiPreviewContract.KEY_RUN_ID))
            assertEquals(UiPreviewContract.ERROR_BUSY_WAIT, error.getString(UiPreviewContract.KEY_ERROR_CODE))
            assertEquals(6, error.getInt(UiPreviewContract.KEY_LINE))
            assertTrue(error.getLong(UiPreviewContract.KEY_ELAPSED_MS) >= 1_000L)
            assertEquals(UiPreviewContract.EXIT_ERROR, awaitExit(callback, busy))
            assertTrue(SystemClock.uptimeMillis() - startedAt < 1_000L + 5_000L)
            assertTrue(callback.console.none { it.getString(UiPreviewContract.KEY_TEXT).orEmpty().contains("caught") })
            // The main thread is back: the session answers at once.
            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.resize(id, 400, 600)))
            // With the watch off, a click handler that never returns still yields to stopScript.
            val handler = "\"ui\";\nui.layout(<frame><button id=\"b\" text=\"spin\"/></frame>);\n" +
                "ui.b.on(\"click\", () => {\n    console.log(\"spin\");\n    while (true) {}\n});\nconsole.log(\"ready\");\n"
            val spinning = runId(service.runScript(id, script(handler, "busy-stop-run").apply { putInt(UiPreviewContract.KEY_BUSY_WAIT_LIMIT_MS, 0) }))
            awaitConsole(callback, "ready")
            val button = JsViewHelper.findViewByStringId(runRoot("busy-stop-run"), "b")!!
            Handler(Looper.getMainLooper()).post { button.performClick() }
            awaitConsole(callback, "spin")
            SystemClock.sleep(1_500)
            assertNull(callback.exits.poll(10, TimeUnit.MILLISECONDS))
            val stopped = service.stopScript(id)
            assertEquals(stopped.getString(UiPreviewContract.KEY_ERROR_MESSAGE), UiPreviewContract.OUTCOME_APPLIED, outcome(stopped))
            assertEquals(UiPreviewContract.EXIT_STOPPED, awaitExit(callback, spinning))
            assertTrue(callback.scriptErrors.isEmpty())
            assertEquals(UiPreviewContract.OUTCOME_APPLIED, outcome(service.resize(id, 300, 500)))
        }
    }

    private companion object {
        const val TAG = "UiPreviewServiceTest"
    }
}
