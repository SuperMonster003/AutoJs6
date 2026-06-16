package org.autojs.autojs.ui.edit

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.view.Choreographer
import android.view.View
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.event.BackPressedHandler.HostActivity
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.model.autocomplete.AutoCompletion
import org.autojs.autojs.model.autocomplete.CodeCompletion
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
import org.autojs.autojs.tool.Callback
import org.autojs.autojs.ui.doc.ManualDialog
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar.OnHintClickListener
import org.autojs.autojs.ui.edit.debug.DebugBar
import org.autojs.autojs.ui.edit.editor.JavaScriptHighlighter
import org.autojs.autojs.ui.edit.editor.ace.AceEditorAssets
import org.autojs.autojs.ui.edit.editor.ace.AceEditorTestHooks
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnostics
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnosticsSnapshot
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailure
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailureType
import org.autojs.autojs.ui.edit.editor.ace.health.AceHealthState
import org.autojs.autojs.ui.edit.editor.command.EditorCommandDispatcher
import org.autojs.autojs.ui.edit.editor.engine.AceScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineFactory
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorCursorChangeListener
import org.autojs.autojs.ui.edit.editor.engine.EditorSearchOptions
import org.autojs.autojs.ui.edit.editor.engine.EditorSearchSyntaxException
import org.autojs.autojs.ui.edit.editor.engine.EditorSelection
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadMetadata
import org.autojs.autojs.ui.edit.editor.loading.EditorTextLoadPolicy
import org.autojs.autojs.ui.edit.editor.loading.EditorTextLoadTarget
import org.autojs.autojs.ui.edit.editor.loading.EngineTextLoadTarget
import org.autojs.autojs.ui.edit.editor.service.EditorBreakpointModelStore
import org.autojs.autojs.ui.edit.editor.service.EditorFileSaveEncoding
import org.autojs.autojs.ui.edit.editor.service.EditorFileService
import org.autojs.autojs.ui.edit.editor.service.EditorTextProvider
import org.autojs.autojs.ui.edit.editor.service.EditorThemeFontService
import org.autojs.autojs.ui.edit.editor.service.EditorVersionHistoryService
import org.autojs.autojs.ui.edit.editor.service.RunScriptTextProvider
import org.autojs.autojs.ui.edit.editor.viewport.EditorViewportPolicy
import org.autojs.autojs.ui.edit.editor.viewport.EditorViewportPolicyFactory
import org.autojs.autojs.ui.edit.keyboard.AceActionFavoritesDialog
import org.autojs.autojs.ui.edit.keyboard.AceActionHistoryStore
import org.autojs.autojs.ui.edit.keyboard.AceShortcutPanel
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
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.widget.EWebView
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DocsUtils.getUrl
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.R.string.text_unknown
import org.autojs.autojs6.databinding.EditorViewBinding
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
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

    /**
     * Callback interface for showing the log panel from the editor
     */
    interface LogPanelCallback {
        fun onShowLogPanel()
    }

    interface ScriptRunningStateListener {
        fun onScriptRunningStateChanged(isRunning: Boolean)
    }

    private var mLogPanelCallback: LogPanelCallback? = null
    private var mScriptRunningStateListener: ScriptRunningStateListener? = null

    fun setLogPanelCallback(callback: LogPanelCallback) {
        mLogPanelCallback = callback
    }

    fun showLogPanel() {
        mLogPanelCallback?.onShowLogPanel()
    }

    fun setScriptRunningStateListener(listener: ScriptRunningStateListener?) {
        mScriptRunningStateListener = listener
        listener?.onScriptRunningStateChanged(isScriptRunning())
    }

    private var binding: EditorViewBinding = EditorViewBinding.bind(inflate(context, R.layout.editor_view, this))

    @JvmField
    var editor: ScriptEditorEngine = EditorEngineFactory.create(context, EditorEnginePreferences.get())

    private var mViewportPolicy: EditorViewportPolicy = EditorViewportPolicyFactory.create(editor, this)
    val breakpointModelStore = EditorBreakpointModelStore { editor }
    private val mCommandDispatcher = EditorCommandDispatcher(
        currentEditor = { editor },
        breakpointModelStore = breakpointModelStore,
        showManual = { urlSuffix, title -> showManual(urlSuffix, title) },
        hideFunctionsKeyboard = { showSoftInput -> mFunctionsKeyboardHelper?.hideFunctionsLayout(showSoftInput) },
    )
    val commandDispatcher: EditorCommandDispatcher
        get() = mCommandDispatcher

    private val textLoadTarget: EditorTextLoadTarget
        get() = EngineTextLoadTarget(editor)
    private val editorTextProvider = EditorTextProvider(this) { editor }
    private val runScriptTextProvider = RunScriptTextProvider(
        context = context,
        editorTextProvider = editorTextProvider,
        onTmpFileCreated = { mTmpSavedFileForRunning = it },
    )

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

    private val versionHistoryService = EditorVersionHistoryService(
        context = context,
        currentEditor = { editor },
        onRestoredToEditor = {
            saveStickyDirty = true
        },
        onRestoredUi = {
            syncPrimaryMenuState()
            showSnack(this@EditorView, R.string.text_done)
        },
    )

    // Whether we have had any direct edits since last save/baseline.
    // Direct edits mean changes NOT caused by undo/redo buttons.
    //
    // zh-CN:
    // 自上次保存/建立基线以来是否发生过任何直接编辑.
    // 直接编辑指不是由撤销/重做按钮触发的文本变化.
    @Volatile
    private var mHadDirectEditSinceSave: Boolean = false

    private val fileService = EditorFileService(
        context = context,
        hostView = this,
        versionHistoryService = versionHistoryService,
        onSaveAsSucceeded = { dest ->
            uri = Uri.fromFile(dest)
            name = getNameWithoutExtension(dest.path)
            editor.markTextAsSaved()
            saveStickyDirty = false
            mHadDirectEditSinceSave = false
            setMenuItemStatus(R.id.save, false)
        },
    )

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

    var scriptExecutionId = ScriptExecution.NO_ID
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

    fun isScriptRunning(): Boolean = scriptExecution != null

    // Whether the editor is currently loading text (including streaming append).
    // zh-CN: 编辑器是否正在加载文本 (包括流式追加阶段).
    @Volatile
    private var mEditorLoading: Boolean = false

    // Large file mode flag: keep undo/redo disabled to avoid memory/perf issues.
    // zh-CN: 大文件模式标记: 为避免内存/性能问题, 保持撤销/重做不可用.
    @Volatile
    private var mLargeFileMode: Boolean = false

    @Volatile
    private var mSwitchingEditorEngine: Boolean = false

    private val mAceFallbackInProgress = AtomicBoolean(false)

    @Volatile
    private var mLastAceDiagnosticsSnapshot: AceDiagnosticsSnapshot? = null

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
    private val mNativeInputMethodEnhanceContent: View = binding.nativeInputMethodEnhanceContent
    private val mAceInputMethodEnhanceContent: View = binding.aceInputMethodEnhanceContent
    private val mSymbolBar: CodeCompletionBar = binding.symbolBar
    private val mShowFunctionsButton: ImageView = binding.functions
    private val mAceShowFunctionsButton: ImageView = binding.aceFunctions
    private val mAceShortcutTopBar: CodeCompletionBar = binding.aceShortcutTopBar
    private val mAceShortcutBottomBar: CodeCompletionBar = binding.aceShortcutBottomBar
    private val mAceSymbolsBar: CodeCompletionBar = binding.aceSymbolsBar
    private val mFunctionsKeyboard: FunctionsKeyboardView = binding.functionsKeyboard
    private val mDocsWebView: EWebView = binding.docs
    private val mDrawerLayout: DrawerLayout = binding.drawerLayout

    private var mCurrentCharsetConfidence: Int = 0
    private var mCurrentCharset: Charset = EditorFileService.DEFAULT_CHARSET_TO_WRITE_FILE
    private var mHadBom = false
    private var mReadOnly = false
    private var mAutoCompletion: AutoCompletion? = null
    private var mFunctionsKeyboardHelper: FunctionsKeyboardHelper? = null
    private val mOnRunFinishedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_ON_EXECUTION_FINISHED == intent.action) {
                scriptExecutionId = ScriptExecution.NO_ID
                if (mDebugging) {
                    exitDebugging()
                }
                syncPrimaryMenuState()
                notifyScriptRunningStateChanged()
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
    private var mReadOnlyLockedByIntent = false
    private var mAceShortcutReturnSelection: EditorSelection? = null

    private val mUiHandler = Handler(Looper.getMainLooper())

    private val mPrimaryMenuStateSyncDebounceMs = 80L
    private val mPrimaryMenuStateSyncRunnable = Runnable {
        syncPrimaryMenuState()
    }

    // Delay showing loading bar to avoid flicker.
    // zh-CN: 延迟显示加载提示条, 避免闪烁.
    private val mLoadingBarShowDelayMs = 300L

    // Once shown, keep it visible for at least this duration.
    // zh-CN: 一旦显示, 则保证最短展示时间.
    private val mLoadingBarMinShowMs = 500L

    private val mLoadingBarContainer: View = binding.loadingBarContainer
    private val mLoadingBarText: TextView = binding.loadingBarText
    private val themeFontService = EditorThemeFontService(
        context = context,
        activity = activity,
        currentEditor = { editor },
        inputMethodEnhanceBar = mInputMethodEnhanceBar,
        loadingBarContainer = mLoadingBarContainer,
        loadingBarText = mLoadingBarText,
        completionBars = listOf(
            mCodeCompletionBar,
            mSymbolBar,
            mAceShortcutTopBar,
            mAceShortcutBottomBar,
            mAceSymbolsBar,
        ),
        showFunctionsButtons = listOf(
            mShowFunctionsButton,
            mAceShowFunctionsButton,
        ),
        invalidateHost = { invalidate() },
    )

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
    private var mLastImeVisible = false
    private var mLastImeEventAtUptimeMillis = 0L

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
        recordAceAssetMissingFallbackIfNeeded()
        attachEditorEngineView()
        setUpEditor()
        setUpInputMethodEnhancedBar()
        setUpFunctionsKeyboard()
        setUpImeInsetsHandling()
        setMenuItemStatus(R.id.save, false)
        mDocsWebView.apply {
            webView.settings.displayZoomControls = true
            webView.loadUrl(getUrl("index.html"))
        }
        themeFontService.loadCurrentTheme()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { theme: Theme? -> setTheme(theme) }
        initNormalToolbar()
    }

    private fun attachEditorEngineView() {
        if (editor.view.parent == null) {
            binding.editorContainer.addView(
                editor.view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun registerFunctionsKeyboardBackHandler() {
        (context as? HostActivity)?.backPressedObserver?.registerHandler(mFunctionsKeyboardHelper)
    }

    private fun unregisterFunctionsKeyboardBackHandler() {
        (context as? HostActivity)?.backPressedObserver?.unregisterHandler(mFunctionsKeyboardHelper)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(mOnRunFinishedReceiver, IntentFilter(ACTION_ON_EXECUTION_FINISHED), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(mOnRunFinishedReceiver, IntentFilter(ACTION_ON_EXECUTION_FINISHED))
        }
        registerFunctionsKeyboardBackHandler()
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
            mReadOnlyLockedByIntent = it
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

    fun getEditorTheme() = themeFontService.currentTheme

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
                editor.view.getLocationOnScreen(loc)
                val editorBottomOnScreen = loc[1] + editor.view.height

                (editorBottomOnScreen - mTmpWindowVisibleRect.bottom).coerceAtLeast(0)
            } else {
                0
            }

            val imeStateChanged = targetBottom != mLastImeBottomInset ||
                imeVisible != mLastImeVisible

            if (imeStateChanged) {
                mLastImeBottomInset = targetBottom
                mLastImeVisible = imeVisible
                mLastImeEventAtUptimeMillis = SystemClock.uptimeMillis()

                mViewportPolicy.onImeInsetsChanged(imeVisible, targetBottom)
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
        editor.ensureCursorVisibleIfPossible()
    }

    private fun ensureEditorHasCursorAfterLoadIfNeeded() {
        // Ensure there is a visible caret after loading, so jump operations can work.
        // zh-CN: 加载完成后确保存在可见光标, 以使跳转操作可用.
        if (editor.textSnapshot.isEmpty()) return

        // Request focus without forcing soft keyboard.
        // zh-CN: 请求焦点但不强制弹出软键盘.
        if (editor.engineType != EditorEngineType.ACE) {
            editor.requestEditorFocus()
        }

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
        val activeContent = if (editor.engineType == EditorEngineType.ACE) {
            mAceInputMethodEnhanceContent
        } else {
            mNativeInputMethodEnhanceContent
        }
        val inactiveContent = if (editor.engineType == EditorEngineType.ACE) {
            mNativeInputMethodEnhanceContent
        } else {
            mAceInputMethodEnhanceContent
        }

        inactiveContent.visibility = GONE
        activeContent.visibility = if (interactive) VISIBLE else INVISIBLE
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
        val sizeOrNull = runCatching { fileService.queryContentLengthOrNull(uri) }.getOrNull()
        val shouldStreamAsLargeFile = EditorTextLoadPolicy.shouldStreamAsLargeFile(sizeOrNull)
        if (EditorTextLoadPolicy.shouldRouteAceToNative(editor.engineType, shouldStreamAsLargeFile, shouldForceLargeFileNativeRoute())) {
            if (!ensureNativeEditorForLargeFile()) {
                return Observable.error(IllegalStateException(context.getString(R.string.error_ace_editor_unavailable)))
            }
        }

        if (shouldStreamAsLargeFile && editor.engineType == EditorEngineType.NATIVE) {
            // Large file: show loading in enhance bar, allow viewing/scrolling (read-only).
            // zh-CN: 大文件: 在增强栏显示加载提示, 允许查看/滚动 (只读).
            beginEditorLoadingUi(context.getString(R.string.text_loading_with_dots))
            return loadUriStreamed(uri)
        }

        return Observable
            .fromCallable {
                fileService.readText(uri)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                beginEditorLoadingUi(context.getString(R.string.text_loading_with_dots))
                mEditorLoading = true
                syncPrimaryMenuState()
            }
            .doOnNext { result ->
                post {
                    runCatching {
                        applyReadMetadata(result.metadata)
                        setInitialText(result.text)

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
            .map { it.text }
            .doOnError {
                endEditorLoadingUi()
                mEditorLoading = false
                syncPrimaryMenuState()
            }
    }

    private fun ensureNativeEditorForLargeFile(): Boolean {
        if (editor.engineType != EditorEngineType.ACE) {
            return true
        }

        val switched = switchEditorEngine(
            targetType = EditorEngineType.NATIVE,
            oldEditor = editor,
            latestText = editor.textSnapshot,
            persistPreference = false,
            showSwitchToast = false,
        )
        if (switched) {
            showAceLargeFileNativeToastOnce()
        }
        return switched
    }

    private fun shouldForceLargeFileNativeRoute(): Boolean {
        return editor.engineType == EditorEngineType.ACE && AceEditorTestHooks.shouldForceLargeFileNativeRoute()
    }

    override fun onDetachedFromWindow() {
        // Cancel loading before unregistering receivers to avoid ANR on exit.
        // zh-CN: 退出前先取消加载, 避免返回退出时 ANR.
        cancelLargeFileLoading()

        super.onDetachedFromWindow()
        context.unregisterReceiver(mOnRunFinishedReceiver)
        unregisterFunctionsKeyboardBackHandler()
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
        // Reset cancel state for a new session.
        // zh-CN: 新的加载会话开始前重置取消标记.
        mLargeFileCancel.set(false)

        return fileService
            .streamTextChunks(
                uri = uri,
                cancel = mLargeFileCancel,
                streamRef = mLargeFileStreamRef,
                onMetadata = ::applyReadMetadata,
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .let { upstream ->
                bridgeStreamToUiWithProgress(
                    upstream = upstream,
                )
            }
    }

    private fun applyReadMetadata(metadata: EditorFileReadMetadata) {
        mCurrentCharsetConfidence = metadata.charsetConfidence
        mCurrentCharset = metadata.charset
        mHadBom = metadata.hadBom
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
        mLargeFileMode = true
        mEditorLoading = true
        mUserMovedCursorDuringLoading = false
        syncPrimaryMenuState()

        val loadTarget = textLoadTarget
        loadTarget.beginStreamedLargeTextLoad()

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
                    loadTarget.cancelStreamedLargeTextLoad()
                    mEditorLoading = false
                    syncPrimaryMenuState()
                    endEditorLoadingUi()
                    mLargeFileFrameCallback = null
                    return
                }

                val finished = done.get() && queue.isEmpty()
                if (finished) {
                    loadTarget.finishStreamedLargeTextLoad(markClean = true)

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
                            runCatching { editor.jumpToStart() }
                        }
                        ensureEditorHasCursorAfterLoadIfNeeded()
                    }

                    mLargeFileFrameCallback = null
                    return
                }

                val now = SystemClock.uptimeMillis()

                mLoadingBarText.text = context.getString(R.string.text_loading_with_dots)

                val userTouching = loadTarget.isUserTouching

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
                    loadTarget.appendTextChunk(sb)
                }

                choreographer.postFrameCallback(this)
            }
        }

        // Keep reference for cancellation.
        // zh-CN: 保存引用以便取消.
        mLargeFileFrameCallback = callback
        choreographer.postFrameCallback(callback)
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
            textLoadTarget.loadRestoredText(mRestoredText!!, undoRedoEnabled = !mLargeFileMode)
            mRestoredText = null

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

        val shouldLoadProgressively = EditorTextLoadPolicy.shouldLoadProgressively(text.length)
        if (EditorTextLoadPolicy.shouldRouteAceToNative(editor.engineType, shouldLoadProgressively, shouldForceLargeFileNativeRoute())) {
            if (!ensureNativeEditorForLargeFile()) {
                mEditorLoading = false
                syncPrimaryMenuState()
                return
            }
        }

        if (shouldLoadProgressively && editor.engineType == EditorEngineType.NATIVE) {
            mLargeFileMode = true
            setInitialTextProgressively(text)
            return
        }

        textLoadTarget.loadSmallText(text)

        // Reset sticky dirty because we just established a new baseline.
        // zh-CN: 因刚刚建立了新的基线, 重置 sticky 脏标记.
        saveStickyDirty = false
        mHadDirectEditSinceSave = false

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
                setMenuItemStatus(R.id.undo, !mReadOnly && editor.canUndo)
                setMenuItemStatus(R.id.redo, !mReadOnly && editor.canRedo)
                setMenuItemStatus(R.id.save, saveStickyDirty)
            }
        }
        updateRunButtonVisualState()
    }

    private fun setInitialTextProgressively(text: String) {
        val loadTarget = textLoadTarget
        loadTarget.beginProgressiveTextLoad()

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
                    loadTarget.finishProgressiveTextLoad(markClean = true)
                    return
                }

                val start = SystemClock.uptimeMillis()

                var appendedChunks = 0

                // Do as much work as possible within the frame budget.
                // zh-CN: 在单帧预算内尽可能推进加载.
                while (index < text.length) {
                    val end = (index + chunkSize).coerceAtMost(text.length)
                    loadTarget.appendTextChunk(text.subSequence(index, end))
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

    private fun updateRunButtonVisualState() {
        val isRunning = isScriptRunning()
        mNormalToolbar.setRunButtonAsStop(isRunning)
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.toolbar_menu)
        if (fragment is NormalToolbarFragment) {
            fragment.setRunButtonAsStop(isRunning)
        }
    }

    private fun schedulePrimaryMenuStateSync() {
        mUiHandler.removeCallbacks(mPrimaryMenuStateSyncRunnable)
        mUiHandler.postDelayed(mPrimaryMenuStateSyncRunnable, mPrimaryMenuStateSyncDebounceMs)
    }

    private fun notifyScriptRunningStateChanged() {
        val isRunning = isScriptRunning()
        updateRunButtonVisualState()
        mScriptRunningStateListener?.onScriptRunningStateChanged(isRunning)
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
                    id == R.id.run && !mReadOnly && !isScriptRunning() -> true.also { debug() }
                    else -> false
                }
            }
        }

        (existing as? ToolbarFragment<*>)?.apply {
            setOnMenuItemClickListener(this@EditorView)
            setOnMenuItemLongClickListener { id ->
                when {
                    id == R.id.run && !mReadOnly && !isScriptRunning() -> true.also { debug() }
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
        updateRunButtonVisualState()
    }

    private fun setUpFunctionsKeyboard() {
        mFunctionsKeyboardHelper = FunctionsKeyboardHelper.with(context as Activity)
            .setContent(binding.editorContainer)
            .setFunctionsView(mFunctionsKeyboard)
            .setEditView(editor.inputView)
            .apply {
                setFunctionsTrigger(mShowFunctionsButton)
                setFunctionsTrigger(mAceShowFunctionsButton)
            }
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

        val beautifyOnLongClick = View.OnLongClickListener {
            true.also { mCommandDispatcher.beautifyCode() }
        }
        mShowFunctionsButton.setOnLongClickListener(beautifyOnLongClick)
        mAceShowFunctionsButton.setOnLongClickListener(beautifyOnLongClick)
    }

    private fun setUpInputMethodEnhancedBar() {
        mAutoCompletion?.shutdown()
        mAutoCompletion = null
        configureInputMethodEnhancedBars()
        listOf(
            mSymbolBar,
            mCodeCompletionBar,
            mAceShortcutTopBar,
            mAceShortcutBottomBar,
            mAceSymbolsBar,
        ).forEach { bar ->
            bar.setOnHintClickListener(this)
        }

        if (editor.engineType == EditorEngineType.ACE) {
            mCodeCompletionBar.codeCompletions = CodeCompletions.just(emptyList())
            mSymbolBar.codeCompletions = CodeCompletions.just(emptyList())
            mAceShortcutTopBar.codeCompletions = AceShortcutPanel.topRowCompletions
            mAceShortcutBottomBar.codeCompletions = AceShortcutPanel.bottomRowCompletions
            refreshSymbolsBar()
            setEnhanceBarInteractive(!mReadOnly && !mEditorLoading)
            return
        }

        mAceShortcutReturnSelection = null
        mAceShortcutTopBar.codeCompletions = CodeCompletions.just(emptyList())
        mAceShortcutBottomBar.codeCompletions = CodeCompletions.just(emptyList())
        mAceSymbolsBar.codeCompletions = CodeCompletions.just(emptyList())
        mCodeCompletionBar.codeCompletions = CodeCompletions.just(emptyList())
        refreshSymbolsBar()
        editor.createAutoCompletion(context)?.apply {
            setAutoCompleteCallback { mCodeCompletionBar.codeCompletions = it }
            mAutoCompletion = this
        }
        setEnhanceBarInteractive(!mReadOnly && !mEditorLoading)
    }

    private fun configureInputMethodEnhancedBars() {
        mCodeCompletionBar.useHorizontalLayout()
        mCodeCompletionBar.resetItemSize()
        mSymbolBar.useHorizontalLayout()
        mSymbolBar.resetItemSize()
        mAceShortcutTopBar.useHorizontalLayout()
        mAceShortcutTopBar.setFixedItemSizeDp(40, 35)
        mAceShortcutBottomBar.useHorizontalLayout()
        mAceShortcutBottomBar.setFixedItemSizeDp(40, 35)
        mAceSymbolsBar.useFlowLayout()
        mAceSymbolsBar.setItemHeightDp(35)
    }

    fun refreshSymbolsBar() {
        if (editor.engineType == EditorEngineType.ACE) {
            SymbolsConfigStore.ensureDefaultProfileExists(context)
            val symbols = SymbolsConfigStore.getEnabledSymbolsForActiveProfile(
                context,
                SymbolsConfigStore.DefaultSymbolSet.ACE,
            )
            mAceSymbolsBar.isVisible = symbols.isNotEmpty()
            mAceSymbolsBar.codeCompletions = CodeCompletions.just(symbols)
            return
        }
        SymbolsConfigStore.ensureDefaultProfileExists(context)
        val symbols = SymbolsConfigStore.getEnabledSymbolsForActiveProfile(context)
        mSymbolBar.isVisible = symbols.isNotEmpty()
        mSymbolBar.codeCompletions = CodeCompletions.just(symbols)
    }

    fun copyAceDiagnosticsToClipboard() {
        val snapshot = createAceDiagnosticsSnapshot()
        ClipboardUtils.setClip(context, AceDiagnostics.format(snapshot))
        showToast(context, R.string.text_ace_diagnostics_copied)
    }

    fun isReadOnly(): Boolean = mReadOnly

    fun isReadOnlyLockedByIntent(): Boolean = mReadOnlyLockedByIntent

    fun canToggleAceReadOnlyMode(): Boolean {
        return editor.engineType == EditorEngineType.ACE && !mReadOnlyLockedByIntent && !mEditorLoading
    }

    fun toggleAceReadOnlyMode() {
        if (!canToggleAceReadOnlyMode()) {
            return
        }
        setAceReadOnlyMode(!mReadOnly, toast = true)
    }

    private fun setAceReadOnlyMode(readOnly: Boolean, toast: Boolean = false) {
        if (mReadOnlyLockedByIntent || editor.engineType != EditorEngineType.ACE) {
            return
        }
        applySessionReadOnly(readOnly)
        if (toast) {
            showToast(
                context,
                if (readOnly) R.string.text_ace_read_only_mode_enabled else R.string.text_ace_read_only_mode_disabled,
            )
        }
    }

    private fun applySessionReadOnly(readOnly: Boolean) {
        if (mReadOnly == readOnly) {
            return
        }
        mReadOnly = readOnly
        editor.setReadOnly(readOnly)
        setEnhanceBarInteractive(!readOnly && !mEditorLoading)
        if (readOnly) {
            mFunctionsKeyboardHelper?.hideFunctionsLayout(false)
        }
        syncPrimaryMenuState()
        activity.invalidateOptionsMenu()
    }

    fun switchEditorEngine(targetType: EditorEngineType) {
        if (editor.engineType == targetType || mSwitchingEditorEngine) {
            return
        }
        if (targetType == EditorEngineType.ACE && AceEditorTestHooks.shouldForceLargeFileNativeRoute()) {
            showAceLargeFileNativeToastOnce()
            return
        }
        if (mEditorLoading) {
            showToast(context, R.string.text_loading_with_dots)
            return
        }
        if (!mReadOnlyLockedByIntent && targetType != EditorEngineType.ACE && mReadOnly) {
            applySessionReadOnly(false)
        }

        val oldEditor = editor
        mSwitchingEditorEngine = true
        runCatching {
            oldEditor.getTextForSaving { latestText ->
                post {
                    try {
                        switchEditorEngine(targetType, oldEditor, latestText)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast(context, e.message, true)
                    } finally {
                        mSwitchingEditorEngine = false
                    }
                }
            }
        }.onFailure { error ->
            mSwitchingEditorEngine = false
            error.printStackTrace()
            showToast(context, error.message, true)
        }
    }

    private fun switchEditorEngine(
        targetType: EditorEngineType,
        oldEditor: ScriptEditorEngine,
        latestText: String,
        persistPreference: Boolean = true,
        showSwitchToast: Boolean = true,
    ): Boolean {
        if (oldEditor !== editor) {
            return false
        }

        val newEditor = EditorEngineFactory.create(context, targetType)
        if (newEditor.engineType != targetType) {
            newEditor.destroy()
            showToast(context, R.string.error_ace_editor_unavailable, true)
            return false
        }

        val wasAttached = isAttachedToWindow
        val oldSaveStickyDirty = saveStickyDirty
        val oldHadDirectEditSinceSave = mHadDirectEditSinceSave
        val oldEditorLoading = mEditorLoading
        val oldLargeFileMode = mLargeFileMode
        val snapshot = oldEditor.createStateSnapshot().copy(
            text = latestText,
            dirty = oldSaveStickyDirty || oldEditor.isTextChanged,
            readOnly = mReadOnly,
            themeKey = themeFontService.currentTheme?.name,
        )

        mEditorLoading = true
        syncPrimaryMenuState()

        if (wasAttached) {
            unregisterFunctionsKeyboardBackHandler()
        }
        mFunctionsKeyboardHelper?.hideFunctionsLayout(false)
        mFunctionsKeyboardHelper = null
        mAutoCompletion?.shutdown()
        mAutoCompletion = null
        recordLastAceDiagnostics(oldEditor)
        (oldEditor as? AceScriptEditorEngine)?.onFatalFailure = null
        oldEditor.onUserTouchInTextArea = null
        binding.editorContainer.removeView(oldEditor.view)

        editor = newEditor
        breakpointModelStore.onEditorChanged()
        mViewportPolicy = EditorViewportPolicyFactory.create(editor, this)
        attachEditorEngineView()
        setUpEditor(resetInitialState = false)
        setUpInputMethodEnhancedBar()
        setUpFunctionsKeyboard()
        mLastImeBottomInset = -1
        requestApplyInsetsSoon()
        if (wasAttached) {
            registerFunctionsKeyboardBackHandler()
        }

        themeFontService.currentTheme?.let { themeFontService.applyTheme(it) }
        editor.restoreStateSnapshot(snapshot)
        editor.setReadOnly(mReadOnly)

        oldEditor.destroy()
        if (persistPreference) {
            EditorEnginePreferences.set(targetType)
        }

        mEditorLoading = oldEditorLoading
        mLargeFileMode = oldLargeFileMode && editor.engineType == EditorEngineType.NATIVE
        saveStickyDirty = oldSaveStickyDirty || snapshot.dirty
        mHadDirectEditSinceSave = oldHadDirectEditSinceSave || snapshot.dirty

        syncPrimaryMenuState()
        activity.invalidateOptionsMenu()
        if (editor.engineType == EditorEngineType.NATIVE) {
            editor.requestEditorFocus()
        }

        val engineName = when (targetType) {
            EditorEngineType.NATIVE -> context.getString(R.string.entry_editor_engine_native)
            EditorEngineType.ACE -> context.getString(R.string.entry_editor_engine_ace)
        }
        if (showSwitchToast) {
            showEditorEngineSwitchedToast(engineName)
        }
        return true
    }

    private fun configureAceFallback(editor: ScriptEditorEngine) {
        (editor as? AceScriptEditorEngine)?.onFatalFailure = { failure ->
            post { fallbackToNativeFromAceFailure(failure) }
        }
    }

    private fun fallbackToNativeFromAceFailure(failure: AceFailure) {
        if (!mAceFallbackInProgress.compareAndSet(false, true)) {
            return
        }

        val oldEditor = editor
        if (oldEditor.engineType != EditorEngineType.ACE) {
            mAceFallbackInProgress.set(false)
            return
        }

        mSwitchingEditorEngine = true
        runCatching {
            (oldEditor as? AceScriptEditorEngine)?.onFatalFailure = null
            val switched = switchEditorEngine(
                targetType = EditorEngineType.NATIVE,
                oldEditor = oldEditor,
                latestText = oldEditor.textSnapshot,
                persistPreference = false,
                showSwitchToast = false,
            )
            if (switched) {
                showAceFallbackToastOnce(failure)
            }
        }.onFailure { error ->
            error.printStackTrace()
            showToast(context, error.message ?: failure.message, true)
        }
        mSwitchingEditorEngine = false
        mAceFallbackInProgress.set(false)
    }

    private fun failureReasonForToast(failure: AceFailure): String {
        return failure.type.name
            .lowercase(Locale.getDefault())
            .replace('_', ' ')
    }

    private fun showEditorEngineSwitchedToast(engineName: String) {
        val message = if (EditorEnginePreferences.consumeEditorSwitchUndoNotice()) {
            context.getString(R.string.text_editor_engine_switched_with_undo_notice, engineName)
        } else {
            context.getString(R.string.text_editor_engine_switched, engineName)
        }
        showToast(context, message)
    }

    private fun showAceFallbackToastOnce(failure: AceFailure) {
        if (!EditorEnginePreferences.consumeAceFallbackNotice()) {
            return
        }
        showToast(
            context,
            context.getString(R.string.text_ace_editor_fallback_native, failureReasonForToast(failure)),
            true,
        )
    }

    private fun showAceLargeFileNativeToastOnce() {
        if (EditorEnginePreferences.consumeAceLargeFileNotice()) {
            showToast(context, R.string.text_ace_large_file_uses_native_editor)
        }
    }

    private fun recordAceAssetMissingFallbackIfNeeded() {
        if (EditorEnginePreferences.get() != EditorEngineType.ACE) {
            return
        }
        if (editor.engineType == EditorEngineType.ACE || AceEditorAssets.hasRequired(context)) {
            return
        }
        val now = SystemClock.uptimeMillis()
        val message = "ACE editor assets are unavailable"
        mLastAceDiagnosticsSnapshot = AceDiagnostics
            .createNativeSnapshot(EditorEngineType.ACE, fileSizeBytes = null, largeFilePolicy = "auto")
            .copy(
                aceHealthState = AceHealthState.FAILED.name,
                lastFailureType = AceFailureType.ASSET_MISSING.name,
                lastFailureMessage = message,
                lastFailureFatal = true,
                runtimeEventHistory = listOf(
                    "$now:failure:type=${AceFailureType.ASSET_MISSING.name},fatal=true,message=$message",
                ),
            )
    }

    private fun createAceDiagnosticsSnapshot(): AceDiagnosticsSnapshot {
        val fileSizeBytes = currentFileSizeOrNull()
        val largeFilePolicy = currentLargeFilePolicyLabel()
        return (editor as? AceScriptEditorEngine)?.createHostAceDiagnosticsSnapshot(fileSizeBytes, largeFilePolicy)
            ?: mLastAceDiagnosticsSnapshot
            ?: AceDiagnostics.createNativeSnapshot(editor.engineType, fileSizeBytes, largeFilePolicy)
    }

    private fun recordLastAceDiagnostics(editor: ScriptEditorEngine) {
        mLastAceDiagnosticsSnapshot = (editor as? AceScriptEditorEngine)
            ?.createHostAceDiagnosticsSnapshot(currentFileSizeOrNull(), currentLargeFilePolicyLabel())
            ?: mLastAceDiagnosticsSnapshot
    }

    private fun AceScriptEditorEngine.createHostAceDiagnosticsSnapshot(
        fileSizeBytes: Long?,
        largeFilePolicy: String,
    ): AceDiagnosticsSnapshot {
        val viewportPolicyEventAt = mLastImeEventAtUptimeMillis.takeIf { it > 0L }
        val viewportPolicyName = mViewportPolicy.javaClass.simpleName
        return createAceDiagnosticsSnapshot(fileSizeBytes, largeFilePolicy).copy(
            viewportPolicy = viewportPolicyName,
            lastViewportPolicyEvent = viewportPolicyEventAt?.let { "$viewportPolicyName.onImeInsetsChanged" },
            lastViewportPolicyEventAtUptimeMillis = viewportPolicyEventAt,
            lastViewportPolicyImeVisible = viewportPolicyEventAt?.let { mLastImeVisible },
            lastViewportPolicyBottomInset = viewportPolicyEventAt?.let { mLastImeBottomInset },
            lastHostImeVisible = mLastImeVisible,
            lastHostImeBottomInset = mLastImeBottomInset.takeIf { it >= 0 },
            lastHostImeEventAtUptimeMillis = mLastImeEventAtUptimeMillis.takeIf { it > 0L },
        )
    }

    private fun currentFileSizeOrNull(): Long? {
        val currentUri = uri ?: return null
        return runCatching { fileService.queryContentLengthOrNull(currentUri) }.getOrNull()
    }

    private fun currentLargeFilePolicyLabel(): String {
        return EditorTextLoadPolicy.largeFilePolicyLabel(
            largeFileMode = mLargeFileMode,
            forceNativeRoute = AceEditorTestHooks.shouldForceLargeFileNativeRoute(),
        )
    }

    private fun setUpEditor(resetInitialState: Boolean = true) {
        configureAceFallback(editor)
        editor.let { editor ->
            // Observe user touch in text area to preserve caret position after large-file load.
            // zh-CN: 监听用户在文本区域的触摸, 以便大文件加载完成后保留光标位置.
            editor.onUserTouchInTextArea = {
                if (mEditorLoading && mLargeFileMode) {
                    mUserMovedCursorDuringLoading = true
                }
            }

            editor.addTextChangeListener {
                // Skip menu state updates during progressive loading.
                // zh-CN: 渐进式加载期间跳过菜单状态更新.
                if (editor.isLoadingText || mEditorLoading) {
                    return@addTextChangeListener
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

                schedulePrimaryMenuStateSync()
            }

            editor.addTextChangeListener {
                // Live refresh search stats in search mode (debounced).
                // Behavior:
                // - Immediately show ".../..." to indicate recounting.
                // - Debounce the actual background scan.
                //
                // zh-CN: 搜索模式下实时刷新统计 (带防抖).
                // - 立刻把 subtitle 变成 ".../..." 表示重新统计中.
                // - 后台扫描做 debounce, 避免频繁扫全文.
                if (!mInSearchMode) return@addTextChangeListener
                if (editor.isLoadingText || mEditorLoading) return@addTextChangeListener

                mSearchStatsCounting = true
                mSearchStatsCurrentOrdinal = null
                mSearchStatsTotal = null
                mSearchStatsTotalCapped = false
                updateSearchSubtitleBestEffort()

                scheduleSearchStatsRefreshDebounced()
            }

            themeFontService.applyEditorPreferences()

            editor.addCursorChangeCallback(EditorCursorChangeListener { line, cursor ->
                autoComplete(line, cursor)
            })
            editor.view.layoutDirection = LAYOUT_DIRECTION_LTR

            // Initialize to "new file" expected state: only Run enabled.
            // zh-CN: 初始化为 "新建文件" 预期状态: 仅运行可用.
            if (resetInitialState) {
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
    }

    private fun autoComplete(line: String, cursor: Int) {
        mAutoCompletion?.onCursorChange(line, cursor)
    }

    private fun setTheme(theme: Theme?) {
        themeFontService.applyTheme(theme)
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
            R.id.run -> when {
                isScriptRunning() -> forceStop()
                else -> runAndSaveFileIfNeeded()
            }
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
    fun runAndSaveFileIfNeeded(showLogPanelAfterRun: Boolean = true) {
        runScriptTextProvider.saveToTmpFile()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ file: File ->
                val execution = runTmpFile(file)
                if (execution != null && showLogPanelAfterRun && shouldAutoShowLogBottomSheetOnRun()) {
                    showLogPanel()
                }
            }, Observers.toastMessage())
    }

    private fun shouldAutoShowLogBottomSheetOnRun(): Boolean = Pref.getBoolean(
        R.string.key_editor_auto_show_log_bottom_sheet_on_run,
        resources.getBoolean(R.bool.pref_editor_auto_show_log_bottom_sheet_on_run),
    )

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
        syncPrimaryMenuState()
        notifyScriptRunningStateChanged()
        return execution
    }

    private fun runTmpFile(file: File? = uri?.path?.let { File(it) }): ScriptExecution? {
        return run(!shouldAutoShowLogBottomSheetOnRun(), file, uri?.path)
    }

    private fun undo() {
        // Mark undo/redo button action so TextWatcher can distinguish it.
        // zh-CN: 标记撤销/重做按钮动作, 以便 TextWatcher 区分来源.
        mUndoRedoButtonInProgress = true
        try {
            mCommandDispatcher.undo()
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
            mCommandDispatcher.redo()
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
        editorTextProvider.getTextForSaving()
            .flatMap { latestText ->
                Observable.fromCallable {
                    when (val uri = uri) {
                        null -> {
                            throw IllegalStateException(context.getString(R.string.error_unable_to_save_file_as_current_file_path_is_unknown))
                        }
                        else -> {
                            // Use a transactional save flow for both file:// and content://.
                            // zh-CN: 对 file:// 与 content:// 统一使用事务式保存流程.
                            fileService.saveTextTransactional(
                                uri = uri,
                                text = latestText,
                                encoding = EditorFileSaveEncoding(
                                    charsetConfidence = mCurrentCharsetConfidence,
                                    charset = mCurrentCharset,
                                    hadBom = mHadBom,
                                ),
                                displayName = name,
                            )
                            latestText
                        }
                    }
                }.subscribeOn(Schedulers.io())
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                editor.markTextAsSaved()

                // Reset sticky dirty only on successful save.
                // zh-CN: 仅在保存成功后重置 sticky 脏标记.
                saveStickyDirty = false
                mHadDirectEditSinceSave = false

                setMenuItemStatus(R.id.save, false)
            }

    fun showVersionHistoryDialog() {
        versionHistoryService.showDialog(uri)
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
        mCommandDispatcher.replaceSelection()
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
        mCommandDispatcher.beautifyCode()
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
                .initialValue(themeFontService.currentTextSize())
                .callback { value: Int -> setTextSize(value) }
                .build()
        }
    }

    fun selectAceEditorFont() {
        val fonts = themeFontService.availableAceFonts()
        val current = themeFontService.currentAceFont()
        val currentIndex = fonts.indexOf(current).coerceAtLeast(0)
        val labels = fonts.map { themeFontService.aceEditorFontLabel(it) }

        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_ace_editor_font)
                .items(labels)
                .choiceWidgetThemeColor()
                .itemsCallbackSingleChoice(currentIndex) { _, _, which, _ ->
                    val font = fonts[which]
                    themeFontService.persistAndApplyAceFont(font)
                    showToast(
                        context,
                        context.getString(R.string.text_ace_editor_font_changed, themeFontService.aceEditorFontLabel(font)),
                    )
                    true
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .build()
        }
    }

    fun setTextSize(value: Int) {
        themeFontService.setTextSize(value)
    }

    private fun selectEditorTheme(themes: List<Theme>) {
        val i = themes.indexOf(themeFontService.currentTheme).coerceAtLeast(0)
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_editor_theme)
                .items(themes)
                .choiceWidgetThemeColor()
                .itemsCallbackSingleChoice(i) { _, _, which, _ ->
                    themes[which].let {
                        themeFontService.persistAndApplyTheme(it)
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
        val currentSelection = editor.selection
        val textSnapshot = editor.textSnapshot

        mSearchStatsDisposable = Observable
            .fromCallable {
                computeSearchStats(
                    text = textSnapshot,
                    query = query,
                    usingRegex = usingRegex,
                    selectionStart = currentSelection.startOffset,
                    selectionEnd = currentSelection.endOffset,
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
        val result = editor.find(keywords, EditorSearchOptions(usingRegex = usingRegex))

        val hasResult = result.found
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
        val result = editor.replace(keywords, replacement, EditorSearchOptions(usingRegex = usingRegex))

        val hasResult = result.found
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

    @Throws(EditorSearchSyntaxException::class)
    fun find(keywords: String, usingRegex: Boolean) {
        // Keep old API for callers that don't care about result.
        // zh-CN: 保留旧 API, 兼容不关心结果的调用方.
        tryFind(keywords, usingRegex)
    }

    @Throws(EditorSearchSyntaxException::class)
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

    @Throws(EditorSearchSyntaxException::class)
    fun replaceAll(keywords: String, replacement: String, usingRegex: Boolean) {
        editor.replaceAll(keywords, replacement, EditorSearchOptions(usingRegex = usingRegex))
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

        if (handleAceShortcutClick(completions[pos])) {
            return
        }
        mCommandDispatcher.onCompletionClick(completions, pos)
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

        if (handleAceShortcutLongClick(completions[pos])) {
            return
        }
        mCommandDispatcher.onCompletionLongClick(completions, pos)
    }

    private fun handleAceShortcutClick(completion: CodeCompletion): Boolean {
        if (editor.engineType != EditorEngineType.ACE) {
            return false
        }
        if (AceShortcutPanel.commandOf(completion) == AceShortcutPanel.Command.ActionFavorites) {
            showAceActionHistoryDialog()
            return true
        }
        return AceShortcutPanel.actionOf(completion)
            ?.let { executeAceShortcutAction(it, record = true) }
            ?: false
    }

    private fun handleAceShortcutLongClick(completion: CodeCompletion): Boolean {
        if (editor.engineType != EditorEngineType.ACE) {
            return false
        }
        return when (AceShortcutPanel.commandOf(completion)) {
            AceShortcutPanel.Command.ActionFavorites -> true.also {
                repeatLastAceAction()
            }
            AceShortcutPanel.Command.Escape -> true.also {
                triggerHostBackPress()
            }
            AceShortcutPanel.Command.MoveUp -> true.also {
                jumpWithAceShortcutReturnPoint { editor.jumpToStart() }
            }
            AceShortcutPanel.Command.MoveDown -> true.also {
                jumpWithAceShortcutReturnPoint { editor.jumpToEnd() }
            }
            AceShortcutPanel.Command.MoveLeft -> true.also {
                jumpWithAceShortcutReturnPoint { editor.jumpToLineStart() }
            }
            AceShortcutPanel.Command.MoveRight -> true.also {
                jumpWithAceShortcutReturnPoint { editor.jumpToLineEnd() }
            }
            AceShortcutPanel.Command.Tab -> true.also {
                toggleAceShortcutReturnPoint()
            }
            null -> false
        }
    }

    private fun showAceActionHistoryDialog() {
        AceActionFavoritesDialog.show(context) { action ->
            executeAceShortcutAction(action, record = true)
        }
    }

    private fun repeatLastAceAction() {
        val action = AceShortcutPanel.actionById(AceActionHistoryStore.getLastActionId(context))
        if (action == null) {
            showToast(context, R.string.text_ace_no_last_action)
            return
        }
        executeAceShortcutAction(action, record = true)
    }

    private fun executeAceShortcutAction(
        action: AceShortcutPanel.Action,
        record: Boolean,
    ): Boolean {
        if (action.mutatesText && editor.isReadOnly) {
            return true
        }
        when (action.command) {
            AceShortcutPanel.Command.Escape -> (editor as? AceScriptEditorEngine)?.handleEscapeKey()
            AceShortcutPanel.Command.MoveUp -> (editor as? AceScriptEditorEngine)?.moveCompletionSelectionOrCursor(-1)
                ?: editor.jumpToPrevLine()
            AceShortcutPanel.Command.MoveDown -> (editor as? AceScriptEditorEngine)?.moveCompletionSelectionOrCursor(1)
                ?: editor.jumpToNextLine()
            AceShortcutPanel.Command.MoveLeft -> editor.moveCursor(-1)
            AceShortcutPanel.Command.MoveRight -> editor.moveCursor(1)
            AceShortcutPanel.Command.Tab -> (editor as? AceScriptEditorEngine)?.acceptCompletionOrInsertTab()
            AceShortcutPanel.Command.ActionFavorites -> return true
            null -> mCommandDispatcher.insert(action.insertText)
        }
        if (record) {
            AceActionHistoryStore.record(context, action)
        }
        return true
    }

    private fun jumpWithAceShortcutReturnPoint(jump: () -> Unit) {
        mAceShortcutReturnSelection = editor.selection
        jump()
    }

    private fun toggleAceShortcutReturnPoint() {
        val target = mAceShortcutReturnSelection ?: return
        val current = editor.selection
        mAceShortcutReturnSelection = current
        editor.jumpTo(target.startLine, target.startColumn)
    }

    private fun triggerHostBackPress() {
        post {
            activity.onBackPressedDispatcher.onBackPressed()
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
        mCommandDispatcher.onModuleLongClick(module)
    }

    override fun onPropertyClick(m: Module, property: Property) {
        mCommandDispatcher.onPropertyClick(m, property)
    }

    override fun onPropertyLongClick(m: Module, property: Property) {
        mCommandDispatcher.onPropertyLongClick(m, property)
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
        syncPrimaryMenuState()
        notifyScriptRunningStateChanged()
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

    fun cleanBeforeExit() {
        mTmpSavedFileForRunning?.deleteOnExit()
    }

    companion object {

        const val EXTRA_PATH = "path"
        const val EXTRA_NAME = "name"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_READ_ONLY = "readOnly"
        const val EXTRA_SAVE_ENABLED = "saveEnabled"
        const val EXTRA_RUN_ENABLED = "runEnabled"
    }
}
