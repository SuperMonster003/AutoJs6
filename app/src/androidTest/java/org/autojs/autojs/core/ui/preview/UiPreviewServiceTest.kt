package org.autojs.autojs.core.ui.preview

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Binds the ui-mode preview service from the instrumentation (same signature and process as the host) and
 * exercises contract v1: info, snapshot sessions with pixels and bounds, embedded sessions on API 30+,
 * failure positions, lenient bindings, the per-client limit, request limits, host-side stop and release.
 *
 * zh-CN: 以 instrumentation (与宿主同签名同进程) 绑定 ui 模式预览服务并验证契约 v1: 信息, 快照会话的像素与边界, API 30+ 的嵌入会话,
 * 失败定位, 宽松绑定, 每客户端上限, 请求上限, 宿主侧停止与释放.
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
        override fun onBounds(sessionId: String, bounds: Bundle) { this.bounds.add(bounds) }
        override fun onLayoutFailed(sessionId: String, failure: Bundle) { failures.add(failure) }
        override fun onSessionLost(sessionId: String, reason: Bundle) { lost.add(reason) }
        override fun onTap(sessionId: String, tap: Bundle) { taps.add(tap) }
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

    private fun awaitBounds(callback: Callback): JSONArray {
        val bounds = callback.bounds.poll(15, TimeUnit.SECONDS) ?: throw AssertionError("no bounds arrived")
        return JSONArray(bounds.getString(UiPreviewContract.KEY_BOUNDS_JSON))
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
        assertEquals(UiPreviewContract.VERSION, service.contractVersion)
        assertEquals(UiPreviewContract.HASH, service.contractHash)
        val info = service.previewInfo
        assertEquals(UiPreviewContract.STATUS_READY, info.getString(UiPreviewContract.KEY_STATUS))
        assertEquals(UiPreviewContract.HASH, info.getString(UiPreviewContract.KEY_CONTRACT_HASH))
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
    fun requestsBeyondTheLimitsAreRejected() {
        val callback = Callback()
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, error(create(request(UiPreviewContract.MODE_SNAPSHOT, 5000, 10), callback)))
        assertEquals(UiPreviewContract.ERROR_CONTRACT_MISMATCH, error(create(request(UiPreviewContract.MODE_SNAPSHOT, 10, 10, contractVersion = 2), callback)))
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
}
