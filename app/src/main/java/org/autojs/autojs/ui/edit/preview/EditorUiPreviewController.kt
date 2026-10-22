package org.autojs.autojs.ui.edit.preview

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.ui.preview.UiPreviewDiagnostic
import org.autojs.autojs.core.ui.preview.UiPreviewFailures
import org.autojs.autojs.core.ui.preview.UiPreviewLayoutFailure
import org.autojs.autojs.core.ui.preview.UiPreviewSession
import org.autojs.autojs.core.ui.preview.UiPreviewSessionParams
import org.autojs.autojs.core.ui.preview.UiPreviewSourceReader
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs6.R
import org.autojs.plugin.ui.preview.IUiPreviewCallback
import org.autojs.plugin.ui.preview.UiPreviewContract
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The layout preview panel of "ui" scripts (3-Block Compose roadmap P11.10, D59 / D71). A toolbar toggle shows it
 * in the lower half of the editor (portrait) or its right half (landscape); an in-process [UiPreviewSession] there
 * applies the layout of the first `ui.layout` call of the text being edited, which [UiPreviewSourceReader] reads
 * without running any of the script, once typing pauses for [REFRESH_DELAY_MS]. Tapping an element moves the cursor
 * to its start tag, failures are explained under the layout, and the toggle is remembered per file. The layout is laid
 * out at the size of the window, as the script's own activity would have it, and scaled down uniformly into the panel
 * (D74); the view transform maps taps into the layout, and the highlight is drawn in the layout's coordinates.
 *
 * zh-CN: "ui" 脚本的布局预览面板 (3-Block Compose 路线图 P11.10, D59 / D71). 工具栏开关把它显示在编辑器下半区 (竖屏) 或右半区 (横屏);
 * 其中的进程内 [UiPreviewSession] 应用正在编辑的文本里第一个 `ui.layout` 调用的布局, 由 [UiPreviewSourceReader] 读取, 不运行脚本的任何部分,
 * 输入停顿 [REFRESH_DELAY_MS] 后刷新. 点击元素把光标移到它的开始标签, 失败原因显示在布局下方, 开关按文件记忆.
 * 布局按窗口尺寸排布 (与脚本自己的界面一致), 再等比缩小放进面板 (D74); 视图变换把点按换算到布局坐标, 高亮框画在布局坐标系中.
 */
internal class EditorUiPreviewController(
    private val context: Context,
    private val split: LinearLayout,
    private val editorContainer: View,
    private val host: Host,
) {

    interface Host {
        /** The text being edited, or null while it is loading. */
        fun previewSourceText(): String?

        /** What the toggle is remembered by, or null for a document that has no lasting name. */
        fun previewDocumentKey(): String?

        /** Whether the document can have a preview at all: a plain JavaScript file, not loaded in large-file mode. */
        fun isPreviewableDocument(): Boolean

        /** Moves the cursor to the 0-based [line] and [column]. */
        fun jumpToSource(line: Int, column: Int)

        /** The toolbar toggle is shown when [visible], as on when [on]. */
        fun onUiPreviewStateChanged(visible: Boolean, on: Boolean)

        fun isDesignerAvailable(): Boolean

        fun openInDesigner()
    }

    private data class LayoutKey(val xml: String, val bindingsJson: String, val androidLayout: Boolean?)

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refresh() }
    private val reader: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "editor-ui-preview").apply { isDaemon = true }
    }
    private val dividerSize = maxOf(1, context.resources.displayMetrics.density.roundToInt())

    private var pane: View? = null
    private var divider: View? = null
    private var hostFrame: FrameLayout? = null
    private var messageView: TextView? = null
    private var designerButton: TextView? = null
    private val paneTexts = ArrayList<TextView>()

    private var session: UiPreviewSession? = null
    private var sessionLost = false

    /** The layout the session shows, which maps taps and failures back to the source; [shownKey] skips unchanged layouts. */
    private var shownLayout: UiPreviewSourceReader.Layout? = null
    private var shownKey: LayoutKey? = null
    private var shownDiagnostics: List<UiPreviewDiagnostic> = emptyList()

    /** Discards a read that a later refresh, a hidden panel or [destroy] made stale. */
    private var generation = 0
    private var visible = false
    private var on = false
    private var reportedState: Pair<Boolean, Boolean>? = null
    private var documentKey: String? = null
    private var palette: Pair<Int, Int>? = null
    private var destroyed = false

    /** Whether the panel is showing (for the editor's tests). */
    val isPaneShown: Boolean
        get() = pane?.isVisible == true

    /** The document changed (opened, saved under another name): the toggle follows what was remembered for it. */
    fun onDocumentChanged() {
        val key = host.previewDocumentKey()
        if (key != documentKey) {
            // A document saved under a new name keeps the panel it had.
            // zh-CN: 另存为新名称的文档保留原有的面板状态.
            on = on || isRemembered(key)
            documentKey = key
            if (on) remember(key, true)
        }
        refreshNow()
    }

    /** The text was loaded, or replaced as a whole. */
    fun onTextLoaded() = refreshNow()

    /** The text changed: the preview follows once typing pauses. */
    fun onTextChanged() {
        if (destroyed) return
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, REFRESH_DELAY_MS)
    }

    fun toggle() {
        if (destroyed || !visible) return
        on = !on
        remember(documentKey, on)
        refreshNow()
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        if (destroyed) return
        arrangeSplit(newConfig.orientation)
        val current = session ?: return
        // The runtime's resources were made for the old night mode and locale.
        // zh-CN: 运行时的资源是按旧的夜间模式与语言创建的.
        if (current.params.nightMode != nightModeOf(newConfig) || current.params.localeTag != localeTagOf(newConfig)) {
            releaseSession()
            refreshNow()
        }
    }

    fun applyPalette(@ColorInt background: Int, @ColorInt foreground: Int) {
        palette = background to foreground
        applyPaletteToPane()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        handler.removeCallbacks(refreshRunnable)
        generation++
        reader.shutdownNow()
        releaseSession()
    }

    // ------------------------------------------------------------------------------------------------ refresh

    private fun refreshNow() {
        handler.removeCallbacks(refreshRunnable)
        refresh()
    }

    private fun refresh() {
        if (destroyed) return
        val text = host.previewSourceText() ?: return
        visible = host.isPreviewableDocument() && isUiScript(text)
        reportState()
        if (!visible || !on) {
            hidePane()
            return
        }
        showPane()
        val requested = ++generation
        reader.execute {
            val result = runCatching { UiPreviewSourceReader.read(text) }
            handler.post {
                if (requested == generation && !destroyed) render(text, result)
            }
        }
    }

    private fun render(text: String, read: Result<UiPreviewSourceReader.Result>) {
        when (val result = read.getOrElse { e -> return showMessages(listOf(unavailableMessage(e)), keepsLastLayout = true) }) {
            is UiPreviewSourceReader.Layout -> applyLayout(text, result)
            is UiPreviewSourceReader.Failure -> showMessages(listOf(sourceFailureMessage(text, result)), keepsLastLayout = true)
        }
    }

    private fun applyLayout(text: String, layout: UiPreviewSourceReader.Layout) {
        val current = session ?: createSession() ?: return
        val key = LayoutKey(layout.xml, layout.bindingsJson, layout.androidLayout)
        if (key == shownKey) {
            // An edit outside the layout only moved it in the source.
            // zh-CN: 布局之外的编辑只改变了布局在源码中的位置.
            shownLayout = layout
            showMessages(diagnosticMessages(text, layout, shownDiagnostics), keepsLastLayout = false)
            return
        }
        val result = current.applyLayout(layout.xml, layout.bindingsJson, null, layout.androidLayout)
        if (current !== session) return
        val failure = result.failure
        if (failure == null) {
            shownLayout = layout
            shownKey = key
            shownDiagnostics = result.diagnostics
            showMessages(diagnosticMessages(text, layout, result.diagnostics), keepsLastLayout = false)
        } else {
            shownKey = null
            if (!hasContent()) shownLayout = null
            showMessages(listOf(layoutFailureMessage(text, layout, failure)), keepsLastLayout = true)
        }
    }

    private fun isUiScript(text: String): Boolean {
        val mode = JavaScriptSource.parseExecutionMode(text).mode
        return mode and JavaScriptSource.EXECUTION_MODE_UI != 0 && mode and OTHER_ENGINES == 0
    }

    private fun reportState() {
        val state = visible to on
        if (state == reportedState) return
        reportedState = state
        host.onUiPreviewStateChanged(visible, on)
    }

    // ------------------------------------------------------------------------------------------------ session

    private fun createSession(): UiPreviewSession? {
        val frame = hostFrame ?: return null
        // Not laid out yet: the frame's layout listener refreshes once it is.
        // zh-CN: 尚未布局: 布局完成后由宿主容器的布局监听器刷新.
        if (frame.width <= 0 || frame.height <= 0) return null
        val configuration = context.resources.configuration
        val (width, height) = layoutSize()
        val params = UiPreviewSessionParams(
            mode = UiPreviewSession.MODE_ATTACHED,
            widthPx = width,
            heightPx = height,
            densityDpi = 0,
            nightMode = nightModeOf(configuration),
            localeTag = localeTagOf(configuration),
            fontScale = 0f,
            displayId = split.display?.displayId ?: Display.DEFAULT_DISPLAY,
            label = context.getString(R.string.text_editor_ui_preview_label),
            clientVersionCode = 0L,
        )
        val created = UiPreviewSession(
            context.applicationContext, "editor-${UUID.randomUUID()}", Process.myUid(), params, null, null, callback,
        ) {}
        try {
            created.create()
        } catch (e: Throwable) {
            created.release(UiPreviewContract.REASON_STOPPED, notify = false)
            showMessages(listOf(unavailableMessage(e)), keepsLastLayout = false)
            return null
        }
        created.setPointerMode(UiPreviewContract.POINTER_MODE_EDIT)
        frame.addView(created.rootView, FrameLayout.LayoutParams(width, height))
        session = created
        sessionLost = false
        fitIntoFrame(created)
        return created
    }

    /** The size the layout is laid out at: that of the editor's window, which the script's own activity would have (D74). */
    private fun layoutSize(): Pair<Int, Int> {
        val window = split.rootView
        if (window.width > 0 && window.height > 0) return window.width to window.height
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * Follows the size of the window (rotation, multi-window) and scales the layout down uniformly into the panel, centered (D74).
     * zh-CN: 跟随窗口尺寸 (旋转, 多窗口), 并把布局等比缩小居中放进面板 (D74).
     */
    private fun fitIntoFrame(current: UiPreviewSession) {
        val frame = hostFrame ?: return
        if (frame.width <= 0 || frame.height <= 0) return
        val (width, height) = layoutSize()
        if (current.width != width || current.height != height) current.resize(width, height)
        val root = current.rootView
        val params = root.layoutParams
        if (params.width != width || params.height != height) {
            params.width = width
            params.height = height
            root.layoutParams = params
        }
        val scale = minOf(frame.width.toFloat() / width, frame.height.toFloat() / height)
        root.pivotX = 0f
        root.pivotY = 0f
        root.scaleX = scale
        root.scaleY = scale
        root.translationX = (frame.width - width * scale) / 2f
        root.translationY = (frame.height - height * scale) / 2f
    }

    private fun releaseSession() {
        val current = session ?: return
        session = null
        hostFrame?.removeView(current.rootView)
        current.release(UiPreviewContract.REASON_STOPPED, notify = false)
        shownLayout = null
        shownKey = null
        shownDiagnostics = emptyList()
    }

    private fun hasContent(): Boolean = (session?.contentParent?.childCount ?: 0) > 0

    private val callback = object : IUiPreviewCallback.Stub() {
        override fun onBounds(sessionId: String?, bounds: Bundle?) = Unit

        override fun onLayoutFailed(sessionId: String?, failure: Bundle?) {
            val message = failure?.getString(UiPreviewContract.KEY_ERROR_MESSAGE) ?: return
            val elementIndex = failure.getInt(UiPreviewContract.KEY_ELEMENT_INDEX, -1)
            handler.post {
                if (session?.id != sessionId) return@post
                val layout = shownLayout
                val text = host.previewSourceText()
                showMessages(listOf(lineMessage(text, layout, elementIndex, message)), keepsLastLayout = false)
            }
        }

        override fun onSessionLost(sessionId: String?, reason: Bundle?) {
            handler.post {
                val current = session?.takeIf { it.id == sessionId } ?: return@post
                session = null
                hostFrame?.removeView(current.rootView)
                shownLayout = null
                shownKey = null
                shownDiagnostics = emptyList()
                sessionLost = true
                showMessages(listOf(context.getString(R.string.text_editor_ui_preview_session_lost)), keepsLastLayout = false)
            }
        }

        override fun onTap(sessionId: String?, tap: Bundle?) {
            val elementIndex = tap?.getInt(UiPreviewContract.KEY_ELEMENT_INDEX, -1) ?: -1
            handler.post { if (session?.id == sessionId) onElementTapped(elementIndex) }
        }

        override fun onConsole(sessionId: String?, entry: Bundle?) = Unit

        override fun onScriptError(sessionId: String?, error: Bundle?) = Unit

        override fun onScriptExit(sessionId: String?, exit: Bundle?) = Unit

        override fun onLifecycleHandled(sessionId: String?, result: Bundle?) = Unit
    }

    private fun onElementTapped(elementIndex: Int) {
        val current = session ?: return
        current.setHighlight(elementIndex)
        if (elementIndex < 0) return
        val layout = shownLayout ?: return
        val text = host.previewSourceText() ?: return
        val (line, column) = UiPreviewSourceReader.positionOf(text, layout.elementOffset(elementIndex))
        host.jumpToSource(line, column)
    }

    // --------------------------------------------------------------------------------------------------- pane

    private fun showPane() {
        val shown = pane ?: inflatePane()
        if (!shown.isVisible) {
            shown.isVisible = true
            divider?.isVisible = true
        }
        designerButton?.isVisible = host.isDesignerAvailable()
    }

    private fun hidePane() {
        generation++
        val shown = pane ?: return
        if (shown.isVisible) {
            shown.isVisible = false
            divider?.isVisible = false
        }
        releaseSession()
        sessionLost = false
        showMessages(emptyList(), keepsLastLayout = false)
    }

    private fun inflatePane(): View {
        val line = View(context)
        val view = LayoutInflater.from(context).inflate(R.layout.editor_ui_preview_pane, split, false)
        split.addView(line)
        split.addView(view)
        divider = line
        pane = view
        hostFrame = view.findViewById<FrameLayout>(R.id.editor_ui_preview_host).apply {
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val width = right - left
                val height = bottom - top
                if (width <= 0 || height <= 0 || width == oldRight - oldLeft && height == oldBottom - oldTop) {
                    return@addOnLayoutChangeListener
                }
                handler.post { onHostResized() }
            }
        }
        messageView = view.findViewById<TextView>(R.id.editor_ui_preview_message).apply {
            setOnClickListener { if (session == null && sessionLost) refreshNow() }
        }
        designerButton = view.findViewById<TextView>(R.id.editor_ui_preview_open_in_designer).apply {
            setOnClickListener { host.openInDesigner() }
        }
        paneTexts.clear()
        paneTexts += listOf(
            view.findViewById(R.id.editor_ui_preview_title),
            view.findViewById(R.id.editor_ui_preview_hint),
            messageView!!,
            designerButton!!,
        )
        arrangeSplit(context.resources.configuration.orientation)
        applyPaletteToPane()
        return view
    }

    private fun onHostResized() {
        if (destroyed || pane?.isVisible != true) return
        val current = session
        when {
            current != null -> fitIntoFrame(current)
            !sessionLost -> refreshNow()
        }
    }

    /** Side by side in landscape, one above the other in portrait, each half of the editor. */
    private fun arrangeSplit(orientation: Int) {
        val landscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        split.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        editorContainer.layoutParams = halfOf(landscape)
        pane?.layoutParams = halfOf(landscape)
        divider?.layoutParams = if (landscape) {
            LinearLayout.LayoutParams(dividerSize, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dividerSize)
        }
    }

    private fun halfOf(landscape: Boolean) = if (landscape) {
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
    } else {
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    private fun applyPaletteToPane() {
        val (background, foreground) = palette ?: return
        pane?.setBackgroundColor(background)
        paneTexts.forEach { it.setTextColor(foreground) }
        divider?.setBackgroundColor(foreground and 0x00FFFFFF or DIVIDER_ALPHA)
    }

    // ----------------------------------------------------------------------------------------------- messages

    private fun showMessages(lines: List<String>, keepsLastLayout: Boolean) {
        val all = if (keepsLastLayout && hasContent()) {
            lines + context.getString(R.string.text_editor_ui_preview_showing_last_layout)
        } else {
            lines
        }
        messageView?.apply {
            text = all.joinToString("\n")
            isVisible = all.isNotEmpty()
        }
    }

    private fun sourceFailureMessage(text: String, failure: UiPreviewSourceReader.Failure): String {
        val (line, column) = UiPreviewSourceReader.positionOf(text, failure.offset)
        return when (failure.kind) {
            UiPreviewSourceReader.FailureKind.SYNTAX_ERROR ->
                context.getString(R.string.text_editor_ui_preview_syntax_error, line + 1, column + 1, failure.detail.orEmpty())
            UiPreviewSourceReader.FailureKind.NO_LAYOUT -> context.getString(R.string.text_editor_ui_preview_no_layout)
            UiPreviewSourceReader.FailureKind.UNSUPPORTED_ARGUMENT ->
                context.getString(R.string.text_editor_ui_preview_unsupported_argument, line + 1)
        }
    }

    private fun layoutFailureMessage(text: String, layout: UiPreviewSourceReader.Layout, failure: UiPreviewLayoutFailure): String {
        val offset = when {
            failure.code == UiPreviewContract.ERROR_XML_INVALID && failure.line > 0 ->
                layout.sourceOffsetOf(UiPreviewFailures.offsetOf(layout.xml, failure.line, failure.column))
            failure.elementIndex >= 0 -> layout.elementOffset(failure.elementIndex)
            else -> return context.getString(R.string.text_editor_ui_preview_layout_failed_without_line, failure.message)
        }
        val line = UiPreviewSourceReader.positionOf(text, offset).first + 1
        return context.getString(R.string.text_editor_ui_preview_layout_failed, line, failure.message)
    }

    private fun diagnosticMessages(text: String, layout: UiPreviewSourceReader.Layout, diagnostics: List<UiPreviewDiagnostic>): List<String> {
        val lines = diagnostics.take(MAX_DIAGNOSTICS).map { lineMessage(text, layout, it.elementIndex, it.message) }
        val rest = diagnostics.size - lines.size
        return if (rest > 0) lines + "(+$rest)" else lines
    }

    /** [message] at the line of the element [elementIndex] (the layout's own line when unknown). */
    private fun lineMessage(text: String?, layout: UiPreviewSourceReader.Layout?, elementIndex: Int, message: String): String {
        if (text == null || layout == null) return message
        val offset = if (elementIndex >= 0) layout.elementOffset(elementIndex) else layout.literalOffset
        return context.getString(R.string.text_editor_ui_preview_line_note, UiPreviewSourceReader.positionOf(text, offset).first + 1, message)
    }

    private fun unavailableMessage(e: Throwable) =
        context.getString(R.string.text_editor_ui_preview_unavailable, UiPreviewFailures.messageOf(e))

    // ------------------------------------------------------------------------------------------------- memory

    private fun rememberedFiles(): List<String> =
        runCatching { MiscStateKeys.EDITOR_UI_PREVIEW_FILES.get() }.getOrDefault(emptyList())

    private fun isRemembered(key: String?) = key != null && key in rememberedFiles()

    private fun remember(key: String?, on: Boolean) {
        key ?: return
        val others = rememberedFiles().filter { it != key }
        MiscStateKeys.EDITOR_UI_PREVIEW_FILES.set(if (on) (listOf(key) + others).take(MAX_REMEMBERED_FILES) else others)
    }

    companion object {
        const val REFRESH_DELAY_MS = 400L
        private const val MAX_DIAGNOSTICS = 3
        private const val MAX_REMEMBERED_FILES = 100
        private const val DIVIDER_ALPHA = 0x40000000
        private const val OTHER_ENGINES =
            JavaScriptSource.EXECUTION_MODE_NODEJS or JavaScriptSource.EXECUTION_MODE_BUN or JavaScriptSource.EXECUTION_MODE_QUICKJS

        private fun nightModeOf(configuration: Configuration) =
            if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                UiPreviewContract.NIGHT_MODE_YES
            } else {
                UiPreviewContract.NIGHT_MODE_NO
            }

        private fun localeTagOf(configuration: Configuration): String =
            configuration.locales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag().orEmpty()
    }
}
