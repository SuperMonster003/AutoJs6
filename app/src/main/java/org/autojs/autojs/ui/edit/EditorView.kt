package org.autojs.autojs.ui.edit

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.view.Choreographer
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.pref.Pref.getEditorTextSize
import org.autojs.autojs.core.pref.Pref.setEditorTextSize
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.event.BackPressedHandler.HostActivity
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.model.autocomplete.AutoCompletion
import org.autojs.autojs.model.autocomplete.CodeCompletions
import org.autojs.autojs.model.indices.Module
import org.autojs.autojs.model.indices.Property
import org.autojs.autojs.model.script.Scripts.ACTION_ON_EXECUTION_FINISHED
import org.autojs.autojs.model.script.Scripts.EXTRA_EXCEPTION_COLUMN_NUMBER
import org.autojs.autojs.model.script.Scripts.EXTRA_EXCEPTION_LINE_NUMBER
import org.autojs.autojs.model.script.Scripts.EXTRA_EXCEPTION_MESSAGE
import org.autojs.autojs.model.script.Scripts.openByOtherApps
import org.autojs.autojs.model.script.Scripts.runWithBroadcastSender
import org.autojs.autojs.pio.PFiles.getNameWithoutExtension
import org.autojs.autojs.pio.PFiles.write
import org.autojs.autojs.storage.file.StableDraftFileHelper
import org.autojs.autojs.storage.file.TmpScriptFiles
import org.autojs.autojs.storage.history.HistoryPrefs
import org.autojs.autojs.storage.history.HistoryRepository
import org.autojs.autojs.storage.history.HistoryUriUtils
import org.autojs.autojs.storage.history.VersionHistoryController
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.tool.Callback
import org.autojs.autojs.ui.doc.ManualDialog
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar.OnHintClickListener
import org.autojs.autojs.ui.edit.debug.DebugBar
import org.autojs.autojs.ui.edit.editor.CodeEditor
import org.autojs.autojs.ui.edit.editor.CodeEditor.CheckedPatternSyntaxException
import org.autojs.autojs.ui.edit.editor.JavaScriptHighlighter
import org.autojs.autojs.ui.edit.editor.LayoutHelper
import org.autojs.autojs.ui.edit.keyboard.FunctionsKeyboardHelper
import org.autojs.autojs.ui.edit.keyboard.FunctionsKeyboardView
import org.autojs.autojs.ui.edit.keyboard.FunctionsKeyboardView.ClickCallback
import org.autojs.autojs.ui.edit.keyboard.SymbolsConfigStore
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.ui.edit.theme.Themes
import org.autojs.autojs.ui.edit.toolbar.DebugToolbarFragment
import org.autojs.autojs.ui.edit.toolbar.NormalToolbarFragment
import org.autojs.autojs.ui.edit.toolbar.SearchToolbarFragment
import org.autojs.autojs.ui.edit.toolbar.ToolbarFragment
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.widget.EWebView
import org.autojs.autojs.ui.widget.SimpleTextWatcher
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DisplayUtils.pxToSp
import org.autojs.autojs.util.DocsUtils.getUrl
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.R.string.text_unknown
import org.autojs.autojs6.databinding.EditorViewBinding
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

/**
 * Created by Stardust on Sep 28, 2017.
 * Transformed by SuperMonster003 on May 1, 2023.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 12, 2026.
 * Modified by SuperMonster003 as of Feb 15, 2026.
 */
@SuppressLint("CheckResult")
class EditorView : LinearLayout, OnHintClickListener, ClickCallback, ToolbarFragment.OnMenuItemClickListener {

    private var binding: EditorViewBinding = EditorViewBinding.bind(inflate(context, R.layout.editor_view, this))

    @JvmField
    val editor: CodeEditor = binding.editor

    @JvmField
    val debugBar: DebugBar = binding.debugBar

    private var _name: String? = null

    // Sticky save dirty flag.
    // Behavior:
    // - Set to true on ANY text change (including undo/redo).
    // - Reset to false only after successful save or when a new baseline text is loaded.
    //
    // zh-CN:
    // 保存按钮的 sticky 脏标记.
    // 行为:
    // - 任意文本变化 (包括 undo/redo) 都会置为 true.
    // - 仅在保存成功或加载新基线文本时重置为 false.
    @Volatile
    var saveStickyDirty: Boolean = false

    // Whether we have had any direct edits since last save/baseline.
    // Direct edits mean changes NOT caused by undo/redo buttons.
    //
    // zh-CN:
    // 自上次保存/建立基线以来是否发生过任何直接编辑.
    // 直接编辑指不是由撤销/重做按钮触发的文本变化.
    @Volatile
    private var mHadDirectEditSinceSave: Boolean = false

    // Whether a draft has been restored in this session.
    // If true, subsequent async file-load setInitialText() must NOT overwrite the restored draft.
    //
    // zh-CN:
    // 本次会话是否已恢复草稿.
    // 若为 true, 则后续异步文件加载触发的 setInitialText() 不得覆盖已恢复的草稿.
    @Volatile
    private var mDraftRestoredInThisSession: Boolean = false

    // Guard flag to mark text changes caused by undo/redo button actions.
    // This is used to distinguish direct edits from history navigation.
    //
    // zh-CN:
    // 用于标记由撤销/重做按钮动作导致的文本变化的哨兵标记.
    // 用于区分直接编辑与历史导航.
    @Volatile
    private var mUndoRedoButtonInProgress: Boolean = false

    // Whether user has touched/moved caret during large file loading.
    // If true, we should NOT force caret to 0 on load completion.
    //
    // zh-CN:
    // 用户是否在大文件加载期间触摸过/移动过光标.
    // 若为 true, 则加载完成时不要强制把光标设为 0.
    @Volatile
    private var mUserMovedCursorDuringLoading: Boolean = false

    var name: String
        get() = _name ?: "[ ${this.context.getString(text_unknown)} ]"
        set(value) {
            _name = value
        }

    var uri: Uri? = null

    var scriptExecutionId = 0
        private set

    val activity: FragmentActivity
        get() {
            var context = context
            while (context !is Activity && context is ContextWrapper) {
                context = context.baseContext
            }
            return context as FragmentActivity
        }

    val isTextChanged: Boolean
        get() = editor.isTextChanged

    private val scriptExecution: ScriptExecution?
        get() = AutoJs.instance.scriptEngineService.getScriptExecution(scriptExecutionId)

    // Whether the editor is currently loading text (including streaming append).
    // zh-CN: 编辑器是否正在加载文本 (包括流式追加阶段).
    @Volatile
    private var mEditorLoading: Boolean = false

    // Large file mode flag: keep undo/redo disabled to avoid memory/perf issues.
    // zh-CN: 大文件模式标记: 为避免内存/性能问题, 保持撤销/重做不可用.
    @Volatile
    private var mLargeFileMode: Boolean = false

    // Active large-file loading cancel flag.
    // zh-CN: 大文件加载的取消标记 (当前会话).
    private val mLargeFileCancel = AtomicBoolean(false)

    // Active IO subscription disposable for large-file loading.
    // zh-CN: 大文件加载的 IO 订阅 Disposable.
    @Volatile
    private var mLargeFileIoDisposable: Disposable? = null

    // Active streaming source stream reference (for forced close).
    // zh-CN: 流式读取的底层流引用 (用于强制 close 以打断阻塞 read).
    private val mLargeFileStreamRef = AtomicReference<InputStream?>(null)

    // Active Choreographer callback for streaming append.
    // zh-CN: 流式追加的 Choreographer 回调引用 (用于取消帧回调).
    @Volatile
    private var mLargeFileFrameCallback: Choreographer.FrameCallback? = null

    private val mCodeCompletionBar: CodeCompletionBar = binding.codeCompletionBar
    private val mInputMethodEnhanceBar: View = binding.inputMethodEnhanceBar
    private val mSymbolBar: CodeCompletionBar = binding.symbolBar
    private val mShowFunctionsButton: ImageView = binding.functions
    private val mFunctionsKeyboard: FunctionsKeyboardView = binding.functionsKeyboard
    private val mDocsWebView: EWebView = binding.docs
    private val mDrawerLayout: DrawerLayout = binding.drawerLayout

    private var mCurrentCharsetConfidence: Int = 0
    private var mCurrentCharset: Charset = DEFAULT_CHARSET_TO_WRITE_FILE
    private var mHadBom = false
    private var mReadOnly = false
    private var mAutoCompletion: AutoCompletion? = null
    private var mEditorTheme: Theme? = null
    private var mFunctionsKeyboardHelper: FunctionsKeyboardHelper? = null
    private val mOnRunFinishedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_ON_EXECUTION_FINISHED == intent.action) {
                scriptExecutionId = ScriptExecution.NO_ID
                if (mDebugging) {
                    exitDebugging()
                }
                setMenuItemStatus(R.id.run, true)
                val msg = intent.getStringExtra(EXTRA_EXCEPTION_MESSAGE)
                val line = intent.getIntExtra(EXTRA_EXCEPTION_LINE_NUMBER, -1)
                val col = intent.getIntExtra(EXTRA_EXCEPTION_COLUMN_NUMBER, 0)
                if (line >= 1) {
                    editor.jumpTo(line - 1, col)
                }
                msg?.let { showErrorMessage(it) }
            }
        }
    }
    private val mMenuItemStatus = SparseBooleanArray()
    private var mRestoredText: String? = null
    private val mNormalToolbar = NormalToolbarFragment()
    private var mDebugging = false
    private var mTmpSavedFileForRunning: File? = null

    private val mUiHandler = Handler(Looper.getMainLooper())

    // Delay showing loading bar to avoid flicker.
    // zh-CN: 延迟显示加载提示条, 避免闪烁.
    private val mLoadingBarShowDelayMs = 300L

    // Once shown, keep it visible for at least this duration.
    // zh-CN: 一旦显示, 则保证最短展示时间.
    private val mLoadingBarMinShowMs = 500L

    private val mLoadingBarContainer: View = binding.loadingBarContainer
    private val mLoadingBarText: TextView = binding.loadingBarText

    private var mLoadingBarRequested = false
    private var mLoadingBarShownAtMs = 0L

    private var mWasReadOnlyBeforeLoading = false

    private val mShowLoadingBarRunnable = Runnable {
        if (!mLoadingBarRequested) return@Runnable
        if (mLoadingBarContainer.isVisible) return@Runnable

        mLoadingBarContainer.visibility = VISIBLE
        mLoadingBarShownAtMs = SystemClock.uptimeMillis()
    }

    // IME bottom inset last applied to editor padding.
    // zh-CN: 最近一次应用到编辑器 padding 的 IME 底部值.
    private var mLastImeBottomInset = 0

    // Reusable rect for cursor visibility requests.
    // zh-CN: 光标可见性请求复用 Rect.
    private val mTmpCursorRect = Rect()

    // Reusable rect for window visible area.
    // zh-CN: 复用的窗口可见区域 Rect.
    private val mTmpWindowVisibleRect = Rect()

    // Search mode UI state.
    // zh-CN: 搜索模式 UI 状态.
    private var mInSearchMode = false
    private var mToolbarTitleBeforeSearch: CharSequence? = null
    private var mToolbarSubtitleBeforeSearch: CharSequence? = null
    private var mCurrentSearchQuery: String? = null
    private var mCurrentSearchUsingRegex: Boolean = false
    private var mCurrentSearchShowReplaceItem: Boolean = false

    // Remember last successful search even after exiting search mode.
    // zh-CN: 即使退出搜索模式, 也保留上一次成功搜索的 query/regex.
    private var mLastSearchQuery: String? = null
    private var mLastSearchUsingRegex: Boolean = false

    // Search stats (global count) state.
    // zh-CN: 搜索统计 (全局匹配数) 状态.
    private var mSearchStatsGeneration: Int = 0
    private var mSearchStatsDisposable: Disposable? = null
    private var mSearchStatsCurrentOrdinal: Int? = null
    private var mSearchStatsTotal: Int? = null
    private var mSearchStatsTotalCapped: Boolean = false
    private var mSearchStatsCounting: Boolean = false

    private val mSearchStatsDebounceMs = 300L
    private val mSearchStatsMaxTotal = 1000

    private val mSearchStatsDebounceRunnable = Runnable {
        restartSearchStatsAsync(reason = "debounce")
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setUpEditor()
        setUpInputMethodEnhancedBar()
        setUpFunctionsKeyboard()
        setUpImeInsetsHandling()
        setMenuItemStatus(R.id.save, false)
        mDocsWebView.apply {
            webView.settings.displayZoomControls = true
            webView.loadUrl(getUrl("index.html"))
        }
        Themes.getCurrent(context)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { theme: Theme? -> setTheme(theme) }
        initNormalToolbar()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(mOnRunFinishedReceiver, IntentFilter(ACTION_ON_EXECUTION_FINISHED), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(mOnRunFinishedReceiver, IntentFilter(ACTION_ON_EXECUTION_FINISHED))
        }
        (context as? HostActivity)?.backPressedObserver?.registerHandler(mFunctionsKeyboardHelper)
    }

    fun handleIntent(intent: Intent): Observable<String> {
        // Initialize menu state early to avoid "first render" flicker.
        // zh-CN: 尽早初始化菜单状态, 避免首次渲染闪烁.
        mEditorLoading = true

        // Do NOT reset dirty flags if we already restored a draft in this session.
        // Otherwise, the editor will keep the draft text but lose "needs save" state,
        // causing Save button to turn off and exit-confirm not to show.
        //
        // zh-CN:
        // 若本会话已恢复草稿, 则不要重置脏标记.
        // 否则会出现草稿文本仍在但 "需要保存" 状态丢失,
        // 导致保存按钮熄灭且退出不提示保存.
        if (!mDraftRestoredInThisSession) {
            saveStickyDirty = false
            mHadDirectEditSinceSave = false
        }

        syncPrimaryMenuState()

        val name = intent.getStringExtra(EXTRA_NAME)
        if (name != null) {
            this.name = name
        }
        val readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false).also {
            mReadOnly = it
        }
        if (readOnly) {
            mInputMethodEnhanceBar.visibility = GONE
        }
        return handleText(intent)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                val saveEnabled = intent.getBooleanExtra(EXTRA_SAVE_ENABLED, true)
                if (!saveEnabled) {
                    findViewById<View>(R.id.save).visibility = GONE
                } else if (readOnly) {
                    findViewById<View>(R.id.undo).visibility = GONE
                    findViewById<View>(R.id.redo).visibility = GONE
                    findViewById<View>(R.id.save).visibility = GONE
                }
                if (mLargeFileMode) {
                    findViewById<View>(R.id.undo).visibility = GONE
                    findViewById<View>(R.id.redo).visibility = GONE
                }
                if (!intent.getBooleanExtra(EXTRA_RUN_ENABLED, true)) {
                    findViewById<View>(R.id.run).visibility = GONE
                }
                if (readOnly) {
                    editor.setReadOnly(true)
                }

                // Loading is finished for intent-handling stage (actual file loading may continue).
                // zh-CN: Intent 处理阶段结束 (实际文件加载可能仍在继续).
                mEditorLoading = false
                syncPrimaryMenuState()
            }
    }

    fun setRestoredText(text: String) {
        mRestoredText = text
        editor.text = text
    }

    /**
     * Restore draft text for this editor session.
     *
     * Behavior:
     * - Applies text immediately.
     * - Marks current session as "draft restored", so async file loading won't overwrite it.
     * - Marks editor state as dirty (needs save) and refreshes menu state.
     *
     * zh-CN:
     * 为本次编辑会话恢复草稿文本.
     *
     * 行为:
     * - 立即应用文本.
     * - 标记本会话为 "已恢复草稿", 防止异步文件加载覆盖.
     * - 标记为未保存并刷新菜单状态.
     */
    fun restoreDraftTextForThisSession(text: String) {
        mDraftRestoredInThisSession = true
        mRestoredText = text
        editor.text = text
        markRestoredDraftAsDirty()
    }

    /**
     * Mark a restored draft as unsaved and refresh menu state.
     *
     * Rationale:
     * - Draft restore should not be treated as "saved baseline".
     * - Otherwise Save button may appear disabled and exit-confirm may not show.
     *
     * zh-CN:
     * 将已恢复的草稿标记为未保存, 并刷新菜单状态.
     *
     * 理由:
     * - 草稿恢复不应被当作 "已保存基线".
     * - 否则保存按钮可能呈灰色, 且退出确认可能不弹出.
     */
    fun markRestoredDraftAsDirty() {
        saveStickyDirty = true
        mHadDirectEditSinceSave = true
        syncPrimaryMenuState()
    }

    private fun handleText(intent: Intent): Observable<String> {
        val content = intent.getStringExtra(EXTRA_CONTENT)
        if (content != null) {
            setInitialText(content)
        }

        val path = intent.getStringExtra(EXTRA_PATH)
        val uri = path?.let {
            Uri.fromFile(File(it))
        } ?: intent.data
        this.uri = uri

        if (_name == null && uri != null) {
            uri.path?.let {
                name = getNameWithoutExtension(it)
            }
        }

        return when {
            content != null -> Observable.just(content)
            uri != null -> loadUri(uri)
            else -> Observable.error(IllegalArgumentException("path and content is empty"))
        }
    }

    private fun findHostActivityOrNull(): Activity? {
        var c = context
        while (c !is Activity && c is ContextWrapper) {
            c = c.baseContext
        }
        return c as? Activity
    }

    private fun setUpImeInsetsHandling() {
        // Handle IME insets to avoid keyboard covering the editor.
        // zh-CN: 处理 IME inset, 避免软键盘遮挡编辑器内容.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            // Compute the real overlap between editor and the visible window area.
            // zh-CN: 计算 editor 与窗口可见区域之间的真实重叠量.
            val activity = findHostActivityOrNull()
            val targetBottom = if (activity != null) {
                activity.window.decorView.getWindowVisibleDisplayFrame(mTmpWindowVisibleRect)

                val loc = IntArray(2)
                editor.getLocationOnScreen(loc)
                val editorBottomOnScreen = loc[1] + editor.height

                (editorBottomOnScreen - mTmpWindowVisibleRect.bottom).coerceAtLeast(0)
            } else {
                0
            }

            if (targetBottom != mLastImeBottomInset) {
                mLastImeBottomInset = targetBottom

                // Apply bottom padding only when the editor is actually overlapped by IME.
                // zh-CN: 仅当 editor 实际被 IME 遮挡时才应用底部 padding.
                val v = editor
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, targetBottom)

                // Only ensure cursor visible when IME becomes visible.
                // zh-CN: 仅在 IME 变为可见时确保光标可见.
                if (imeVisible) {
                    post { ensureCursorVisibleIfPossible() }
                }
            }

            // Do not consume insets.
            // zh-CN: 不消费 insets.
            insets
        }
    }

    private fun requestApplyInsetsSoon() {
        // Helps fixing timing issues when IME hides/shows during dialog dismiss/focus change.
        // zh-CN: 用于修复对话框 dismiss/焦点切换期间 IME 显隐导致的时序问题.
        post { ViewCompat.requestApplyInsets(this@EditorView) }
    }

    private fun ensureCursorVisibleIfPossible() {
        // Bring the caret into visible area when keyboard shows, without "scrolling back" on hide.
        // zh-CN: 键盘弹起时将光标区域滚动到可见范围内, 键盘收起时不回滚.
        val editText = editor.codeEditText
        val textLen = editText.text?.length ?: 0
        if (textLen <= 0) return

        val layout = editText.layout ?: return
        val sel = editText.selectionStart.coerceIn(0, textLen)

        val line = LayoutHelper.getLineOfChar(layout, sel).coerceAtLeast(0)
        val lineTop = layout.getLineTop(line)
        val lineBottom = layout.getLineBottom(line)

        // Compute a small rect around the current line/caret.
        // zh-CN: 计算一个围绕当前行/光标的小矩形区域.
        val x = runCatching { layout.getPrimaryHorizontal(sel).toInt() }.getOrElse { 0 }
        val left = (x - editText.paddingLeft - 16).coerceAtLeast(0)
        val right = left + 32

        mTmpCursorRect.set(
            /* left = */ left,
            /* top = */ lineTop,
            /* right = */ right,
            /* bottom = */ lineBottom,
        )

        // Request rectangle visible inside scroll container.
        // zh-CN: 请求在滚动容器内显示该矩形区域.
        editText.requestRectangleOnScreen(mTmpCursorRect, true)
    }

    private fun ensureEditorHasCursorAfterLoadIfNeeded() {
        // Ensure there is a visible caret after loading, so jump operations can work.
        // zh-CN: 加载完成后确保存在可见光标, 以使跳转操作可用.
        val editText = editor.codeEditText
        val len = editText.text?.length ?: 0
        if (len <= 0) return

        // If selection is invalid or collapsed at -1-like state, normalize.
        // zh-CN: 若 selection 异常, 则归一化.
        val safeSel = editText.selectionStart.coerceIn(0, len)

        // Request focus without forcing soft keyboard.
        // zh-CN: 请求焦点但不强制弹出软键盘.
        editText.requestFocus()
        editText.isCursorVisible = true
        editText.setSelection(safeSel)

        // Optionally ensure visible once.
        // zh-CN: 可选地确保一次可见.
        ensureCursorVisibleIfPossible()
    }

    private fun setLoadingBar(loading: Boolean) {
        if (loading) {
            mLoadingBarRequested = true
            mUiHandler.removeCallbacks(mShowLoadingBarRunnable)
            mUiHandler.postDelayed(mShowLoadingBarRunnable, mLoadingBarShowDelayMs)
            return
        }

        mLoadingBarRequested = false
        mUiHandler.removeCallbacks(mShowLoadingBarRunnable)

        if (mLoadingBarContainer.visibility != VISIBLE) {
            mLoadingBarContainer.visibility = GONE
            return
        }

        val elapsed = SystemClock.uptimeMillis() - mLoadingBarShownAtMs
        val remain = (mLoadingBarMinShowMs - elapsed).coerceAtLeast(0L)

        if (remain == 0L) {
            mLoadingBarContainer.visibility = GONE
            return
        }

        mUiHandler.postDelayed({
            if (!mLoadingBarRequested) {
                mLoadingBarContainer.visibility = GONE
            }
        }, remain)
    }

    private fun setEnhanceBarInteractive(interactive: Boolean) {
        // When not interactive, hide function/completion bars and show loading bar.
        // zh-CN: 非可操作状态时隐藏功能/补全栏, 显示加载提示条.
        when (interactive) {
            true -> {
                mShowFunctionsButton.visibility = VISIBLE
                mCodeCompletionBar.visibility = VISIBLE
            }
            else -> {
                mShowFunctionsButton.visibility = INVISIBLE
                mCodeCompletionBar.visibility = INVISIBLE
            }
        }
        if (!mSymbolBar.isGone) {
            when (interactive) {
                true -> {
                    mSymbolBar.visibility = VISIBLE
                }
                else -> {
                    mSymbolBar.visibility = INVISIBLE
                }
            }
        }
    }

    private fun beginEditorLoadingUi(initialMessage: String) {
        mLoadingBarText.text = initialMessage
        setEnhanceBarInteractive(false)
        setLoadingBar(true)

        // Use read-only during loading to avoid accidental edits.
        // zh-CN: 加载期间启用只读以避免误修改.
        mWasReadOnlyBeforeLoading = mReadOnly
        if (!mReadOnly) {
            editor.setReadOnly(true)
        }
    }

    private fun endEditorLoadingUi() {
        setLoadingBar(false)
        setEnhanceBarInteractive(!mReadOnly)

        // Restore read-only state.
        // zh-CN: 恢复只读状态.
        if (!mWasReadOnlyBeforeLoading) {
            editor.setReadOnly(false)
        }
    }

    private fun loadUri(uri: Uri): Observable<String> {
        val streamThresholdBytes = LARGE_FILE_MODE_THRESHOLD

        val sizeOrNull = runCatching { queryContentLengthOrNull(uri) }.getOrNull()
        if (sizeOrNull != null && sizeOrNull >= streamThresholdBytes) {
            // Large file: show loading in enhance bar, allow viewing/scrolling (read-only).
            // zh-CN: 大文件: 在增强栏显示加载提示, 允许查看/滚动 (只读).
            beginEditorLoadingUi(context.getString(R.string.text_loading_with_dots))
            return loadUriStreamed(uri)
        }

        return Observable
            .fromCallable {
                val resolver = context.contentResolver
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

                val detectedCharsetWrapper = StringUtils.detectCharset(rawBytes)
                mCurrentCharsetConfidence = detectedCharsetWrapper.confidence ?: 0
                mCurrentCharset = detectedCharsetWrapper.charsetOrDefault()
                mHadBom = StringUtils.hasBom(rawBytes, mCurrentCharset)

                val offset = if (mHadBom) {
                    StringUtils.bomBytes(mCurrentCharset).size.coerceAtMost(rawBytes.size)
                } else 0
                val len = (rawBytes.size - offset).coerceAtLeast(0)

                String(rawBytes, offset, len, mCurrentCharset)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                beginEditorLoadingUi(context.getString(R.string.text_loading_with_dots))
                mEditorLoading = true
                syncPrimaryMenuState()
            }
            .doOnNext { text: String ->
                post {
                    runCatching {
                        setInitialText(text)

                        // IMPORTANT: end loading UI on success.
                        // zh-CN: 重要: 成功时必须结束加载 UI.
                        endEditorLoadingUi()

                        mEditorLoading = false
                        syncPrimaryMenuState()
                    }.onFailure {
                        endEditorLoadingUi()
                        mEditorLoading = false
                        syncPrimaryMenuState()
                    }
                }
            }
            .doOnError {
                endEditorLoadingUi()
                mEditorLoading = false
                syncPrimaryMenuState()
            }
    }

    private fun queryContentLengthOrNull(uri: Uri): Long? {
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it).length().coerceAtLeast(0L) }
            "content" -> {
                val resolver = context.contentResolver
                var cursor: Cursor? = null
                try {
                    cursor = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                    if (cursor != null && cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0 && !cursor.isNull(idx)) {
                            cursor.getLong(idx).coerceAtLeast(0L)
                        } else null
                    } else null
                } finally {
                    runCatching { cursor?.close() }
                }
            }
            else -> null
        }
    }

    override fun onDetachedFromWindow() {
        // Cancel loading before unregistering receivers to avoid ANR on exit.
        // zh-CN: 退出前先取消加载, 避免返回退出时 ANR.
        cancelLargeFileLoading()

        super.onDetachedFromWindow()
        context.unregisterReceiver(mOnRunFinishedReceiver)
        (context as? HostActivity)?.backPressedObserver?.unregisterHandler(mFunctionsKeyboardHelper)
    }

    fun getPreferredSearchQueryForDialogOrNull(): String? =
        (mCurrentSearchQuery ?: mLastSearchQuery)

    fun getPreferredSearchUsingRegexForDialog(): Boolean =
        (if (mInSearchMode) mCurrentSearchUsingRegex else mLastSearchUsingRegex)

    // Cancel current large-file loading as soon as possible.
    // zh-CN: 尽快取消当前大文件加载.
    fun cancelLargeFileLoading() {
        // Mark cancel first so both IO/UI can observe it.
        // zh-CN: 先标记取消, 让 IO/UI 两侧都能观察到.
        mLargeFileCancel.set(true)

        // Stop UI frame callback quickly.
        // zh-CN: 尽快停止 UI 帧回调.
        mLargeFileFrameCallback?.let { cb ->
            runCatching { Choreographer.getInstance().removeFrameCallback(cb) }
        }
        mLargeFileFrameCallback = null

        // Dispose IO subscription.
        // zh-CN: 取消 IO 订阅.
        mLargeFileIoDisposable?.let { d ->
            runCatching { d.dispose() }
        }
        mLargeFileIoDisposable = null

        // Force close stream to interrupt blocking read().
        // zh-CN: 强制关闭流以打断可能阻塞的 read().
        mLargeFileStreamRef.getAndSet(null)?.let { s ->
            runCatching { s.close() }
        }

        // Cleanup UI state best-effort (do NOT re-enable editing in read-only editor).
        // zh-CN: 尽力清理 UI 状态 (只读编辑器不要被重新启用编辑).
        post {
            runCatching {
                endEditorLoadingUi()
            }
            mEditorLoading = false
            syncPrimaryMenuState()
        }
    }

    private fun loadUriStreamed(uri: Uri): Observable<String> {
        val readBytes = AtomicLong(0L)

        // Reset cancel state for a new session.
        // zh-CN: 新的加载会话开始前重置取消标记.
        mLargeFileCancel.set(false)

        return Observable
            .create { emitter ->
                val resolver = context.contentResolver

                // Emit an early placeholder so handleIntent() can proceed.
                // zh-CN: 提前发出占位字符串, 使 handleIntent() 能继续运行.
                emitter.onNext("")

                val raw = resolver.openInputStream(uri) ?: run {
                    emitter.onError(IllegalStateException("Cannot open input stream for $uri"))
                    return@create
                }

                // Keep a reference for cancellation.
                // zh-CN: 保存引用以便取消时强制 close.
                mLargeFileStreamRef.set(raw)

                val counted = CountingInputStream(raw, readBytes)
                val bis = BufferedInputStream(counted)

                try {
                    bis.use { input ->
                        if (mLargeFileCancel.get() || emitter.isDisposed) {
                            // Cancel early.
                            // zh-CN: 提前取消.
                            return@use
                        }

                        val sampleLimitBytes = 64 * 1024
                        input.mark(sampleLimitBytes + 8)

                        val sample = ByteArray(sampleLimitBytes)
                        val sampleRead = input.read(sample).coerceAtLeast(0)
                        val sampleBytes = if (sampleRead == sample.size) sample else sample.copyOf(sampleRead)

                        val detected = StringUtils.detectCharset(sampleBytes)
                        mCurrentCharsetConfidence = detected.confidence ?: 0
                        mCurrentCharset = detected.charsetOrDefault()
                        mHadBom = StringUtils.hasBom(sampleBytes, mCurrentCharset)

                        runCatching { input.reset() }.getOrElse {
                            if (!emitter.isDisposed && !mLargeFileCancel.get()) {
                                emitter.onError(IllegalStateException("Stream reset failed for $uri"))
                            }
                            return@use
                        }

                        streamDecodeAndEmit(input, emitter)
                    }
                } finally {
                    // Clear reference when finished/failed.
                    // zh-CN: 结束/失败后清理引用.
                    mLargeFileStreamRef.set(null)
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .let { upstream ->
                bridgeStreamToUiWithProgress(
                    upstream = upstream,
                )
            }
    }

    private fun bridgeStreamToUiWithProgress(
        upstream: Observable<String>,
    ): Observable<String> {
        val queue = ConcurrentLinkedQueue<String>()
        val done = AtomicBoolean(false)
        val started = AtomicBoolean(false)

        // Dispose previous session if any.
        // zh-CN: 若存在上一会话, 则先取消.
        runCatching { mLargeFileIoDisposable?.dispose() }
        mLargeFileIoDisposable = null

        // Subscribe on IO side, push chunks into queue.
        // zh-CN: 在 IO 侧订阅, 将分片推入队列.
        mLargeFileIoDisposable = upstream
            .observeOn(Schedulers.io())
            .subscribe({ chunk ->
                if (mLargeFileCancel.get()) {
                    // Stop producing.
                    // zh-CN: 停止生产.
                    done.set(true)
                    return@subscribe
                }
                if (chunk.isNotEmpty()) {
                    queue.add(chunk)
                }
            }, { e ->
                done.set(true)
                if (!mLargeFileCancel.get()) {
                    post { endEditorLoadingUi() }
                    e.printStackTrace()
                }
            }, {
                done.set(true)
            })

        return Observable
            .just("")
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                if (started.compareAndSet(false, true)) {
                    startStreamingAppendWithProgress(queue, done)
                }
            }
    }

    private fun startStreamingAppendWithProgress(
        queue: ConcurrentLinkedQueue<String>,
        done: AtomicBoolean,
    ) {
        val editText = editor.codeEditText

        mLargeFileMode = true
        mEditorLoading = true
        mUserMovedCursorDuringLoading = false
        syncPrimaryMenuState()

        editText.setLoadingText(true)
        editor.setRedoUndoEnabled(false)
        editText.setLoadingGutterDigits(3)
        editText.setText("")
        editText.setSelection(0)

        val choreographer = Choreographer.getInstance()

        // Per-frame time budget for appending text (favor throughput).
        // zh-CN: 每帧用于追加文本的时间预算 (优先吞吐).
        val appendBudgetMsIdle = 12L
        val appendBudgetMsTouching = 3L

        // Upper bound of chars appended per frame as a second guardrail.
        // zh-CN: 每帧追加字符数的上限, 作为第二道护栏.
        val maxCharsPerFrameIdle = 128 * 1024
        val maxCharsPerFrameTouching = 16 * 1024

        // Reuse StringBuilder to reduce per-frame allocations.
        // zh-CN: 复用 StringBuilder, 降低每帧分配压力.
        val sb = StringBuilder(128 * 1024)

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (mLargeFileCancel.get()) {
                    // Cancel: stop immediately and do not schedule next frame.
                    // zh-CN: 取消: 立即停止且不再调度下一帧.
                    editText.setLoadingText(false)
                    mEditorLoading = false
                    syncPrimaryMenuState()
                    endEditorLoadingUi()
                    mLargeFileFrameCallback = null
                    return
                }

                val finished = done.get() && queue.isEmpty()
                if (finished) {
                    editText.setLoadingText(false)
                    editor.setRedoUndoEnabled(false)
                    editor.markUndoRedoBaselineAsUnchanged()

                    mEditorLoading = false
                    syncPrimaryMenuState()

                    // IMPORTANT: end loading UI on success.
                    // zh-CN: 重要: 成功时必须结束加载 UI.
                    endEditorLoadingUi()

                    // Ensure caret/focus after loading is done.
                    // - Default: caret at 0 for "read from top".
                    // - If user touched during loading: keep current caret (do not force to 0).
                    //
                    // zh-CN:
                    // 加载结束后确保光标/焦点.
                    // - 默认: 光标置于 0, 方便从头阅读.
                    // - 若用户在加载期间触摸过: 保持当前光标, 不强制跳回 0.
                    post {
                        if (!mUserMovedCursorDuringLoading) {
                            runCatching { editText.setSelection(0) }
                        }
                        ensureEditorHasCursorAfterLoadIfNeeded()
                    }

                    mLargeFileFrameCallback = null
                    return
                }

                val now = SystemClock.uptimeMillis()

                mLoadingBarText.text = context.getString(R.string.text_loading_with_dots)

                val userTouching = editor.isUserTouching()

                val budgetMs = if (userTouching) appendBudgetMsTouching else appendBudgetMsIdle
                val maxCharsThisFrame = if (userTouching) maxCharsPerFrameTouching else maxCharsPerFrameIdle

                // Append with time budget.
                // zh-CN: 采用时间预算追加.
                val t0 = now

                sb.setLength(0)
                while (sb.length < maxCharsThisFrame) {
                    if (mLargeFileCancel.get()) {
                        break
                    }
                    if (SystemClock.uptimeMillis() - t0 >= budgetMs) {
                        break
                    }
                    val s = queue.poll() ?: break
                    sb.append(s)
                }

                if (sb.isNotEmpty()) {
                    editText.beginBatchEdit()
                    try {
                        editText.text?.append(sb)
                    } finally {
                        editText.endBatchEdit()
                    }
                }

                choreographer.postFrameCallback(this)
            }
        }

        // Keep reference for cancellation.
        // zh-CN: 保存引用以便取消.
        mLargeFileFrameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    private fun streamDecodeAndEmit(input: BufferedInputStream, emitter: ObservableEmitter<String>) {
        // Skip BOM bytes by consuming from raw stream before decoding.
        // zh-CN: 在解码前先从原始字节流中消费 BOM 字节.
        val bomBytes = StringUtils.bomBytes(mCurrentCharset)
        if (mHadBom && bomBytes.isNotEmpty()) {
            var skipped = 0
            while (skipped < bomBytes.size) {
                if (mLargeFileCancel.get() || emitter.isDisposed) {
                    // Cancel early.
                    // zh-CN: 提前取消.
                    return
                }
                val r = input.read()
                if (r == -1) break
                skipped++
            }
        }

        val reader = InputStreamReader(input, mCurrentCharset)
        val buf = CharArray(32 * 1024)
        while (!emitter.isDisposed && !mLargeFileCancel.get()) {
            val n = reader.read(buf)
            if (n <= 0) break
            emitter.onNext(String(buf, 0, n))
        }

        if (!emitter.isDisposed && !mLargeFileCancel.get()) {
            emitter.onComplete()
        }
    }

    private fun setInitialText(text: String) {
        // NOTE: This method should not manage loading UI lifecycle anymore.
        // zh-CN: 注意: 此方法不再管理加载 UI 的生命周期.
        mEditorLoading = true
        syncPrimaryMenuState()

        // If draft has been restored in this session, do NOT overwrite it with async file load results.
        // Still end loading flags and sync menu state to keep UI consistent.
        //
        // zh-CN:
        // 若本会话已恢复草稿, 则不要用异步文件加载结果覆盖它.
        // 但仍需结束 loading 标记并同步菜单状态, 保持 UI 一致.
        if (mDraftRestoredInThisSession) {
            mEditorLoading = false
            syncPrimaryMenuState()
            return
        }

        if (mRestoredText != null) {
            editor.text = mRestoredText!!
            mRestoredText = null

            editor.markUndoRedoBaselineAsUnchanged()
            editor.setRedoUndoEnabled(!mLargeFileMode)

            // Reset sticky dirty because we just established a new baseline.
            // zh-CN: 因刚刚建立了新的基线, 重置 sticky 脏标记.
            saveStickyDirty = false
            mHadDirectEditSinceSave = false

            mEditorLoading = false
            syncPrimaryMenuState()

            // Refresh highlight for restored text if size allows.
            // zh-CN: 若大小允许, 则对恢复文本刷新一次高亮.
            post { editor.refreshHighlightTokensIfAllowed() }

            return
        }

        val progressiveThreshold = LARGE_FILE_MODE_THRESHOLD
        if (text.length > progressiveThreshold) {
            mLargeFileMode = true
            setInitialTextProgressively(text)
            return
        }

        val editText = editor.codeEditText
        editText.setLoadingText(true)
        editor.setRedoUndoEnabled(false)
        try {
            editText.setText(text)
            editor.markUndoRedoBaselineAsUnchanged()

            // Reset sticky dirty because we just established a new baseline.
            // zh-CN: 因刚刚建立了新的基线, 重置 sticky 脏标记.
            saveStickyDirty = false
            mHadDirectEditSinceSave = false
        } finally {
            editor.setRedoUndoEnabled(true)
            editText.setLoadingText(false)
        }

        mEditorLoading = false
        syncPrimaryMenuState()

        // Ensure caret/focus after fast load.
        // zh-CN: 快速加载完成后确保光标/焦点.
        post { ensureEditorHasCursorAfterLoadIfNeeded() }

        // Re-highlight once after fast load, but only when size is within highlighter limit.
        // zh-CN: 快速加载完成后主动触发一次高亮, 但仅在文本大小不超过高亮上限时执行.
        if (text.length <= JavaScriptHighlighter.MAX_HIGHLIGHT_CHARS) {
            post { editor.refreshHighlightTokensIfAllowed() }
        }
    }

    internal fun syncPrimaryMenuState() {
        // Unified primary buttons state.
        // zh-CN: 统一主按钮状态.
        when {
            mEditorLoading -> {
                setMenuItemStatus(R.id.run, false)
                setMenuItemStatus(R.id.undo, false)
                setMenuItemStatus(R.id.redo, false)
                setMenuItemStatus(R.id.save, false)
            }
            mLargeFileMode -> {
                // Large file: keep undo/redo disabled; save is enabled only when text changed by explicit edits.
                // zh-CN: 大文件: 撤销/重做保持禁用; 保存仅在明确编辑导致文本变化时启用.
                setMenuItemStatus(R.id.run, true)
                setMenuItemStatus(R.id.undo, false)
                setMenuItemStatus(R.id.redo, false)
                setMenuItemStatus(R.id.save, saveStickyDirty)
            }
            else -> {
                // Normal mode.
                // zh-CN: 普通模式.
                setMenuItemStatus(R.id.run, true)
                setMenuItemStatus(R.id.undo, editor.canUndo())
                setMenuItemStatus(R.id.redo, editor.canRedo())
                setMenuItemStatus(R.id.save, saveStickyDirty)
            }
        }
    }

    private fun setInitialTextProgressively(text: String) {
        val editText = editor.codeEditText

        // Mark loading state to suppress heavy callbacks during bulk insertion.
        // zh-CN: 标记加载状态, 在批量插入期间抑制高开销回调.
        editText.setLoadingText(true)

        // Disable undo/redo recording during progressive load to avoid huge history and allocations.
        // zh-CN: 渐进式加载期间禁用 undo/redo 记录, 避免巨大历史与大量分配.
        editor.setRedoUndoEnabled(false)

        // Clear existing content quickly.
        // zh-CN: 快速清空现有内容.
        editText.setText("")

        // Do NOT call editor.setProgress(true) here.
        // zh-CN: 不要在这里再次 setProgress(true), 进度对话框已由 loadUri() 负责显示.
        // editor.setProgress(true)

        // Frame time budget in ms (tune for devices).
        // zh-CN: 单帧时间预算 (毫秒), 可按设备调参.
        val frameBudgetMs = 7L

        // Adaptive chunk size to balance layout cost and responsiveness.
        // zh-CN: 自适应分片大小, 在布局成本与响应性之间取平衡.
        var chunkSize = 64 * 1024
        val minChunkSize = 4 * 1024
        val maxChunkSize = 256 * 1024

        var index = 0

        val choreographer = Choreographer.getInstance()

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (index >= text.length) {
                    // Finish: mark current content as baseline without re-setting text.
                    // zh-CN: 完成: 将当前内容设为基线, 避免再次 setText.
                    editText.setLoadingText(false)
                    editor.setRedoUndoEnabled(true)
                    editor.markUndoRedoBaselineAsUnchanged()
                    editor.setProgress(false)
                    return
                }

                val start = SystemClock.uptimeMillis()

                var appendedChunks = 0

                // Do as much work as possible within the frame budget.
                // zh-CN: 在单帧预算内尽可能推进加载.
                while (index < text.length) {
                    val end = (index + chunkSize).coerceAtMost(text.length)
                    editText.text?.append(text, index, end)
                    index = end
                    appendedChunks++

                    if (SystemClock.uptimeMillis() - start >= frameBudgetMs) {
                        break
                    }
                }

                // Adapt chunk size based on how many chunks we managed to append in this frame.
                // zh-CN: 根据本帧完成的分片数量自适应调整 chunkSize.
                @Suppress("AssignedValueIsNeverRead")
                chunkSize = when {
                    appendedChunks <= 1 -> (chunkSize / 2).coerceAtLeast(minChunkSize)
                    appendedChunks >= 4 -> (chunkSize * 2).coerceAtMost(maxChunkSize)
                    else -> chunkSize
                }

                // Continue on next frame.
                // zh-CN: 下一帧继续执行.
                choreographer.postFrameCallback(this)
            }
        }

        // Start on next frame to let the progress dialog animate.
        // zh-CN: 下一帧开始, 让进度对话框先跑起来.
        choreographer.postFrameCallback(callback)
    }

    private fun setMenuItemStatus(id: Int, enabled: Boolean) {
        mMenuItemStatus.put(id, enabled)

        // Always update the normal toolbar cache as well, so switching toolbars won't lose state.
        // zh-CN: 同时更新普通工具栏的缓存, 避免切换工具栏后状态丢失.
        mNormalToolbar.setMenuItemStatus(id, enabled)

        // Also update current visible toolbar fragment if exists.
        // zh-CN: 若当前存在正在显示的工具栏 Fragment, 也同步更新它.
        val supportManager = activity.supportFragmentManager
        val fragment = supportManager.findFragmentById(R.id.toolbar_menu) as ToolbarFragment<*>?
        fragment?.setMenuItemStatus(id, enabled)
    }

    fun getMenuItemStatus(id: Int, defValue: Boolean): Boolean {
        return mMenuItemStatus[id, defValue]
    }

    private fun initNormalToolbar() {
        val fm = activity.supportFragmentManager
        val existing = fm.findFragmentById(R.id.toolbar_menu)

        // Always (re)bind listeners for both the cached normal toolbar and any restored fragment.
        // This fixes the case where FragmentManager restores a ToolbarFragment instance after
        // Activity recreation, but its listener fields are lost (null), causing "click does nothing".
        //
        // zh-CN:
        // 总是为普通工具栏缓存对象与任何已恢复的 Fragment 重新绑定监听器.
        // 这可以修复 Activity 重建后 FragmentManager 恢复出的 ToolbarFragment 实例其监听字段丢失 (null),
        // 从而出现 "点击无反应" 的问题.
        mNormalToolbar.apply {
            setOnMenuItemClickListener(this@EditorView)
            setOnMenuItemLongClickListener { id ->
                when {
                    id == R.id.run && !mReadOnly -> true.also { debug() }
                    else -> false
                }
            }
        }

        (existing as? ToolbarFragment<*>)?.apply {
            setOnMenuItemClickListener(this@EditorView)
            setOnMenuItemLongClickListener { id ->
                when {
                    id == R.id.run && !mReadOnly -> true.also { debug() }
                    else -> false
                }
            }

            // Sync menu state after rebinding to avoid stale enabled flags.
            // zh-CN: 重新绑定后同步一次菜单状态, 避免 enabled 标记过期.
            post { syncPrimaryMenuState() }
        }

        if (existing == null) {
            showNormalToolbar()
        }
    }

    private fun setUpFunctionsKeyboard() {
        mFunctionsKeyboardHelper = FunctionsKeyboardHelper.with(context as Activity)
            .setContent(editor)
            .setFunctionsTrigger(mShowFunctionsButton)
            .setFunctionsView(mFunctionsKeyboard)
            .setEditView(editor.codeEditText)
            .build()

        // @TodoDiary by 抠脚本人 on Jul 10, 2023.
        //  ! 不清楚作用, 暂时注释掉.
        //  ! en-US (translated by SuperMonster003 on Jul 28, 2024):
        //  ! Not sure of its function, commented it out temporarily.
        // @Hint by SuperMonster003 on Jul 12, 2023.
        //  ! The click event callback is registered here
        //  ! so that the properties name of the functional keyboard
        //  ! can implement the functionality of the interface:
        //  ! Click: auto-completion, with parentheses, periods, etc. as appropriate.
        //  ! Long-press: display the method, property or module equivalent in a floating window (if exists).
        //  ! zh-CN:
        //  ! 此处的点击事件回调注册是为了使功能键盘智能提示的属性可以实现其接口对应的功能:
        //  ! 点击: 自动补全, 并根据情况添加括号或句点符号等.
        //  ! 长按: 以浮动窗口形式展示 [方法/属性/模块] 对应的文档内容 (如果存在的话).
        mFunctionsKeyboard.setClickCallback(this)

        mShowFunctionsButton.setOnLongClickListener {
            true.also { editor.beautifyCode() }
        }
    }

    private fun setUpInputMethodEnhancedBar() {
        mSymbolBar.let { bar ->
            bar.setOnHintClickListener(this)
            refreshSymbolsBar()
        }
        mCodeCompletionBar.let { bar ->
            bar.setOnHintClickListener(this)
            AutoCompletion(context, editor.codeEditText).apply {
                setAutoCompleteCallback { bar.codeCompletions = it }
                mAutoCompletion = this
            }
        }
    }

    fun refreshSymbolsBar() {
        SymbolsConfigStore.ensureDefaultProfileExists(context)
        val symbols = SymbolsConfigStore.getEnabledSymbolsForActiveProfile(context)
        mSymbolBar.isVisible = symbols.isNotEmpty()
        mSymbolBar.codeCompletions = CodeCompletions.just(symbols)
    }

    private fun setUpEditor() {
        editor.let { editor ->
            editor.codeEditText.let { editText ->
                // Observe user touch in text area to preserve caret position after large-file load.
                // zh-CN: 监听用户在文本区域的触摸, 以便大文件加载完成后保留光标位置.
                editText.onUserTouchInTextArea = {
                    if (mEditorLoading && mLargeFileMode) {
                        mUserMovedCursorDuringLoading = true
                    }
                }

                editText.addTextChangedListener(SimpleTextWatcher { _ ->
                    // Skip menu state updates during progressive loading.
                    // zh-CN: 渐进式加载期间跳过菜单状态更新.
                    if (editText.isLoadingText() || mEditorLoading) {
                        return@SimpleTextWatcher
                    }

                    // If this change is not caused by undo/redo buttons, treat it as a direct edit.
                    // Direct edits keep Save sticky until next successful save.
                    //
                    // zh-CN:
                    // 若本次变化并非由撤销/重做按钮触发, 则视为直接编辑.
                    // 直接编辑会使保存按钮保持 sticky, 直到下一次保存成功.
                    if (!mUndoRedoButtonInProgress) {
                        mHadDirectEditSinceSave = true
                        saveStickyDirty = true
                    } else {
                        // Undo/redo navigation:
                        // Save should reflect (baseline changed) OR (there has been any direct edit since save).
                        //
                        // zh-CN:
                        // undo/redo 历史导航:
                        // 保存按钮应反映 (相对基线有变化) 或 (自保存以来发生过直接编辑).
                        saveStickyDirty = editor.isTextChanged || mHadDirectEditSinceSave
                    }

                    syncPrimaryMenuState()
                })

                editText.addTextChangedListener(SimpleTextWatcher { _ ->
                    // Live refresh search stats in search mode (debounced).
                    // Behavior:
                    // - Immediately show ".../..." to indicate recounting.
                    // - Debounce the actual background scan.
                    //
                    // zh-CN: 搜索模式下实时刷新统计 (带防抖).
                    // - 立刻把 subtitle 变成 ".../..." 表示重新统计中.
                    // - 后台扫描做 debounce, 避免频繁扫全文.
                    if (!mInSearchMode) return@SimpleTextWatcher
                    if (editText.isLoadingText() || mEditorLoading) return@SimpleTextWatcher

                    mSearchStatsCounting = true
                    mSearchStatsCurrentOrdinal = null
                    mSearchStatsTotal = null
                    mSearchStatsTotalCapped = false
                    updateSearchSubtitleBestEffort()

                    scheduleSearchStatsRefreshDebounced()
                })

                editText.textSize = getEditorTextSize(pxToSp(editText.textSize).toInt()).toFloat()
            }

            editor.addCursorChangeCallback(object : CodeEditor.CursorChangeCallback {
                override fun onCursorChange(line: String, cursor: Int) {
                    autoComplete(line, cursor)
                }
            })
            editor.layoutDirection = LAYOUT_DIRECTION_LTR

            // Initialize to "new file" expected state: only Run enabled.
            // zh-CN: 初始化为 "新建文件" 预期状态: 仅运行可用.
            mEditorLoading = false
            mLargeFileMode = false
            saveStickyDirty = false
            mHadDirectEditSinceSave = false
            setMenuItemStatus(R.id.run, true)
            setMenuItemStatus(R.id.undo, false)
            setMenuItemStatus(R.id.redo, false)
            setMenuItemStatus(R.id.save, false)
        }
    }

    private fun autoComplete(line: String, cursor: Int) {
        mAutoCompletion!!.onCursorChange(line, cursor)
    }

    private fun setTheme(theme: Theme?) {
        theme?.let {
            mEditorTheme = it

            val appThemeColor = ThemeColorManager.colorPrimary
            val imeBarBackgroundColor = it.imeBarBackgroundColor

            editor.setTheme(it)

            mInputMethodEnhanceBar.setBackgroundColor(imeBarBackgroundColor)
            mLoadingBarContainer.setBackgroundColor(imeBarBackgroundColor)

            run {
                val adjustedImageContrastColor = ColorUtils.adjustColorForContrast(imeBarBackgroundColor, appThemeColor, 3.6)
                mLoadingBarText.setTextColor(adjustedImageContrastColor)
            }

            val textColor = it.imeBarForegroundColor
            mCodeCompletionBar.setTextColor(textColor)
            mSymbolBar.setTextColor(textColor)
            mShowFunctionsButton.setColorFilter(textColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ViewUtils.setNavigationBarBackgroundColor(activity, imeBarBackgroundColor)
            } else {
                val adjustedImageContrastColor = ColorUtils.adjustColorForContrast(Color.WHITE, ColorUtils.applyAlpha(imeBarBackgroundColor, 1.0), 2.3)
                ViewUtils.setNavigationBarBackgroundColor(activity, adjustedImageContrastColor)
            }
            invalidate()
        }
    }

    fun onBackPressed(): Boolean {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            if (mDocsWebView.webView.canGoBack()) {
                mDocsWebView.webView.goBack()
            } else {
                mDrawerLayout.closeDrawer(GravityCompat.START)
            }
            return true
        }
        return false
    }

    override fun onToolbarMenuItemClick(id: Int) {
        when (id) {
            R.id.run -> runAndSaveFileIfNeeded()
            R.id.save -> saveFile()
            R.id.undo -> undo()
            R.id.redo -> redo()

            R.id.find_next -> {
                // IMPORTANT: base "next" on current caret, not previous foundIndex.
                // zh-CN: 重要: "下一个" 必须以当前光标为起点, 而不是以上次 foundIndex 为起点.
                editor.resetFoundIndexForFindNextFromCursor()
                findNext()
                onSearchStateMaybeChanged()
            }

            R.id.find_prev -> {
                // IMPORTANT: base "prev" on current caret, not previous foundIndex.
                // zh-CN: 重要: "上一个" 必须以当前光标为起点, 而不是以上次 foundIndex 为起点.
                editor.resetFoundIndexForFindPrevFromCursor()
                findPrev()
                onSearchStateMaybeChanged()
            }

            R.id.cancel_search -> {
                cancelSearch()
            }

            R.id.replace -> {
                replace()
                onSearchStateMaybeChanged()
            }
        }
    }

    private fun onSearchStateMaybeChanged() {
        // After next/prev/replace, selection likely changed -> current ordinal changes.
        // zh-CN: next/prev/replace 后 selection 可能变化 -> current 序号需要刷新.
        if (!mInSearchMode) return
        updateSearchSubtitleBestEffort()
        scheduleSearchStatsRefreshDebounced()
    }

    private fun scheduleSearchStatsRefreshDebounced() {
        mUiHandler.removeCallbacks(mSearchStatsDebounceRunnable)
        mUiHandler.postDelayed(mSearchStatsDebounceRunnable, mSearchStatsDebounceMs)
    }

    @SuppressLint("CheckResult")
    fun runAndSaveFileIfNeeded() {
        saveToTmpFile()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ file: File -> runTmpFile(file) }, Observers.toastMessage())
    }

    @JvmOverloads
    fun run(showMessage: Boolean, file: File? = uri?.path?.let { File(it) }, overriddenFullPath: String? = null): ScriptExecution? {
        file ?: return null
        if (showMessage) {
            showSnack(this, R.string.text_start_running)
        }
        // TODO by Stardust on Oct 24, 2018.
        val execution = runWithBroadcastSender(
            file,
            workingDirectory = uri?.path?.let { File(it).parent },
            overriddenFullPath,
        ) ?: return null
        scriptExecutionId = execution.id
        setMenuItemStatus(R.id.run, false)
        return execution
    }

    private fun runTmpFile(file: File? = uri?.path?.let { File(it) }): ScriptExecution? {
        return run(true, file, uri?.path)
    }

    private fun undo() {
        // Mark undo/redo button action so TextWatcher can distinguish it.
        // zh-CN: 标记撤销/重做按钮动作, 以便 TextWatcher 区分来源.
        mUndoRedoButtonInProgress = true
        try {
            editor.undo()
        } finally {
            mUndoRedoButtonInProgress = false
        }

        // Recompute Save state after history navigation.
        // zh-CN: 历史导航后重新计算保存按钮状态.
        saveStickyDirty = editor.isTextChanged || mHadDirectEditSinceSave
        syncPrimaryMenuState()
    }

    private fun redo() {
        // Mark undo/redo button action so TextWatcher can distinguish it.
        // zh-CN: 标记撤销/重做按钮动作, 以便 TextWatcher 区分来源.
        mUndoRedoButtonInProgress = true
        try {
            editor.redo()
        } finally {
            mUndoRedoButtonInProgress = false
        }

        // Recompute Save state after history navigation.
        // This makes "edit -> save -> undo -> redo" turn Save off again,
        // because we are back to baseline and there was no direct edit since save.
        //
        // zh-CN:
        // 历史导航后重新计算保存按钮状态.
        // 这会使 "编辑 -> 保存 -> 撤销 -> 重做" 再次熄灭保存按钮,
        // 因为已回到基线, 且保存后没有发生直接编辑.
        saveStickyDirty = editor.isTextChanged || mHadDirectEditSinceSave
        syncPrimaryMenuState()
    }

    fun save(): Observable<String> =
        Observable
            .fromCallable {
                when (val uri = uri) {
                    null -> {
                        throw IllegalStateException(context.getString(R.string.error_unable_to_save_file_as_current_file_path_is_unknown))
                    }
                    else -> {
                        // Use a transactional save flow for both file:// and content://.
                        // zh-CN: 对 file:// 与 content:// 统一使用事务式保存流程.
                        editor.text.apply {
                            writeTextWithCharsetTransactional(uri, this)
                        }
                    }
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                editor.markTextAsSaved()

                // Reset sticky dirty only on successful save.
                // zh-CN: 仅在保存成功后重置 sticky 脏标记.
                saveStickyDirty = false
                mHadDirectEditSinceSave = false

                setMenuItemStatus(R.id.save, false)

                val keyPath = uri?.path
                val draftFileHelper = StableDraftFileHelper(context, keyPath)
                draftFileHelper.deleteDraft()
            }

    /**
     * Transactional save for text files.
     *
     * Behavior:
     * 1) SAVE_PRE: read old bytes (best-effort).
     * 2) Build new bytes with charset/BOM strategy.
     * 3) Write with openOutputStream(uri, "rwt").
     * 4) Verify by reading back and comparing hash.
     * 5) On failure:
     *    - Try rollback to SAVE_PRE when possible.
     *    - Always save an emergency draft, then prompt user to "Save as".
     *
     * zh-CN:
     *
     * 面向文本文件的事务式保存.
     *
     * 行为:
     * 1) SAVE_PRE: 尽力读取旧 bytes.
     * 2) 按 charset/BOM 策略生成新 bytes.
     * 3) 通过 openOutputStream(uri, "rwt") 写入.
     * 4) 写后读回并比对 hash 校验.
     * 5) 失败时:
     *    - 在可能时回滚到 SAVE_PRE.
     *    - 无论能否回滚, 均保存草稿, 并提示用户 "另存为".
     */
    private fun writeTextWithCharsetTransactional(uri: Uri, text: String) {
        val resolver = context.contentResolver

        val (targetCharset, needBom) = when {
            mHadBom -> {
                mCurrentCharset to true
            }
            mCurrentCharsetConfidence >= MIN_CONFIDENCE_TO_WRITE_FILE -> {
                mCurrentCharset to false
            }
            else -> {
                DEFAULT_CHARSET_TO_WRITE_FILE to false
            }
        }

        val newBytes = buildBytesToWrite(text, targetCharset, needBom)

        // SAVE_PRE snapshot (best-effort).
        // zh-CN: SAVE_PRE 快照 (尽力而为).
        val oldBytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()

        // Track history only when within size guardrails and inside internal storage.
        // zh-CN: 仅在满足大小护栏且位于内部存储时纳管历史.
        val logicalPath = HistoryUriUtils.toLogicalPathOrNull(uri)
        if (logicalPath != null && shouldTrackHistory(oldBytes, newBytes) && oldBytes != null) {
            runCatching {
                HistoryRepository(context.applicationContext).recordSavePre(
                    logicalPath = logicalPath,
                    oldBytes = oldBytes,
                    encodingName = targetCharset.name(),
                    hadBom = needBom,
                )
            }
        }

        val newHash = sha256(newBytes)

        try {
            resolver.openOutputStream(uri, "rwt")?.use { out ->
                out.write(newBytes)
                out.flush()
            } ?: throw IOException("Cannot open output stream for $uri")

            val readBack = runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            }.getOrElse { e ->
                throw IOException("Read-back failed after write: $uri", e)
            }

            val readBackHash = sha256(readBack)
            if (!readBackHash.contentEquals(newHash)) {
                throw IOException("Write verification failed (hash mismatch) for uri: $uri")
            }
        } catch (e: SecurityException) {
            // Permission denied by provider or missing URI grants.
            // zh-CN: provider 拒绝访问或缺少 URI 授权导致权限被拒绝.
            handleSaveFailure(uri, oldBytes, newBytes, IOException("Permission denied for uri: $uri", e))
        } catch (e: Throwable) {
            handleSaveFailure(uri, oldBytes, newBytes, e)
        }
    }

    /**
     * Handle transactional save failure.
     *
     * Steps:
     * - Try rollback (best-effort).
     * - Save emergency draft (always).
     * - Prompt user actions on main thread.
     *
     * zh-CN:
     *
     * 处理事务保存失败.
     *
     * 步骤:
     * - 尝试回滚 (尽力而为).
     * - 保存草稿 (始终执行).
     * - 在主线程弹窗提示用户后续操作.
     */
    private fun handleSaveFailure(uri: Uri, oldBytes: ByteArray?, newBytes: ByteArray, error: Throwable): Nothing {
        val resolver = context.contentResolver

        // Try rollback to SAVE_PRE if possible.
        // zh-CN: 若可能则回滚到 SAVE_PRE.
        if (oldBytes != null) {
            runCatching {
                resolver.openOutputStream(uri, "rwt")?.use { out ->
                    out.write(oldBytes)
                    out.flush()
                }
            }
        }

        val draftFile = runCatching {
            EmergencyDraftStore(context).saveDraft(
                displayName = name.ifBlank { "untitled" },
                bytes = newBytes,
            )
        }.getOrNull()

        // Prompt user on main thread.
        // zh-CN: 在主线程提示用户.
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.error_save_failed)
                .content(
                    buildString {
                        append(error.message ?: error.toString())
                        if (draftFile != null) {
                            append("\n\n")
                            append(context.getString(R.string.text_draft_file_path))
                            append(":\n")
                            append(draftFile.absolutePath)
                        }
                    }
                )
                .also { builder ->
                    if (draftFile != null) {
                        builder.neutralText(R.string.dialog_button_copy_path)
                        builder.neutralColorRes(R.color.dialog_button_hint)
                        builder.onNeutral { d, _ ->
                            ClipboardUtils.setClip(context, draftFile.absolutePath)
                            showSnack(d.view, R.string.text_already_copied_to_clip, false)
                        }
                    }
                }
                .negativeText(R.string.dialog_button_dismiss)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .positiveText(R.string.dialog_button_save_as)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { d, _ ->
                    // Guide user to save as a normal file path (file://).
                    // zh-CN: 引导用户另存为普通文件路径 (file://).
                    runCatching {
                        FileChooserDialogBuilder(context)
                            .title(R.string.text_save_to)
                            .dir(INTERNAL_STORAGE_ROOT)
                            .chooseDir()
                            .singleChoice()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ dir ->
                                // Determine extension from current uri path if possible.
                                // zh-CN: 尽可能从当前 uri.path 推导扩展名.
                                val ext = uri.path?.let { targetExtFromPath(it) } ?: ""

                                val dest = File(
                                    dir.path,
                                    buildSafeFileNameForSaveAs(
                                        baseName = name,
                                        ext = ext,
                                    )
                                )

                                Schedulers.io().scheduleDirect {
                                    runCatching {
                                        dest.parentFile?.mkdirs()
                                        File(dest.path).outputStream().use { it.write(newBytes) }

                                        // If "Save as" succeeded, update current uri to the new file.
                                        // zh-CN: 若另存为成功, 则将当前 uri 更新为新文件.
                                        this@EditorView.uri = Uri.fromFile(dest)
                                        this@EditorView.name = getNameWithoutExtension(dest.path)

                                        // Mark as saved on UI thread.
                                        // zh-CN: 在 UI 线程标记为已保存.
                                        post {
                                            d.dismiss()
                                            editor.markTextAsSaved()
                                            setMenuItemStatus(R.id.save, false)
                                        }
                                    }.onFailure {
                                        post {
                                            showSnack(d.view, R.string.error_save_failed, false)
                                            showToast(context, it.message, true)
                                        }
                                    }
                                }
                            }, { e ->
                                e.printStackTrace()
                            })
                    }
                }
                .autoDismiss(false)
                .cancelable(false)
                .build()
        }

        if (error is IOException) {
            throw error
        }
        throw IOException(error)
    }

    private fun buildBytesToWrite(text: String, charset: Charset, needBom: Boolean): ByteArray {
        val body = text.toByteArray(charset)
        if (!needBom) return body

        val bom = StringUtils.bomBytes(charset)
        return ByteArray(bom.size + body.size).also {
            System.arraycopy(bom, 0, it, 0, bom.size)
            System.arraycopy(body, 0, it, bom.size, body.size)
        }
    }

    private fun shouldTrackHistory(oldBytes: ByteArray?, newBytes: ByteArray): Boolean {
        val oldSize = oldBytes?.size ?: 0
        val newSize = newBytes.size
        val limit = HistoryPrefs.maxFileSizeToTrackBytes().coerceAtLeast(0L)

        return oldSize.toLong() <= limit && newSize.toLong() <= limit
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    private fun targetExtFromPath(path: String): String {
        val name = File(path).name
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot >= name.length - 1) return ""
        return name.substring(dot + 1)
    }

    private fun buildSafeFileNameForSaveAs(baseName: String, ext: String): String {
        val raw = baseName.ifBlank { "untitled" }
        val sanitized = raw.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val niceExt = ext.trim().trimStart('.')
        return if (niceExt.isBlank()) sanitized else "$sanitized.$niceExt"
    }

    fun showVersionHistoryDialog() {
        when (val uri = uri) {
            null -> {
                DialogUtils.buildAndShowAdaptive {
                    MaterialDialog.Builder(context)
                        .title(R.string.text_prompt)
                        .content(R.string.error_unable_to_display_version_history_as_current_file_path_is_unknown)
                        .positiveText(R.string.dialog_button_dismiss)
                        .positiveColorRes(R.color.dialog_button_default)
                        .build()
                }
            }
            else -> {
                // Delegate to controller to unify editor/explorer/history page behavior.
                // zh-CN: 委托给 Controller, 统一 editor/explorer/history page 的行为.
                VersionHistoryController(context).showForEditor(
                    uri = uri,
                    onRestoreToEditor = { restoredText ->
                        editor.text = restoredText

                        // Restoring changes content => mark sticky dirty.
                        // zh-CN: 恢复版本会改变内容, 因此置 sticky 脏标记.
                        saveStickyDirty = true
                    },
                    onRestoredUi = {
                        syncPrimaryMenuState()
                        showSnack(this@EditorView, R.string.text_done)
                    },
                )
            }
        }
    }

    // A minimal emergency draft store.
    // zh-CN: 一个最小实现的草稿存储器.
    private class EmergencyDraftStore(private val context: Context) {

        private val draftsDir: File by lazy { File(context.filesDir, "drafts") }

        fun saveDraft(displayName: String, bytes: ByteArray): File {
            draftsDir.mkdirs()

            val safeName = displayName
                .ifBlank { "untitled" }
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .lowercase(Locale.getDefault())

            val now = System.currentTimeMillis()
            val f = File(draftsDir, "${now}_${safeName}.bin")

            f.outputStream().use { it.write(bytes) }

            // Cleanup drafts after save (best-effort).
            // zh-CN: 保存后顺便清理草稿 (尽力而为).
            runCatching { cleanupLocked() }

            return f
        }

        private fun cleanupLocked() {
            val files = draftsDir.listFiles()?.toList() ?: return
            if (files.isEmpty()) return

            val now = System.currentTimeMillis()

            // 1) Remove expired.
            // zh-CN: 1) 删除过期草稿.
            val maxDraftLifetime = HistoryPrefs.draftsMaxDays().coerceAtLeast(0).toLong() * 24L * 60L * 60L * 1000L
            val expiredBefore = now - maxDraftLifetime
            files.forEach { f ->
                if (f.lastModified() < expiredBefore) {
                    // noinspection ResultOfMethodCallIgnored
                    f.delete()
                }
            }

            // 2) Enforce total bytes limit (keep newest first).
            // zh-CN: 2) 约束总容量上限 (优先保留最新).
            val remained = draftsDir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: return
            var total = remained.sumOf { it.length().coerceAtLeast(0L) }

            val draftLimit = HistoryPrefs.draftsMaxTotalBytes().coerceAtLeast(0L)

            if (total <= draftLimit) return

            for (f in remained.asReversed()) {
                if (total <= draftLimit) break
                val len = f.length().coerceAtLeast(0L)
                if (f.delete()) {
                    total -= len
                }
            }
        }
    }

    fun forceStop() {
        doWithCurrentEngine { obj: ScriptEngine<*> -> obj.forceStop() }
    }

    private fun doWithCurrentEngine(callback: Callback<ScriptEngine<*>>) {
        scriptExecution?.engine?.let { callback.call(it) }
    }

    @SuppressLint("CheckResult")
    fun saveFile() {
        save()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(Observers.emptyConsumer()) { e: Throwable ->
                e.printStackTrace()
                showSnack(this, e.message, true)
                showToast(context, e.message, true)
            }
    }

    private fun findNext() = editor.findNext()

    private fun findPrev() = editor.findPrev()

    private fun showNormalToolbar() {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.toolbar_menu, mNormalToolbar)
            .commitAllowingStateLoss()

        // Sync menu state after toolbar switch to avoid stale enabled flags.
        // zh-CN: 切换工具栏后同步一次菜单状态, 避免 enabled 标记过期.
        post { syncPrimaryMenuState() }
    }

    fun replace() {
        editor.replaceSelection()
    }

    fun showConsole() {
        doWithCurrentEngine { engine: ScriptEngine<*> -> (engine as JavaScriptEngine).runtime.console.show() }
    }

    fun openByOtherApps() {
        uri?.let { openByOtherApps(it) } ?: run {
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.error_unable_to_open_with_other_apps_as_current_file_path_is_unknown)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .build()
            }
        }
    }

    fun beautifyCode() {
        editor.beautifyCode()
    }

    @SuppressLint("CheckResult")
    fun selectEditorTheme() {
        editor.setProgress(true)
        Themes.getAllThemes(context)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { themes: List<Theme> ->
                editor.setProgress(false)
                selectEditorTheme(themes)
            }
    }

    fun selectTextSize() {
        DialogUtils.buildAndShowAdaptive {
            TextSizeSettingDialogBuilder(context)
                .initialValue(pxToSp(editor.codeEditText.textSize).toInt())
                .callback { value: Int -> setTextSize(value) }
                .build()
        }
    }

    fun setTextSize(value: Int) {
        setEditorTextSize(value)
        editor.codeEditText.textSize = value.toFloat()
        editor.lastTextSize = value
    }

    private fun selectEditorTheme(themes: List<Theme>) {
        val i = themes.indexOf(mEditorTheme).coerceAtLeast(0)
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_editor_theme)
                .items(themes)
                .choiceWidgetThemeColor()
                .itemsCallbackSingleChoice(i) { _, _, which, _ ->
                    themes[which].let {
                        setTheme(it)
                        Themes.setCurrent(it.name)
                    }
                    true
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .build()
        }
    }

    private fun getHostToolbarOrNull(): androidx.appcompat.widget.Toolbar? {
        // The activity toolbar id is "toolbar".
        // zh-CN: Activity Toolbar 的 id 为 toolbar.
        return activity.findViewById(R.id.toolbar)
    }

    private fun enterSearchMode(query: String, usingRegex: Boolean, showReplaceItem: Boolean) {
        if (!mInSearchMode) {
            val tb = getHostToolbarOrNull()
            mToolbarTitleBeforeSearch = tb?.title
            mToolbarSubtitleBeforeSearch = tb?.subtitle
        }

        mInSearchMode = true
        mCurrentSearchQuery = query
        mCurrentSearchUsingRegex = usingRegex
        mCurrentSearchShowReplaceItem = showReplaceItem

        // Persist last successful search so reopening dialog restores it even after exit.
        // zh-CN: 记录最近一次成功搜索, 使退出后再次打开对话框仍可恢复.
        mLastSearchQuery = query
        mLastSearchUsingRegex = usingRegex

        // Reset stats UI to "counting".
        // zh-CN: 重置统计 UI 为 "统计中".
        mSearchStatsCurrentOrdinal = null
        mSearchStatsTotal = null
        mSearchStatsTotalCapped = false
        mSearchStatsCounting = true

        val tb = getHostToolbarOrNull()
        tb?.title = query
        tb?.subtitle = null

        // Start stats immediately.
        // zh-CN: 立即启动统计.
        restartSearchStatsAsync(reason = "enter")
        requestApplyInsetsSoon()
    }

    private fun exitSearchModeAndRestoreToolbar() {
        if (!mInSearchMode) return

        mInSearchMode = false
        mCurrentSearchQuery = null
        mCurrentSearchUsingRegex = false
        mCurrentSearchShowReplaceItem = false

        cancelSearchStats()

        val tb = getHostToolbarOrNull()
        tb?.title = mToolbarTitleBeforeSearch
        tb?.subtitle = mToolbarSubtitleBeforeSearch

        requestApplyInsetsSoon()
    }

    private fun cancelSearchStats() {
        mSearchStatsGeneration++
        mUiHandler.removeCallbacks(mSearchStatsDebounceRunnable)
        mSearchStatsDisposable?.let { d -> runCatching { d.dispose() } }
        mSearchStatsDisposable = null
        mSearchStatsCounting = false
    }

    private fun buildSearchSubtitleText(): CharSequence? {
        if (!mInSearchMode) return null

        val current = mSearchStatsCurrentOrdinal
        val total = mSearchStatsTotal
        val counting = mSearchStatsCounting

        fun formatTotal(): String {
            if (total == null) return "..."
            return if (mSearchStatsTotalCapped) "${mSearchStatsMaxTotal - 1}+" else total.toString()
        }

        val totalText = when {
            total != null -> formatTotal()
            counting -> "..."
            else -> null
        }

        val currentText = when {
            current != null -> current.toString()
            counting -> "..."
            total != null -> "..." // <-- key: total known but current unknown => show ".../total"
            else -> null
        }

        return when {
            currentText != null && totalText != null -> "$currentText/$totalText"
            else -> null
        }
    }

    private fun updateSearchSubtitleBestEffort() {
        val tb = getHostToolbarOrNull() ?: return
        if (!mInSearchMode) return
        tb.subtitle = buildSearchSubtitleText()
    }

    private fun showNoSearchResultDialog(query: String) {
        // Show a clear no-result prompt.
        // zh-CN: 显示明确的无结果提示.
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(context.getString(R.string.text_no_search_results_for_keyword, query))
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .build()
        }
    }

    private fun restartSearchStatsAsync(reason: String) {
        if (!mInSearchMode) return

        val query = mCurrentSearchQuery
        if (query.isNullOrEmpty()) return

        // Cancel previous task.
        // zh-CN: 取消上一轮任务.
        cancelSearchStats()

        mSearchStatsCounting = true
        mSearchStatsCurrentOrdinal = null
        mSearchStatsTotal = null
        mSearchStatsTotalCapped = false

        updateSearchSubtitleBestEffort()

        val gen = ++mSearchStatsGeneration

        val usingRegex = mCurrentSearchUsingRegex
        val selStart = editor.codeEditText.selectionStart
        val selEnd = editor.codeEditText.selectionEnd
        val textSnapshot = editor.text

        mSearchStatsDisposable = Observable
            .fromCallable {
                computeSearchStats(
                    text = textSnapshot,
                    query = query,
                    usingRegex = usingRegex,
                    selectionStart = minOf(selStart, selEnd),
                    selectionEnd = maxOf(selStart, selEnd),
                    maxTotal = mSearchStatsMaxTotal,
                    generation = gen,
                )
            }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                // Ignore stale results.
                // zh-CN: 忽略过期结果.
                if (gen != mSearchStatsGeneration) return@subscribe
                if (!mInSearchMode) return@subscribe

                mSearchStatsCounting = false
                mSearchStatsCurrentOrdinal = result.currentOrdinal
                mSearchStatsTotal = result.total
                mSearchStatsTotalCapped = result.capped

                updateSearchSubtitleBestEffort()
            }, { e ->
                if (gen != mSearchStatsGeneration) return@subscribe
                mSearchStatsCounting = false
                e.printStackTrace()
                updateSearchSubtitleBestEffort()
            })
    }

    private data class SearchStatsResult(
        val currentOrdinal: Int?,
        val total: Int,
        val capped: Boolean,
    )

    private fun computeSearchStats(
        text: String,
        query: String,
        usingRegex: Boolean,
        selectionStart: Int,
        selectionEnd: Int,
        maxTotal: Int,
        generation: Int,
    ): SearchStatsResult {
        // Note:
        // - Non-regex: count non-overlapping occurrences.
        // - Regex: count matcher.find() occurrences.
        // - currentOrdinal: match whose range equals current selection range.
        // zh-CN:
        // - 非正则: 统计不重叠匹配次数.
        // - 正则: 统计 matcher.find() 次数.
        // - currentOrdinal: 匹配区间与当前 selection 区间相等时记录序号.
        if (query.isEmpty()) return SearchStatsResult(null, 0, false)

        fun cancelled(): Boolean = generation != mSearchStatsGeneration

        var total = 0
        var capped = false
        var currentOrdinal: Int? = null

        if (!usingRegex) {
            var from = 0
            while (from <= text.length) {
                if (cancelled()) break

                val idx = text.indexOf(query, from)
                if (idx < 0) break

                total++
                val end = idx + query.length
                if (idx == selectionStart && end == selectionEnd) {
                    currentOrdinal = total
                }

                if (total >= maxTotal) {
                    capped = true
                    break
                }

                // Non-overlapping.
                // zh-CN: 不重叠推进.
                from = end
            }
        } else {
            val p = Pattern.compile(query)
            val m = p.matcher(text)
            while (m.find()) {
                if (cancelled()) break

                total++
                val start = m.start()
                val end = m.end()
                if (start == selectionStart && end == selectionEnd) {
                    currentOrdinal = total
                }

                if (total >= maxTotal) {
                    capped = true
                    break
                }
            }
        }

        return SearchStatsResult(
            currentOrdinal = currentOrdinal,
            total = total,
            capped = capped,
        )
    }

    // Try to start "Find" and enter search mode. Returns true if any result is found.
    // zh-CN: 尝试开始 "查找" 并进入搜索模式. 若找到任意结果则返回 true.
    fun tryFind(keywords: String, usingRegex: Boolean): Boolean {
        editor.find(keywords, usingRegex)

        val hasResult = editor.getFoundIndex() >= 0
        if (!hasResult) {
            showNoSearchResultDialog(keywords)
            return false
        }

        enterSearchMode(query = keywords, usingRegex = usingRegex, showReplaceItem = false)
        showSearchToolbar(showReplaceItem = false)
        updateSearchSubtitleBestEffort()

        // Fix possible IME/insets timing issues after dialog confirm and IME hide.
        // zh-CN: 修复对话框确认导致 IME 收起后的 insets 时序问题.
        requestApplyInsetsSoon()
        return true
    }

    // Try to start "Replace (single)" and enter search mode. Returns true if any result is found.
    // zh-CN: 尝试开始 "替换 (单次)" 并进入搜索模式. 若找到任意结果则返回 true.
    fun tryReplace(keywords: String, replacement: String, usingRegex: Boolean): Boolean {
        editor.replace(keywords, replacement, usingRegex)

        val hasResult = editor.getFoundIndex() >= 0
        if (!hasResult) {
            showNoSearchResultDialog(keywords)
            return false
        }

        enterSearchMode(query = keywords, usingRegex = usingRegex, showReplaceItem = true)
        showSearchToolbar(showReplaceItem = true)
        updateSearchSubtitleBestEffort()

        // Fix possible IME/insets timing issues after dialog confirm and IME hide.
        // zh-CN: 修复对话框确认导致 IME 收起后的 insets 时序问题.
        requestApplyInsetsSoon()
        return true
    }

    private fun cancelSearch() {
        exitSearchModeAndRestoreToolbar()
        showNormalToolbar()
    }

    @Throws(CheckedPatternSyntaxException::class)
    fun find(keywords: String, usingRegex: Boolean) {
        // Keep old API for callers that don't care about result.
        // zh-CN: 保留旧 API, 兼容不关心结果的调用方.
        tryFind(keywords, usingRegex)
    }

    @Throws(CheckedPatternSyntaxException::class)
    fun replace(keywords: String, replacement: String, usingRegex: Boolean) {
        // Keep old API for callers that don't care about result.
        // zh-CN: 保留旧 API, 兼容不关心结果的调用方.
        tryReplace(keywords, replacement, usingRegex)
    }

    private fun showSearchToolbar(showReplaceItem: Boolean) {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.toolbar_menu, SearchToolbarFragment().apply {
                setOnMenuItemClickListener(this@EditorView)
                arguments ?: let { arguments = Bundle() }
                arguments!!.putBoolean(SearchToolbarFragment.ARGUMENT_SHOW_REPLACE_ITEM, showReplaceItem)
            })
            .commit()

        // Sync menu state after toolbar switch to avoid stale enabled flags.
        // zh-CN: 切换工具栏后同步一次菜单状态, 避免 enabled 标记过期.
        post { syncPrimaryMenuState() }
    }

    @Throws(CheckedPatternSyntaxException::class)
    fun replaceAll(keywords: String, replacement: String, usingRegex: Boolean) {
        editor.replaceAll(keywords, replacement, usingRegex)
    }

    fun debug() {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.toolbar_menu, DebugToolbarFragment())
            .commit()
        debugBar.visibility = VISIBLE
        if (!mReadOnly) {
            mInputMethodEnhanceBar.visibility = GONE
        }
        mDebugging = true

        // Sync menu state after toolbar switch to avoid stale enabled flags.
        // zh-CN: 切换工具栏后同步一次菜单状态, 避免 enabled 标记过期.
        post { syncPrimaryMenuState() }
    }

    fun exitDebugging() {
        val fragmentManager = activity.supportFragmentManager
        val fragment = fragmentManager.findFragmentById(R.id.toolbar_menu)
        if (fragment is DebugToolbarFragment) {
            fragment.detachDebugger()
        }
        showNormalToolbar()
        editor.setDebuggingLine(-1)
        debugBar.visibility = GONE
        if (!mReadOnly) {
            mInputMethodEnhanceBar.visibility = VISIBLE
        }
        mDebugging = false

        // Sync menu state after toolbar switch to avoid stale enabled flags.
        // zh-CN: 切换工具栏后同步一次菜单状态, 避免 enabled 标记过期.
        post { syncPrimaryMenuState() }
    }

    private fun showErrorMessage(msg: String) {
        Snackbar.make(this@EditorView, /* context.getString(R.string.text_error) + ": " + msg */ msg, Snackbar.LENGTH_LONG)
            .setAction(R.string.text_details) { LogActivity.launch(context) }
            .show()
    }

    override fun onHintClick(completions: CodeCompletions, pos: Int) {
        val completion = completions[pos]

        // @Overruled by SuperMonster003 on Jul 12, 2023.
        //  ! Author: 抠脚本人
        //  ! Related PR: http://pr.autojs6.com/98
        //  ! Reason:
        //  ! In any case, only the simplest input functions should be realized
        //  ! when clicking on the keys of the function keyboard.
        //  ! zh-CN: 在任何情况下, 单击功能键盘的按键时, 均应实现且仅实现最简单的输入功能.
        //  !
        // @TodoDiary by 抠脚本人 on Jul 11, 2023.
        //  ! 增加行注释.
        //  ! en-US (translated by SuperMonster003 on Jul 28, 2024):
        //  ! Add line comment.
        //  !
        //  # if (completion.insertText == "/") {
        //  #     editor!!.commentLine()
        //  # } else editor!!.insert(completion.insertText)

        editor.insert(completion.insertText)
    }

    override fun onHintLongClick(completions: CodeCompletions, pos: Int) {

        // @Overruled by SuperMonster003 on Jul 12, 2023.
        //  ! Author: 抠脚本人
        //  ! Related PR:
        //  ! http://pr.autojs6.com/98
        //  ! Reason:
        //  ! Given the confusion caused by combinations of
        //  ! block comments and certain syntactic of RegEx,
        //  ! multi-line comments are also commented with double slashes.
        //  ! zh-CN: 鉴于块注释与正则表达式的某些句法组合造成混淆, 多行注释也采用双斜杠注释方式.
        //  !
        // @TodoDiary by 抠脚本人 on Jul 10, 2023.
        //  ! 增加块注释.
        //  ! en-US (translated by SuperMonster003 on Jul 28, 2024):
        //  ! Add block comment.
        //  !
        //  # val completion = completions[pos]
        //  # if (completion.insertText == "/") {
        //  #     editor!!.commentBlock()
        //  #     return
        //  # }

        completions[pos].let {
            when {
                // @Inspired by 抠脚本人 (https://github.com/little-alei) on Jul 13, 2023.
                it.insertText == "/" -> editor.commentHelper.toggle()
                it.insertText == "=" -> editor.clear()
                it.insertText == "-" -> editor.deleteLine()
                it.insertText == "(" -> editor.moveCursor(-1)
                it.insertText == ")" -> editor.moveCursor(1)
                it.insertText == "[" -> editor.jumpToLineStart()
                it.insertText == "]" -> editor.jumpToLineEnd()
                it.insertText == "{" -> editor.jumpToStart()
                it.insertText == "}" -> editor.jumpToEnd()
                it.insertText == "<" -> editor.jumpToPrevLine()
                it.insertText == ">" -> editor.jumpToNextLine()
                it.url != null -> showManual(it.url, it.hint)
            }
        }
    }

    private fun showManual(urlSuffix: String, title: String) {
        val absUrl = getUrl(urlSuffix)
        ManualDialog(context)
            .title(title)
            .url(absUrl)
            .pinToLeft {
                mDocsWebView.webView.loadUrl(absUrl)
                mDrawerLayout.openDrawer(GravityCompat.START)
            }
            .show()
    }

    override fun onModuleLongClick(module: Module) {
        showManual(module.url, module.name)
    }

    override fun onPropertyClick(m: Module, property: Property) {
        var p = property.key
        if (!property.isVariable) {
            p = "$p()"
        }
        if (property.isGlobal) {
            editor.insert(p)
        } else {
            editor.insert(m.name + "." + p)
        }
        if (!property.isVariable) {
            editor.moveCursor(-1)
        }
        mFunctionsKeyboardHelper!!.hideFunctionsLayout(true)
    }

    override fun onPropertyLongClick(m: Module, property: Property) {
        if (TextUtils.isEmpty(property.url)) {
            showManual(m.url, property.key)
        } else {
            showManual(property.url, property.key)
        }
    }

    override fun onSaveInstanceState() = Bundle().apply {
        putParcelable("super_data", super.onSaveInstanceState())
        putInt("script_execution_id", scriptExecutionId)
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val bundle = state as Bundle

        @Suppress("DEPRECATION")
        val superData = bundle.getParcelable<Parcelable>("super_data")
        scriptExecutionId = bundle.getInt("script_execution_id", ScriptExecution.NO_ID)
        super.onRestoreInstanceState(superData)
        setMenuItemStatus(R.id.run, scriptExecutionId == ScriptExecution.NO_ID)
    }

    fun destroy() {
        // Cancel loading before destroying editor resources.
        // zh-CN: 销毁前先取消加载, 再销毁编辑器相关资源.
        cancelLargeFileLoading()

        // Cancel search stats.
        // zh-CN: 取消搜索统计任务.
        cancelSearchStats()

        editor.destroy()
        mAutoCompletion?.shutdown()
        if (mDebugging) {
            exitDebugging()
            forceStop()
        }
    }

    @SuppressLint("CheckResult")
    private fun saveToTmpFile(): Observable<File> {
        return Observable.fromCallable {
            TmpScriptFiles.create(context).also {
                write(it, editor.text)
                mTmpSavedFileForRunning = it
            }
        }.observeOn(Schedulers.io())
    }

    fun cleanBeforeExit() {
        mTmpSavedFileForRunning?.deleteOnExit()
    }

    private class CountingInputStream(private val delegate: InputStream, private val counter: AtomicLong) : InputStream() {
        override fun read(): Int {
            val r = delegate.read()
            if (r >= 0) counter.incrementAndGet()
            return r
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) counter.addAndGet(n.toLong())
            return n
        }

        override fun skip(n: Long): Long {
            val s = delegate.skip(n)
            if (s > 0) counter.addAndGet(s)
            return s
        }

        override fun available(): Int = delegate.available()
        override fun close() = delegate.close()
        override fun mark(readlimit: Int) = delegate.mark(readlimit)
        override fun reset() = delegate.reset()
        override fun markSupported(): Boolean = delegate.markSupported()
    }

    companion object {

        private const val MIN_CONFIDENCE_TO_WRITE_FILE = 90
        private val DEFAULT_CHARSET_TO_WRITE_FILE = StandardCharsets.UTF_8

        private const val LARGE_FILE_MODE_THRESHOLD = JavaScriptHighlighter.MAX_HIGHLIGHT_CHARS

        // Internal storage root (no external SD).
        // zh-CN: 内部存储根目录 (不访问外置 SD).
        private const val INTERNAL_STORAGE_ROOT: String = "/storage/emulated/0"

        const val EXTRA_PATH = "path"
        const val EXTRA_NAME = "name"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_READ_ONLY = "readOnly"
        const val EXTRA_SAVE_ENABLED = "saveEnabled"
        const val EXTRA_RUN_ENABLED = "runEnabled"
    }
}
