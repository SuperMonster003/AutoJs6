package org.autojs.autojs.core.ui.preview

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.SurfaceControlViewHost
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.annotation.RequiresApi
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.core.ui.inflater.AttributeOutcome
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.ui.UI
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.autojs.autojs6.R
import org.autojs.plugin.ui.preview.IUiPreviewCallback
import org.autojs.plugin.ui.preview.UiPreviewContract
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.json.JsonParser
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.Locale

/**
 * One preview session: a themed root (embedded through [SurfaceControlViewHost] on API 30+, or offscreen for
 * snapshots), a preview runtime registered as a [UiPreviewScriptExecution], element tracking for bounds and
 * failure reports, taps and highlight. Every method runs on the main thread unless noted; the service
 * marshals Binder calls there.
 *
 * zh-CN: 一个预览会话: 带主题的根容器 (API 30+ 经 [SurfaceControlViewHost] 嵌入, 或离屏供快照), 注册为 [UiPreviewScriptExecution]
 * 的预览运行时, 供边界与失败报告的元素跟踪, 点击与高亮. 除特别说明外全部方法在主线程运行; 服务负责把 Binder 调用转到主线程.
 */
internal class UiPreviewSession(
    private val appContext: Context,
    val id: String,
    val callerUid: Int,
    val params: UiPreviewSessionParams,
    private val hostToken: IBinder?,
    private val inputTransferToken: Parcelable?,
    private val callback: IUiPreviewCallback,
    private val onReleased: (UiPreviewSession) -> Unit,
) : UiContentHost, UiPreviewTrackingDelegate.Tracker, UiPreviewRootView.Listener {

    /**
     * The unpacked resources of a v3 layout request (host roadmap R2): [directory] holds the client's zip; with
     * [layoutPath] the XML is that file of it and resources resolve from there, like `ui.layoutFile`; without it
     * they resolve from [directory] as the working directory.
     */
    class LayoutResources(val directory: File, val layoutPath: String?)

    /** The unpacked project of a v3 `runScript` request: the run's working directory and its script's path in it. */
    class ProjectRun(val directory: File, val scriptPath: String)

    /** What a layout request produced on the main thread; the service turns it into the response Bundle. */
    class LayoutResult(
        val failure: UiPreviewLayoutFailure?,
        val elementCount: Int,
        val diagnostics: List<UiPreviewDiagnostic>,
        val snapshot: ByteArray?,
        val snapshotWidth: Int,
        val snapshotHeight: Int,
        val elapsedMs: Long,
    )

    private class ViewRecord(view: View, val elementIndex: Int, val id: String?, val sequence: Int) {
        val ref = WeakReference(view)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val display: Display? = (appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).let { manager ->
        manager.getDisplay(params.displayId) ?: manager.getDisplay(Display.DEFAULT_DISPLAY)
    }
    private val isSnapshot = params.mode == UiPreviewContract.MODE_SNAPSHOT

    override val inflaterContext: Context = themedContext()
    private val root = UiPreviewRootView(inflaterContext, this)
    override val contentParent: ViewGroup get() = root
    override val contentRoot: ViewGroup get() = root
    override val hostWindow: Window? get() = null

    var width = params.widthPx
        private set
    var height = params.heightPx
        private set

    private var viewHost: SurfaceControlViewHost? = null
    private var execution: UiPreviewScriptExecution? = null
    private var tracking: UiPreviewTrackingDelegate? = null

    private val records = ArrayList<ViewRecord>()
    private var sequence = 0
    private val elementStack = ArrayDeque<Int>()
    private var currentAttr: String? = null
    private var applying = false
    private val diagnostics = ArrayList<UiPreviewDiagnostic>()
    private var lastElementCount = 0
    private val warnedFunctions = HashSet<String>()
    private var boundsPending = false
    private var highlighted = -1
    /** The interactive-preview run of the session (contract v2), if a script is running. */
    private var run: UiPreviewScriptRun? = null
    private var runCounter = 0
    private val runDirectory: File get() = File(appContext.cacheDir, "ui-preview/$id")
    /** Guards the unpacked archives, which the service receives on Binder threads (contract v3). */
    private val archiveLock = Any()
    /** The digest and directory of the last resources zip; reused while the digest stays the same. */
    private var resourcesArchive: Pair<String, File>? = null
    /** The project copy of the current or last run; each run gets its own, removed when the next one starts. */
    private var projectDirectory: File? = null
    private var archiveCounter = 0

    @Volatile
    var released = false
        private set

    /** Set by the service when a layout request timed out; the session is released once the work returns. */
    @Volatile
    var timedOut = false

    private val runtime: ScriptRuntime?
        get() = execution?.runtime

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { scheduleBounds() }

    // ---------------------------------------------------------------------------------------------- lifecycle

    /** Creates the view host (embedded) and the preview runtime; throws [UiPreviewRequestException] on failure. */
    fun create(): Bundle {
        val startedAt = SystemClock.elapsedRealtime()
        val result = Bundle().apply { putString(UiPreviewContract.KEY_SESSION_ID, id) }
        if (!isSnapshot) {
            if (Build.VERSION.SDK_INT < UiPreviewContract.MIN_API_EMBEDDED) {
                throw UiPreviewRequestException(UiPreviewContract.ERROR_UNSUPPORTED_MODE, "the embedded mode needs API ${UiPreviewContract.MIN_API_EMBEDDED}")
            }
            val host = createViewHost()
            viewHost = host
            result.putParcelable(UiPreviewContract.KEY_SURFACE_PACKAGE, host.surfacePackage)
            root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
        startRuntime()
        Log.i(TAG, "[$id] created mode=${params.mode} ${width}x$height dpi=${inflaterContext.resources.configuration.densityDpi} " +
            "night=${params.nightMode} display=${params.displayId} uid=$callerUid client=${params.clientVersionCode} in ${SystemClock.elapsedRealtime() - startedAt} ms")
        return result
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun createViewHost(): SurfaceControlViewHost {
        val target = display ?: throw UiPreviewRequestException(UiPreviewContract.ERROR_INVALID_REQUEST, "display ${params.displayId} is not available")
        val host = if (Build.VERSION.SDK_INT >= 35 && inputTransferToken is android.window.InputTransferToken) {
            SurfaceControlViewHost(inflaterContext, target, inputTransferToken)
        } else {
            val token = hostToken ?: throw UiPreviewRequestException(
                UiPreviewContract.ERROR_INVALID_REQUEST,
                if (Build.VERSION.SDK_INT >= 35) "hostToken or inputTransferToken is required for the embedded mode" else "hostToken is required for the embedded mode",
            )
            @Suppress("DEPRECATION")
            SurfaceControlViewHost(inflaterContext, target, token)
        }
        setViewCompat(host)
        return host
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun setViewCompat(host: SurfaceControlViewHost) {
        if (Build.VERSION.SDK_INT >= 37) {
            host.setView(root, SurfaceControlViewHost.LayoutParams(width, height, true))
        } else {
            host.setView(root, width, height)
        }
    }

    private fun startRuntime() {
        val source = StringScriptSource(params.label, UiPreviewScript.SOURCE)
        val config = ExecutionConfig(workingDirectory = appContext.cacheDir.absolutePath).also { it.uiContentHost = this }
        val listener = object : SimpleScriptExecutionListener() {
            override fun onException(execution: ScriptExecution, e: Throwable) {
                mainHandler.post { release(UiPreviewContract.REASON_SCRIPT_ERROR, notify = true) }
            }
        }
        val started = ScriptEngineService.getInstance().execute(source, listener, config) as UiPreviewScriptExecution
        if (started.engine == null) {
            val failure = started.startFailure
            throw UiPreviewRequestException(
                UiPreviewContract.ERROR_UNAVAILABLE,
                "the preview runtime could not start" + (failure?.message?.let { ": $it" } ?: ""),
            )
        }
        execution = started
    }

    // ------------------------------------------------------------------------------------- archives (v3)

    /**
     * Receives the resources zip of a v3 layout request on the calling Binder thread and returns its unpacked
     * directory; an unchanged digest reuses the previous copy without reading [fd] (which is closed either way).
     */
    @Throws(UiPreviewRequestException::class)
    fun receiveResources(fd: ParcelFileDescriptor, sha256: String): File {
        synchronized(archiveLock) {
            resourcesArchive?.takeIf { it.first == sha256 && it.second.isDirectory }?.let {
                runCatching { fd.close() }
                return it.second
            }
            val target = File(runDirectory, "archives/resources-${++archiveCounter}")
            UiPreviewArchive.receive(fd, sha256, File(runDirectory, "archives/incoming-$archiveCounter.zip"), target)
            resourcesArchive?.second?.deleteRecursively()
            resourcesArchive = sha256 to target
            return target
        }
    }

    /** Receives the project zip of a v3 `runScript` request on the calling Binder thread into a fresh directory. */
    @Throws(UiPreviewRequestException::class)
    fun receiveProject(fd: ParcelFileDescriptor, sha256: String): File = synchronized(archiveLock) {
        val target = File(runDirectory, "archives/project-${++archiveCounter}")
        UiPreviewArchive.receive(fd, sha256, File(runDirectory, "archives/incoming-$archiveCounter.zip"), target)
        target
    }

    // ------------------------------------------------------------------------------- interactive preview (v2)

    /**
     * Runs [text] as a normal `"ui"` mode script in this session's root (3-Block Compose roadmap P10.6); a run that
     * is still going is replaced first. Throws [UiPreviewRequestException] when the script cannot start.
     */
    fun runScript(text: String, name: String, project: ProjectRun? = null): Bundle {
        stopRun(UiPreviewContract.EXIT_REPLACED)
        // v3: a project run works in its own unpacked copy; the previous copy goes with the run it served.
        synchronized(archiveLock) {
            projectDirectory?.takeIf { it != project?.directory }?.deleteRecursively()
            projectDirectory = project?.directory
        }
        val directory = project?.directory ?: runDirectory.apply { mkdirs() }
        val scriptFile = project?.let { File(it.directory, it.scriptPath) }
        val started = UiPreviewScriptRun(appContext, this, "$id-r${++runCounter}", name, text, directory, scriptFile, callback) { ended ->
            if (run === ended) run = null
        }
        if (!started.start()) {
            throw UiPreviewRequestException(UiPreviewContract.ERROR_SCRIPT_FAILED, started.startFailure ?: "the script could not start")
        }
        run = started
        return Bundle().apply {
            putString(UiPreviewContract.KEY_OUTCOME, UiPreviewContract.OUTCOME_APPLIED)
            putString(UiPreviewContract.KEY_RUN_ID, started.runId)
        }
    }

    /** Stops the running script, if any; the layout-preview runtime and the root stay. */
    fun stopScript(): Bundle {
        val stopped = run?.runId
        stopRun(UiPreviewContract.EXIT_STOPPED)
        return Bundle().apply {
            putString(UiPreviewContract.KEY_OUTCOME, UiPreviewContract.OUTCOME_APPLIED)
            stopped?.let { putString(UiPreviewContract.KEY_RUN_ID, it) }
        }
    }

    private fun stopRun(reason: String) {
        val current = run ?: return
        run = null
        current.stop(reason)
    }

    fun release(reason: String, notify: Boolean) {
        if (released) return
        released = true
        stopRun(UiPreviewContract.EXIT_RELEASED)
        runCatching { runDirectory.deleteRecursively() }
        runCatching { execution?.finish() }.onFailure { Log.w(TAG, "[$id] runtime release failed", it) }
        execution = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { viewHost?.release() }.onFailure { Log.w(TAG, "[$id] view host release failed", it) }
        }
        viewHost = null
        root.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        root.removeAllViews()
        records.clear()
        if (notify) {
            runCatching {
                callback.onSessionLost(id, Bundle().apply { putString(UiPreviewContract.KEY_REASON, reason) })
            }
        }
        Log.i(TAG, "[$id] released: $reason")
        onReleased(this)
    }

    // ------------------------------------------------------------------------------------------------ layout

    /**
     * Applies [xml] with [bindingsJson] as the binding context; for snapshots the pixels are drawn here too. v3:
     * [resources] lets the layout resolve the project's resources, [androidLayout] is the `ui.useAndroidLayout`
     * mode (null automatic); both are set on every request, so a request without them gets the v2 behavior.
     */
    fun applyLayout(xml: String, bindingsJson: String?, resources: LayoutResources? = null, androidLayout: Boolean? = null): LayoutResult {
        val startedAt = SystemClock.elapsedRealtime()
        val runtime = runtime ?: return LayoutResult(
            UiPreviewLayoutFailure(UiPreviewContract.ERROR_UNAVAILABLE, "the preview runtime is gone", -1, null, -1, -1),
            0, emptyList(), null, 0, 0, 0,
        )
        val inflater = runtime.ui.layoutInflater
        val delegate = inflater.layoutInflaterDelegate
        val tracking = delegate as? UiPreviewTrackingDelegate
            ?: UiPreviewTrackingDelegate(delegate, this).also { inflater.layoutInflaterDelegate = it }
        this.tracking = tracking
        elementStack.clear()
        currentAttr = null
        diagnostics.clear()
        highlighted = -1
        root.highlightRect = null
        applying = true
        var failure: UiPreviewLayoutFailure? = null
        try {
            withRhinoContext(runtime) { cx ->
                runtime.ui.bindingContext = parseBindings(cx, runtime, bindingsJson)
                runtime.ui.isAndroidLayout = androidLayout
                records.clear()
                // The inflater resolves project resources from the working directory, or from the layout's own file.
                execution?.engine?.setTag(ScriptEngine.TAG_WORKING_DIRECTORY, (resources?.directory ?: appContext.cacheDir).absolutePath)
                val layoutFile = resources?.layoutPath?.let { File(resources.directory, it) }
                if (layoutFile != null) {
                    layoutFile.parentFile?.mkdirs()
                    layoutFile.writeText(xml)
                    UI.layoutFile(runtime, arrayOf(layoutFile.absolutePath))
                } else {
                    UI.layout(runtime, arrayOf(xml))
                }
            }
        } catch (e: UiPreviewRequestException) {
            failure = UiPreviewLayoutFailure(e.code, e.message ?: e.code, -1, null, -1, -1)
        } catch (e: Throwable) {
            failure = UiPreviewFailures.classify(e, elementStack.lastOrNull() ?: -1, currentAttr, xml)
            Log.w(TAG, "[$id] layout failed: ${failure.code} element=${failure.elementIndex} attr=${failure.attrName}")
        } finally {
            applying = false
        }
        lastElementCount = tracking.lastDocumentElementCount
        var snapshot: ByteArray? = null
        if (failure == null) {
            // A measure and layout pass at the session size before the window's own traversal: a view whose
            // measure throws (DatePicker's calendar on Android 7 under an exact-size parent) fails the layout
            // here instead of the host's main thread, and the content is dropped so the traversal never sees it.
            // zh-CN: 在窗口自己的遍历之前先按会话尺寸测量布局一遍: 某个 View 在测量时抛出 (Android 7 的 DatePicker
            // 日历在精确尺寸父容器下) 时在这里判为布局失败而不是让宿主主线程崩溃, 并移除内容, 遍历不会再碰到它.
            try {
                layoutOffscreen()
                if (isSnapshot) {
                    snapshot = drawSnapshot()
                    reportBounds()
                }
            } catch (e: Throwable) {
                failure = UiPreviewFailures.classify(e, -1, null, xml)
                Log.w(TAG, "[$id] layout pass failed: ${failure.code}: ${failure.message}")
                root.removeAllViews()
                records.clear()
            }
        }
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        if (failure == null) {
            Log.i(TAG, "[$id] layout applied: ${lastElementCount} elements, ${records.size} views, ${diagnostics.size} diagnostics in $elapsed ms")
        }
        return LayoutResult(failure, lastElementCount, ArrayList(diagnostics), snapshot, width, height, elapsed)
    }

    private fun parseBindings(cx: org.mozilla.javascript.Context, runtime: ScriptRuntime, bindingsJson: String?): Scriptable {
        val text = bindingsJson?.takeIf { it.isNotBlank() } ?: "{}"
        val parsed = try {
            // The host's own JSON parser (RhinoUtils.js_json_parse uses it too): the 4-argument NativeJSON.parse of
            // this Rhino build requires a reviver.
            // zh-CN: 宿主自己的 JSON 解析器 (RhinoUtils.js_json_parse 也用它): 这个 Rhino 构建的四参数 NativeJSON.parse 要求 reviver 非空.
            JsonParser(cx, runtime.topLevelScope).parseValue(text)
        } catch (e: Throwable) {
            throw UiPreviewRequestException(UiPreviewContract.ERROR_INVALID_REQUEST, "bindingsJson is not valid JSON: ${e.message}")
        }
        if (parsed !is Scriptable || parsed is NativeArray) {
            throw UiPreviewRequestException(UiPreviewContract.ERROR_INVALID_REQUEST, "bindingsJson must be a JSON object")
        }
        return parsed
    }

    fun resize(newWidth: Int, newHeight: Int) {
        width = newWidth
        height = newHeight
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            viewHost?.let { host ->
                if (Build.VERSION.SDK_INT >= 37) host.relayout(SurfaceControlViewHost.LayoutParams(width, height, true))
                else host.relayout(width, height)
            }
        }
        if (isSnapshot) layoutOffscreen() else scheduleBounds()
        Log.i(TAG, "[$id] resized to ${width}x$height")
    }

    /** A fresh package for the same hierarchy after the client's surface was recreated (embedded mode). */
    fun reattach(): Bundle = Bundle().apply {
        putString(UiPreviewContract.KEY_SESSION_ID, id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val host = viewHost ?: throw UiPreviewRequestException(UiPreviewContract.ERROR_UNSUPPORTED_MODE, "reattach needs an embedded session")
            putParcelable(UiPreviewContract.KEY_SURFACE_PACKAGE, host.surfacePackage)
        } else {
            throw UiPreviewRequestException(UiPreviewContract.ERROR_UNSUPPORTED_MODE, "reattach needs an embedded session")
        }
    }

    fun setPointerMode(mode: Int) {
        root.interceptsPointer = mode == UiPreviewContract.POINTER_MODE_EDIT
    }

    fun setHighlight(elementIndex: Int) {
        highlighted = elementIndex
        root.highlightRect = if (elementIndex < 0) null else firstShownView(elementIndex)?.let { view ->
            val (left, top) = offsetInRoot(view)
            Rect(left, top, left + view.width, top + view.height)
        }
    }

    // ----------------------------------------------------------------------------------------- UiContentHost

    override fun showContent(view: View) {
        root.removeAllViews()
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Embedded: the layout pass that follows addView reports the bounds, once the views have their sizes.
    }

    override fun onWindowFunctionIgnored(functionName: String) {
        if (!warnedFunctions.add(functionName)) return
        runCatching { runtime?.console?.warn(appContext.getString(R.string.ui_preview_window_function_ignored, functionName)) }
    }

    override fun onAttributeIgnored(attrName: String, outcome: AttributeOutcome, message: String) {
        if (!applying) return
        val code = when (outcome) {
            AttributeOutcome.UNSUPPORTED -> UiPreviewContract.ATTRIBUTE_OUTCOME_UNSUPPORTED
            AttributeOutcome.UNKNOWN -> UiPreviewContract.ATTRIBUTE_OUTCOME_UNKNOWN
            AttributeOutcome.IGNORED_NAMESPACE -> UiPreviewContract.ATTRIBUTE_OUTCOME_NAMESPACE
            AttributeOutcome.APPLIED -> return
        }
        diagnostics.add(UiPreviewDiagnostic(UiPreviewContract.ERROR_ATTRIBUTE_IGNORED, message, elementStack.lastOrNull() ?: -1, attrName, code))
    }

    override fun onBindingFailed(attrName: String, rawValue: String, error: Throwable): Boolean {
        val diagnostic = UiPreviewDiagnostic(
            UiPreviewContract.ERROR_BINDING_FAILED,
            UiPreviewFailures.messageOf(error),
            elementStack.lastOrNull() ?: -1,
            attrName,
        )
        if (applying) {
            diagnostics.add(diagnostic)
        } else {
            runCatching {
                callback.onLayoutFailed(id, Bundle().apply {
                    putString(UiPreviewContract.KEY_ERROR_CODE, diagnostic.code)
                    putString(UiPreviewContract.KEY_ERROR_MESSAGE, diagnostic.message)
                    putInt(UiPreviewContract.KEY_ELEMENT_INDEX, diagnostic.elementIndex)
                    putString(UiPreviewContract.KEY_ATTR_NAME, diagnostic.attrName)
                })
            }
        }
        return true
    }

    override fun onEngineForceStop() {
        mainHandler.post { release(UiPreviewContract.REASON_STOPPED, notify = true) }
    }

    // ------------------------------------------------------------------------------------------------ Tracker

    override fun onElementEnter(elementIndex: Int) {
        elementStack.addLast(elementIndex)
    }

    override fun onElementExit() {
        elementStack.removeLastOrNull()
    }

    override fun onAttributeEnter(attrName: String) {
        currentAttr = attrName
    }

    override fun onAttributeExit() {
        currentAttr = null
    }

    override fun replaceCreatedView(view: View): View? {
        if (!isSnapshot) return null
        val needsWindow = view is WebView || view is SurfaceView || view is TextureView || view is VideoView
        return if (needsWindow) UiPreviewPlaceholderView(view.context, view.javaClass.simpleName) else null
    }

    override fun onViewCreated(view: View, elementIndex: Int, id: String?) {
        records.add(ViewRecord(view, elementIndex, id, sequence++))
    }

    // ------------------------------------------------------------------------------------- bounds and taps

    override fun onRootTap(x: Int, y: Int) {
        val hit = shownRecords()
            .map { (record, view) -> Triple(record, view, offsetInRoot(view)) }
            .filter { (_, view, offset) -> x >= offset.first && x < offset.first + view.width && y >= offset.second && y < offset.second + view.height }
            .minByOrNull { (_, view, _) -> view.width.toLong() * view.height }
        runCatching {
            callback.onTap(id, Bundle().apply {
                putInt(UiPreviewContract.KEY_X, x)
                putInt(UiPreviewContract.KEY_Y, y)
                putInt(UiPreviewContract.KEY_ELEMENT_INDEX, hit?.first?.elementIndex ?: -1)
                putString(UiPreviewContract.KEY_CLASS_NAME, hit?.second?.javaClass?.simpleName ?: "")
            })
        }
    }

    private fun scheduleBounds() {
        if (boundsPending || released) return
        boundsPending = true
        mainHandler.postDelayed({
            boundsPending = false
            if (!released) reportBounds()
        }, BOUNDS_DEBOUNCE_MS)
    }

    private fun reportBounds() {
        // Before the first traversal of the embedded hierarchy every view is 0 x 0; that layout pass reports instead.
        if (!isSnapshot && (root.width == 0 || root.height == 0)) return
        val entries = ArrayList<UiPreviewBoundsEntry>()
        val instances = HashMap<Int, Int>()
        for ((record, view) in attachedRecords()) {
            val (left, top) = offsetInRoot(view)
            val instance = instances.getOrDefault(record.elementIndex, 0)
            instances[record.elementIndex] = instance + 1
            entries.add(
                UiPreviewBoundsEntry(
                    record.elementIndex, left, top, left + view.width, top + view.height,
                    view.javaClass.simpleName, record.id, instance, view.visibility,
                ),
            )
        }
        if (highlighted >= 0) setHighlight(highlighted)
        val encoded = UiPreviewBounds.encode(entries)
        runCatching {
            callback.onBounds(id, Bundle().apply {
                putString(UiPreviewContract.KEY_BOUNDS_JSON, encoded.json)
                putInt(UiPreviewContract.KEY_BOUNDS_COUNT, encoded.count)
                putBoolean(UiPreviewContract.KEY_BOUNDS_TRUNCATED, encoded.truncated)
                putInt(UiPreviewContract.KEY_ELEMENT_COUNT, lastElementCount)
            })
        }.onFailure { Log.w(TAG, "[$id] bounds not delivered", it) }
    }

    /** Records whose view is still part of the root hierarchy, in creation order. */
    private fun attachedRecords(): List<Pair<ViewRecord, View>> {
        val result = ArrayList<Pair<ViewRecord, View>>()
        val iterator = records.iterator()
        while (iterator.hasNext()) {
            val record = iterator.next()
            val view = record.ref.get()
            if (view == null) {
                iterator.remove()
                continue
            }
            if (isInRoot(view)) result.add(record to view)
        }
        return result
    }

    private fun shownRecords(): List<Pair<ViewRecord, View>> = attachedRecords().filter { (_, view) -> view.isShown || isSnapshot && view.visibility == View.VISIBLE }

    private fun firstShownView(elementIndex: Int): View? =
        shownRecords().firstOrNull { (record, _) -> record.elementIndex == elementIndex }?.second

    private fun isInRoot(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === root) return true
            current = current.parent as? View
        }
        return false
    }

    private fun offsetInRoot(view: View): Pair<Int, Int> {
        var left = 0f
        var top = 0f
        var current: View = view
        while (current !== root) {
            left += current.left + current.translationX
            top += current.top + current.translationY
            val parent = current.parent as? View ?: break
            left -= parent.scrollX
            top -= parent.scrollY
            current = parent
        }
        return left.toInt() to top.toInt()
    }

    // ------------------------------------------------------------------------------------------- snapshot

    private fun layoutOffscreen() {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
    }

    private fun drawSnapshot(): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            root.draw(Canvas(bitmap))
            val buffer = ByteBuffer.allocate(bitmap.byteCount)
            bitmap.copyPixelsToBuffer(buffer)
            return buffer.array()
        } finally {
            bitmap.recycle()
        }
    }

    // --------------------------------------------------------------------------------------------- context

    private fun themedContext(): Context {
        val base = display?.let { appContext.createDisplayContext(it) } ?: appContext
        val configuration = Configuration(base.resources.configuration)
        if (params.densityDpi > 0) configuration.densityDpi = params.densityDpi
        when (params.nightMode) {
            UiPreviewContract.NIGHT_MODE_NO -> configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            UiPreviewContract.NIGHT_MODE_YES -> configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        if (params.localeTag.isNotEmpty()) configuration.setLocale(Locale.forLanguageTag(params.localeTag))
        if (params.fontScale > 0f) configuration.fontScale = params.fontScale
        return ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.ScriptTheme)
    }

    companion object {
        private const val TAG = "UiPreviewSession"
        private const val BOUNDS_DEBOUNCE_MS = 50L
    }
}
