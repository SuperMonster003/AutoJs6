package org.autojs.autojs.core.ui.preview

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.util.Log
import android.view.Display
import org.autojs.plugin.ui.preview.IUiPreviewCallback
import org.autojs.plugin.ui.preview.IUiPreviewService
import org.autojs.plugin.ui.preview.UiPreviewContract
import org.autojs.autojs6.R
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The ui-mode layout preview service (3-Block Compose roadmap P10.2, D46 - D48, D50): exported with the
 * signature-level PLUGIN permission, action `org.autojs.plugin.ui.PREVIEW`, contract v1. Sessions live on the
 * main thread; Binder calls validate on their own thread, run the session work on the main thread with a
 * bounded wait, and never block the main thread on a client. A session is released when the client releases
 * it, when its callback Binder dies, when the host stops its runtime, when a layout request times out, or
 * when the service is destroyed.
 *
 * zh-CN: ui 模式布局预览服务 (3-Block Compose 路线图 P10.2, D46 - D48, D50): 以签名级 PLUGIN 权限导出, action
 * `org.autojs.plugin.ui.PREVIEW`, 契约 v1. 会话在主线程; Binder 调用在自己的线程校验, 把会话工作交给主线程并限时等待, 从不让主线程等待客户端.
 * 会话在客户端释放, 回调 Binder 死亡, 宿主停止其运行时, 布局请求超时或服务销毁时释放.
 */
class UiPreviewService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessions = ConcurrentHashMap<String, UiPreviewSession>()
    private val random = SecureRandom()
    private var counter = 0

    private val binder = object : IUiPreviewService.Stub() {

        // The frozen v1 base, so a v1 client keeps working; getPreviewInfo carries the current version and hash.
        // zh-CN: 冻结的 v1 基础面, 让 v1 客户端照常工作; 当前版本与哈希由 getPreviewInfo 给出.
        override fun getContractVersion(): Int = UiPreviewContract.BASE_VERSION

        override fun getContractHash(): String = UiPreviewContract.HASH_V1

        override fun getPreviewInfo(): Bundle {
            enforcePermission()
            return previewInfo()
        }

        override fun createSession(request: Bundle?, callback: IUiPreviewCallback?): Bundle {
            enforcePermission()
            val uid = Binder.getCallingUid()
            if (request == null || callback == null) return failure(UiPreviewContract.ERROR_INVALID_REQUEST, "request and callback are required")
            return try {
                val params = UiPreviewSessionParams(
                    mode = request.getString(UiPreviewContract.KEY_MODE).orEmpty(),
                    widthPx = request.getInt(UiPreviewContract.KEY_WIDTH_PX),
                    heightPx = request.getInt(UiPreviewContract.KEY_HEIGHT_PX),
                    densityDpi = request.getInt(UiPreviewContract.KEY_DENSITY_DPI),
                    nightMode = request.getInt(UiPreviewContract.KEY_NIGHT_MODE),
                    localeTag = request.getString(UiPreviewContract.KEY_LOCALE_TAG).orEmpty(),
                    fontScale = request.getFloat(UiPreviewContract.KEY_FONT_SCALE),
                    displayId = request.getInt(UiPreviewContract.KEY_DISPLAY_ID, Display.DEFAULT_DISPLAY),
                    label = UiPreviewRequests.sanitizeLabel(request.getString(UiPreviewContract.KEY_LABEL), getString(R.string.ui_preview_default_label)),
                    clientVersionCode = request.getLong(UiPreviewContract.KEY_CLIENT_VERSION_CODE),
                )
                UiPreviewRequests.validateSession(params, request.getInt(UiPreviewContract.KEY_CONTRACT_VERSION), Build.VERSION.SDK_INT)
                val hostToken = request.getBinder(UiPreviewContract.KEY_HOST_TOKEN)
                val inputTransferToken: Parcelable? = if (Build.VERSION.SDK_INT >= 35) {
                    request.getParcelable(UiPreviewContract.KEY_INPUT_TRANSFER_TOKEN, android.window.InputTransferToken::class.java)
                } else {
                    null
                }
                // A cold host start can outlive the wait; the ticket tells the main thread to release what it created.
                // zh-CN: 宿主冷启动可能超过等待时间; 票据让主线程释放它已创建的会话.
                val ticket = java.util.concurrent.atomic.AtomicBoolean(false)
                onMain(UiPreviewContract.SESSION_CREATE_TIMEOUT_MS) { createOnMain(uid, params, hostToken, inputTransferToken, callback, ticket) }
                    ?: run {
                        ticket.set(true)
                        failure(UiPreviewContract.ERROR_TIMEOUT, "the preview runtime did not start within ${UiPreviewContract.SESSION_CREATE_TIMEOUT_MS} ms")
                    }
            } catch (e: UiPreviewRequestException) {
                failure(e.code, e.message ?: e.code)
            }
        }

        override fun setLayout(sessionId: String?, layout: Bundle?, output: ParcelFileDescriptor?): Bundle {
            enforcePermission()
            try {
                val session = sessionOf(sessionId) ?: return failure(UiPreviewContract.ERROR_SESSION_NOT_FOUND, "no session $sessionId")
                if (layout == null) return failure(UiPreviewContract.ERROR_INVALID_REQUEST, "layout is required")
                val xml = layout.getString(UiPreviewContract.KEY_XML)
                val bindingsJson = layout.getString(UiPreviewContract.KEY_BINDINGS_JSON)
                UiPreviewRequests.validateLayout(xml, bindingsJson, layout.getString(UiPreviewContract.KEY_XML_SHA256), session.params.mode, output != null)
                val result = onMain(UiPreviewContract.LAYOUT_TIMEOUT_MS) { session.applyLayout(xml!!, bindingsJson) }
                if (result == null) {
                    session.timedOut = true
                    Log.w(TAG, "[${session.id}] layout timed out after ${UiPreviewContract.LAYOUT_TIMEOUT_MS} ms; releasing when the main thread returns")
                    mainHandler.post { session.release(UiPreviewContract.REASON_TIMEOUT, notify = true) }
                    return Bundle().apply {
                        putString(UiPreviewContract.KEY_OUTCOME, UiPreviewContract.OUTCOME_TIMED_OUT)
                        putString(UiPreviewContract.KEY_ERROR_CODE, UiPreviewContract.ERROR_TIMEOUT)
                        putString(UiPreviewContract.KEY_ERROR_MESSAGE, "the layout did not finish within ${UiPreviewContract.LAYOUT_TIMEOUT_MS} ms")
                    }
                }
                result.failure?.let { failure ->
                    return Bundle().apply {
                        putString(UiPreviewContract.KEY_OUTCOME, UiPreviewContract.OUTCOME_FAILED)
                        putString(UiPreviewContract.KEY_ERROR_CODE, failure.code)
                        putString(UiPreviewContract.KEY_ERROR_MESSAGE, failure.message)
                        putInt(UiPreviewContract.KEY_ELEMENT_INDEX, failure.elementIndex)
                        failure.attrName?.let { putString(UiPreviewContract.KEY_ATTR_NAME, it) }
                        putInt(UiPreviewContract.KEY_LINE, failure.line)
                        putInt(UiPreviewContract.KEY_COLUMN, failure.column)
                        putInt(UiPreviewContract.KEY_ELEMENT_COUNT, result.elementCount)
                        putLong(UiPreviewContract.KEY_ELAPSED_MS, result.elapsedMs)
                        putString(UiPreviewContract.KEY_DIAGNOSTICS_JSON, UiPreviewBounds.encodeDiagnostics(result.diagnostics))
                    }
                }
                val response = Bundle().apply {
                    putString(UiPreviewContract.KEY_OUTCOME, UiPreviewContract.OUTCOME_APPLIED)
                    putInt(UiPreviewContract.KEY_ELEMENT_COUNT, result.elementCount)
                    putLong(UiPreviewContract.KEY_ELAPSED_MS, result.elapsedMs)
                    putString(UiPreviewContract.KEY_DIAGNOSTICS_JSON, UiPreviewBounds.encodeDiagnostics(result.diagnostics))
                }
                val pixels = result.snapshot
                if (pixels != null && output != null) {
                    // The digest and the write happen on this Binder thread; the main thread only drew the pixels.
                    // zh-CN: 摘要与写入在这个 Binder 线程完成; 主线程只负责绘制像素.
                    try {
                        ParcelFileDescriptor.AutoCloseOutputStream(output).use { it.write(pixels) }
                    } catch (e: IOException) {
                        return failure(UiPreviewContract.ERROR_OUTPUT_TRANSPORT, "snapshot could not be written: ${e.message}")
                    }
                    response.putLong(UiPreviewContract.KEY_SNAPSHOT_BYTE_LENGTH, pixels.size.toLong())
                    response.putString(UiPreviewContract.KEY_SNAPSHOT_SHA256, UiPreviewRequests.sha256Hex(pixels))
                    response.putString(UiPreviewContract.KEY_SNAPSHOT_FORMAT, UiPreviewContract.SNAPSHOT_FORMAT_ARGB8888_ROW_MAJOR)
                    response.putInt(UiPreviewContract.KEY_SNAPSHOT_WIDTH_PX, result.snapshotWidth)
                    response.putInt(UiPreviewContract.KEY_SNAPSHOT_HEIGHT_PX, result.snapshotHeight)
                } else if (output != null) {
                    runCatching { output.close() }
                }
                return response
            } catch (e: UiPreviewRequestException) {
                runCatching { output?.close() }
                return failure(e.code, e.message ?: e.code)
            }
        }

        override fun resize(sessionId: String?, widthPx: Int, heightPx: Int): Bundle {
            enforcePermission()
            val session = sessionOf(sessionId) ?: return failure(UiPreviewContract.ERROR_SESSION_NOT_FOUND, "no session $sessionId")
            return try {
                UiPreviewRequests.validateSize(widthPx, heightPx)
                onMain(UiPreviewContract.LAYOUT_TIMEOUT_MS) { session.resize(widthPx, heightPx); applied() }
                    ?: failure(UiPreviewContract.ERROR_TIMEOUT, "resize timed out")
            } catch (e: UiPreviewRequestException) {
                failure(e.code, e.message ?: e.code)
            }
        }

        override fun release(sessionId: String?) {
            enforcePermission()
            val session = sessionOf(sessionId) ?: return
            mainHandler.post { session.release("client", notify = false) }
        }

        override fun reattach(sessionId: String?): Bundle {
            enforcePermission()
            val session = sessionOf(sessionId) ?: return failure(UiPreviewContract.ERROR_SESSION_NOT_FOUND, "no session $sessionId")
            return try {
                onMain(UiPreviewContract.LAYOUT_TIMEOUT_MS) { session.reattach() } ?: failure(UiPreviewContract.ERROR_TIMEOUT, "reattach timed out")
            } catch (e: UiPreviewRequestException) {
                failure(e.code, e.message ?: e.code)
            }
        }

        override fun setPointerMode(sessionId: String?, mode: Int): Bundle {
            enforcePermission()
            val session = sessionOf(sessionId) ?: return failure(UiPreviewContract.ERROR_SESSION_NOT_FOUND, "no session $sessionId")
            return try {
                UiPreviewRequests.validatePointerMode(mode, Build.VERSION.SDK_INT)
                onMain(UiPreviewContract.LAYOUT_TIMEOUT_MS) { session.setPointerMode(mode); applied().apply { putInt(UiPreviewContract.KEY_POINTER_MODE, mode) } }
                    ?: failure(UiPreviewContract.ERROR_TIMEOUT, "setPointerMode timed out")
            } catch (e: UiPreviewRequestException) {
                failure(e.code, e.message ?: e.code)
            }
        }

        override fun setHighlight(sessionId: String?, elementIndex: Int): Bundle {
            enforcePermission()
            val session = sessionOf(sessionId) ?: return failure(UiPreviewContract.ERROR_SESSION_NOT_FOUND, "no session $sessionId")
            return onMain(UiPreviewContract.LAYOUT_TIMEOUT_MS) { session.setHighlight(elementIndex); applied() }
                ?: failure(UiPreviewContract.ERROR_TIMEOUT, "setHighlight timed out")
        }

        // v2 (3-Block Compose roadmap P10.6): a real run of the whole script in the session's root.
        override fun runScript(sessionId: String?, request: Bundle?): Bundle {
            enforcePermission()
            val session = sessionOf(sessionId) ?: return failure(UiPreviewContract.ERROR_SESSION_NOT_FOUND, "no session $sessionId")
            if (request == null) return failure(UiPreviewContract.ERROR_INVALID_REQUEST, "request is required")
            return try {
                val text = request.getString(UiPreviewContract.KEY_SCRIPT_TEXT)
                UiPreviewRequests.validateScript(text, session.params.mode, Build.VERSION.SDK_INT)
                val name = UiPreviewRequests.sanitizeLabel(request.getString(UiPreviewContract.KEY_SCRIPT_NAME), session.params.label)
                onMain(UiPreviewContract.SCRIPT_START_TIMEOUT_MS) { session.runScript(text!!, name) }
                    ?: failure(UiPreviewContract.ERROR_TIMEOUT, "the script did not start within ${UiPreviewContract.SCRIPT_START_TIMEOUT_MS} ms")
            } catch (e: UiPreviewRequestException) {
                failure(e.code, e.message ?: e.code)
            }
        }

        override fun stopScript(sessionId: String?): Bundle {
            enforcePermission()
            val session = sessionOf(sessionId) ?: return failure(UiPreviewContract.ERROR_SESSION_NOT_FOUND, "no session $sessionId")
            return onMain(UiPreviewContract.LAYOUT_TIMEOUT_MS) { session.stopScript() }
                ?: failure(UiPreviewContract.ERROR_TIMEOUT, "stopScript timed out")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        sessions.values.toList().forEach { it.release(UiPreviewContract.REASON_SERVICE_DESTROYED, notify = true) }
        sessions.clear()
        super.onDestroy()
    }

    // ------------------------------------------------------------------------------------------- sessions

    private fun createOnMain(uid: Int, params: UiPreviewSessionParams, hostToken: IBinder?, inputTransferToken: Parcelable?, callback: IUiPreviewCallback, abandoned: java.util.concurrent.atomic.AtomicBoolean): Bundle {
        val owned = sessions.values.count { it.callerUid == uid && !it.released }
        if (owned >= UiPreviewContract.MAX_SESSIONS_PER_CLIENT) {
            return failure(UiPreviewContract.ERROR_SESSION_LIMIT, "at most ${UiPreviewContract.MAX_SESSIONS_PER_CLIENT} sessions per client")
        }
        val id = "s${++counter}-${Integer.toHexString(random.nextInt())}"
        val session = UiPreviewSession(applicationContext, id, uid, params, hostToken, inputTransferToken, callback) { sessions.remove(it.id, it) }
        return try {
            val result = session.create()
            if (abandoned.get()) {
                // The caller already received a timeout; nobody will ever address this session.
                Log.w(TAG, "[$id] created after the caller gave up; releasing")
                session.release(UiPreviewContract.REASON_TIMEOUT, notify = false)
                return failure(UiPreviewContract.ERROR_TIMEOUT, "the preview runtime started after the wait")
            }
            sessions[id] = session
            try {
                callback.asBinder().linkToDeath({ mainHandler.post { sessions[id]?.release("client died", notify = false) } }, 0)
            } catch (e: Exception) {
                session.release("client gone", notify = false)
                return failure(UiPreviewContract.ERROR_UNAVAILABLE, "the client callback is not alive")
            }
            result
        } catch (e: UiPreviewRequestException) {
            session.release("creation failed", notify = false)
            failure(e.code, e.message ?: e.code)
        } catch (e: Throwable) {
            Log.w(TAG, "[$id] creation failed", e)
            session.release("creation failed", notify = false)
            failure(UiPreviewContract.ERROR_UNAVAILABLE, "session could not be created: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** The caller's own, live session; sessions of other callers are reported as not found. */
    private fun sessionOf(sessionId: String?): UiPreviewSession? {
        val session = sessions[sessionId ?: return null] ?: return null
        if (session.callerUid != Binder.getCallingUid() || session.released) return null
        return session
    }

    private fun previewInfo(): Bundle = Bundle().apply {
        val sdk = Build.VERSION.SDK_INT
        // The current contract (v2); getContractVersion / getContractHash answer for the v1 base.
        putInt(UiPreviewContract.KEY_CONTRACT_VERSION, UiPreviewContract.VERSION)
        putString(UiPreviewContract.KEY_CONTRACT_HASH, UiPreviewContract.HASH)
        putString(UiPreviewContract.KEY_STATUS, UiPreviewContract.STATUS_READY)
        putBoolean(UiPreviewContract.KEY_SCRIPT_RUN_SUPPORTED, sdk >= UiPreviewContract.MIN_API_INTERACTIVE)
        runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()?.let { info ->
            putLong(UiPreviewContract.KEY_HOST_VERSION_CODE, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong())
            putString(UiPreviewContract.KEY_HOST_VERSION_NAME, info.versionName)
        }
        val modes = if (sdk >= UiPreviewContract.MIN_API_EMBEDDED) arrayOf(UiPreviewContract.MODE_EMBEDDED, UiPreviewContract.MODE_SNAPSHOT) else arrayOf(UiPreviewContract.MODE_SNAPSHOT)
        putStringArray(UiPreviewContract.KEY_MODES, modes)
        putBoolean(UiPreviewContract.KEY_INTERACTIVE_POINTER_SUPPORTED, sdk >= UiPreviewContract.MIN_API_INTERACTIVE)
        putBoolean(UiPreviewContract.KEY_IME_SUPPORTED, sdk >= UiPreviewContract.MIN_API_IME)
        putInt(UiPreviewContract.KEY_MAX_SESSIONS_PER_CLIENT, UiPreviewContract.MAX_SESSIONS_PER_CLIENT)
        putInt(UiPreviewContract.KEY_MAX_WIDTH_PX, UiPreviewContract.MAX_WIDTH_PX)
        putInt(UiPreviewContract.KEY_MAX_HEIGHT_PX, UiPreviewContract.MAX_HEIGHT_PX)
        putInt(UiPreviewContract.KEY_MAX_XML_CHARS, UiPreviewContract.MAX_XML_CHARS)
        putInt(UiPreviewContract.KEY_MAX_BINDINGS_CHARS, UiPreviewContract.MAX_BINDINGS_CHARS)
        putInt(UiPreviewContract.KEY_MAX_BOUNDS_ENTRIES, UiPreviewContract.MAX_BOUNDS_ENTRIES)
        putLong(UiPreviewContract.KEY_MAX_SNAPSHOT_PIXELS, UiPreviewContract.MAX_SNAPSHOT_PIXELS)
        putLong(UiPreviewContract.KEY_LAYOUT_TIMEOUT_MS, UiPreviewContract.LAYOUT_TIMEOUT_MS)
    }

    // -------------------------------------------------------------------------------------------- helpers

    private fun enforcePermission() {
        if (checkCallingOrSelfPermission(UiPreviewContract.PLUGIN_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("${UiPreviewContract.PLUGIN_PERMISSION} is required")
        }
    }

    /** Runs [block] on the main thread and waits at most [timeoutMs]; null on timeout (the work still completes). */
    private fun <T> onMain(timeoutMs: Long, block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask(block)
        mainHandler.post(task)
        return try {
            task.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            null
        } catch (e: java.util.concurrent.ExecutionException) {
            throw (e.cause ?: e)
        }
    }

    private fun applied(): Bundle = Bundle().apply { putString(UiPreviewContract.KEY_OUTCOME, UiPreviewContract.OUTCOME_APPLIED) }

    private fun failure(code: String, message: String): Bundle = Bundle().apply {
        putString(UiPreviewContract.KEY_OUTCOME, UiPreviewContract.OUTCOME_FAILED)
        putString(UiPreviewContract.KEY_ERROR_CODE, code)
        putString(UiPreviewContract.KEY_ERROR_MESSAGE, message.take(UiPreviewContract.MAX_ERROR_MESSAGE_CHARS))
    }

    companion object {
        private const val TAG = "UiPreviewService"
    }
}
