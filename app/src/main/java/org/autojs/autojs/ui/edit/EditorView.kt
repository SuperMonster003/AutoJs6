package org.autojs.autojs.ui.edit

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.SystemClock
import android.text.InputType
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.view.Choreographer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.editor.AceEditorPluginStateMonitor
import org.autojs.autojs.core.plugin.typescript.TypeScriptEditorDefinitionTargetResolver
import org.autojs.autojs.core.plugin.typescript.TypeScriptEditorProjectRenameException
import org.autojs.autojs.core.plugin.typescript.TypeScriptEditorProjectRenameFailureReason
import org.autojs.autojs.core.plugin.typescript.TypeScriptEditorProjectRenameTransaction
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.event.BackPressedHandler.HostActivity
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.model.autocomplete.AutoCompletion
import org.autojs.autojs.model.autocomplete.CodeCompletion
import org.autojs.autojs.model.autocomplete.CodeCompletions
import org.autojs.autojs.model.explorer.PrivilegedAccessMode
import org.autojs.autojs.model.explorer.PrivilegedEditSessions
import org.autojs.autojs.model.indices.AndroidClass
import org.autojs.autojs.model.indices.ClassSearchingItem
import org.autojs.autojs.model.indices.Module
import org.autojs.autojs.model.indices.Property
import org.autojs.autojs.model.script.Scripts.ACTION_ON_EXECUTION_FINISHED
import org.autojs.autojs.model.script.Scripts.EXTRA_EXCEPTION_COLUMN_NUMBER
import org.autojs.autojs.model.script.Scripts.EXTRA_EXCEPTION_LINE_NUMBER
import org.autojs.autojs.model.script.Scripts.EXTRA_EXCEPTION_MESSAGE
import org.autojs.autojs.model.script.Scripts.openByOtherApps
import org.autojs.autojs.model.script.Scripts.runWithBroadcastSenderInteractive
import org.autojs.autojs.permission.ShizukuPermission
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.pio.PFiles.getNameWithoutExtension
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.tool.Callback
import org.autojs.autojs.ui.doc.ManualDialog
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar.OnHintClickListener
import org.autojs.autojs.ui.edit.debug.DebugBar
import org.autojs.autojs.ui.edit.editor.EditorDisplayPreferences
import org.autojs.autojs.ui.edit.editor.JavaScriptHighlighter
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontDialog
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontManager
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontOption
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontPreviewFilter
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontType
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontUiPreferences
import org.autojs.autojs.ui.edit.editor.ace.AceFontCatalogRefreshSubscription
import org.autojs.autojs.ui.edit.editor.ace.AceEditorTestHooks
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadEvent
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadSubscription
import org.autojs.autojs.ui.edit.editor.ace.font.FontFeature
import org.autojs.autojs.ui.edit.editor.ace.font.FontInstallationStatus
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnostics
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnosticsSnapshot
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailure
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailureType
import org.autojs.autojs.ui.edit.editor.ace.health.AceHealthState
import org.autojs.autojs.ui.edit.editor.command.EditorCommandDispatcher
import org.autojs.autojs.ui.edit.editor.engine.AceScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineFactory
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorHistoryDirection
import org.autojs.autojs.ui.edit.editor.engine.EditorCursorChangeListener
import org.autojs.autojs.ui.edit.editor.engine.EditorSearchOptions
import org.autojs.autojs.ui.edit.editor.engine.EditorSearchSyntaxException
import org.autojs.autojs.ui.edit.editor.engine.EditorSelection
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.ui.edit.editor.engine.EditorStateSnapshot
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingCandidate
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingCodec
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingDetector
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingDialog
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingRisk
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingRiskEvaluator
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadMetadata
import org.autojs.autojs.ui.edit.editor.loading.EditorTextLoadPolicy
import org.autojs.autojs.ui.edit.editor.loading.EditorTextLoadTarget
import org.autojs.autojs.ui.edit.editor.loading.EngineTextLoadTarget
import org.autojs.autojs.ui.edit.editor.service.EditorBreakpointModelStore
import org.autojs.autojs.ui.edit.editor.service.EditorCurrentDocumentCodeActionAuthorizer
import org.autojs.autojs.ui.edit.editor.service.EditorDefinitionNavigationDispatcher
import org.autojs.autojs.ui.edit.editor.service.EditorFileRollbackFailedException
import org.autojs.autojs.ui.edit.editor.service.EditorFileSaveEncoding
import org.autojs.autojs.ui.edit.editor.service.EditorFileService
import org.autojs.autojs.ui.edit.editor.service.EditorFileIoCoordinator
import org.autojs.autojs.ui.edit.editor.service.EditorPendingSaveAsRecord
import org.autojs.autojs.ui.edit.editor.service.EditorPendingSaveAsResult
import org.autojs.autojs.ui.edit.editor.service.EditorScriptRunPolicy
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
import org.autojs.autojs.ui.edit.theme.EditorChromePalette
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.ui.edit.theme.Themes
import org.autojs.autojs.ui.edit.toolbar.DebugToolbarFragment
import org.autojs.autojs.ui.edit.toolbar.NormalToolbarFragment
import org.autojs.autojs.ui.edit.toolbar.SearchToolbarFragment
import org.autojs.autojs.ui.edit.toolbar.ToolbarFragment
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.main.scripts.EditableFileInfoDialogManager
import org.autojs.autojs.ui.project.BuildActivity
import org.autojs.autojs.ui.settings.EditorSettingActionRegistry
import org.autojs.autojs.ui.settings.AceEditorPluginUi
import org.autojs.autojs.ui.widget.EWebView
import org.autojs.autojs.ui.widget.SimpleTextWatcher
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.core.plugin.compose.ThreeBlockComposeDesigner
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.DocsUtils.INDEX_URL_SUFFIX
import org.autojs.autojs.util.DocsUtils.getUrl
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ScriptOriginResolver
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.R.string.text_unknown
import org.autojs.autojs6.databinding.DialogAceEditorFontFilterBinding
import org.autojs.autojs6.databinding.EditorViewBinding
import org.autojs.plugin.editor.api.EditorPluginDefinitionTarget
import org.autojs.plugin.editor.api.EditorPluginCurrentDocumentCodeAction
import org.autojs.plugin.editor.api.EditorPluginProjectRenameCallback
import org.autojs.plugin.editor.api.EditorPluginProjectRenameContract
import org.autojs.plugin.editor.api.EditorPluginProjectRenameRequest
import org.autojs.plugin.editor.api.EditorPluginProjectRenameResult
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
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
    private var mDocumentLocationChangedListener: ((Uri, String) -> Unit)? = null
    private var mCompletedSaveAsAdoptedListener: ((EditorPendingSaveAsRecord, Boolean) -> Unit)? = null
    private var mEditorSessionId: String = UUID.randomUUID().toString()

    fun setLogPanelCallback(callback: LogPanelCallback) {
        mLogPanelCallback = callback
    }

    fun setDocumentLocationChangedListener(listener: (Uri, String) -> Unit) {
        mDocumentLocationChangedListener = listener
    }

    fun setEditorSessionId(sessionId: String) {
        require(sessionId.isNotBlank())
        mEditorSessionId = sessionId
    }

    fun setCompletedSaveAsAdoptedListener(
        listener: (EditorPendingSaveAsRecord, Boolean) -> Unit,
    ) {
        mCompletedSaveAsAdoptedListener = listener
    }

    fun prepareCompletedSaveAsAdoption(record: EditorPendingSaveAsRecord) {
        mPendingCompletedSaveAs = record
        mCurrentCharset = record.encoding.charset
        mCurrentCharsetConfidence = 100
        mHadBom = record.encoding.hadBom
        mPersistedFileEncoding = record.encoding
        mCurrentHadDecodingErrors = false
        mCurrentDecodingErrorsWereAccepted = false
        mFileEncodingValidationComplete = true
        updateEncodingStatus()
    }

    fun showLogPanel() {
        mLogPanelCallback?.onShowLogPanel()
    }

    fun openEditorLogByPreference() {
        val mode = Pref.getString(
            R.string.key_editor_log_button_behavior,
            key(R.string.default_key_editor_log_button_behavior),
        )
        if (mode == key(R.string.key_editor_log_button_behavior_fullscreen_page)) {
            LogActivity.launch(context, name, uri?.path)
            return
        }
        showLogPanel()
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
        extensionWithDotProvider = ::temporaryScriptExtensionWithDot,
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
        onSaveRequested = { saveFile() },
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
        editorSessionIdProvider = { mEditorSessionId },
        onSaveAsSucceeded = ::handleSaveAsSucceeded,
        onSaveAsInProgressChanged = { inProgress ->
            if (!mDestroyed) {
                mSaveAsInProgress = inProgress
                syncPrimaryMenuState()
            }
        },
    )

    private fun handleSaveAsSucceeded(
        result: EditorPendingSaveAsResult,
    ): Boolean {
        if (mDestroyed) return false
        val sourceUri = result.record.sourceUri
        val destination = result.record.destination
        val encoding = result.record.encoding
        val savedText = result.savedText
        val destinationUri = Uri.fromFile(destination)
        if (PrivilegedEditSessions.isSessionUri(context, sourceUri)) {
            mRunEnabledByIntent = true
        }
        uri = destinationUri
        name = getNameWithoutExtension(destination.path)
        syncEditorDocumentPath()
        mCurrentCharset = encoding.charset
        mCurrentCharsetConfidence = 100
        mHadBom = encoding.hadBom
        mPersistedFileEncoding = encoding
        mCurrentHadDecodingErrors = false
        mCurrentDecodingErrorsWereAccepted = false
        mFileStateRequiresReload = false
        mCurrentFileSizeBytes = runCatching { destination.length() }.getOrNull()
        mDraftEncodingValidationPending = false
        mFileEncodingValidationComplete = true
        updateEncodingStatus()
        if (editor.textSnapshot == savedText) {
            fileService.discardStableDraft(sourceUri)
            resolvePrivilegedDraftAfterSaveAs(sourceUri)
            editor.markTextAsSaved()
            saveStickyDirty = false
            mHadDirectEditSinceSave = false
            setMenuItemStatus(R.id.save, false)
        } else {
            // The failure dialog may remain open while the editor changes. Save As establishes
            // the new document URI, but only the actual saved snapshot may become the baseline.
            // zh-CN: 保存失败对话框停留期间编辑器内容可能继续变化. Save As 会建立新的文档 URI,
            // 但只有实际写入的文本快照才可成为已保存基线.
            saveStickyDirty = true
            syncPrimaryMenuState()
        }
        mDocumentLocationChangedListener?.invoke(destinationUri, name)
        return true
    }

    private fun resolvePrivilegedDraftAfterSaveAs(sourceUri: Uri) {
        if (!PrivilegedEditSessions.isSessionUri(context, sourceUri)) return
        Schedulers.io().scheduleDirect {
            runCatching { PrivilegedEditSessions.discardDraft(context, sourceUri) }
                .onFailure(Throwable::printStackTrace)
        }
    }

    // Whether a draft has been restored in this session.
    // If true, subsequent async file-load setInitialText() must NOT overwrite the restored draft.
    //
    // zh-CN:
    // 本次会话是否已恢复草稿.
    // 若为 true, 则后续异步文件加载触发的 setInitialText() 不得覆盖已恢复的草稿.
    @Volatile
    private var mDraftRestoredInThisSession: Boolean = false
    private var mPrivilegedDraftNeedsSave: Boolean = false
    private var mPrivilegedDraftNoticeShown: Boolean = false
    @Volatile
    private var mDraftEncodingValidationPending: Boolean = false
    @Volatile
    private var mDraftRestoreInProgress: Boolean = false
    @Volatile
    private var mFileEncodingValidationComplete: Boolean = true

    /**
     * Encoding metadata restored alongside a lifecycle draft. The on-disk file is still read to
     * validate the source bytes, so this state is applied only after that validation completes.
     */
    private var mPendingRestoredDraftEncodingState: EncodingHistoryState? = null

    // ACE applies toolbar history commands asynchronously. Keep the requested direction pending
    // until the matching history event arrives so its text deltas are never mistaken for direct
    // edits and encoding metadata advances atomically with the document history.
    //
    // zh-CN:
    // ACE 会异步执行工具栏历史命令. 在对应历史事件抵达前保留请求方向,
    // 避免把文本增量误判为直接编辑, 并让编码元数据与文档历史原子推进.
    @Volatile
    private var mPendingHistoryNavigation: EditorHistoryDirection? = null
    private var mHistoryNavigationGeneration = 0L

    // Whether user has touched/moved caret during large file loading.
    // If true, we should NOT force caret to 0 on load completion.
    //
    // zh-CN:
    // 用户是否在大文件加载期间触摸过/移动过光标.
    // 若为 true, 则加载完成时不要强制把光标设为 0.
    @Volatile
    private var mUserMovedCursorDuringLoading: Boolean = false

    private var mDocumentDisplayName: String? = null
    private var mDocumentIdentityGeneration: Long = 0L

    @Volatile
    private var mCursorStatusReady: Boolean = false

    var name: String
        get() = _name ?: "[ ${this.context.getString(text_unknown)} ]"
        set(value) {
            if (_name != value) {
                mDocumentIdentityGeneration++
            }
            _name = value
            updateEditorStatusMetadata()
        }

    var uri: Uri? = null
        set(value) {
            mDocumentIdentityGeneration++
            field = value
            mPrivilegedDraftNeedsSave = false
            mPrivilegedDraftNoticeShown = false
            mDocumentDisplayName = value
                ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
                ?.path
                ?.let(::File)
                ?.name
                ?.takeIf(String::isNotBlank)
            updateEditorStatusMetadata()
            refreshDocumentRunPolicy()
        }

    private fun currentDocumentPathOrName(): String? {
        if (uri?.let { PrivilegedEditSessions.isSessionUri(context, it) } == true) {
            // The session token is neither a source path nor a language hint.
            return mDocumentDisplayName ?: _name
        }
        return uri?.path?.takeIf { it.isNotBlank() }
            ?: uri?.toString()?.takeIf { it.isNotBlank() }
            ?: _name?.takeIf { it.isNotBlank() }
    }

    private fun isRunEnabledForCurrentDocument(): Boolean =
        mRunEnabledByIntent && mRunEnabledForDocument &&
            uri?.let { !PrivilegedEditSessions.isSessionUri(context, it) } != false

    private fun refreshDocumentRunPolicy() {
        mRunEnabledForDocument = EditorScriptRunPolicy.isRunEnabledForDocument(
            documentDisplayName = mDocumentDisplayName,
            uriPath = uri?.path,
            editorName = _name,
        )
        findViewById<View>(R.id.run)?.visibility = if (isRunEnabledForCurrentDocument()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        syncPrimaryMenuState()
    }

    private fun syncEditorDocumentPath() {
        editor.setDocumentPath(currentDocumentPathOrName())
    }

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

    private var mEditorEngineSwitchGeneration: Long = 0L
    private var mActiveEditorEngineSwitch: EditorEngineSwitchRequest? = null
    private var mPendingEditorEngineSwitch: EditorEngineSwitchRequest? = null

    private val mAceFallbackInProgress = AtomicBoolean(false)
    private var mAceSessionInvalidated = false

    private var mLastAcePluginState: AceEditorPluginStateMonitor.State? = null
    private var mLastAcePluginChangeId: Long = 0L
    private val mAcePluginStateListener: (AceEditorPluginStateMonitor.Change) -> Unit = { change ->
        post { handleAcePluginStateChange(change) }
    }

    private enum class EditorEngineSwitchReason(val priority: Int) {
        PLUGIN_RELOAD(0),
        PREFERENCE(1),
        ACE_FAILURE(2),
        PLUGIN_UNAVAILABLE(3),
    }

    private data class EditorEngineSwitchRequest(
        val targetType: EditorEngineType,
        val reason: EditorEngineSwitchReason,
        val recreateSameType: Boolean = false,
        val showSwitchToast: Boolean = false,
        val allowFactoryFallback: Boolean = false,
        val pluginChange: AceEditorPluginStateMonitor.Change? = null,
        val aceFailure: AceFailure? = null,
    )

    private data class SystemBarProtectionLayout(
        val width: Int,
        val height: Int,
        val gravity: Int,
        val visibleSize: Int,
    )

    @Volatile
    private var mLastAceDiagnosticsSnapshot: AceDiagnosticsSnapshot? = null

    private var mPendingInitialPosition: InitialPositionRequest? = null
    private var mPendingRestoredSelection: Pair<Int, Int>? = null

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
    private var mProgressiveTextLoadActive = false

    private val mCodeCompletionBar: CodeCompletionBar = binding.codeCompletionBar
    private val mEditorAppBar: View = binding.editorAppBar
    private val mEditorStatusBar: View = binding.editorStatusBar
    private val mExpandedStatusBarHeight = mEditorStatusBar.layoutParams.height
    private val mEditorFileNameStatus: TextView = binding.editorFileNameStatus
    private val mEditorCursorStatus: TextView = binding.editorCursorStatus
    private val mEditorEncodingStatus: TextView = binding.editorEncodingStatus
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
    private val mDocsPaneController =
        EditorDocumentationPaneController(context, binding.root, mDocsWebView)
    private val mDrawerLayout: DrawerLayout = binding.drawerLayout
    private val mThemePreviewBar: View = binding.themePreviewBar
    private val mThemePreviewTitle: TextView = binding.themePreviewTitle
    private val mThemePreviewIndex: TextView = binding.themePreviewIndex
    private val mThemePreviewList: ImageView = binding.themePreviewList
    private val mThemePreviewFilter: ImageView = binding.themePreviewFilter
    private val mThemePreviewPrevious: ImageView = binding.themePreviewPrevious
    private val mThemePreviewNext: ImageView = binding.themePreviewNext
    private val mThemePreviewExit: ImageView = binding.themePreviewExit
    private val mThemePreviewUse: ImageView = binding.themePreviewUse
    private val mFontPreviewInfo: View = binding.fontPreviewInfo
    private val mFontPreviewInfoType: TextView = binding.fontPreviewInfoType
    private val mFontPreviewInfoSeparator: TextView = binding.fontPreviewInfoSeparator
    private val mFontPreviewInfoSize: TextView = binding.fontPreviewInfoSize
    private val mFontPreviewUnavailableHint: TextView = binding.fontPreviewUnavailableHint
    private val mFontPreviewProgress: ProgressBar = binding.fontPreviewProgress

    private var mCurrentCharsetConfidence: Int = 0
    private var mCurrentCharset: Charset = EditorFileService.DEFAULT_CHARSET_TO_WRITE_FILE
    private var mHadBom = false
    private var mPersistedFileEncoding = EditorFileSaveEncoding(
        charset = EditorFileService.DEFAULT_CHARSET_TO_WRITE_FILE,
        hadBom = false,
    )
    private var mCurrentHadDecodingErrors = false
    private var mCurrentDecodingErrorsWereAccepted = false
    private var mFileStateRequiresReload = false
    @Volatile
    private var mCurrentFileSizeBytes: Long? = null
    @Volatile
    private var mEncodingDialogLoading = false
    @Volatile
    private var mEncodingOperationInProgress = false
    @Volatile
    private var mFileSaveInProgress = false
    @Volatile
    private var mSaveThenReloadInProgress = false
    @Volatile
    private var mSaveAsInProgress = false
    private var mPendingCompletedSaveAs: EditorPendingSaveAsRecord? = null
    private var mSaveAsAdoptionValidationInProgress = false
    private var mSaveAsAdoptionDisposable: Disposable? = null
    private var mDefinitionNavigationDisposable: Disposable? = null
    private var mDefinitionNavigationGeneration = 0L
    private val mProjectRenameTransaction = TypeScriptEditorProjectRenameTransaction()
    private var mProjectRenameDisposable: Disposable? = null
    private var mProjectRenameDialog: MaterialDialog? = null
    private var mProjectRenameCompletion: EditorPluginProjectRenameCallback? = null
    private var mProjectRenameRequestedEditor: ScriptEditorEngine? = null
    private var mProjectRenamePublication: TypeScriptEditorProjectRenameTransaction.Publication? = null
    private var mProjectRenameActiveBufferApplied = false
    private var mProjectRenameGeneration = 0L
    @Volatile
    private var mProjectRenameInProgress = false
    private var mTextRevision = 0L
    @Volatile
    private var mEncodingReloadRollback: EncodingReloadRollback? = null
    private var mEncodingReloadReplacementStarted = false
    private val mEncodingHistoryEntries = LinkedHashMap<String, EncodingHistoryEntry>()
    private var mEncodingOperationGeneration = 0L
    private val mFileLoadGeneration = AtomicLong(0L)
    private var mEncodingDialogDisposable: Disposable? = null
    private var mEncodingOperationDisposable: Disposable? = null
    @Volatile
    private var mDestroyed = false
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
    private var mRunEnabledByIntent = true
    private var mRunEnabledForDocument = true
    private var mAceShortcutReturnSelection: EditorSelection? = null
    private var mAceShortcutLastObservedSelection: EditorSelection? = null
    private var mAceShortcutReturnJumpInProgress = false
    private var mDrawerGestureGuardDepth = 0
    private var mAceShortcutBottomBarDrawerGuardInstalled = false
    private var mEditorUiPaletteChangedListener: ((EditorChromePalette.Colors) -> Unit)? = null
    private val mFullscreenModeSupported = activity is EditActivity
    private var mFullscreenMode = mFullscreenModeSupported &&
        Pref.getBoolean(
            R.string.key_editor_fullscreen_mode,
            R.bool.pref_editor_fullscreen_mode,
        )
    private var mFullscreenHintPending = mFullscreenMode
    private var mFullscreenHintSnackbar: Snackbar? = null
    private var mFullscreenSnackbarBottomProtection: View? = null
    private var mFullscreenSnackbarBottomProtectionOwner: Snackbar? = null
    private var mThemePreviewActive = false
    private var mThemePreviewReturnToThemeDialog = false
    private var mThemePreviewThemes: List<Theme> = emptyList()
    private var mThemePreviewCurrentTheme: Theme? = null
    private var mThemePreviewOriginalTheme: Theme? = null
    private var mThemePreviewFilterMode = ThemePreviewFilter.All
    private var mPreviewMode = PreviewMode.None
    private val mAceFontManager = AceEditorFontManager.get(context)
    private var mFontPreviewActive = false
    private var mFontPreviewReturnToFontDialog = false
    private var mFontPreviewOptions: List<AceEditorFontOption> = emptyList()
    private var mFontPreviewCurrentFontId: String? = null
    private var mFontPreviewOriginalFontId: String? = null
    private var mFontPreviewDownloadId: String? = null
    private var mFontPreviewDownloadSubscription: FontDownloadSubscription? = null
    private var mFontPreviewCatalogRefreshSubscription: AceFontCatalogRefreshSubscription? = null

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
        editorToolbar = binding.toolbar,
        toolbarMenuContainer = binding.toolbarMenu,
        editorStatusBar = mEditorStatusBar,
        editorStatusTextViews = listOf(
            mEditorFileNameStatus,
            mEditorCursorStatus,
            mEditorEncodingStatus,
        ),
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
        onPaletteChanged = { background, foreground ->
            mEditorUiPaletteChangedListener?.invoke(
                EditorChromePalette.Colors(background, foreground),
            )
        },
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
    private var mLastEditorBottomOverlap = 0
    private var mLastImeVisible = false
    private var mLastImeEventAtUptimeMillis = 0L
    private var mLastWindowInsets: WindowInsetsCompat? = null
    private var mStableDecorBottomOnScreen = 0
    private var mStableDecorWidth = 0
    private var mLegacyImeLayoutObserver: ViewTreeObserver? = null
    private val mLegacyImeGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && isAttachedToWindow) {
            (ViewCompat.getRootWindowInsets(this) ?: mLastWindowInsets)
                ?.let(::applyImeInsetsState)
        }
    }
    private val mBaseWindowInsetPadding = intArrayOf(
        paddingLeft,
        paddingTop,
        paddingRight,
        paddingBottom,
    )

    // Reusable rect for window visible area.
    // zh-CN: 复用的窗口可见区域 Rect.
    private val mTmpWindowVisibleRect = Rect()
    private val mTmpStableWindowBounds = Rect()
    private val mTmpLocationOnScreen = IntArray(2)
    private val mTmpDecorLocationOnScreen = IntArray(2)
    private val mMinImeOverlapPx =
        (MIN_IME_OVERLAP_DP * resources.displayMetrics.density + 0.5F).toInt()

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

    private val mEditorDisplayPreferenceKeys = setOf(
        EditorDisplayPreferences.KEY_WORD_WRAP_ENABLED,
        EditorDisplayPreferences.KEY_WORD_WRAP_INDENT_STYLE,
        EditorDisplayPreferences.KEY_LINE_NUMBERS_ENABLED,
        EditorDisplayPreferences.KEY_PRINT_MARGIN_ENABLED,
        EditorDisplayPreferences.KEY_INDENT_GUIDES_ENABLED,
        EditorDisplayPreferences.KEY_BREAKPOINT_MARKERS_ENABLED,
        EditorDisplayPreferences.KEY_FOLD_MARKERS_ENABLED,
        EditorDisplayPreferences.KEY_GUTTER_WIDTH_MODE,
        EditorDisplayPreferences.KEY_FONT_LIGATURES_ENABLED,
        EditorDisplayPreferences.KEY_FONT_STYLES_ENABLED,
        EditorDisplayPreferences.KEY_HIGHLIGHT_INDENT_GUIDES_ENABLED,
        EditorDisplayPreferences.KEY_SVG_GUTTER_ICONS_ENABLED,
        EditorDisplayPreferences.KEY_CUSTOM_SCROLLBAR_ENABLED,
        EditorDisplayPreferences.KEY_SCROLLBAR_AUTO_HIDE_ENABLED,
        EditorDisplayPreferences.KEY_WHITESPACES_IN_SELECTION_ENABLED,
        EditorDisplayPreferences.KEY_TOUCH_MAGNIFIER_ENABLED,
        EditorDisplayPreferences.KEY_SMART_HINT_COMPLETION_TRIGGER,
        EditorDisplayPreferences.KEY_SMART_HINT_COMPLETION_MIN_CHARS,
        EditorDisplayPreferences.KEY_SMART_HINT_COMPLETION_DELAY,
        EditorDisplayPreferences.KEY_SMART_HINT_SIGNATURE_TRIGGER,
        EditorDisplayPreferences.KEY_SMART_HINT_ARROW_OVERLOAD_CYCLING,
        EditorDisplayPreferences.KEY_SMART_HINT_SYMBOL_HIGHLIGHT,
        EditorDisplayPreferences.KEY_SMART_HINT_INLINE_COMPLETION_PREVIEW,
    )

    private val mEditorPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        when (changedKey) {
            in mEditorDisplayPreferenceKeys -> post { editor.notifyDisplayPreferencesChanged() }
            key(R.string.key_editor_text_size) -> post { themeFontService.applyPreferredTextSize() }
            key(R.string.key_ace_editor_font) -> post { themeFontService.applyPreferredAceFont() }
            key(R.string.key_editor_theme),
            key(R.string.key_editor_theme_light),
            key(R.string.key_editor_theme_dark),
            key(R.string.key_editor_theme_mode),
                -> post { applyCurrentEditorThemePreference() }
            key(R.string.key_editor_engine) -> post { applyCurrentEditorEnginePreference() }
            key(R.string.key_editor_fullscreen_mode) -> post {
                if (mFullscreenModeSupported) {
                    val enabled = Pref.getBoolean(
                        R.string.key_editor_fullscreen_mode,
                        R.bool.pref_editor_fullscreen_mode,
                    )
                    setFullscreenMode(enabled, persist = false, showHint = enabled)
                }
            }
            key(R.string.key_editor_pinch_to_zoom_change_text_size_enabled) -> post { editor.notifyPinchToZoomStrategyChanged(null) }
            key(R.string.key_ace_lsp_enabled),
            key(R.string.key_ace_lsp_declaration_groups),
            key(R.string.key_ace_lsp_file_types),
                -> post { (editor as? AceScriptEditorEngine)?.refreshLsp() }
        }
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        mEditorAppBar.isVisible = !mFullscreenMode
        recordAcePluginUnavailableFallbackIfNeeded()
        val immediatelyAvailableTheme = themeFontService.loadCurrentThemeImmediatelyAvailable()
        immediatelyAvailableTheme?.let(::setTheme)
        attachEditorEngineView()
        setUpEditor()
        setUpInputMethodEnhancedBar()
        setUpFunctionsKeyboard()
        setUpEditorPreviewer()
        setUpImeInsetsHandling()
        mEditorFileNameStatus.setOnClickListener {
            if (!uri?.path.isNullOrEmpty()) showFileDetailsDialog()
        }
        mEditorCursorStatus.setOnClickListener { showJumpToLineDialog() }
        mEditorEncodingStatus.setOnClickListener { showEncodingDialog() }
        updateEditorStatusMetadata()
        showCursorStatusPlaceholder()
        updateEncodingStatus()
        setMenuItemStatus(R.id.save, false)
        mDocsWebView.apply {
            webView.settings.displayZoomControls = true
        }
        mDocsPaneController.start(getUrl(INDEX_URL_SUFFIX))
        if (immediatelyAvailableTheme == null) {
            themeFontService.loadCurrentTheme()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { theme: Theme? -> setTheme(theme) }
        }
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
        registerLegacyImeGlobalLayoutListener()
        mDocsPaneController.start(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(mOnRunFinishedReceiver, IntentFilter(ACTION_ON_EXECUTION_FINISHED), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(mOnRunFinishedReceiver, IntentFilter(ACTION_ON_EXECUTION_FINISHED))
        }
        registerFunctionsKeyboardBackHandler()
        Pref.get().registerOnSharedPreferenceChangeListener(mEditorPreferenceChangeListener)
        val previousAceState = mLastAcePluginState
        val latestAceChange = AceEditorPluginStateMonitor.latestChange(context)
        val desiredEngineType = EditorEnginePreferences.reconcile(context)
        mLastAcePluginState = latestAceChange.current
        AceEditorPluginStateMonitor.addListener(context, mAcePluginStateListener)
        when {
            editor.engineType == EditorEngineType.ACE && !latestAceChange.current.available -> post {
                mAceSessionInvalidated = true
                fallbackToNativeFromPluginState(latestAceChange)
            }
            editor.engineType != desiredEngineType -> post {
                applyCurrentEditorEnginePreference()
            }
            editor.engineType == EditorEngineType.ACE &&
                (mAceSessionInvalidated ||
                    (latestAceChange.id > mLastAcePluginChangeId && latestAceChange.requiresSessionReload) ||
                    (previousAceState != null &&
                        previousAceState.packageIdentity != latestAceChange.current.packageIdentity)) -> post {
                reloadAcePluginSession(latestAceChange)
            }
        }
        mLastAcePluginChangeId = latestAceChange.id
        applyFullscreenSystemBars()
    }

    fun handleIntent(intent: Intent): Observable<String> {
        // Initialize menu state early to avoid "first render" flicker.
        // zh-CN: 尽早初始化菜单状态, 避免首次渲染闪烁.
        mEditorLoading = true
        editor.setLoadingText(true)
        showCursorStatusPlaceholder()

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

        mRunEnabledByIntent = intent.getBooleanExtra(EXTRA_RUN_ENABLED, true)
        refreshDocumentRunPolicy()

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
                refreshDocumentRunPolicy()
                if (readOnly) {
                    editor.setReadOnly(true)
                }

                applyInitialPositionIfRequested(intent)
            }
            .doOnError {
                editor.setLoadingText(false)
                mEditorLoading = false
                syncPrimaryMenuState()
                scheduleCursorStatusAfterInitialLoad()
            }
    }

    private fun applyInitialPositionIfRequested(intent: Intent) {
        val lineNumber = intent.getIntExtra(EXTRA_INITIAL_LINE, -1)
        if (lineNumber < 0) {
            return
        }
        val columnNumber = intent.getIntExtra(EXTRA_INITIAL_COLUMN, 0).coerceAtLeast(0)
        mPendingInitialPosition = InitialPositionRequest(lineNumber, columnNumber)
        scheduleCursorStatusAfterInitialLoad()
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
        val encodingValidationWasComplete = mFileEncodingValidationComplete
        if (mEditorLoading || editor.isLoadingText || mLargeFileFrameCallback != null) {
            // A file load may already be appending when a large on-disk draft finishes reading.
            // Stop it before applying the draft so no later frame can mix file chunks into it.
            // zh-CN: 磁盘中的大草稿读取完成时, 文件加载可能已经开始追加文本.
            // 应先停止加载再应用草稿, 防止后续帧把文件分片混入草稿.
            cancelLargeFileLoading()
        }
        mDraftRestoredInThisSession = true
        mDraftEncodingValidationPending = uri != null && !encodingValidationWasComplete
        mRestoredText = text
        editor.text = text
        applyPendingRestoredDraftEncodingStateIfReady()
        updateEncodingStatus()
        markRestoredDraftAsDirty()
        scheduleCursorStatusAfterInitialLoad(preserveCurrentCursor = true)
    }

    private fun applyPendingRestoredDraftEncodingStateIfReady() {
        val state = mPendingRestoredDraftEncodingState ?: return
        if (!mDraftRestoredInThisSession || !mFileEncodingValidationComplete) return
        mPendingRestoredDraftEncodingState = null
        applyEncodingHistoryState(
            state.copy(
                draftEncodingValidationPending = false,
                fileEncodingValidationComplete = true,
            ),
        )
    }

    fun setDraftRestorePending(pending: Boolean) {
        mDraftRestoreInProgress = pending
        if (!mReadOnly) {
            if (pending) {
                editor.setReadOnly(true)
            } else if (!mEditorLoading && !mEncodingOperationInProgress &&
                !mSaveAsInProgress && !mFileSaveInProgress
            ) {
                editor.setReadOnly(false)
            }
        }
        syncPrimaryMenuState()
        if (!pending) {
            tryFinalizeCompletedSaveAsAdoption()
            scheduleCursorStatusAfterInitialLoad(preserveCurrentCursor = true)
        }
    }

    /** Keeps a recreated editor inert while the previous Activity finishes Save As IO. */
    fun setSaveAsRecreationPending(pending: Boolean) {
        mSaveAsInProgress = pending
        if (pending) {
            mLoadingBarText.text = context.getString(R.string.text_saving)
            setEnhanceBarInteractive(false)
            setLoadingBar(true)
            if (!mReadOnly) {
                editor.setReadOnly(true)
            }
        } else {
            setLoadingBar(false)
            setEnhanceBarInteractive(!mReadOnly)
            if (!mReadOnly && !mDraftRestoreInProgress && !mEditorLoading &&
                !mEncodingOperationInProgress && !mFileSaveInProgress
            ) {
                editor.setReadOnly(false)
            }
        }
        syncPrimaryMenuState()
    }

    fun textForDraftSaving(): String =
        mEncodingReloadRollback?.text ?: editor.textSnapshot

    fun shouldSaveDraft(): Boolean = when {
        mEncodingReloadRollback != null -> mEncodingReloadRollback?.saveStickyDirty == true
        mDraftRestoreInProgress -> false
        mEditorLoading && !mDraftRestoredInThisSession -> false
        else -> editor.isTextChanged || saveStickyDirty
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

    fun discardPrivilegedDraftForExit(): Observable<Unit> {
        val currentUri = uri?.takeIf { PrivilegedEditSessions.isSessionUri(context, it) }
            ?: return Observable.just(Unit)
        return Observable.fromCallable { PrivilegedEditSessions.discardDraft(context, currentUri) }
            .subscribeOn(Schedulers.io())
    }

    internal fun isPrivilegedSessionCleanForClose(): Boolean =
        !shouldSaveDraft() && !mFileSaveInProgress && !mSaveAsInProgress &&
            !mSaveThenReloadInProgress && !mEncodingOperationInProgress &&
            !mDraftRestoreInProgress && !mEditorLoading && !editor.isLoadingText

    fun getEditorTheme() = themeFontService.currentTheme

    fun applyCurrentEditorChrome() = themeFontService.applyCurrentEditorChrome()

    fun applyEditorMenuTheme(menu: Menu) = themeFontService.applyMenuColors(menu)

    fun applyToolbarTheme(root: View) = themeFontService.applyToolbarMenuItemColors(root)

    fun setEditorUiPaletteChangedListener(
        listener: ((EditorChromePalette.Colors) -> Unit)?,
    ) {
        mEditorUiPaletteChangedListener = listener
        if (listener != null) {
            themeFontService.currentEditorChromeColors()?.let(listener)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        resetStableDecorBaseline()
        super.onConfigurationChanged(newConfig)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Measure the requested chrome first. Temporarily reclaim its space only when the
        // keyboard leaves too little room for text; keep read-only/debug visibility intact.
        setChromeHeightForMeasure(mInputMethodEnhanceBar, LayoutParams.WRAP_CONTENT)
        setChromeHeightForMeasure(mEditorStatusBar, mExpandedStatusBarHeight)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val minimumViewport = (48 * resources.displayMetrics.density).toInt()
        if (mLastImeVisible && binding.editorContainer.measuredHeight < minimumViewport) {
            setChromeHeightForMeasure(mInputMethodEnhanceBar, 0)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (binding.editorContainer.measuredHeight < minimumViewport) {
                setChromeHeightForMeasure(mEditorStatusBar, 0)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    private fun setChromeHeightForMeasure(view: View, height: Int) {
        if (view.layoutParams.height == height) return
        view.layoutParams.height = height
        // Invalidate the nested layout's measurement cache without scheduling another
        // traversal: the next super.onMeasure call must see the changed child height.
        var child: View? = view
        while (child != null && child !== this) {
            child.forceLayout()
            child = child.parent as? View
        }
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        if (oldWidth > 0 && width > 0 && width != oldWidth) {
            resetStableDecorBaseline()
        }
        super.onSizeChanged(width, height, oldWidth, oldHeight)
    }

    fun currentEditorUiPalette(): EditorChromePalette.Colors =
        themeFontService.currentEditorChromeColors() ?: EditorChromePalette.current()

    fun isFullscreenMode(): Boolean = mFullscreenMode

    fun isAceEditorEngine(): Boolean = editor.engineType == EditorEngineType.ACE

    fun toggleFullscreenMode() {
        if (!mFullscreenModeSupported) {
            return
        }
        setFullscreenMode(
            enabled = !mFullscreenMode,
            persist = true,
            showHint = true,
        )
    }

    fun reapplyFullscreenModeIfNeeded() {
        if (!mFullscreenModeSupported || !mFullscreenMode || !isAttachedToWindow) {
            return
        }
        post {
            if (mFullscreenMode && isAttachedToWindow) {
                applyFullscreenSystemBars()
                showPendingFullscreenHint()
            }
        }
    }

    private fun setFullscreenMode(
        enabled: Boolean,
        persist: Boolean,
        showHint: Boolean,
    ) {
        if (!mFullscreenModeSupported) {
            return
        }
        val changed = mFullscreenMode != enabled
        mFullscreenMode = enabled

        if (
            persist &&
            Pref.getBoolean(
                R.string.key_editor_fullscreen_mode,
                R.bool.pref_editor_fullscreen_mode,
            ) != enabled
        ) {
            Pref.putBoolean(R.string.key_editor_fullscreen_mode, enabled)
        }

        mEditorAppBar.isVisible = !enabled
        mLastWindowInsets?.let { insets ->
            val rootBottomOverlap = calculateImeBottomOverlap(this, insets)
            val imeVisible = isImeEffectivelyVisible(insets, rootBottomOverlap)
            applyEditorSystemBarInsets(
                insets,
                imeBottomOverlap = if (imeVisible) {
                    rootBottomOverlap
                } else {
                    0
                },
            )
        }
        applyFullscreenSystemBars()
        editor.view.requestLayout()
        ViewCompat.requestApplyInsets(this)

        if (!changed) {
            return
        }

        activity.invalidateOptionsMenu()
        if (enabled && showHint) {
            mFullscreenHintPending = true
            post(::showPendingFullscreenHint)
        } else if (!enabled) {
            mFullscreenHintPending = false
            val snackbar = mFullscreenHintSnackbar
            mFullscreenHintSnackbar = null
            snackbar?.dismiss()
            removeFullscreenSnackbarBottomProtection()
        }
    }

    private fun exitFullscreenModeIfNeeded(): Boolean {
        if (!mFullscreenMode) {
            return false
        }
        setFullscreenMode(
            enabled = false,
            persist = true,
            showHint = false,
        )
        return true
    }

    private fun applyFullscreenSystemBars() {
        if (!mFullscreenModeSupported || !isAttachedToWindow) {
            return
        }
        val controller = WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView,
        )
        when {
            mFullscreenMode -> {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            else -> {
                controller.show(WindowInsetsCompat.Type.systemBars())
                themeFontService.applyCurrentEditorChrome()
            }
        }
    }

    private fun showPendingFullscreenHint() {
        if (themeFontService.currentEditorChromeColors() == null) {
            return
        }
        if (
            !mFullscreenHintPending ||
            !mFullscreenMode ||
            !isAttachedToWindow ||
            !activity.hasWindowFocus()
        ) {
            return
        }
        mFullscreenHintPending = false
        mFullscreenHintSnackbar?.dismiss()
        val snackbar = Snackbar
            .make(this, R.string.text_editor_fullscreen_mode_hint, Snackbar.LENGTH_LONG)
            .setAction(R.string.text_exit_fullscreen_mode) {
                setFullscreenMode(
                    enabled = false,
                    persist = true,
                    showHint = false,
                )
            }
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                if (mFullscreenHintSnackbar === transientBottomBar) {
                    mFullscreenHintSnackbar = null
                }
                removeFullscreenSnackbarBottomProtection(transientBottomBar)
            }
        })
        mFullscreenHintSnackbar = snackbar
        applyFullscreenHintPalette(snackbar)
        showFullscreenSnackbarBottomProtection(snackbar)
        snackbar.show()
    }

    private fun applyFullscreenHintPalette(snackbar: Snackbar) {
        val colors = themeFontService.currentEditorChromeColors() ?: return
        snackbar.setBackgroundTint(colors.background)
        snackbar.setTextColor(colors.foreground)
        snackbar.setActionTextColor(colors.foreground)
        if (mFullscreenSnackbarBottomProtectionOwner === snackbar) {
            mFullscreenSnackbarBottomProtection?.setBackgroundColor(colors.background)
        }
    }

    private fun showFullscreenSnackbarBottomProtection(snackbar: Snackbar) {
        if (!mFullscreenMode || !isAttachedToWindow) {
            return
        }
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val protection = mFullscreenSnackbarBottomProtection ?: View(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                Gravity.BOTTOM,
            )
            content.addView(this)
            mFullscreenSnackbarBottomProtection = this
        }
        mFullscreenSnackbarBottomProtectionOwner = snackbar
        themeFontService.currentEditorChromeColors()?.let { colors ->
            protection.setBackgroundColor(colors.background)
        }
        updateFullscreenSnackbarBottomProtection(
            mLastWindowInsets ?: ViewCompat.getRootWindowInsets(this),
        )
    }

    private fun updateFullscreenSnackbarBottomProtection(insets: WindowInsetsCompat?) {
        val protection = mFullscreenSnackbarBottomProtection ?: return
        if (
            !mFullscreenMode ||
            mFullscreenSnackbarBottomProtectionOwner == null ||
            insets == null
        ) {
            protection.visibility = GONE
            return
        }
        val navigationBars = insets
            .getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
        val tappableElements = insets
            .getInsetsIgnoringVisibility(WindowInsetsCompat.Type.tappableElement())
        val mandatoryGestures = insets
            .getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
        val left = maxOf(navigationBars.left, tappableElements.left, mandatoryGestures.left)
        val top = maxOf(navigationBars.top, tappableElements.top, mandatoryGestures.top)
        val right = maxOf(navigationBars.right, tappableElements.right, mandatoryGestures.right)
        val bottom = maxOf(navigationBars.bottom, tappableElements.bottom, mandatoryGestures.bottom)
        val layoutParams = protection.layoutParams as FrameLayout.LayoutParams
        val (width, height, gravity, size) = when {
            bottom > 0 && bottom >= left && bottom >= right && bottom >= top ->
                SystemBarProtectionLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    bottom,
                    Gravity.BOTTOM,
                    bottom,
                )
            right > 0 && right >= left && right >= top ->
                SystemBarProtectionLayout(
                    right,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.END,
                    right,
                )
            left > 0 && left >= top ->
                SystemBarProtectionLayout(
                    left,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.START,
                    left,
                )
            top > 0 ->
                SystemBarProtectionLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    top,
                    Gravity.TOP,
                    top,
                )
            else ->
                SystemBarProtectionLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    Gravity.BOTTOM,
                    0,
                )
        }
        if (
            layoutParams.width != width ||
            layoutParams.height != height ||
            layoutParams.gravity != gravity
        ) {
            layoutParams.width = width
            layoutParams.height = height
            layoutParams.gravity = gravity
            protection.layoutParams = layoutParams
        }
        protection.visibility = if (size > 0) VISIBLE else GONE
    }

    private fun removeFullscreenSnackbarBottomProtection(owner: Snackbar? = null) {
        if (
            owner != null &&
            mFullscreenSnackbarBottomProtectionOwner !== owner
        ) {
            return
        }
        mFullscreenSnackbarBottomProtectionOwner = null
        mFullscreenSnackbarBottomProtection?.let { protection ->
            (protection.parent as? ViewGroup)?.removeView(protection)
        }
        mFullscreenSnackbarBottomProtection = null
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
        syncEditorDocumentPath()

        return when {
            content != null -> Observable.just(content)
            uri != null -> loadUri(uri, mPendingCompletedSaveAs?.encoding?.charset)
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
            applyImeInsetsState(insets)
            // Do not consume insets.
            // zh-CN: 不消费 insets.
            insets
        }
    }

    private fun applyImeInsetsState(insets: WindowInsetsCompat) {
        mLastWindowInsets = insets
        val decorResizeOverlap = calculateDecorResizeBottomOverlap()
        val rootBottomOverlap = calculateImeBottomOverlap(this, insets)
        val imeVisible = isImeEffectivelyVisible(
            insets,
            rootBottomOverlap,
            decorResizeOverlap,
        )
        val rootImeBottomOverlap = if (imeVisible) {
            rootBottomOverlap
        } else {
            0
        }
        applyEditorSystemBarInsets(insets, rootImeBottomOverlap)

        // Compute the real overlap between editor and the visible window area.
        // zh-CN: 计算 editor 与窗口可见区域之间的真实重叠量.
        val targetBottom = calculateImeBottomOverlap(editor.view, insets)
        val editorBottomOverlap = when {
            !imeVisible -> 0
            rootImeBottomOverlap > 0 ->
                (targetBottom - rootImeBottomOverlap).coerceAtLeast(0)
            else -> targetBottom
        }
        val imeBottomInset = if (imeVisible) {
            maxOf(
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
                decorResizeOverlap,
                rootImeBottomOverlap,
                targetBottom,
            )
        } else {
            0
        }

        val imeStateChanged = imeBottomInset != mLastImeBottomInset ||
            editorBottomOverlap != mLastEditorBottomOverlap ||
            imeVisible != mLastImeVisible

        if (imeStateChanged) {
            val imeJustHidden = mLastImeVisible && !imeVisible
            mLastImeBottomInset = imeBottomInset
            mLastEditorBottomOverlap = editorBottomOverlap
            mLastImeVisible = imeVisible
            mLastImeEventAtUptimeMillis = SystemClock.uptimeMillis()

            mViewportPolicy.onImeInsetsChanged(
                visible = imeVisible,
                bottomInset = imeBottomInset,
                editorBottomOverlap = editorBottomOverlap,
            )
            if (imeJustHidden && mFullscreenMode) {
                postOnAnimation {
                    if (mFullscreenMode && !mLastImeVisible && isAttachedToWindow) {
                        applyFullscreenSystemBars()
                    }
                }
            }
        }
        if (!imeVisible) {
            updateStableDecorBottomOnScreen()
        }
    }

    private fun isImeEffectivelyVisible(
        insets: WindowInsetsCompat,
        rootBottomOverlap: Int,
        decorResizeOverlap: Int = calculateDecorResizeBottomOverlap(),
    ): Boolean {
        if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
            return true
        }
        val hiddenNavigationBottom = insets
            .getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
            .bottom
        return maxOf(
            rootBottomOverlap - hiddenNavigationBottom,
            decorResizeOverlap,
        ) >= mMinImeOverlapPx
    }

    private fun registerLegacyImeGlobalLayoutListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return
        }
        val observer = viewTreeObserver
        if (mLegacyImeLayoutObserver === observer && observer.isAlive) {
            return
        }
        unregisterLegacyImeGlobalLayoutListener()
        if (observer.isAlive) {
            observer.addOnGlobalLayoutListener(mLegacyImeGlobalLayoutListener)
            mLegacyImeLayoutObserver = observer
        }
    }

    private fun unregisterLegacyImeGlobalLayoutListener() {
        mLegacyImeLayoutObserver
            ?.takeIf { it.isAlive }
            ?.removeOnGlobalLayoutListener(mLegacyImeGlobalLayoutListener)
        mLegacyImeLayoutObserver = null
    }

    private fun calculateVisibleWindowBottomOverlap(view: View): Int {
        val hostActivity = findHostActivityOrNull() ?: return 0
        hostActivity.window.decorView.getWindowVisibleDisplayFrame(mTmpWindowVisibleRect)
        view.getLocationOnScreen(mTmpLocationOnScreen)
        val viewBottomOnScreen = mTmpLocationOnScreen[1] + view.height
        return (viewBottomOnScreen - mTmpWindowVisibleRect.bottom).coerceAtLeast(0)
    }

    private fun calculateDecorBottomOnScreen(): Int {
        val decor = findHostActivityOrNull()?.window?.decorView ?: return 0
        val decorWidth = decor.width
        if (
            decorWidth > 0 &&
            mStableDecorWidth > 0 &&
            decorWidth != mStableDecorWidth
        ) {
            resetStableDecorBaseline()
        }
        if (decorWidth > 0) {
            mStableDecorWidth = decorWidth
        }
        decor.getLocationOnScreen(mTmpDecorLocationOnScreen)
        return mTmpDecorLocationOnScreen[1] + decor.height
    }

    private fun calculateDecorResizeBottomOverlap(): Int {
        val currentDecorBottomOnScreen = calculateDecorBottomOnScreen()
        val stableDecorBottomOnScreen = mStableDecorBottomOnScreen
        if (
            stableDecorBottomOnScreen <= 0 ||
            currentDecorBottomOnScreen <= 0
        ) {
            return 0
        }
        return (stableDecorBottomOnScreen - currentDecorBottomOnScreen)
            .coerceAtLeast(0)
    }

    private fun updateStableDecorBottomOnScreen() {
        calculateDecorBottomOnScreen()
            .takeIf { it > 0 }
            ?.let { mStableDecorBottomOnScreen = it }
    }

    private fun resetStableDecorBaseline() {
        mStableDecorBottomOnScreen = calculateUnresizedWindowBottomOnScreen()
        mStableDecorWidth = 0
    }

    @Suppress("DEPRECATION")
    private fun calculateUnresizedWindowBottomOnScreen(): Int {
        val hostActivity = findHostActivityOrNull() ?: return 0
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                hostActivity.windowManager.currentWindowMetrics.bounds.bottom
            else -> {
                hostActivity.windowManager.defaultDisplay
                    .getRectSize(mTmpStableWindowBounds)
                mTmpStableWindowBounds.bottom
            }
        }
    }

    /**
     * Computes the portion of [view] that is geometrically covered by the IME.
     *
     * Some edge-to-edge WebView/OEM combinations keep the visible display frame at full height
     * while still publishing a correct IME inset. Older releases do the opposite. Combining the
     * raw-inset geometry with the legacy visible frame covers both without double-padding a view
     * that has already been resized above the keyboard.
     */
    private fun calculateImeBottomOverlap(
        view: View,
        insets: WindowInsetsCompat,
    ): Int {
        val legacyOverlap = calculateVisibleWindowBottomOverlap(view)
        val rawImeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        if (rawImeBottom <= 0) {
            return legacyOverlap
        }
        view.getLocationOnScreen(mTmpLocationOnScreen)
        val decorBottomOnScreen = calculateDecorBottomOnScreen()
        val stableWindowBottomOnScreen = mStableDecorBottomOnScreen
            .takeIf { it > 0 }
            ?: decorBottomOnScreen
        val imeTopOnScreen = stableWindowBottomOnScreen - rawImeBottom
        val viewBottomOnScreen = mTmpLocationOnScreen[1] + view.height
        val geometryOverlap = (viewBottomOnScreen - imeTopOnScreen)
            .coerceIn(0, rawImeBottom)
        return maxOf(legacyOverlap, geometryOverlap)
    }

    private fun applyEditorSystemBarInsets(
        insets: WindowInsetsCompat,
        imeBottomOverlap: Int,
    ) {
        val insetTypes = when {
            mFullscreenMode -> WindowInsetsCompat.Type.displayCutout()
            else -> WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
        }
        val systemBarInsets = insets.getInsets(insetTypes)
        val left = mBaseWindowInsetPadding[0] + systemBarInsets.left
        val top = mBaseWindowInsetPadding[1] + systemBarInsets.top
        val right = mBaseWindowInsetPadding[2] + systemBarInsets.right
        val bottom = mBaseWindowInsetPadding[3] +
            maxOf(systemBarInsets.bottom, imeBottomOverlap)
        if (
            paddingLeft != left ||
            paddingTop != top ||
            paddingRight != right ||
            paddingBottom != bottom
        ) {
            setPadding(left, top, right, bottom)
        }
        updateFullscreenSnackbarBottomProtection(insets)
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

    private fun scheduleCursorStatusAfterInitialLoad(preserveCurrentCursor: Boolean = false) {
        post {
            publishCursorStatusAfterInitialLoadIfReady(preserveCurrentCursor)
        }
    }

    private fun publishCursorStatusAfterInitialLoadIfReady(preserveCurrentCursor: Boolean) {
        if (
            mDestroyed ||
            mEditorLoading ||
            mDraftRestoreInProgress ||
            !editor.isReady ||
            editor.isLoadingText ||
            mLargeFileFrameCallback != null ||
            (mCursorStatusReady && mPendingInitialPosition == null && mPendingRestoredSelection == null)
        ) {
            return
        }

        // Restore after both asynchronous document/draft loading and engine readiness.
        // A restored range takes precedence over the original launch location, which may
        // have been consumed long before this Activity was recreated.
        val restoredSelection = mPendingRestoredSelection
        if (restoredSelection != null) {
            val length = editor.textSnapshot.length
            editor.selectRange(
                restoredSelection.first.coerceIn(0, length),
                restoredSelection.second.coerceIn(0, length),
            )
            mPendingRestoredSelection = null
            mPendingInitialPosition = null
        }
        val requestedPosition = mPendingInitialPosition
        val appliedInitialPosition = applyPendingInitialPositionIfPossible(clearOutOfRange = true)
        val finalPosition = when {
            appliedInitialPosition && requestedPosition != null ->
                initialPositionForStatus(requestedPosition)
            preserveCurrentCursor || restoredSelection != null -> editor.selection.let { it.endLine to it.endColumn }
            else -> {
                runCatching { editor.jumpToStart() }
                0 to 0
            }
        }

        // Request focus without forcing the soft keyboard, then publish one stable position.
        // zh-CN: 请求焦点但不强制弹出软键盘, 随后只发布一次稳定的坐标.
        if (!appliedInitialPosition) {
            editor.requestEditorFocus()
        }
        mCursorStatusReady = true
        updateCursorStatus(finalPosition.first, finalPosition.second)
        ensureCursorVisibleIfPossible()
    }

    private fun initialPositionForStatus(request: InitialPositionRequest): Pair<Int, Int> {
        val selection = editor.selection
        val lineLength = editor.textSnapshot
            .lineSequence()
            .drop(request.lineNumber)
            .firstOrNull()
            ?.length
        val reportedColumn = if (selection.endLine == request.lineNumber) {
            selection.endColumn
        } else {
            request.columnNumber
        }
        return request.lineNumber to reportedColumn.coerceAtMost(lineLength ?: reportedColumn)
    }

    private fun applyPendingInitialPositionIfPossible(clearOutOfRange: Boolean): Boolean {
        val request = mPendingInitialPosition ?: return false
        if (editor.textSnapshot.isEmpty() && !clearOutOfRange) {
            return false
        }
        if (request.lineNumber !in 0 until effectiveInitialPositionLineCount()) {
            if (clearOutOfRange) {
                mPendingInitialPosition = null
            }
            return false
        }
        return runCatching {
            editor.jumpTo(request.lineNumber, request.columnNumber)
            editor.requestEditorFocus()
            mPendingInitialPosition = null
            true
        }.getOrDefault(false)
    }

    private fun effectiveInitialPositionLineCount(): Int {
        val textLineCount = editor.textSnapshot.count { it == '\n' } + 1
        return maxOf(editor.lineCountSnapshot, textLineCount)
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
        if (!interactive && mReadOnly && !mEditorLoading && !mLoadingBarRequested) {
            activeContent.visibility = GONE
            mInputMethodEnhanceBar.visibility = GONE
            return
        }
        mInputMethodEnhanceBar.visibility = VISIBLE
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
        if (!mWasReadOnlyBeforeLoading && !mDraftRestoreInProgress) {
            editor.setReadOnly(false)
        }
    }

    private fun loadUri(uri: Uri, charsetOverride: Charset? = null): Observable<String> {
        val loadGeneration = mFileLoadGeneration.incrementAndGet()
        val reloadToken = if (charsetOverride != null) mEncodingReloadRollback else null
        mFileEncodingValidationComplete = false
        mDraftEncodingValidationPending = charsetOverride == null && mDraftRestoredInThisSession
        return Observable.fromCallable {
            ContentLengthResult(
                sizeOrNull = runCatching { fileService.queryContentLengthOrNull(uri) }.getOrNull(),
                displayName = runCatching { fileService.queryDisplayNameOrNull(uri) }.getOrNull(),
                privilegedDraftNeedsSave = PrivilegedEditSessions.isSessionUri(context, uri) &&
                    PrivilegedEditSessions.hasPendingDraft(context, uri),
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .flatMap { result ->
                if (!isCurrentFileLoad(loadGeneration)) {
                    Observable.empty()
                } else {
                    mPrivilegedDraftNeedsSave = result.privilegedDraftNeedsSave
                    mCurrentFileSizeBytes = result.sizeOrNull
                    result.displayName?.let { displayName ->
                        if (this.uri == uri) {
                            if (mDocumentDisplayName != displayName) {
                                mDocumentIdentityGeneration++
                            }
                            mDocumentDisplayName = displayName
                            syncEditorDocumentPath()
                            updateEditorStatusMetadata()
                            refreshDocumentRunPolicy()
                        }
                    }
                    loadUriWithKnownSize(
                        uri = uri,
                        charsetOverride = charsetOverride,
                        reloadToken = reloadToken,
                        loadGeneration = loadGeneration,
                        sizeOrNull = result.sizeOrNull,
                    )
                }
            }
            .doOnSubscribe {
                if (isCurrentFileLoad(loadGeneration)) {
                    beginEditorLoadingUi(context.getString(R.string.text_loading_with_dots))
                    mEditorLoading = true
                    syncPrimaryMenuState()
                }
            }
            .onErrorResumeNext { error: Throwable ->
                if (isCurrentFileLoad(loadGeneration)) {
                    Observable.error(error)
                } else {
                    Observable.empty()
                }
            }
            .doOnError {
                if (mDestroyed || !isCurrentFileLoad(loadGeneration)) return@doOnError
                editor.setLoadingText(false)
                endEditorLoadingUi()
                mEditorLoading = false
                updateEncodingStatus()
                syncPrimaryMenuState()
                scheduleCursorStatusAfterInitialLoad()
            }
    }

    private fun loadUriWithKnownSize(
        uri: Uri,
        charsetOverride: Charset?,
        reloadToken: EncodingReloadRollback?,
        loadGeneration: Long,
        sizeOrNull: Long?,
    ): Observable<String> {
        if (mSwitchingEditorEngine) {
            return Observable.timer(
                EDITOR_ENGINE_SWITCH_LOAD_RETRY_DELAY_MS,
                TimeUnit.MILLISECONDS,
                AndroidSchedulers.mainThread(),
            )
                .filter { isCurrentFileLoad(loadGeneration) }
                .flatMap {
                    loadUriWithKnownSize(
                        uri = uri,
                        charsetOverride = charsetOverride,
                        reloadToken = reloadToken,
                        loadGeneration = loadGeneration,
                        sizeOrNull = sizeOrNull,
                    )
                }
        }
        val shouldStreamAsLargeFile = EditorTextLoadPolicy.shouldStreamAsLargeFile(sizeOrNull)
        if (EditorTextLoadPolicy.shouldRouteAceToNative(editor.engineType, shouldStreamAsLargeFile, shouldForceLargeFileNativeRoute())) {
            if (!ensureNativeEditorForLargeFile()) {
                return Observable.error(IllegalStateException(context.getString(R.string.error_ace_editor_unavailable)))
            }
        }

        if (shouldStreamAsLargeFile && editor.engineType == EditorEngineType.NATIVE) {
            return loadUriStreamed(uri, charsetOverride, reloadToken, loadGeneration)
        }

        return Observable
            .fromCallable {
                fileService.readText(uri, charsetOverride)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .filter { isCurrentFileLoad(loadGeneration) }
            .doOnNext { result ->
                post {
                    if (mDestroyed || !isCurrentFileLoad(loadGeneration)) return@post
                    if (reloadToken != null && mEncodingReloadRollback !== reloadToken) {
                        return@post
                    }
                    runCatching {
                        mFileEncodingValidationComplete = true
                        applyReadMetadataOnMain(result.metadata)
                        setInitialText(
                            text = result.text,
                            endLoadingUiWhenFinished = true,
                            loadGeneration = loadGeneration,
                        )
                    }.onFailure {
                        editor.setLoadingText(false)
                        endEditorLoadingUi()
                        mEditorLoading = false
                        syncPrimaryMenuState()
                        scheduleCursorStatusAfterInitialLoad()
                        if (!rollbackEncodingReload(it)) {
                            showEncodingOperationError(it.message ?: it.toString())
                        }
                    }
                }
            }
            .map { it.text }
    }

    private fun isCurrentFileLoad(loadGeneration: Long): Boolean =
        mFileLoadGeneration.get() == loadGeneration

    private fun ensureNativeEditorForLargeFile(): Boolean {
        if (editor.engineType != EditorEngineType.ACE) {
            return true
        }

        val switched = switchEditorEngine(
            targetType = EditorEngineType.NATIVE,
            oldEditor = editor,
            latestText = editor.textSnapshot,
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

    private fun shouldRetainNativeEditorForLargeFile(): Boolean =
        mLargeFileMode || AceEditorTestHooks.shouldForceLargeFileNativeRoute()

    override fun onDetachedFromWindow() {
        // Cancel loading before unregistering receivers to avoid ANR on exit.
        // zh-CN: 退出前先取消加载, 避免返回退出时 ANR.
        cancelLargeFileLoading()
        unregisterLegacyImeGlobalLayoutListener()

        super.onDetachedFromWindow()
        context.unregisterReceiver(mOnRunFinishedReceiver)
        unregisterFunctionsKeyboardBackHandler()
        Pref.get().unregisterOnSharedPreferenceChangeListener(mEditorPreferenceChangeListener)
        AceEditorPluginStateMonitor.removeListener(mAcePluginStateListener)
        mDocsPaneController.stop()
        mFullscreenHintSnackbar?.dismiss()
        mFullscreenHintSnackbar = null
        removeFullscreenSnackbarBottomProtection()
    }

    fun getPreferredSearchQueryForDialogOrNull(): String? =
        (mCurrentSearchQuery ?: mLastSearchQuery)

    fun getPreferredSearchUsingRegexForDialog(): Boolean =
        (if (mInSearchMode) mCurrentSearchUsingRegex else mLastSearchUsingRegex)

    // Cancel current large-file loading as soon as possible.
    // zh-CN: 尽快取消当前大文件加载.
    fun cancelLargeFileLoading() {
        // Invalidate every read path, including a non-streaming read that cannot be interrupted
        // once ContentResolver IO has started. Its eventual callback must not mutate editor state.
        // zh-CN: 使所有读取路径失效, 包括 ContentResolver IO 开始后无法中断的非流式读取.
        // 它稍后的回调不得再修改编辑器状态.
        mFileLoadGeneration.incrementAndGet()

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

        mEncodingDialogDisposable?.dispose()
        mEncodingDialogDisposable = null
        mEncodingDialogLoading = false

        cancelActiveEditorTextLoad()

        // Reload owns a complete editor snapshot. Restore it synchronously before the host checks
        // dirty state or offers Save and exit, so a partial decode can never be persisted.
        // zh-CN: Reload 持有完整的编辑器快照. 必须在宿主检查脏状态或提供保存退出前同步恢复,
        // 确保部分解码内容永远不会被写回文件.
        if (mEncodingReloadRollback != null) {
            mEncodingOperationDisposable?.dispose()
            mEncodingOperationDisposable = null
            rollbackEncodingReload(
                error = IllegalStateException("Encoding reload cancelled"),
                showError = false,
            )
            return
        }

        // Convert is a transactional write and cannot be interrupted safely once it starts. Keep
        // its read-only/loading lock until its own completion callback runs.
        // zh-CN: Convert 是事务式写入, 开始后无法安全中断. 在其完成回调执行前保持只读和加载锁.
        if (mEncodingOperationInProgress) {
            return
        }

        // Cleanup UI state best-effort (do NOT re-enable editing in read-only editor).
        // zh-CN: 尽力清理 UI 状态 (只读编辑器不要被重新启用编辑).
        runCatching { endEditorLoadingUi() }
        mEditorLoading = false
        updateEncodingStatus()
        syncPrimaryMenuState()
    }

    private fun cancelActiveEditorTextLoad() {
        if (!editor.isLoadingText) {
            mProgressiveTextLoadActive = false
            return
        }
        runCatching {
            if (mProgressiveTextLoadActive) {
                textLoadTarget.cancelProgressiveTextLoad()
            } else {
                textLoadTarget.cancelStreamedLargeTextLoad()
            }
        }
        mProgressiveTextLoadActive = false
    }

    private fun loadUriStreamed(
        uri: Uri,
        charsetOverride: Charset? = null,
        reloadToken: EncodingReloadRollback? = null,
        loadGeneration: Long,
    ): Observable<String> {
        // Fully stop an older producer/frame loop before reusing the shared cancel flag.
        // zh-CN: 复用共享取消标记前, 完整停止上一个生产者和帧循环.
        mLargeFileCancel.set(true)
        mLargeFileFrameCallback?.let { callback ->
            runCatching { Choreographer.getInstance().removeFrameCallback(callback) }
        }
        mLargeFileFrameCallback = null
        runCatching { mLargeFileIoDisposable?.dispose() }
        mLargeFileIoDisposable = null
        mLargeFileStreamRef.getAndSet(null)?.let { stream -> runCatching { stream.close() } }
        cancelActiveEditorTextLoad()

        // Reset cancel state for a new session.
        // zh-CN: 新的加载会话开始前重置取消标记.
        mLargeFileCancel.set(false)
        val pendingMetadata = AtomicReference<EditorFileReadMetadata?>(null)
        val preserveExistingText = mDraftRestoredInThisSession

        return fileService
            .streamTextChunks(
                uri = uri,
                cancel = mLargeFileCancel,
                streamRef = mLargeFileStreamRef,
                // Metadata is produced on an IO thread. Keep it immutable here and apply it from
                // the frame callback, where the reload token check and all editor-state mutations
                // happen together on the main thread.
                // zh-CN: 元数据由 IO 线程生成. 此处仅保存不可变快照, 再由帧回调在主线程内
                // 同时完成 Reload 令牌校验和编辑器状态修改.
                onMetadata = pendingMetadata::set,
                charsetOverride = charsetOverride,
            )
            .subscribeOn(Schedulers.io())
            .let { upstream ->
                bridgeStreamToUiWithProgress(
                    upstream = upstream,
                    pendingMetadata = pendingMetadata,
                    reloadToken = reloadToken,
                    loadGeneration = loadGeneration,
                    preserveExistingText = preserveExistingText,
                )
            }
    }

    private fun applyReadMetadataOnMain(metadata: EditorFileReadMetadata) {
        mCurrentCharsetConfidence = metadata.charsetConfidence
        mCurrentCharset = metadata.charset
        mHadBom = metadata.hadBom
        mPersistedFileEncoding = EditorFileSaveEncoding(
            charset = metadata.charset,
            hadBom = metadata.hadBom,
        )
        mCurrentHadDecodingErrors = metadata.hadDecodingErrors
        mCurrentDecodingErrorsWereAccepted = metadata.decodingErrorsWereAccepted
        updateEncodingStatus()
    }

    private fun applyPendingStreamMetadataOnMain(
        pendingMetadata: AtomicReference<EditorFileReadMetadata?>,
        reloadToken: EncodingReloadRollback?,
        loadGeneration: Long,
    ) {
        val metadata = pendingMetadata.getAndSet(null) ?: return
        if (!isCurrentFileLoad(loadGeneration)) return
        if (reloadToken != null && mEncodingReloadRollback !== reloadToken) return
        applyReadMetadataOnMain(metadata)
    }

    private fun updateEncodingStatus() {
        val label = buildString {
            append(mCurrentCharset.name())
            if (mHadBom) append(" BOM")
        }
        mEditorEncodingStatus.text = label
        val showWarning = mCurrentHadDecodingErrors || mDraftEncodingValidationPending
        if (mFileStateRequiresReload || showWarning) {
            val iconSize = (16f * resources.displayMetrics.density).toInt()
            val riskIcon = context.getDrawable(
                if (mFileStateRequiresReload) {
                    R.drawable.ic_error_black_48dp
                } else {
                    R.drawable.ic_warning_black_48dp
                },
            )?.mutate()?.apply {
                setBounds(0, 0, iconSize, iconSize)
            }
            mEditorEncodingStatus.setCompoundDrawablesRelative(riskIcon, null, null, null)
            mEditorEncodingStatus.compoundDrawableTintList = ColorStateList.valueOf(
                context.getColor(
                    if (mFileStateRequiresReload) {
                        R.color.editor_encoding_risk_error
                    } else {
                        R.color.editor_encoding_risk_warning
                    },
                ),
            )
            mEditorEncodingStatus.compoundDrawablePadding =
                (4f * resources.displayMetrics.density).toInt()
        } else {
            mEditorEncodingStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            mEditorEncodingStatus.compoundDrawableTintList = null
        }
        mEditorEncodingStatus.contentDescription = buildString {
            append(context.getString(R.string.text_file_encoding_status, label))
            if (mFileStateRequiresReload) {
                append(". ")
                append(context.getString(R.string.error_file_state_unknown_reload_required))
            } else if (mCurrentHadDecodingErrors) {
                append(". ")
                append(context.getString(R.string.text_encoding_cannot_decode_file))
            } else if (mDraftEncodingValidationPending) {
                append(". ")
                append(context.getString(R.string.error_editor_encoding_validation_incomplete))
            }
        }
    }

    fun canChangeFileEncoding(): Boolean =
        uri != null && editor.isReady && !mEditorLoading && !mSwitchingEditorEngine &&
            !editor.isLoadingText && mLargeFileFrameCallback == null &&
            !mEncodingDialogLoading && !mEncodingOperationInProgress && !mFileSaveInProgress &&
            !mSaveAsInProgress && !mDraftRestoreInProgress

    private fun bridgeStreamToUiWithProgress(
        upstream: Observable<String>,
        pendingMetadata: AtomicReference<EditorFileReadMetadata?>,
        reloadToken: EncodingReloadRollback?,
        loadGeneration: Long,
        preserveExistingText: Boolean,
    ): Observable<String> {
        // Bound decoded text waiting for the UI. This keeps a fast disk/decoder producer from
        // materializing an entire large file in an unbounded queue.
        // zh-CN: 限制等待 UI 消费的已解码文本, 避免磁盘/解码速度较快时把整个大文件堆积到内存.
        val queue: BlockingQueue<String> = LinkedBlockingQueue(STREAM_UI_QUEUE_CAPACITY)
        val done = AtomicBoolean(false)
        val started = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)

        // Dispose previous session if any.
        // zh-CN: 若存在上一会话, 则先取消.
        runCatching { mLargeFileIoDisposable?.dispose() }
        mLargeFileIoDisposable = null

        // Subscribe on IO side, push chunks into queue.
        // zh-CN: 在 IO 侧订阅, 将分片推入队列.
        mLargeFileIoDisposable = upstream
            .subscribe({ chunk ->
                if (mLargeFileCancel.get() || !isCurrentFileLoad(loadGeneration)) {
                    // Stop producing.
                    // zh-CN: 停止生产.
                    done.set(true)
                    return@subscribe
                }
                if (!preserveExistingText && chunk.isNotEmpty()) {
                    while (!mLargeFileCancel.get() && isCurrentFileLoad(loadGeneration)) {
                        val enqueued = try {
                            queue.offer(chunk, STREAM_UI_QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                        if (enqueued) break
                        // Re-check cancellation between bounded waits.
                        // zh-CN: 在有界等待之间重新检查取消状态.
                    }
                }
            }, { e ->
                failure.compareAndSet(null, e)
                queue.clear()
                done.set(true)
                if (!mLargeFileCancel.get()) e.printStackTrace()
            }, {
                done.set(true)
            })

        return Observable
            .just("")
            .observeOn(AndroidSchedulers.mainThread())
            .filter { isCurrentFileLoad(loadGeneration) }
            .doOnNext {
                if (started.compareAndSet(false, true)) {
                    if (!isCurrentFileLoad(loadGeneration)) return@doOnNext
                    startStreamingAppendWithProgress(
                        queue = queue,
                        done = done,
                        failure = failure,
                        pendingMetadata = pendingMetadata,
                        reloadToken = reloadToken,
                        loadGeneration = loadGeneration,
                        preserveExistingText = preserveExistingText,
                    )
                }
            }
    }

    private fun startStreamingAppendWithProgress(
        queue: BlockingQueue<String>,
        done: AtomicBoolean,
        failure: AtomicReference<Throwable?>,
        pendingMetadata: AtomicReference<EditorFileReadMetadata?>,
        reloadToken: EncodingReloadRollback?,
        loadGeneration: Long,
        preserveExistingText: Boolean,
    ) {
        mProgressiveTextLoadActive = false
        if (!preserveExistingText) {
            mLargeFileMode = true
        }
        mEditorLoading = true
        mUserMovedCursorDuringLoading = false
        syncPrimaryMenuState()

        val loadTarget = textLoadTarget
        if (!preserveExistingText) {
            loadTarget.beginStreamedLargeTextLoad()
        }

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
                if (!isCurrentFileLoad(loadGeneration)) {
                    return
                }
                if (mLargeFileCancel.get()) {
                    // Cancel: stop immediately and do not schedule next frame.
                    // zh-CN: 取消: 立即停止且不再调度下一帧.
                    if (!preserveExistingText) {
                        loadTarget.cancelStreamedLargeTextLoad()
                    }
                    mEditorLoading = false
                    syncPrimaryMenuState()
                    endEditorLoadingUi()
                    mLargeFileFrameCallback = null
                    scheduleCursorStatusAfterInitialLoad(preserveCurrentCursor = true)
                    return
                }

                applyPendingStreamMetadataOnMain(pendingMetadata, reloadToken, loadGeneration)

                val finished = done.get() && queue.isEmpty()
                if (finished) {
                    failure.get()?.let { error ->
                        if (!preserveExistingText) {
                            loadTarget.cancelStreamedLargeTextLoad()
                        }
                        mEditorLoading = false
                        endEditorLoadingUi()
                        if (!rollbackEncodingReload(error)) {
                            mLargeFileMode = false
                            syncPrimaryMenuState()
                            showEncodingOperationError(error.message ?: error.toString())
                        }
                        mLargeFileFrameCallback = null
                        scheduleCursorStatusAfterInitialLoad(preserveCurrentCursor = true)
                        return
                    }
                    // Drain the final metadata update (for example, a malformed sequence found at
                    // EOF) before completing this load session.
                    // zh-CN: 完成加载会话前应用最后一次元数据更新, 例如在 EOF 处发现的坏序列.
                    applyPendingStreamMetadataOnMain(pendingMetadata, reloadToken, loadGeneration)
                    if (!preserveExistingText) {
                        loadTarget.finishStreamedLargeTextLoad(markClean = true)
                        saveStickyDirty = false
                        mHadDirectEditSinceSave = false
                    }
                    mDraftEncodingValidationPending = false
                    mFileEncodingValidationComplete = true
                    applyPendingRestoredDraftEncodingStateIfReady()
                    updateEncodingStatus()

                    mEditorLoading = false
                    preservePendingPrivilegedDraft()
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
                    val textLength = editor.textSnapshot.length
                    post {
                        if (!isCurrentFileLoad(loadGeneration)) return@post
                        publishCursorStatusAfterInitialLoadIfReady(
                            preserveCurrentCursor = preserveExistingText || mUserMovedCursorDuringLoading,
                        )
                        if (Pref.isCodeHighlightEnabledForLength(textLength)) {
                            editor.refreshHighlightTokensIfAllowed()
                        }
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

    private fun setInitialText(
        text: String,
        endLoadingUiWhenFinished: Boolean = false,
        loadGeneration: Long? = null,
    ) {
        if (loadGeneration != null && !isCurrentFileLoad(loadGeneration)) return
        // A baseline load resets the engine history, so tokens owned by the previous history
        // must not be allowed to mutate this document's encoding metadata.
        // zh-CN: 基线加载会重置引擎历史, 因此旧历史拥有的令牌不能再修改当前文档的编码元数据.
        if (mEncodingReloadRollback == null) {
            mEncodingHistoryEntries.clear()
        }
        mEditorLoading = true
        syncPrimaryMenuState()

        // If draft has been restored in this session, do NOT overwrite it with async file load results.
        // Still end loading flags and sync menu state to keep UI consistent.
        //
        // zh-CN:
        // 若本会话已恢复草稿, 则不要用异步文件加载结果覆盖它.
        // 但仍需结束 loading 标记并同步菜单状态, 保持 UI 一致.
        if (mDraftRestoredInThisSession) {
            mDraftEncodingValidationPending = false
            applyPendingRestoredDraftEncodingStateIfReady()
            updateEncodingStatus()
            finishInitialTextLoad(
                endLoadingUi = endLoadingUiWhenFinished,
                preserveCurrentCursor = true,
            )
            return
        }

        if (mRestoredText != null) {
            textLoadTarget.loadRestoredText(mRestoredText!!, undoRedoEnabled = !mLargeFileMode)
            mRestoredText = null

            // Reset sticky dirty because we just established a new baseline.
            // zh-CN: 因刚刚建立了新的基线, 重置 sticky 脏标记.
            saveStickyDirty = false
            mHadDirectEditSinceSave = false

            finishInitialTextLoad(
                endLoadingUi = endLoadingUiWhenFinished,
                preserveCurrentCursor = true,
            )

            // Refresh highlight for restored text if size allows.
            // zh-CN: 若大小允许, 则对恢复文本刷新一次高亮.
            post { editor.refreshHighlightTokensIfAllowed() }

            return
        }

        val shouldLoadProgressively = EditorTextLoadPolicy.shouldLoadProgressively(text.length)
        if (EditorTextLoadPolicy.shouldRouteAceToNative(editor.engineType, shouldLoadProgressively, shouldForceLargeFileNativeRoute())) {
            if (!ensureNativeEditorForLargeFile()) {
                finishInitialTextLoad(endLoadingUiWhenFinished)
                return
            }
        }

        if (shouldLoadProgressively && editor.engineType == EditorEngineType.NATIVE) {
            mLargeFileMode = true
            setInitialTextProgressively(text, endLoadingUiWhenFinished, loadGeneration)
            return
        }

        textLoadTarget.loadSmallText(text)

        // Reset sticky dirty because we just established a new baseline.
        // zh-CN: 因刚刚建立了新的基线, 重置 sticky 脏标记.
        saveStickyDirty = false
        mHadDirectEditSinceSave = false
        finishInitialTextLoad(endLoadingUiWhenFinished)

        // Re-highlight once after fast load, but only when size is within highlighter limit.
        // zh-CN: 快速加载完成后主动触发一次高亮, 但仅在文本大小不超过高亮上限时执行.
        if (Pref.isCodeHighlightEnabledForLength(text.length)) {
            post { editor.refreshHighlightTokensIfAllowed() }
        }
    }

    internal fun syncPrimaryMenuState() {
        // Unified primary buttons state.
        // zh-CN: 统一主按钮状态.
        when {
            mEditorLoading || mEncodingOperationInProgress || mFileSaveInProgress ||
                mSaveThenReloadInProgress || mProjectRenameInProgress ||
                mSaveAsInProgress || mDraftRestoreInProgress -> {
                setMenuItemStatus(R.id.run, false)
                setMenuItemStatus(R.id.undo, false)
                setMenuItemStatus(R.id.redo, false)
                setMenuItemStatus(R.id.save, false)
            }
            mLargeFileMode -> {
                // Large file: keep undo/redo disabled; save is enabled only when text changed by explicit edits.
                // zh-CN: 大文件: 撤销/重做保持禁用; 保存仅在明确编辑导致文本变化时启用.
                setMenuItemStatus(R.id.run, isRunEnabledForCurrentDocument())
                setMenuItemStatus(R.id.undo, false)
                setMenuItemStatus(R.id.redo, false)
                setMenuItemStatus(R.id.save, saveStickyDirty && !mFileStateRequiresReload)
            }
            else -> {
                // Normal mode.
                // zh-CN: 普通模式.
                setMenuItemStatus(R.id.run, isRunEnabledForCurrentDocument())
                val historyNavigationIdle = mPendingHistoryNavigation == null
                setMenuItemStatus(R.id.undo, historyNavigationIdle && !mReadOnly && editor.canUndo)
                setMenuItemStatus(R.id.redo, historyNavigationIdle && !mReadOnly && editor.canRedo)
                setMenuItemStatus(R.id.save, saveStickyDirty && !mFileStateRequiresReload)
            }
        }
        updateRunButtonVisualState()
        drainPendingEditorEngineSwitchIfIdle()
    }

    private fun finishInitialTextLoad(
        endLoadingUi: Boolean,
        preserveCurrentCursor: Boolean = false,
    ) {
        editor.setLoadingText(false)
        mEditorLoading = false
        if (endLoadingUi) {
            endEditorLoadingUi()
        }
        preservePendingPrivilegedDraft()
        syncPrimaryMenuState()
        tryFinalizeCompletedSaveAsAdoption()
        scheduleCursorStatusAfterInitialLoad(preserveCurrentCursor)
    }

    private fun preservePendingPrivilegedDraft() {
        if (mPrivilegedDraftNeedsSave) {
            saveStickyDirty = true
            mHadDirectEditSinceSave = true
            if (!mPrivilegedDraftNoticeShown) {
                mPrivilegedDraftNoticeShown = true
                post {
                    if (!mDestroyed) showSnack(this, R.string.text_privileged_edit_pending, true)
                }
            }
        }
    }

    private fun tryFinalizeCompletedSaveAsAdoption() {
        val record = mPendingCompletedSaveAs ?: return
        if (mSaveAsAdoptionValidationInProgress || mDestroyed || mEditorLoading ||
            editor.isLoadingText || mDraftRestoreInProgress || mLargeFileFrameCallback != null
        ) {
            return
        }
        if (uri != Uri.fromFile(record.destination)) return

        val textSnapshot = editor.textSnapshot
        val revision = mTextRevision
        mSaveAsAdoptionValidationInProgress = true
        mSaveAsAdoptionDisposable?.dispose()
        mSaveAsAdoptionDisposable = Observable.fromCallable {
            EditorEncodingCodec.encodedDigest(
                text = textSnapshot,
                charset = record.encoding.charset,
                includeBom = record.encoding.hadBom,
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally {
                mSaveAsAdoptionValidationInProgress = false
                mSaveAsAdoptionDisposable = null
            }
            .subscribe({ digest ->
                if (mDestroyed || mPendingCompletedSaveAs?.operationId != record.operationId) {
                    return@subscribe
                }
                val exactSavedSnapshot = mTextRevision == revision &&
                    digest.byteCount == record.savedByteCount &&
                    digest.sha256 == record.savedSha256
                completeCompletedSaveAsAdoption(record, exactSavedSnapshot)
            }, {
                if (!mDestroyed && mPendingCompletedSaveAs?.operationId == record.operationId) {
                    completeCompletedSaveAsAdoption(record, exactSavedSnapshot = false)
                }
            })
    }

    private fun completeCompletedSaveAsAdoption(
        record: EditorPendingSaveAsRecord,
        exactSavedSnapshot: Boolean,
    ) {
        if (mPendingCompletedSaveAs?.operationId != record.operationId) return
        mPendingCompletedSaveAs = null
        if (exactSavedSnapshot) {
            resolvePrivilegedDraftAfterSaveAs(record.sourceUri)
            editor.markTextAsSaved()
            saveStickyDirty = false
            mHadDirectEditSinceSave = false
            mDraftRestoredInThisSession = false
            mDraftEncodingValidationPending = false
            mRestoredText = null
            setMenuItemStatus(R.id.save, false)
        } else if (mDraftRestoredInThisSession || editor.isTextChanged || saveStickyDirty) {
            saveStickyDirty = true
            syncPrimaryMenuState()
        }
        mCompletedSaveAsAdoptedListener?.invoke(record, exactSavedSnapshot)
    }

    private fun setInitialTextProgressively(
        text: String,
        endLoadingUiWhenFinished: Boolean,
        loadGeneration: Long?,
    ) {
        val loadTarget = textLoadTarget
        mProgressiveTextLoadActive = true
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
                if (loadGeneration != null && !isCurrentFileLoad(loadGeneration)) {
                    return
                }
                if (index >= text.length) {
                    // Finish: mark current content as baseline without re-setting text.
                    // zh-CN: 完成: 将当前内容设为基线, 避免再次 setText.
                    loadTarget.finishProgressiveTextLoad(markClean = true)
                    mProgressiveTextLoadActive = false
                    saveStickyDirty = false
                    mHadDirectEditSinceSave = false
                    mLargeFileFrameCallback = null
                    finishInitialTextLoad(endLoadingUiWhenFinished)
                    if (Pref.isCodeHighlightEnabledForLength(text.length)) {
                        post { editor.refreshHighlightTokensIfAllowed() }
                    }
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
        mLargeFileFrameCallback = callback
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
            true.also {
                actionForMenuItem(R.id.action_beautify)?.let(::recordAceHistoryAction)
                mCommandDispatcher.beautifyCode()
            }
        }
        mShowFunctionsButton.setOnLongClickListener(beautifyOnLongClick)
        mAceShowFunctionsButton.setOnLongClickListener(beautifyOnLongClick)
        setUpDrawerGestureGuards()
    }

    private fun setUpDrawerGestureGuards() {
        installDrawerGestureGuard(mShowFunctionsButton)
        installDrawerGestureGuard(mAceShowFunctionsButton)
        installLeftEdgeDrawerGestureGuard(mAceShortcutBottomBar, ACE_SHORTCUT_ITEM_WIDTH_DP)
    }

    private fun installDrawerGestureGuard(view: View) {
        var guarding = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    guarding = true
                    beginDrawerGestureGuard()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (guarding) {
                        mDrawerLayout.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (guarding) {
                        endDrawerGestureGuard()
                    }
                    guarding = false
                }
            }
            false
        }
    }

    private fun installLeftEdgeDrawerGestureGuard(view: RecyclerView, widthDp: Float) {
        if (mAceShortcutBottomBarDrawerGuardInstalled) {
            return
        }
        mAceShortcutBottomBarDrawerGuardInstalled = true
        val guardWidthPx = widthDp * view.resources.displayMetrics.density
        var guarding = false
        view.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                handleEvent(event)
                return false
            }

            private fun handleEvent(event: MotionEvent) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        guarding = event.x in 0f..guardWidthPx
                        if (guarding) {
                            beginDrawerGestureGuard()
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (guarding) {
                            mDrawerLayout.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        if (guarding) {
                            endDrawerGestureGuard()
                        }
                        guarding = false
                    }
                }
            }
        })
    }

    private fun installLeftEdgeDrawerGestureGuard(view: View, widthDp: Float) {
        val guardWidthPx = widthDp * view.resources.displayMetrics.density
        var guarding = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    guarding = event.x in 0f..guardWidthPx
                    if (guarding) {
                        beginDrawerGestureGuard()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (guarding) {
                        mDrawerLayout.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (guarding) {
                        endDrawerGestureGuard()
                    }
                    guarding = false
                }
            }
            false
        }
    }

    private fun beginDrawerGestureGuard() {
        if (mDrawerGestureGuardDepth++ == 0) {
            if (!mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            }
            mDrawerLayout.requestDisallowInterceptTouchEvent(true)
        }
    }

    private fun endDrawerGestureGuard() {
        if (mDrawerGestureGuardDepth <= 0) {
            mDrawerGestureGuardDepth = 0
            return
        }
        if (--mDrawerGestureGuardDepth == 0) {
            mDrawerLayout.requestDisallowInterceptTouchEvent(false)
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
        }
    }

    private fun setUpEditorPreviewer() {
        mThemePreviewFilter.setOnClickListener {
            when (mPreviewMode) {
                PreviewMode.Theme -> cycleThemePreviewFilter()
                PreviewMode.Font -> showFontPreviewFilter()
                PreviewMode.None -> Unit
            }
        }
        mThemePreviewPrevious.setOnClickListener { moveEditorPreview(-1) }
        mThemePreviewNext.setOnClickListener { moveEditorPreview(1) }
        mThemePreviewList.setOnClickListener {
            when (mPreviewMode) {
                PreviewMode.Theme -> showThemePreviewList()
                PreviewMode.Font -> showFontPreviewList()
                PreviewMode.None -> Unit
            }
        }
        mThemePreviewUse.setOnClickListener {
            when (mPreviewMode) {
                PreviewMode.Theme -> finishThemePreview(useTheme = true)
                PreviewMode.Font -> useOrDownloadPreviewFont()
                PreviewMode.None -> Unit
            }
        }
        mThemePreviewExit.setOnClickListener {
            when (mPreviewMode) {
                PreviewMode.Theme -> finishThemePreview(useTheme = false)
                PreviewMode.Font -> finishFontPreview(useFont = false)
                PreviewMode.None -> Unit
            }
        }
    }

    private fun moveEditorPreview(delta: Int) {
        when (mPreviewMode) {
            PreviewMode.Theme -> moveThemePreview(delta)
            PreviewMode.Font -> moveFontPreview(delta)
            PreviewMode.None -> Unit
        }
    }

    @SuppressLint("CheckResult")
    private fun openThemePreviewer(returnToThemeDialog: Boolean, initialTheme: Theme? = null) {
        if (mFontPreviewActive) {
            mFontPreviewReturnToFontDialog = false
            finishFontPreview(useFont = false)
        }
        if (mThemePreviewActive && mThemePreviewThemes.isNotEmpty()) {
            mThemePreviewReturnToThemeDialog = mThemePreviewReturnToThemeDialog || returnToThemeDialog
            mThemePreviewBar.isVisible = true
            if (initialTheme != null) {
                previewTheme(initialTheme)
            } else {
                syncThemePreviewWithAppliedTheme(themeFontService.currentTheme ?: mThemePreviewCurrentTheme)
            }
            return
        }

        editor.setProgress(true)
        Themes.getAllThemes(context, editor.engineType)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ themes ->
                editor.setProgress(false)
                startThemePreviewer(themes, returnToThemeDialog, initialTheme)
            }) { error ->
                editor.setProgress(false)
                showToast(context, error.message ?: error.toString(), true)
            }
    }

    private fun startThemePreviewer(themes: List<Theme>, returnToThemeDialog: Boolean, initialTheme: Theme? = null) {
        if (themes.isEmpty()) {
            return
        }
        val activeTheme = themeFontService.currentTheme
        if (!mThemePreviewActive) {
            mThemePreviewOriginalTheme = activeTheme
            mThemePreviewFilterMode = ThemePreviewFilter.All
        }
        mThemePreviewThemes = themes
        mThemePreviewReturnToThemeDialog = mThemePreviewReturnToThemeDialog || returnToThemeDialog
        mThemePreviewActive = true
        mPreviewMode = PreviewMode.Theme
        mFontPreviewUnavailableHint.isVisible = false
        mFontPreviewInfo.isVisible = false
        mFontPreviewProgress.visibility = View.INVISIBLE
        mThemePreviewUse.setImageResource(R.drawable.mt_ic_check_white_36dp)
        mThemePreviewUse.contentDescription = context.getString(R.string.text_theme_preview_use)
        mThemePreviewUse.isEnabled = true
        mThemePreviewUse.alpha = 1f
        mThemePreviewBar.isVisible = true

        val previewTheme = initialTheme?.let { initial ->
            themes.firstOrNull { it == initial } ?: initial
        } ?: activeTheme?.let { active ->
            themes.firstOrNull { it == active } ?: active
        } ?: themes.first()
        previewTheme(previewTheme)
    }

    private fun previewTheme(theme: Theme) {
        mThemePreviewCurrentTheme = mThemePreviewThemes.firstOrNull { it == theme } ?: theme
        applyEditorTheme(mThemePreviewCurrentTheme)
        refreshThemePreviewBar(mThemePreviewCurrentTheme)
    }

    private fun syncThemePreviewWithAppliedTheme(theme: Theme?) {
        if (!mThemePreviewActive || theme == null) {
            return
        }
        mThemePreviewCurrentTheme = mThemePreviewThemes.firstOrNull { it == theme } ?: theme
        refreshThemePreviewBar(mThemePreviewCurrentTheme)
    }

    private fun refreshThemePreviewBar(theme: Theme?) {
        if (theme == null) {
            return
        }
        mThemePreviewTitle.text = theme.name
        mThemePreviewIndex.text = formatThemePreviewIndex(theme)
        mThemePreviewFilter.setImageResource(mThemePreviewFilterMode.iconResId)
        mThemePreviewFilter.contentDescription = context.getString(mThemePreviewFilterMode.labelResId)
        val backgroundColor = themeFontService.inputMethodBarBackgroundColor(theme)
        val foregroundColor = themeFontService.inputMethodBarForegroundColor(theme)
        applyEditorPreviewColors(backgroundColor, foregroundColor)
    }

    private fun applyEditorPreviewColors(backgroundColor: Int, foregroundColor: Int) {
        mThemePreviewBar.setBackgroundColor(backgroundColor)
        mFontPreviewUnavailableHint.setBackgroundColor(backgroundColor)
        themePreviewTextViews().forEach { it.setTextColor(foregroundColor) }
        themePreviewImageViews().forEach { it.imageTintList = ColorStateList.valueOf(foregroundColor) }
        val progressColors = themeFontService.editorPreviewProgressColors(backgroundColor)
        mFontPreviewProgress.progressTintList = ColorStateList.valueOf(progressColors.indicatorColor)
        mFontPreviewProgress.indeterminateTintList = ColorStateList.valueOf(progressColors.indicatorColor)
        mFontPreviewProgress.progressBackgroundTintList = ColorStateList.valueOf(
            progressColors.trackColor,
        )
    }

    private fun formatThemePreviewIndex(theme: Theme): String {
        val themes = filteredThemePreviewThemes()
        val width = themes.size.toString().length.coerceAtLeast(2)
        val index = themes.indexOf(theme).takeIf { it >= 0 }?.plus(1)
        val indexText = index?.toString()?.padStart(width, '0') ?: "--"
        val totalText = themes.size.toString().padStart(width, '0')
        return "$indexText/$totalText"
    }

    private fun filteredThemePreviewThemes(): List<Theme> {
        return mThemePreviewThemes.filter { mThemePreviewFilterMode.allows(it) }
    }

    private fun themePreviewTextViews(): List<TextView> = listOf(
        mThemePreviewTitle,
        mThemePreviewIndex,
        mFontPreviewInfoType,
        mFontPreviewInfoSeparator,
        mFontPreviewInfoSize,
        mFontPreviewUnavailableHint,
    )

    private fun themePreviewImageViews(): List<ImageView> = listOf(
        mThemePreviewList,
        mThemePreviewFilter,
        mThemePreviewPrevious,
        mThemePreviewNext,
        mThemePreviewExit,
        mThemePreviewUse,
    )

    private fun cycleThemePreviewFilter() {
        mThemePreviewFilterMode = mThemePreviewFilterMode.next()
        refreshThemePreviewBar(mThemePreviewCurrentTheme)
    }

    private fun moveThemePreview(delta: Int) {
        val themes = mThemePreviewThemes
        if (themes.isEmpty()) {
            return
        }
        if (themes.none { mThemePreviewFilterMode.allows(it) }) {
            showToast(context, R.string.text_theme_preview_no_available_theme)
            return
        }
        val size = themes.size
        var index = themes.indexOf(mThemePreviewCurrentTheme)
        if (index < 0) {
            index = if (delta > 0) -1 else 0
        }
        repeat(size) {
            index = (index + delta + size) % size
            val candidate = themes[index]
            if (mThemePreviewFilterMode.allows(candidate)) {
                previewTheme(candidate)
                return
            }
        }
    }

    private fun showThemePreviewList() {
        val themes = filteredThemePreviewThemes()
        if (themes.isEmpty()) {
            showToast(context, R.string.text_theme_preview_no_available_theme)
            return
        }
        val current = mThemePreviewCurrentTheme
        val items = themes.map { theme ->
            val marker = if (theme == current) "* " else "  "
            marker + theme.name
        }
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_theme_previewer)
                .items(items)
                .itemsCallback { dialog, _, which, _ ->
                    val theme = themes.getOrNull(which) ?: return@itemsCallback
                    previewTheme(theme)
                    dialog.dismiss()
                }
                .negativeText(R.string.dialog_button_dismiss)
                .negativeColorRes(R.color.dialog_button_default)
                .build()
        }
    }

    private fun finishThemePreview(useTheme: Boolean) {
        if (!mThemePreviewActive) {
            return
        }
        val returnToThemeDialog = mThemePreviewReturnToThemeDialog
        val selectedTheme = mThemePreviewCurrentTheme
        val originalTheme = mThemePreviewOriginalTheme

        mThemePreviewActive = false
        mPreviewMode = PreviewMode.None
        mThemePreviewReturnToThemeDialog = false
        mThemePreviewThemes = emptyList()
        mThemePreviewCurrentTheme = null
        mThemePreviewOriginalTheme = null
        mThemePreviewBar.isVisible = false

        if (useTheme && selectedTheme != null) {
            persistAndApplyEditorTheme(selectedTheme)
        } else if (originalTheme != null) {
            applyEditorTheme(originalTheme)
        } else {
            applyCurrentEditorThemePreference()
        }

        if (returnToThemeDialog) {
            post { selectEditorTheme() }
        }
    }

    private fun openFontPreviewer(returnToFontDialog: Boolean, initialFontId: String? = null) {
        if (mFontPreviewActive && mFontPreviewOptions.isNotEmpty()) {
            mFontPreviewReturnToFontDialog = mFontPreviewReturnToFontDialog || returnToFontDialog
            mThemePreviewBar.isVisible = true
            val targetId = initialFontId ?: mFontPreviewCurrentFontId
            targetId?.let(::previewFont)
            return
        }
        if (mThemePreviewActive) {
            mThemePreviewReturnToThemeDialog = false
            finishThemePreview(useTheme = false)
        }
        startFontPreviewer(
            options = mAceFontManager.options(context),
            returnToFontDialog = returnToFontDialog,
            initialFontId = initialFontId,
        )
        mFontPreviewCatalogRefreshSubscription?.cancel()
        mFontPreviewCatalogRefreshSubscription = mAceFontManager.refreshAsync { result ->
            if (mFontPreviewActive && result.isSuccess) {
                updateFontPreviewOptions(mAceFontManager.options(context))
            }
        }
    }

    private fun startFontPreviewer(
        options: List<AceEditorFontOption>,
        returnToFontDialog: Boolean,
        initialFontId: String?,
    ) {
        if (options.isEmpty()) return
        if (!mFontPreviewActive) {
            mFontPreviewOriginalFontId = themeFontService.currentAceFont()
        }
        mFontPreviewOptions = options
        mFontPreviewReturnToFontDialog = mFontPreviewReturnToFontDialog || returnToFontDialog
        mFontPreviewActive = true
        mPreviewMode = PreviewMode.Font
        mFontPreviewInfo.isVisible = true
        mThemePreviewBar.isVisible = true

        val prepared = preparedFontPreviewOptions()
        val target = prepared.firstOrNull { it.id == initialFontId }
            ?: prepared.firstOrNull { it.id == mFontPreviewOriginalFontId }
            ?: prepared.firstOrNull()
            ?: options.first()
        previewFont(target.id)
    }

    private fun updateFontPreviewOptions(options: List<AceEditorFontOption>) {
        if (!mFontPreviewActive || options.isEmpty()) return
        mFontPreviewOptions = options
        val prepared = preparedFontPreviewOptions()
        val currentId = mFontPreviewCurrentFontId
        val next = prepared.firstOrNull { it.id == currentId }
            ?: prepared.firstOrNull()
            ?: options.first()
        previewFont(next.id)
    }

    private fun preparedFontPreviewOptions(): List<AceEditorFontOption> {
        return AceEditorFontUiPreferences.preparedPreviewOptions(context, mFontPreviewOptions)
    }

    private fun previewFont(fontId: String) {
        val option = mFontPreviewOptions.firstOrNull { it.id == fontId } ?: return
        detachFontPreviewDownloadObserver()
        mFontPreviewCurrentFontId = option.id
        if (isFontInstalledForPreview(option)) {
            themeFontService.previewAceFont(option.id)
        }
        observeCurrentFontDownload(option)
        refreshFontPreviewBar(option)
    }

    private fun moveFontPreview(delta: Int) {
        val fonts = preparedFontPreviewOptions()
        if (fonts.isEmpty()) return
        val currentIndex = fonts.indexOfFirst { it.id == mFontPreviewCurrentFontId }
        val base = currentIndex.takeIf { it >= 0 } ?: if (delta > 0) -1 else 0
        val index = (base + delta + fonts.size) % fonts.size
        previewFont(fonts[index].id)
    }

    private fun showFontPreviewList() {
        val fonts = preparedFontPreviewOptions()
        if (fonts.isEmpty()) return
        val currentId = mFontPreviewCurrentFontId
        val items = fonts.map { font ->
            (if (font.id == currentId) "* " else "  ") + font.displayName
        }
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_ace_editor_font)
                .items(items)
                .itemsCallback { dialog, _, which, _ ->
                    fonts.getOrNull(which)?.let { previewFont(it.id) }
                    dialog.dismiss()
                }
                .negativeText(R.string.dialog_button_dismiss)
                .negativeColorRes(R.color.dialog_button_default)
                .build()
        }
    }

    private fun showFontPreviewFilter() {
        // Refresh the type snapshot so installed/uninstalled filters reflect downloads or deletes
        // that completed while the previewer was already open.
        mFontPreviewOptions = mAceFontManager.options(context)
        val binding = DialogAceEditorFontFilterBinding.inflate(LayoutInflater.from(context))
        val featureCheckBoxes = linkedMapOf(
            FontFeature.MONO to binding.fontFeatureMono,
            FontFeature.NERD to binding.fontFeatureNerd,
            FontFeature.VARIABLE to binding.fontFeatureVariable,
            FontFeature.CN to binding.fontFeatureCn,
            FontFeature.JP to binding.fontFeatureJp,
        )
        val typeCheckBoxes = linkedMapOf(
            AceEditorFontType.BUILT_IN to binding.fontTypeBuiltIn,
            AceEditorFontType.INSTALLED to binding.fontTypeInstalled,
            AceEditorFontType.NOT_INSTALLED to binding.fontTypeNotInstalled,
        )
        val filter = AceEditorFontUiPreferences.getPreviewFilter(context)
        featureCheckBoxes.forEach { (feature, checkBox) ->
            checkBox.isChecked = feature !in filter.excludedFeatures
        }
        binding.fontFeatureOther.isChecked = !filter.excludeOther
        typeCheckBoxes.forEach { (type, checkBox) ->
            checkBox.isChecked = type !in filter.excludedTypes
        }
        val categoryColor = themeFontService.fontFilterCategoryColor(
            context.getColor(R.color.window_background),
        )
        binding.fontFeatureTitle.setTextColor(categoryColor)
        binding.fontTypeTitle.setTextColor(categoryColor)
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_filter)
                .options(
                    listOf(
                        MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_use_default)) {
                            featureCheckBoxes.values.forEach { it.isChecked = true }
                            binding.fontFeatureOther.isChecked = true
                            typeCheckBoxes.values.forEach { it.isChecked = true }
                        },
                    ),
                )
                .customView(binding.root, false)
                .autoDismiss(false)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { dialog, _ -> dialog.dismiss() }
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { dialog, _ ->
                    val includedFeatures = featureCheckBoxes
                        .filterValues { it.isChecked }
                        .keys
                    val includedTypes = typeCheckBoxes
                        .filterValues { it.isChecked }
                        .keys
                    val nextFilter = AceEditorFontPreviewFilter(
                        excludedFeatures = featureCheckBoxes.keys - includedFeatures,
                        excludeOther = !binding.fontFeatureOther.isChecked,
                        excludedTypes = typeCheckBoxes.keys - includedTypes,
                    )
                    when {
                        !nextFilter.hasSelectedFeatures -> {
                            ViewUtils.showSnack(
                                dialog.view,
                                R.string.text_ace_editor_font_filter_feature_required,
                                true,
                            )
                        }
                        !nextFilter.hasSelectedTypes -> {
                            ViewUtils.showSnack(
                                dialog.view,
                                R.string.text_ace_editor_font_filter_type_required,
                                true,
                            )
                        }
                        mFontPreviewOptions.none(nextFilter::allows) -> {
                            ViewUtils.showSnack(
                                dialog.view,
                                R.string.text_ace_editor_font_filter_no_matches,
                                true,
                            )
                        }
                        else -> {
                            AceEditorFontUiPreferences.setPreviewFilter(context, nextFilter)
                            val prepared = preparedFontPreviewOptions()
                            val next = prepared.firstOrNull { it.id == mFontPreviewCurrentFontId }
                                ?: prepared.first()
                            previewFont(next.id)
                            dialog.dismiss()
                        }
                    }
                }
                .build()
        }
    }

    private fun isFontInstalledForPreview(option: AceEditorFontOption): Boolean {
        if (option.delivery != AceEditorFontOption.Delivery.DOWNLOADABLE) return true
        return mAceFontManager.installationState(option)?.status in setOf(
            FontInstallationStatus.INSTALLED,
            FontInstallationStatus.UPDATE_AVAILABLE,
        )
    }

    private fun observeCurrentFontDownload(option: AceEditorFontOption) {
        if (option.delivery != AceEditorFontOption.Delivery.DOWNLOADABLE) return
        val subscription = mAceFontManager.observeActiveDownload(option.id) { event ->
            handleFontPreviewDownloadEvent(option.id, event)
        } ?: return
        mFontPreviewDownloadId = option.id
        mFontPreviewDownloadSubscription = subscription
    }

    private fun startFontPreviewDownload(option: AceEditorFontOption) {
        val remote = option.remoteFont ?: return
        detachFontPreviewDownloadObserver()
        mFontPreviewDownloadId = option.id
        mFontPreviewDownloadSubscription = runCatching {
            mAceFontManager.download(option.id, remote.artifact.sha256) { event ->
                handleFontPreviewDownloadEvent(option.id, event)
            }
        }.getOrElse { error ->
            mFontPreviewDownloadId = null
            ViewUtils.showToast(
                context,
                context.getString(R.string.text_ace_editor_font_download_failed, error.message ?: error.toString()),
                true,
            )
            null
        }
        refreshFontPreviewBar(option)
    }

    private fun handleFontPreviewDownloadEvent(fontId: String, event: FontDownloadEvent) {
        if (!mFontPreviewActive || mFontPreviewCurrentFontId != fontId) return
        when (event) {
            FontDownloadEvent.Queued -> {
                mFontPreviewProgress.isIndeterminate = true
                mFontPreviewProgress.visibility = View.VISIBLE
            }
            is FontDownloadEvent.Progress -> {
                mFontPreviewProgress.isIndeterminate = event.totalBytes <= 0L
                if (event.totalBytes > 0L) {
                    mFontPreviewProgress.progress = (
                        event.bytesDownloaded.coerceIn(0L, event.totalBytes) * mFontPreviewProgress.max /
                            event.totalBytes
                        ).toInt()
                }
                mFontPreviewProgress.visibility = View.VISIBLE
            }
            is FontDownloadEvent.Success -> {
                clearFinishedFontPreviewDownload()
                mFontPreviewOptions = mAceFontManager.options(context)
                themeFontService.previewAceFont(fontId)
            }
            is FontDownloadEvent.Failure -> {
                clearFinishedFontPreviewDownload()
                ViewUtils.showToast(
                    context,
                    context.getString(
                        R.string.text_ace_editor_font_download_failed,
                        event.error.message ?: event.error.toString(),
                    ),
                    true,
                )
            }
            FontDownloadEvent.Cancelled -> clearFinishedFontPreviewDownload()
        }
        mFontPreviewOptions.firstOrNull { it.id == fontId }?.let(::refreshFontPreviewBar)
    }

    private fun clearFinishedFontPreviewDownload() {
        mFontPreviewDownloadSubscription = null
        mFontPreviewDownloadId = null
        mFontPreviewProgress.isIndeterminate = false
        mFontPreviewProgress.progress = 0
        mFontPreviewProgress.visibility = View.INVISIBLE
    }

    private fun detachFontPreviewDownloadObserver() {
        mFontPreviewDownloadSubscription?.cancel()
        mFontPreviewDownloadSubscription = null
        mFontPreviewDownloadId = null
        mFontPreviewProgress.isIndeterminate = false
        mFontPreviewProgress.progress = 0
        mFontPreviewProgress.visibility = View.INVISIBLE
    }

    private fun useOrDownloadPreviewFont() {
        val option = mFontPreviewOptions.firstOrNull { it.id == mFontPreviewCurrentFontId } ?: return
        if (mFontPreviewDownloadId == option.id && mAceFontManager.isDownloading(option.id)) {
            mAceFontManager.cancelDownload(option.id)
            return
        }
        if (isFontInstalledForPreview(option)) {
            finishFontPreview(useFont = true)
        } else {
            startFontPreviewDownload(option)
        }
    }

    private fun refreshFontPreviewBar(option: AceEditorFontOption) {
        if (!mFontPreviewActive) return
        mThemePreviewTitle.text = option.displayName
        refreshFontPreviewInfo(option)
        val fonts = preparedFontPreviewOptions()
        val width = fonts.size.toString().length.coerceAtLeast(2)
        val index = fonts.indexOfFirst { it.id == option.id }.takeIf { it >= 0 }?.plus(1)
        mThemePreviewIndex.text = "${index?.toString()?.padStart(width, '0') ?: "--"}/${fonts.size.toString().padStart(width, '0')}"
        mThemePreviewFilter.setImageResource(R.drawable.ic_filter_bigger)
        mThemePreviewFilter.contentDescription = context.getString(R.string.text_filter)

        val downloading = mFontPreviewDownloadId == option.id && mAceFontManager.isDownloading(option.id)
        val installed = isFontInstalledForPreview(option)
        mFontPreviewUnavailableHint.isVisible = !installed
        when {
            downloading -> {
                mThemePreviewUse.setImageResource(R.drawable.ic_stop_button_fill)
                mThemePreviewUse.contentDescription = context.getString(R.string.text_stop)
                mThemePreviewUse.isEnabled = true
                mThemePreviewUse.alpha = 1f
            }
            installed -> {
                mThemePreviewUse.setImageResource(R.drawable.mt_ic_check_white_36dp)
                mThemePreviewUse.contentDescription = context.getString(R.string.text_theme_preview_use)
                mThemePreviewUse.isEnabled = true
                mThemePreviewUse.alpha = 1f
            }
            else -> {
                mThemePreviewUse.setImageResource(R.drawable.ic_download_thicker)
                mThemePreviewUse.contentDescription = context.getString(R.string.text_download)
                val downloadable = option.remoteFont != null &&
                    mAceFontManager.installationState(option)?.status != FontInstallationStatus.REVOKED
                mThemePreviewUse.isEnabled = downloadable
                mThemePreviewUse.alpha = if (downloadable) 1f else 0.38f
            }
        }
        refreshFontPreviewColors()
    }

    private fun refreshFontPreviewInfo(option: AceEditorFontOption) {
        val installation = mAceFontManager.installationState(option)
        val typeText = context.getString(
            when {
                option.delivery != AceEditorFontOption.Delivery.DOWNLOADABLE -> {
                    R.string.text_ace_editor_font_built_in
                }
                installation?.installed != null -> R.string.text_installed
                else -> R.string.text_not_installed
            },
        )
        val sizeText = option.sizeBytes?.let(PFiles::formatSizeWithUnit)
            ?: context.getString(R.string.text_ace_editor_font_unknown_size)
        mFontPreviewInfoType.text = typeText
        mFontPreviewInfoSize.text = sizeText
        mFontPreviewInfo.contentDescription = context.getString(
            R.string.text_ace_editor_font_status,
            typeText,
            sizeText,
        )
    }

    private fun refreshFontPreviewColors() {
        val theme = themeFontService.currentTheme
        val backgroundColor = theme?.let(themeFontService::inputMethodBarBackgroundColor) ?: Color.WHITE
        val foregroundColor = theme?.let(themeFontService::inputMethodBarForegroundColor)
            ?: themeFontService.editorPreviewFallbackForegroundColor(backgroundColor)
        applyEditorPreviewColors(backgroundColor, foregroundColor)
    }

    private fun finishFontPreview(useFont: Boolean) {
        if (!mFontPreviewActive) return
        val returnToFontDialog = mFontPreviewReturnToFontDialog
        val selectedId = mFontPreviewCurrentFontId
        val originalId = mFontPreviewOriginalFontId

        mFontPreviewActive = false
        mFontPreviewReturnToFontDialog = false
        mFontPreviewOptions = emptyList()
        mFontPreviewCurrentFontId = null
        mFontPreviewOriginalFontId = null
        mPreviewMode = PreviewMode.None
        mThemePreviewUse.isEnabled = true
        mThemePreviewUse.alpha = 1f
        mFontPreviewUnavailableHint.isVisible = false
        mThemePreviewBar.isVisible = false
        detachFontPreviewDownloadObserver()
        mFontPreviewCatalogRefreshSubscription?.cancel()
        mFontPreviewCatalogRefreshSubscription = null

        if (useFont && selectedId != null) {
            themeFontService.persistAndApplyAceFont(selectedId)
        } else if (originalId != null) {
            themeFontService.previewAceFont(originalId)
        } else {
            themeFontService.applyPreferredAceFont()
        }

        if (returnToFontDialog) {
            post { selectAceEditorFont() }
        }
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
        mAceShortcutLastObservedSelection = null
        mAceShortcutReturnJumpInProgress = false
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

    fun canToggleReadOnlyMode(): Boolean {
        return !mReadOnlyLockedByIntent && !mEditorLoading && !mEncodingOperationInProgress &&
            !mFileSaveInProgress && !mSaveThenReloadInProgress && !mSaveAsInProgress &&
            !mDraftRestoreInProgress && !mProjectRenameInProgress
    }

    fun toggleReadOnlyMode() {
        if (!canToggleReadOnlyMode()) {
            return
        }
        setSessionReadOnlyMode(!mReadOnly, toast = true)
    }

    private fun setSessionReadOnlyMode(readOnly: Boolean, toast: Boolean = false) {
        if (mReadOnlyLockedByIntent) {
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
        val request = EditorEngineSwitchRequest(
            targetType = targetType,
            reason = EditorEngineSwitchReason.PREFERENCE,
            showSwitchToast = true,
        )
        if (mSwitchingEditorEngine) {
            enqueueEditorEngineSwitch(request)
            return
        }
        if (editor.engineType == targetType) {
            return
        }
        if (targetType == EditorEngineType.ACE && shouldRetainNativeEditorForLargeFile()) {
            showAceLargeFileNativeToastOnce()
            return
        }
        if (isEditorEngineSwitchBlocked()) {
            queuePendingEditorEngineSwitch(request)
            showToast(context, R.string.text_loading_with_dots)
            return
        }
        enqueueEditorEngineSwitch(request)
    }

    private fun enqueueEditorEngineSwitch(request: EditorEngineSwitchRequest) {
        if (mDestroyed) return
        if (mSwitchingEditorEngine) {
            queuePendingEditorEngineSwitch(request)
            return
        }
        if (
            request.targetType == EditorEngineType.ACE &&
            editor.engineType == EditorEngineType.NATIVE &&
            shouldRetainNativeEditorForLargeFile()
        ) {
            if (request.reason == EditorEngineSwitchReason.PREFERENCE) {
                showAceLargeFileNativeToastOnce()
            }
            return
        }
        if (!request.recreateSameType && editor.engineType == request.targetType) {
            if (request.reason == EditorEngineSwitchReason.PLUGIN_UNAVAILABLE ||
                request.reason == EditorEngineSwitchReason.ACE_FAILURE
            ) {
                mAceFallbackInProgress.set(false)
            }
            return
        }

        val oldEditor = editor
        val generation = ++mEditorEngineSwitchGeneration
        mSwitchingEditorEngine = true
        mActiveEditorEngineSwitch = request
        val oldInputEnabled = runCatching { oldEditor.inputView.isEnabled }.getOrDefault(true)
        runCatching { oldEditor.inputView.isEnabled = false }
        runCatching { oldEditor.setReadOnly(true) }
        setEnhanceBarInteractive(false)
        syncPrimaryMenuState()

        val delivered = AtomicBoolean(false)
        fun deliverLatestText(text: String) {
            if (!delivered.compareAndSet(false, true)) return
            post {
                completeEditorEngineSwitch(
                    generation = generation,
                    request = request,
                    oldEditor = oldEditor,
                    oldInputEnabled = oldInputEnabled,
                    latestText = text,
                )
            }
        }
        runCatching {
            oldEditor.getTextForSaving(::deliverLatestText)
        }.onFailure { error ->
            error.printStackTrace()
            deliverLatestText(runCatching { oldEditor.textSnapshot }.getOrDefault(""))
        }
    }

    private fun queuePendingEditorEngineSwitch(request: EditorEngineSwitchRequest) {
        val pending = mPendingEditorEngineSwitch
        if (
            pending == null ||
            !isEditorEngineSwitchRequestValid(pending) ||
            request.reason.priority >= pending.reason.priority
        ) {
            mPendingEditorEngineSwitch = request
        }
    }

    private fun isEditorEngineSwitchBlocked(): Boolean =
        mEditorLoading || editor.isLoadingText || mLargeFileFrameCallback != null ||
            mEncodingOperationInProgress || mFileSaveInProgress || mSaveThenReloadInProgress ||
            mSaveAsInProgress || mDraftRestoreInProgress || mProjectRenameInProgress

    private fun drainPendingEditorEngineSwitchIfIdle() {
        if (mDestroyed || mSwitchingEditorEngine || isEditorEngineSwitchBlocked()) return
        val pending = mPendingEditorEngineSwitch ?: return
        mPendingEditorEngineSwitch = null
        if (!isEditorEngineSwitchRequestValid(pending)) return
        if (!pending.recreateSameType && editor.engineType == pending.targetType) {
            completeEditorEngineSwitchEffects(pending)
            return
        }
        post { enqueueEditorEngineSwitch(pending) }
    }

    private fun completeEditorEngineSwitch(
        generation: Long,
        request: EditorEngineSwitchRequest,
        oldEditor: ScriptEditorEngine,
        oldInputEnabled: Boolean,
        latestText: String,
    ) {
        if (mDestroyed || generation != mEditorEngineSwitchGeneration || oldEditor !== editor) {
            return
        }
        if (!isEditorEngineSwitchRequestValid(request)) {
            thawEditorAfterCancelledSwitch(oldEditor, oldInputEnabled)
            finishEditorEngineSwitch(generation, reconcileDesired = true)
            return
        }

        val result = try {
            Result.success(
                switchEditorEngine(
                    targetType = request.targetType,
                    oldEditor = oldEditor,
                    latestText = latestText,
                    showSwitchToast = request.showSwitchToast,
                    allowFactoryFallback = request.allowFactoryFallback,
                ),
            )
        } catch (error: Exception) {
            Result.failure(error)
        } catch (error: LinkageError) {
            Result.failure(error)
        }

        result.onSuccess { switched ->
            if (!switched) {
                thawEditorAfterCancelledSwitch(oldEditor, oldInputEnabled)
                finishEditorEngineSwitch(generation, reconcileDesired = false)
                return@onSuccess
            }
            completeEditorEngineSwitchEffects(request)
            val reachedRequestedEngine = editor.engineType == request.targetType
            finishEditorEngineSwitch(
                generation,
                reconcileDesired = reachedRequestedEngine &&
                    request.reason != EditorEngineSwitchReason.ACE_FAILURE,
            )
        }.onFailure { error ->
            error.printStackTrace()
            if (oldEditor === editor) {
                thawEditorAfterCancelledSwitch(oldEditor, oldInputEnabled)
            }
            val rescuedToNative = oldEditor !== editor && editor.engineType == EditorEngineType.NATIVE
            when {
                rescuedToNative && request.reason == EditorEngineSwitchReason.PLUGIN_UNAVAILABLE -> {
                    completeEditorEngineSwitchEffects(request)
                }
                rescuedToNative && request.reason == EditorEngineSwitchReason.ACE_FAILURE -> {
                    completeEditorEngineSwitchEffects(request)
                }
                else -> showToast(context, error.message ?: error.toString(), true)
            }
            finishEditorEngineSwitch(generation, reconcileDesired = false)
        }
    }

    private fun completeEditorEngineSwitchEffects(request: EditorEngineSwitchRequest) {
        when (request.reason) {
            EditorEngineSwitchReason.PLUGIN_UNAVAILABLE -> {
                mAceSessionInvalidated = false
                mAceFallbackInProgress.set(false)
                request.pluginChange?.let { AceEditorPluginUi.showUnavailableToastOnce(context, it) }
            }
            EditorEngineSwitchReason.ACE_FAILURE -> {
                mAceSessionInvalidated = false
                mAceFallbackInProgress.set(false)
                request.aceFailure?.let(::showAceFallbackToastOnce)
            }
            EditorEngineSwitchReason.PLUGIN_RELOAD -> {
                mAceSessionInvalidated = false
                if (editor.engineType != EditorEngineType.ACE) {
                    showToast(context, R.string.error_ace_editor_unavailable, true)
                }
            }
            EditorEngineSwitchReason.PREFERENCE -> Unit
        }
    }

    private fun thawEditorAfterCancelledSwitch(
        oldEditor: ScriptEditorEngine,
        oldInputEnabled: Boolean,
    ) {
        if (mDestroyed || oldEditor !== editor) return
        runCatching { oldEditor.inputView.isEnabled = oldInputEnabled }
        runCatching { oldEditor.setReadOnly(shouldKeepEditorReadOnlyAfterSwitch()) }
        setEnhanceBarInteractive(!shouldKeepEditorReadOnlyAfterSwitch())
        syncPrimaryMenuState()
    }

    private fun isEditorEngineSwitchRequestValid(request: EditorEngineSwitchRequest): Boolean {
        val pluginAvailable = AceEditorPluginStateMonitor.current(context).available
        val desiredType = EditorEnginePreferences.get()
        return when (request.reason) {
            EditorEngineSwitchReason.PREFERENCE ->
                desiredType == request.targetType &&
                    (request.targetType != EditorEngineType.ACE ||
                        (pluginAvailable && !shouldRetainNativeEditorForLargeFile()))
            EditorEngineSwitchReason.PLUGIN_UNAVAILABLE ->
                request.targetType == EditorEngineType.NATIVE &&
                    (!pluginAvailable || desiredType == EditorEngineType.NATIVE)
            EditorEngineSwitchReason.PLUGIN_RELOAD ->
                pluginAvailable && desiredType == EditorEngineType.ACE &&
                    editor.engineType == EditorEngineType.ACE
            EditorEngineSwitchReason.ACE_FAILURE -> editor.engineType == EditorEngineType.ACE
        }
    }

    private fun switchEditorEngine(
        targetType: EditorEngineType,
        oldEditor: ScriptEditorEngine,
        latestText: String,
        showSwitchToast: Boolean = true,
        allowFactoryFallback: Boolean = false,
    ): Boolean {
        if (oldEditor !== editor) {
            return false
        }

        var newEditor = EditorEngineFactory.create(context, targetType)
        if (newEditor.engineType != targetType && !allowFactoryFallback) {
            runCatching { newEditor.destroy() }
            if (targetType == EditorEngineType.ACE) {
                recordAcePluginUnavailableFallbackIfNeeded(requireAcePreference = false)
            }
            showToast(context, R.string.error_ace_editor_unavailable, true)
            return false
        }

        val wasAttached = isAttachedToWindow
        val oldSaveStickyDirty = saveStickyDirty
        val oldHadDirectEditSinceSave = mHadDirectEditSinceSave
        val oldEditorLoading = mEditorLoading
        val oldLargeFileMode = mLargeFileMode
        val snapshot = captureEditorTransferSnapshot(
            oldEditor = oldEditor,
            latestText = latestText,
            stickyDirty = oldSaveStickyDirty,
        )
        var preparation = runCatching { prepareEditorForAdoption(newEditor, snapshot) }
        if (preparation.isFailure && targetType == EditorEngineType.ACE && allowFactoryFallback) {
            runCatching { newEditor.destroy() }
            newEditor = EditorEngineFactory.create(context, EditorEngineType.NATIVE)
            preparation = runCatching { prepareEditorForAdoption(newEditor, snapshot) }
        }
        preparation.exceptionOrNull()?.let { error ->
            runCatching { newEditor.destroy() }
            throw error
        }

        try {
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
            cancelAceDefinitionNavigation()
            (oldEditor as? AceScriptEditorEngine)?.onFatalFailure = null
            (oldEditor as? AceScriptEditorEngine)?.onAceStateChanged = null
            (oldEditor as? AceScriptEditorEngine)?.onSelectionAction = null
            (oldEditor as? AceScriptEditorEngine)?.onDefinitionNavigationRequested = null
            (oldEditor as? AceScriptEditorEngine)?.onProjectRenameRequested = null
            oldEditor.setHistoryOperationListener(null)
            oldEditor.onUserTouchInTextArea = null
            binding.editorContainer.removeView(oldEditor.view)

            editor = newEditor
            mEncodingHistoryEntries.clear()
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

            applyCurrentEditorThemePreference()
            mEditorLoading = oldEditorLoading
            mLargeFileMode = oldLargeFileMode && editor.engineType == EditorEngineType.NATIVE
            saveStickyDirty = oldSaveStickyDirty || snapshot.dirty
            mHadDirectEditSinceSave = oldHadDirectEditSinceSave || snapshot.dirty
            editor.setReadOnly(shouldKeepEditorReadOnlyAfterSwitch())
            setEnhanceBarInteractive(!shouldKeepEditorReadOnlyAfterSwitch())
            runCatching { oldEditor.destroy() }
        } catch (error: Exception) {
            recoverNativeEditorAfterFailedSwitch(
                oldEditor = oldEditor,
                failedEditor = newEditor,
                snapshot = snapshot,
                wasAttached = wasAttached,
                oldEditorLoading = oldEditorLoading,
                oldLargeFileMode = oldLargeFileMode,
                oldSaveStickyDirty = oldSaveStickyDirty,
                oldHadDirectEditSinceSave = oldHadDirectEditSinceSave,
            )
            throw error
        } catch (error: LinkageError) {
            recoverNativeEditorAfterFailedSwitch(
                oldEditor = oldEditor,
                failedEditor = newEditor,
                snapshot = snapshot,
                wasAttached = wasAttached,
                oldEditorLoading = oldEditorLoading,
                oldLargeFileMode = oldLargeFileMode,
                oldSaveStickyDirty = oldSaveStickyDirty,
                oldHadDirectEditSinceSave = oldHadDirectEditSinceSave,
            )
            throw error
        }

        syncPrimaryMenuState()
        activity.invalidateOptionsMenu()
        if (editor.engineType == EditorEngineType.NATIVE) {
            editor.requestEditorFocus()
        }

        val engineName = when (editor.engineType) {
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

    private fun configureAceActionHistory(editor: ScriptEditorEngine) {
        (editor as? AceScriptEditorEngine)?.let { aceEditor ->
            aceEditor.onAceStateChanged = {
                schedulePrimaryMenuStateSync()
                if (!mCursorStatusReady || mPendingRestoredSelection != null) {
                    scheduleCursorStatusAfterInitialLoad()
                }
            }
            aceEditor.onSelectionAction = { action ->
                actionForAceSelectionAction(action)?.let(::recordAceHistoryAction)
            }
        }
    }

    private fun configureAceDefinitionNavigation(editor: ScriptEditorEngine) {
        (editor as? AceScriptEditorEngine)?.onDefinitionNavigationRequested = { target ->
            requestAceDefinitionNavigation(target)
        }
    }

    private fun configureAceCodeActions(editor: ScriptEditorEngine) {
        (editor as? AceScriptEditorEngine)?.onCurrentDocumentCodeActionRequested =
            codeAction@{ action: EditorPluginCurrentDocumentCodeAction, onComplete ->
                val authorized = runCatching {
                    !mDestroyed &&
                        this.editor === editor &&
                        editor.engineType == EditorEngineType.ACE &&
                        EditorCurrentDocumentCodeActionAuthorizer.isAuthorized(
                            action = action,
                            currentText = editor.textSnapshot,
                            readOnly = editor.isReadOnly,
                        )
                }.getOrDefault(false)
                onComplete.onComplete(authorized)
            }
    }

    private fun configureAceProjectRename(editor: ScriptEditorEngine) {
        (editor as? AceScriptEditorEngine)?.onProjectRenameRequested =
            projectRename@{ request, onComplete ->
                requestAceProjectRename(editor, request, onComplete)
            }
    }

    private fun requestAceProjectRename(
        requestedEditor: ScriptEditorEngine,
        request: EditorPluginProjectRenameRequest,
        onComplete: EditorPluginProjectRenameCallback,
    ) {
        val documentPath = uri
            ?.takeIf { value -> value.scheme.equals("file", ignoreCase = true) }
            ?.path
            ?.takeIf(String::isNotBlank)
        if (
            mDestroyed || mProjectRenameInProgress || requestedEditor !== editor ||
            requestedEditor.engineType != EditorEngineType.ACE || mReadOnly ||
            requestedEditor.isReadOnly || mEditorLoading || requestedEditor.isLoadingText ||
            mEncodingOperationInProgress || mFileSaveInProgress || mSaveAsInProgress ||
            mSaveThenReloadInProgress || mDraftRestoreInProgress || mFileStateRequiresReload ||
            !mFileEncodingValidationComplete || documentPath == null ||
            !EditorPluginProjectRenameContract.isSupported(request)
        ) {
            onComplete.onComplete(EditorPluginProjectRenameResult.REJECTED)
            return
        }
        if (requestedEditor.isTextChanged) {
            showSnack(this, R.string.error_typescript_project_rename_save_first)
            onComplete.onComplete(EditorPluginProjectRenameResult.REJECTED)
            return
        }

        mProjectRenameInProgress = true
        mProjectRenameCompletion = onComplete
        mProjectRenameRequestedEditor = requestedEditor
        val generation = ++mProjectRenameGeneration
        val identityGeneration = mDocumentIdentityGeneration
        val textRevision = mTextRevision
        val baseText = requestedEditor.textSnapshot
        syncPrimaryMenuState()
        mProjectRenameDisposable = Observable.fromCallable {
            mProjectRenameTransaction.prepare(documentPath, baseText, request)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ prepared ->
                mProjectRenameDisposable = null
                if (
                    !isAceProjectRenameContextCurrent(
                        generation,
                        identityGeneration,
                        textRevision,
                        requestedEditor,
                        baseText,
                    )
                ) {
                    finishAceProjectRename(generation, EditorPluginProjectRenameResult.STALE)
                    return@subscribe
                }
                showAceProjectRenamePreview(
                    generation = generation,
                    identityGeneration = identityGeneration,
                    textRevision = textRevision,
                    requestedEditor = requestedEditor,
                    baseText = baseText,
                    prepared = prepared,
                )
            }, { error ->
                mProjectRenameDisposable = null
                handleAceProjectRenameFailure(generation, error)
            })
    }

    private fun showAceProjectRenamePreview(
        generation: Long,
        identityGeneration: Long,
        textRevision: Long,
        requestedEditor: ScriptEditorEngine,
        baseText: String,
        prepared: TypeScriptEditorProjectRenameTransaction.Prepared,
    ) {
        val request = prepared.request
        val content = buildString {
            append(
                context.getString(
                    R.string.text_typescript_project_rename_preview,
                    request.symbolName,
                    request.newName,
                    prepared.fileCount,
                    prepared.editCount,
                ),
            )
            append("\n\n")
            request.files.forEach { file ->
                append("- ")
                append(file.relativePath)
                append(": ")
                append(file.edits.size)
                append('\n')
            }
        }.trimEnd()
        val dialog = runCatching {
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.text_typescript_project_rename)
                    .content(content)
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_attraction)
                    .autoDismiss(false)
                    .onNegative { current, _ ->
                        if (mProjectRenameDialog === current) {
                            mProjectRenameDialog = null
                            current.dismiss()
                            finishAceProjectRename(
                                generation,
                                EditorPluginProjectRenameResult.REJECTED,
                            )
                        }
                    }
                    .onPositive { current, _ ->
                        if (mProjectRenameDialog !== current) return@onPositive
                        mProjectRenameDialog = null
                        current.dismiss()
                        commitAceProjectRename(
                            generation = generation,
                            identityGeneration = identityGeneration,
                            textRevision = textRevision,
                            requestedEditor = requestedEditor,
                            baseText = baseText,
                            prepared = prepared,
                        )
                    }
                    .build()
            }
        }.getOrElse {
            finishAceProjectRename(generation, EditorPluginProjectRenameResult.FAILED)
            return
        }
        mProjectRenameDialog = dialog
        dialog.setOnDismissListener {
            if (mProjectRenameDialog === dialog) {
                mProjectRenameDialog = null
                finishAceProjectRename(generation, EditorPluginProjectRenameResult.REJECTED)
            }
        }
    }

    private fun commitAceProjectRename(
        generation: Long,
        identityGeneration: Long,
        textRevision: Long,
        requestedEditor: ScriptEditorEngine,
        baseText: String,
        prepared: TypeScriptEditorProjectRenameTransaction.Prepared,
    ) {
        if (
            !isAceProjectRenameContextCurrent(
                generation,
                identityGeneration,
                textRevision,
                requestedEditor,
                baseText,
            )
        ) {
            showSnack(this, R.string.error_typescript_project_rename_stale)
            finishAceProjectRename(generation, EditorPluginProjectRenameResult.STALE)
            return
        }
        requestedEditor.setReadOnly(true)
        syncPrimaryMenuState()
        mProjectRenameDisposable = Observable.fromCallable {
            EditorFileIoCoordinator.withWriteAccess(
                prepared.files.map { plan -> Uri.fromFile(plan.target) },
            ) {
                mProjectRenameTransaction.publish(prepared)
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ publication ->
                mProjectRenameDisposable = null
                mProjectRenamePublication = publication
                if (
                    !isAceProjectRenameContextCurrent(
                        generation,
                        identityGeneration,
                        textRevision,
                        requestedEditor,
                        baseText,
                    )
                ) {
                    rollbackAceProjectRenamePublication(
                        generation,
                        publication,
                        EditorPluginProjectRenameResult.STALE,
                    )
                    return@subscribe
                }
                val token = "typescript-project-rename-${SystemClock.uptimeMillis()}"
                requestedEditor.replaceAllTextUndoably(
                    prepared.activeDocumentResultText,
                    token,
                ) { succeeded ->
                    if (
                        !succeeded || mDestroyed || generation != mProjectRenameGeneration ||
                        requestedEditor !== editor ||
                        requestedEditor.textSnapshot != prepared.activeDocumentResultText
                    ) {
                        runCatching {
                            if (requestedEditor === editor && !mDestroyed) {
                                requestedEditor.text = baseText
                            }
                        }
                        rollbackAceProjectRenamePublication(
                            generation,
                            publication,
                            EditorPluginProjectRenameResult.FAILED,
                        )
                        return@replaceAllTextUndoably
                    }
                    mProjectRenameActiveBufferApplied = true
                    completeAceProjectRenamePublication(
                        generation,
                        requestedEditor,
                        prepared,
                        publication,
                    )
                }
            }, { error ->
                mProjectRenameDisposable = null
                handleAceProjectRenameFailure(generation, error)
            })
    }

    private fun completeAceProjectRenamePublication(
        generation: Long,
        requestedEditor: ScriptEditorEngine,
        prepared: TypeScriptEditorProjectRenameTransaction.Prepared,
        publication: TypeScriptEditorProjectRenameTransaction.Publication,
    ) {
        mProjectRenameDisposable = Observable.fromCallable {
            mProjectRenameTransaction.complete(publication)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                mProjectRenameDisposable = null
                if (
                    mDestroyed || generation != mProjectRenameGeneration ||
                    mProjectRenamePublication !== publication || requestedEditor !== editor
                ) {
                    return@subscribe
                }
                mProjectRenamePublication = null
                mCurrentFileSizeBytes = null
                uri?.let(fileService::discardStableDraft)
                requestedEditor.markTextAsSaved()
                saveStickyDirty = false
                mHadDirectEditSinceSave = false
                requestedEditor.setDocumentPath(prepared.documentPath)
                showSnack(this, R.string.text_typescript_project_rename_applied)
                finishAceProjectRename(generation, EditorPluginProjectRenameResult.APPLIED)
            }, { error ->
                mProjectRenameDisposable = null
                handleAceProjectRenameFailure(generation, error)
            })
    }

    private fun rollbackAceProjectRenamePublication(
        generation: Long,
        publication: TypeScriptEditorProjectRenameTransaction.Publication,
        result: EditorPluginProjectRenameResult,
    ) {
        mProjectRenameDisposable = Observable.fromCallable {
            EditorFileIoCoordinator.withWriteAccess(
                publication.entries.map { entry -> Uri.fromFile(entry.plan.target) },
            ) {
                mProjectRenameTransaction.rollback(publication)
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                mProjectRenameDisposable = null
                if (mProjectRenamePublication === publication) {
                    mProjectRenamePublication = null
                }
                if (result == EditorPluginProjectRenameResult.STALE && !mDestroyed) {
                    showSnack(this, R.string.error_typescript_project_rename_stale)
                }
                finishAceProjectRename(generation, result)
            }, { error ->
                mProjectRenameDisposable = null
                handleAceProjectRenameFailure(generation, error)
            })
    }

    private fun handleAceProjectRenameFailure(generation: Long, error: Throwable) {
        val failure = error as? TypeScriptEditorProjectRenameException
        val result = when (failure?.reason) {
            TypeScriptEditorProjectRenameFailureReason.STALE ->
                EditorPluginProjectRenameResult.STALE
            TypeScriptEditorProjectRenameFailureReason.REJECTED,
            TypeScriptEditorProjectRenameFailureReason.UNSAVED_DOCUMENT,
                -> EditorPluginProjectRenameResult.REJECTED
            else -> EditorPluginProjectRenameResult.FAILED
        }
        if (!mDestroyed && generation == mProjectRenameGeneration) {
            when (failure?.reason) {
                TypeScriptEditorProjectRenameFailureReason.UNSAVED_DOCUMENT ->
                    showSnack(this, R.string.error_typescript_project_rename_save_first)
                TypeScriptEditorProjectRenameFailureReason.STALE ->
                    showSnack(this, R.string.error_typescript_project_rename_stale)
                TypeScriptEditorProjectRenameFailureReason.ROLLBACK_FAILED -> {
                    markFileStateRequiresReload()
                    showAceProjectRenameRollbackFailure(failure)
                }
                else -> showSnack(this, R.string.error_typescript_project_rename_failed)
            }
        }
        finishAceProjectRename(generation, result)
    }

    private fun showAceProjectRenameRollbackFailure(
        failure: TypeScriptEditorProjectRenameException,
    ) {
        val content = buildString {
            append(context.getString(R.string.error_typescript_project_rename_rollback_failed))
            if (failure.recoveryFiles.isNotEmpty()) {
                append("\n\n")
                failure.recoveryFiles.forEach { file ->
                    append(file.absolutePath)
                    append('\n')
                }
            }
        }.trimEnd()
        runCatching {
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.error_save_failed)
                    .content(content)
                    .positiveText(R.string.dialog_button_confirm)
                    .build()
            }
        }
    }

    private fun isAceProjectRenameContextCurrent(
        generation: Long,
        identityGeneration: Long,
        textRevision: Long,
        requestedEditor: ScriptEditorEngine,
        baseText: String,
    ): Boolean =
        !mDestroyed && mProjectRenameInProgress && generation == mProjectRenameGeneration &&
            identityGeneration == mDocumentIdentityGeneration && textRevision == mTextRevision &&
            requestedEditor === editor && requestedEditor.engineType == EditorEngineType.ACE &&
            requestedEditor.textSnapshot == baseText

    private fun finishAceProjectRename(
        generation: Long,
        result: EditorPluginProjectRenameResult,
    ) {
        if (generation != mProjectRenameGeneration) return
        mProjectRenameDisposable?.dispose()
        mProjectRenameDisposable = null
        mProjectRenameDialog?.let { dialog ->
            mProjectRenameDialog = null
            runCatching { dialog.dismiss() }
        }
        val requestedEditor = mProjectRenameRequestedEditor
        mProjectRenameRequestedEditor = null
        if (!mDestroyed && requestedEditor === editor) {
            requestedEditor.setReadOnly(mReadOnly)
        }
        mProjectRenamePublication = null
        mProjectRenameActiveBufferApplied = false
        mProjectRenameInProgress = false
        mProjectRenameGeneration++
        val completion = mProjectRenameCompletion
        mProjectRenameCompletion = null
        if (!mDestroyed) {
            syncPrimaryMenuState()
        }
        completion?.onComplete(result)
    }

    private fun abandonAceProjectRenameOnDestroy() {
        if (!mProjectRenameInProgress) return
        mProjectRenameDialog?.let { dialog ->
            mProjectRenameDialog = null
            runCatching { dialog.dismiss() }
        }
        mProjectRenameCompletion?.onComplete(EditorPluginProjectRenameResult.REJECTED)
        mProjectRenameCompletion = null
        if (mProjectRenameDisposable != null) {
            // A running prepare/publish/complete/rollback call is synchronous on its IO worker.
            // Keep its observer attached so a publication that races destruction is still either
            // completed after an applied buffer edit or rolled back before it is abandoned.
            return
        }
        val publication = mProjectRenamePublication
        val activeBufferApplied = mProjectRenameActiveBufferApplied
        if (publication != null) {
            Schedulers.io().scheduleDirect {
                runCatching {
                    if (activeBufferApplied) {
                        mProjectRenameTransaction.complete(publication)
                    } else {
                        EditorFileIoCoordinator.withWriteAccess(
                            publication.entries.map { entry -> Uri.fromFile(entry.plan.target) },
                        ) {
                            mProjectRenameTransaction.rollback(publication)
                        }
                    }
                }
            }
        }
        mProjectRenamePublication = null
        mProjectRenameRequestedEditor = null
        mProjectRenameActiveBufferApplied = false
        mProjectRenameInProgress = false
        mProjectRenameGeneration++
    }

    private fun requestAceDefinitionNavigation(target: EditorPluginDefinitionTarget) {
        val documentPath = currentDocumentPathOrName()?.takeIf(String::isNotBlank) ?: return
        val requestedEditor = editor
        val documentIdentityGeneration = mDocumentIdentityGeneration
        val textRevision = mTextRevision
        val currentDocumentText = requestedEditor.textSnapshot
        cancelAceDefinitionNavigation()
        val requestGeneration = mDefinitionNavigationGeneration
        mDefinitionNavigationDisposable = Observable.fromCallable {
            listOfNotNull(
                TypeScriptEditorDefinitionTargetResolver.resolve(
                    documentPath = documentPath,
                    target = target,
                    currentDocumentText = currentDocumentText,
                ),
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ targets ->
                if (requestGeneration == mDefinitionNavigationGeneration) {
                    mDefinitionNavigationDisposable = null
                }
                handleResolvedAceDefinitionNavigation(
                    requestGeneration = requestGeneration,
                    documentIdentityGeneration = documentIdentityGeneration,
                    textRevision = textRevision,
                    requestedEditor = requestedEditor,
                    target = targets.singleOrNull(),
                )
            }, {
                if (requestGeneration == mDefinitionNavigationGeneration) {
                    mDefinitionNavigationDisposable = null
                }
            })
    }

    private fun handleResolvedAceDefinitionNavigation(
        requestGeneration: Long,
        documentIdentityGeneration: Long,
        textRevision: Long,
        requestedEditor: ScriptEditorEngine,
        target: TypeScriptEditorDefinitionTargetResolver.ResolvedTarget?,
    ) {
        if (
            target == null ||
            mDestroyed ||
            requestGeneration != mDefinitionNavigationGeneration ||
            documentIdentityGeneration != mDocumentIdentityGeneration ||
            textRevision != mTextRevision ||
            requestedEditor !== editor ||
            editor.engineType != EditorEngineType.ACE
        ) {
            return
        }
        EditorDefinitionNavigationDispatcher.dispatch(
            target = target,
            jumpCurrentDocument = { line, column ->
                editor.jumpTo(line, column)
                editor.requestEditorFocus()
            },
            openTargetDocument = { name, path, line, column ->
                EditActivity.editFileAt(
                    context = activity,
                    name = name,
                    path = path,
                    lineNumber = line,
                    columnNumber = column,
                    newTask = false,
                )
            },
        )
    }

    private fun cancelAceDefinitionNavigation() {
        mDefinitionNavigationGeneration++
        mDefinitionNavigationDisposable?.dispose()
        mDefinitionNavigationDisposable = null
    }

    private fun fallbackToNativeFromAceFailure(failure: AceFailure) {
        if (!mAceFallbackInProgress.compareAndSet(false, true)) {
            return
        }

        if (editor.engineType != EditorEngineType.ACE) {
            mAceFallbackInProgress.set(false)
            return
        }

        when {
            mEncodingReloadRollback != null -> rollbackEncodingReload(
                IllegalStateException(failure.message),
                showError = false,
            )
            mEncodingOperationInProgress -> finishEncodingOperation()
        }
        (editor as? AceScriptEditorEngine)?.onFatalFailure = null
        enqueueEditorEngineSwitch(
            EditorEngineSwitchRequest(
                targetType = EditorEngineType.NATIVE,
                reason = EditorEngineSwitchReason.ACE_FAILURE,
                aceFailure = failure,
            ),
        )
    }

    private fun handleAcePluginStateChange(change: AceEditorPluginStateMonitor.Change) {
        if (mDestroyed) return
        mLastAcePluginState = change.current
        mLastAcePluginChangeId = change.id
        when {
            !change.current.available && editor.engineType == EditorEngineType.ACE -> {
                mAceSessionInvalidated = true
                fallbackToNativeFromPluginState(change)
            }
            change.current.available && editor.engineType == EditorEngineType.ACE &&
                (change.requiresSessionReload || mAceSessionInvalidated) ->
                reloadAcePluginSession(change)
        }
    }

    private fun fallbackToNativeFromPluginState(change: AceEditorPluginStateMonitor.Change) {
        mAceFallbackInProgress.compareAndSet(false, true)
        if (editor.engineType != EditorEngineType.ACE) {
            mAceFallbackInProgress.set(false)
            return
        }
        closeAceFontPreviewForPluginFallback()
        when {
            mEncodingReloadRollback != null -> rollbackEncodingReload(
                IllegalStateException(change.current.errorCode?.name ?: change.current.status.name),
                showError = false,
            )
            mEncodingOperationInProgress -> finishEncodingOperation()
        }
        (editor as? AceScriptEditorEngine)?.onFatalFailure = null
        enqueueEditorEngineSwitch(
            EditorEngineSwitchRequest(
                targetType = EditorEngineType.NATIVE,
                reason = EditorEngineSwitchReason.PLUGIN_UNAVAILABLE,
                pluginChange = change,
            ),
        )
    }

    private fun closeAceFontPreviewForPluginFallback() {
        if (!mFontPreviewActive) return
        mFontPreviewReturnToFontDialog = false
        runCatching { finishFontPreview(useFont = false) }
    }

    private fun reloadAcePluginSession(change: AceEditorPluginStateMonitor.Change) {
        if (editor.engineType != EditorEngineType.ACE) return
        val request = EditorEngineSwitchRequest(
            targetType = EditorEngineType.ACE,
            reason = EditorEngineSwitchReason.PLUGIN_RELOAD,
            recreateSameType = true,
            allowFactoryFallback = true,
            pluginChange = change,
        )
        if (isEditorEngineSwitchBlocked()) {
            queuePendingEditorEngineSwitch(request)
        } else {
            enqueueEditorEngineSwitch(request)
        }
        mLastAcePluginState = change.current
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

    private fun recordAcePluginUnavailableFallbackIfNeeded(requireAcePreference: Boolean = true) {
        if (requireAcePreference && EditorEnginePreferences.get() != EditorEngineType.ACE) {
            return
        }
        if (editor.engineType == EditorEngineType.ACE) {
            return
        }
        val now = SystemClock.uptimeMillis()
        val failure = EditorEngineFactory.lastAcePluginFailure()
        val code = failure?.code ?: "PLUGIN_UNAVAILABLE"
        val message = failure?.message ?: "ACE editor plugin is unavailable"
        mLastAceDiagnosticsSnapshot = AceDiagnostics
            .createNativeSnapshot(EditorEngineType.ACE, fileSizeBytes = null, largeFilePolicy = "auto")
            .copy(
                aceHealthState = AceHealthState.FAILED.name,
                lastFailureType = AceFailureType.UNKNOWN.name,
                lastFailureMessage = message,
                lastFailureFatal = true,
                runtimeEventHistory = listOf(
                    "$now:failure:type=${AceFailureType.UNKNOWN.name},code=$code,fatal=true,message=$message",
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
        val aceEditor = editor as? AceScriptEditorEngine ?: return
        runCatching {
            aceEditor.createHostAceDiagnosticsSnapshot(
                currentFileSizeOrNull(),
                currentLargeFilePolicyLabel(),
            )
        }.getOrNull()?.let { snapshot ->
            mLastAceDiagnosticsSnapshot = snapshot
        }
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
        // Diagnostics are built on the main thread. Use the value captured by the asynchronous
        // load probe so a queued writer can never make this path block behind the URI lock.
        // zh-CN: 诊断快照在主线程构建. 使用异步加载探测时缓存的值, 避免排队中的写入者
        // 让此路径在 URI 锁之后阻塞主线程.
        return mCurrentFileSizeBytes
    }

    private fun currentLargeFilePolicyLabel(): String {
        return EditorTextLoadPolicy.largeFilePolicyLabel(
            largeFileMode = mLargeFileMode,
            forceNativeRoute = AceEditorTestHooks.shouldForceLargeFileNativeRoute(),
        )
    }

    private fun setUpEditor(resetInitialState: Boolean = true) {
        configureAceFallback(editor)
        configureAceActionHistory(editor)
        configureAceDefinitionNavigation(editor)
        configureAceCodeActions(editor)
        configureAceProjectRename(editor)
        editor.let { editor ->
            editor.setHistoryOperationListener { token, direction ->
                // Both engines dispatch history completion on the main thread. Applying it here
                // keeps encoding metadata in the same ordered callback stream as ACE text/state.
                applyEncodingHistoryOperation(token, direction)
            }
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

                // A save writes a point-in-time text snapshot. Every later text mutation advances
                // the revision so its completion cannot mark newer editor content as saved.
                // zh-CN: 保存操作写入的是某一时刻的文本快照. 后续每次文本变化都会推进修订号,
                // 避免保存完成回调把更新后的编辑器内容误标为已保存.
                mTextRevision++

                // If this change is not caused by undo/redo buttons, treat it as a direct edit.
                // Direct edits keep Save sticky until next successful save.
                //
                // zh-CN:
                // 若本次变化并非由撤销/重做按钮触发, 则视为直接编辑.
                // 直接编辑会使保存按钮保持 sticky, 直到下一次保存成功.
                if (mPendingHistoryNavigation == null) {
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

            editor.addCursorChangeCallback(EditorCursorChangeListener { lineText, line, column ->
                autoComplete(lineText, column)
                if (mCursorStatusReady) {
                    updateCursorStatus(line, column)
                }
                recordAceShortcutCursorHistory()
            })
            if (mCursorStatusReady) {
                editor.selection.let { selection ->
                    updateCursorStatus(selection.endLine, selection.endColumn)
                }
            }
            editor.view.layoutDirection = LAYOUT_DIRECTION_LTR

            // Initialize to "new file" expected state: only Run enabled.
            // zh-CN: 初始化为 "新建文件" 预期状态: 仅运行可用.
            if (resetInitialState) {
                mEditorLoading = false
                mLargeFileMode = false
                saveStickyDirty = false
                mHadDirectEditSinceSave = false
                setMenuItemStatus(R.id.run, isRunEnabledForCurrentDocument())
                setMenuItemStatus(R.id.undo, false)
                setMenuItemStatus(R.id.redo, false)
                setMenuItemStatus(R.id.save, false)
            }
        }
    }

    private fun autoComplete(line: String, cursor: Int) {
        mAutoCompletion?.onCursorChange(line, cursor)
    }

    private fun updateEditorStatusMetadata() {
        val displayName = mDocumentDisplayName ?: name
        mEditorFileNameStatus.text = displayName
        mEditorFileNameStatus.contentDescription =
            "${context.getString(R.string.text_file_details)}: $displayName"
        val fileDetailsAvailable = !uri?.path.isNullOrEmpty()
        mEditorFileNameStatus.isClickable = fileDetailsAvailable
        mEditorFileNameStatus.isFocusable = fileDetailsAvailable
        mEditorEncodingStatus.visibility = if (uri == null) GONE else VISIBLE
    }

    private fun updateCursorStatus(line: Int, column: Int) {
        val position = "${line.coerceAtLeast(0) + 1}:${column.coerceAtLeast(0) + 1}"
        mEditorCursorStatus.text = position
        mEditorCursorStatus.contentDescription =
            "$position, ${context.getString(R.string.text_jump_to_line)}"
    }

    private fun showCursorStatusPlaceholder() {
        mCursorStatusReady = false
        mEditorCursorStatus.text = CURSOR_STATUS_PLACEHOLDER
        mEditorCursorStatus.contentDescription =
            "${context.getString(R.string.text_loading_with_dots)}, " +
                context.getString(R.string.text_jump_to_line)
    }

    private fun setTheme(theme: Theme?) {
        applyEditorTheme(theme)
    }

    private fun applyEditorTheme(theme: Theme?): Theme? =
        themeFontService.applyTheme(theme)?.also(::onEditorThemeApplied)

    private fun persistAndApplyEditorTheme(theme: Theme): Theme =
        themeFontService.persistAndApplyTheme(theme).also(::onEditorThemeApplied)

    private fun onEditorThemeApplied(appliedTheme: Theme) {
        setBackgroundColor(appliedTheme.backgroundColor)
        val colors = currentEditorUiPalette()
        mFunctionsKeyboard.setEditorPalette(
            appliedTheme.imeBarBackgroundColor,
            themeFontService.inputMethodBarForegroundColor(appliedTheme),
        )
        mDocsPaneController.applyPalette(colors.background, colors.foreground)
        syncThemePreviewWithAppliedTheme(appliedTheme)
        mFullscreenHintSnackbar?.let(::applyFullscreenHintPalette)
        showPendingFullscreenHint()
    }

    @SuppressLint("CheckResult")
    private fun applyCurrentEditorThemePreference() {
        themeFontService.loadCurrentTheme()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ theme -> setTheme(theme) }) { error ->
                showToast(context, error.message ?: error.toString(), true)
            }
    }

    private fun applyCurrentEditorEnginePreference() {
        val targetType = EditorEnginePreferences.get()
        if (
            targetType == EditorEngineType.NATIVE &&
            editor.engineType == EditorEngineType.ACE &&
            !AceEditorPluginStateMonitor.current(context).available
        ) {
            fallbackToNativeFromPluginState(AceEditorPluginStateMonitor.latestChange(context))
            return
        }
        switchEditorEngine(targetType)
    }

    fun onBackPressed(): Boolean {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            if (mDocsPaneController.canGoBack()) {
                mDocsPaneController.goBack()
            } else {
                mDrawerLayout.closeDrawer(GravityCompat.START)
            }
            return true
        }
        if ((editor as? AceScriptEditorEngine)?.handleSelectionBackIfNeeded() == true) {
            return true
        }
        if (exitFullscreenModeIfNeeded()) {
            return true
        }
        if (mDraftRestoreInProgress || mFileSaveInProgress || mSaveThenReloadInProgress ||
            mProjectRenameInProgress ||
            mSaveAsInProgress ||
            (mEncodingOperationInProgress && mEncodingReloadRollback == null)
        ) {
            showSnack(this, R.string.text_please_wait)
            return true
        }
        return false
    }

    override fun onToolbarMenuItemClick(id: Int) {
        val action = actionForToolbarItem(id)
        when (id) {
            R.id.action_log -> openEditorLogByPreference()
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
        action?.let(::recordAceHistoryAction)
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
        if (!isRunEnabledForCurrentDocument()) return
        val runOrigin = captureEditorRunOrigin()
        runScriptTextProvider.saveToTmpFile()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ file: File ->
                if (!isEditorRunOriginCurrent(runOrigin)) {
                    discardRunFile(file)
                    showSnack(this, R.string.text_please_wait)
                    return@subscribe
                }
                val execution = try {
                    runTmpFile(file, runOrigin)
                } catch (error: Throwable) {
                    discardRunFile(file)
                    throw error
                }
                if (execution == null) {
                    discardRunFile(file)
                }
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
    fun run(showMessage: Boolean, file: File? = null, overriddenFullPath: String? = null): ScriptExecution? {
        if (!isRunEnabledForCurrentDocument()) return null
        return runWithOrigin(
            showMessage = showMessage,
            file = file,
            origin = captureEditorRunOrigin(overriddenFullPath),
        )
    }

    private fun runWithOrigin(
        showMessage: Boolean,
        file: File?,
        origin: EditorRunOrigin,
    ): ScriptExecution? {
        val originPath = origin.path
        val executableFile = file
            ?: originPath?.let(::File)
            ?: return null
        if (showMessage) {
            showSnack(this, R.string.text_start_running)
        }
        val workingDirectory = originPath
            ?.let { File(it).parent }
            ?: WorkingDirectoryUtils.path
        val execution = runWithBroadcastSenderInteractive(
            context = context,
            file = executableFile,
            workingDirectory = workingDirectory,
            overriddenFullPath = originPath,
            originUri = origin.uriText,
            semanticDisplayName = origin.displayName,
            originMimeType = origin.mimeType,
        ) ?: return null
        scriptExecutionId = execution.id
        syncPrimaryMenuState()
        notifyScriptRunningStateChanged()
        return execution
    }

    private fun runTmpFile(file: File, origin: EditorRunOrigin): ScriptExecution? {
        return runWithOrigin(
            !shouldAutoShowLogBottomSheetOnRun(),
            file,
            origin,
        )
    }

    private fun captureEditorRunOrigin(overriddenFullPath: String? = null): EditorRunOrigin {
        val currentUri = uri
        return EditorRunOrigin(
            documentIdentityGeneration = mDocumentIdentityGeneration,
            fileLoadGeneration = mFileLoadGeneration.get(),
            uri = currentUri,
            uriText = currentUri?.toString(),
            path = overriddenFullPath ?: ScriptOriginResolver.resolveFilePath(context, currentUri),
            displayName = mDocumentDisplayName ?: name,
            mimeType = currentUri?.let(context.contentResolver::getType),
        )
    }

    private fun isEditorRunOriginCurrent(origin: EditorRunOrigin): Boolean =
        !mDestroyed &&
            origin.documentIdentityGeneration == mDocumentIdentityGeneration &&
            origin.fileLoadGeneration == mFileLoadGeneration.get() &&
            origin.uri == uri

    private fun discardRunFile(file: File) {
        if (mTmpSavedFileForRunning == file) {
            mTmpSavedFileForRunning = null
        }
        if (file.exists() && !file.delete()) {
            file.deleteOnExit()
        }
    }

    private fun undo() {
        if (!beginHistoryNavigation(EditorHistoryDirection.UNDO)) return
        mCommandDispatcher.undo()

        // Recompute Save state after history navigation.
        // zh-CN: 历史导航后重新计算保存按钮状态.
        saveStickyDirty = editor.isTextChanged || mHadDirectEditSinceSave
        syncPrimaryMenuState()
        schedulePrimaryMenuStateSync()
    }

    private fun redo() {
        if (!beginHistoryNavigation(EditorHistoryDirection.REDO)) return
        mCommandDispatcher.redo()

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
        schedulePrimaryMenuStateSync()
    }

    fun save(): Observable<String> {
        return Observable.defer {
            if (mDraftRestoreInProgress || mFileSaveInProgress || mSaveAsInProgress ||
                mProjectRenameInProgress ||
                mEditorLoading || editor.isLoadingText
            ) {
                return@defer Observable.error(
                    IllegalStateException(context.getString(R.string.text_please_wait)),
                )
            }
            if (mFileStateRequiresReload) {
                return@defer Observable.error(
                    IllegalStateException(
                        context.getString(R.string.error_file_state_unknown_reload_required),
                    ),
                )
            }
            if (mCurrentHadDecodingErrors && !mCurrentDecodingErrorsWereAccepted) {
                return@defer Observable.error(
                    IllegalStateException(
                        context.getString(R.string.error_file_contains_unaccepted_decoding_replacements),
                    ),
                )
            }
            if (mDraftEncodingValidationPending) {
                return@defer Observable.error(
                    IllegalStateException(
                        context.getString(R.string.error_editor_encoding_validation_incomplete),
                    ),
                )
            }

            mFileSaveInProgress = true
            syncPrimaryMenuState()

            editorTextProvider.getTextForSaving()
                .flatMap { latestText ->
                    // getTextForSaving() is delivered on the main thread, so the text and revision
                    // form one consistent editor snapshot.
                    // zh-CN: getTextForSaving() 在主线程回传, 因此文本与修订号构成一致快照.
                    val currentUri = uri ?: return@flatMap Observable.error(
                        IllegalStateException(
                            context.getString(R.string.error_unable_to_save_file_as_current_file_path_is_unknown),
                        ),
                    )
                    val encoding = EditorFileSaveEncoding(
                        charset = mCurrentCharset,
                        hadBom = mHadBom,
                    )
                    val previousEncoding = mPersistedFileEncoding
                    val writeResult = SaveWriteResult(
                        text = latestText,
                        revision = mTextRevision,
                        currentUri = currentUri,
                        encoding = encoding,
                    )
                    val displayName = name
                    Observable.fromCallable {
                        // Use a transactional save flow for both file:// and content://.
                        // zh-CN: 对 file:// 与 content:// 统一使用事务式保存流程.
                        fileService.saveTextTransactional(
                            uri = currentUri,
                            text = latestText,
                            encoding = encoding,
                            displayName = displayName,
                            previousEncoding = previousEncoding,
                            discardStableDraftOnSuccess = false,
                        )
                        writeResult
                    }.subscribeOn(Schedulers.io())
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError { error ->
                    if (!mDestroyed && error is EditorFileRollbackFailedException) {
                        markFileStateRequiresReload()
                    }
                    if (!mDestroyed && uri?.let { PrivilegedEditSessions.isSessionUri(context, it) } == true) {
                        mPrivilegedDraftNeedsSave = true
                        markRestoredDraftAsDirty()
                    }
                }
                .doOnNext { result ->
                    if (mDestroyed) return@doOnNext
                    mPrivilegedDraftNeedsSave = false
                    mCurrentFileSizeBytes = null
                    // The write has completed even if the editor changed while IO was in flight.
                    // Keep the external disk baseline independent from undo/redo history state.
                    mPersistedFileEncoding = result.encoding

                    if (mTextRevision == result.revision &&
                        mCurrentCharset == result.encoding.charset &&
                        mHadBom == result.encoding.hadBom
                    ) {
                        mCurrentHadDecodingErrors = false
                        mCurrentDecodingErrorsWereAccepted = false
                        fileService.discardStableDraft(result.currentUri)
                        editor.markTextAsSaved()
                        syncEditorDocumentPath()

                        // Reset sticky dirty only when the current text is the exact snapshot that
                        // reached disk. Edits made while IO was in flight remain dirty.
                        // zh-CN: 仅当当前文本正是已写入磁盘的快照时重置 sticky 脏标记.
                        // IO 期间产生的新编辑必须继续保持未保存状态.
                        saveStickyDirty = false
                        mHadDirectEditSinceSave = false
                        setMenuItemStatus(R.id.save, false)
                    } else {
                        saveStickyDirty = true
                        syncPrimaryMenuState()
                    }
                }
                .map { it.text }
                .doFinally {
                    mUiHandler.post {
                        if (mDestroyed) return@post
                        mFileSaveInProgress = false
                        syncPrimaryMenuState()
                    }
                }
        }
    }

    private fun markFileStateRequiresReload() {
        mFileStateRequiresReload = true
        updateEncodingStatus()
        syncPrimaryMenuState()
    }

    fun showVersionHistoryDialog() {
        if (mEncodingOperationInProgress || mFileSaveInProgress || mSaveAsInProgress ||
            mEditorLoading || editor.isLoadingText || mDraftRestoreInProgress
        ) {
            showSnack(this, R.string.text_please_wait)
            return
        }
        versionHistoryService.showDialog(uri)
    }

    fun showCreateSnapshotDialog() {
        if (mEncodingOperationInProgress || mFileSaveInProgress || mSaveAsInProgress ||
            mEditorLoading || editor.isLoadingText || mDraftRestoreInProgress
        ) {
            showSnack(this, R.string.text_please_wait)
            return
        }
        versionHistoryService.showCreateSnapshotDialog(uri, mCurrentCharset, mHadBom)
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
                if (!showPrivilegedSaveError(e)) {
                    showSnack(this, e.message, true)
                    showToast(context, e.message, true)
                }
            }
    }

    @SuppressLint("CheckResult")
    fun showPrivilegedSaveError(error: Throwable): Boolean {
        val currentUri = uri?.takeIf { PrivilegedEditSessions.isSessionUri(context, it) } ?: return false
        if (mDestroyed) return true
        editorTextProvider.getTextForSaving()
            .take(1)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ text ->
                if (!mDestroyed && uri == currentUri) {
                    fileService.showPrivilegedSaveFailure(
                        uri = currentUri,
                        displayName = name,
                        text = text,
                        encoding = EditorFileSaveEncoding(mCurrentCharset, mHadBom),
                        error = error,
                        configureAccess = { showPrivilegedAccessOptions(currentUri) },
                    )
                }
            }, { readError ->
                showSnack(this, error.message ?: readError.message, true)
            })
        return true
    }

    @SuppressLint("CheckResult")
    private fun showPrivilegedAccessOptions(currentUri: Uri) {
        if (mDestroyed || uri != currentUri) return
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_explorer_access_mode)
                .content(R.string.text_privileged_edit_access_hint)
                .items(
                    context.getString(R.string.text_explorer_access_shizuku),
                    context.getString(R.string.text_explorer_access_root),
                )
                .itemsCallback { _, _, which, _ ->
                    val mode = if (which == 0) PrivilegedAccessMode.SHIZUKU else PrivilegedAccessMode.ROOT
                    Observable.fromCallable { PrivilegedEditSessions.changeAccessMode(context, currentUri, mode) }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            if (!mDestroyed && uri == currentUri && mode == PrivilegedAccessMode.SHIZUKU) {
                                val permission = ShizukuPermission(context)
                                if (!permission.has()) permission.request()
                            }
                        }, { error -> showSnack(this, error.message, true) })
                }
                .negativeText(R.string.dialog_button_cancel)
                .build()
        }
    }

    private fun findNext() = editor.findNext()

    private fun findPrev() = editor.findPrev()

    private fun showNormalToolbar() {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.toolbar_menu, mNormalToolbar)
            .commitAllowingStateLoss()

        // setUri may have run before this toolbar view existed; refresh visibility and menu state.
        post { refreshDocumentRunPolicy() }
    }

    fun replace() {
        mCommandDispatcher.replaceSelection()
    }

    fun showConsole() {
        doWithCurrentEngine { engine: ScriptEngine<*> -> (engine as JavaScriptEngine).runtime.console.show() }
    }

    fun openByOtherApps() {
        if (showPrivilegedExternalOpenNotice()) return
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

    // region 3-Block Compose designer hand-over (plugin roadmap P8.4 / D39 / Q10)

    private var mDesignerHandoff: DesignerHandoff? = null

    private class DesignerHandoff(val uri: Uri, val fingerprint: String?)

    fun isThreeBlockComposeDesignerAvailable(): Boolean =
        uri?.let { !PrivilegedEditSessions.isSessionUri(context, it) } != false &&
            ThreeBlockComposeDesigner.isAvailable(context)

    /** Saves the current file when needed, then opens it in the plugin's designer with a read / write grant. */
    fun openInThreeBlockComposeDesigner() {
        if (showPrivilegedExternalOpenNotice()) return
        val currentUri = uri ?: run {
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.error_unable_to_open_with_other_apps_as_current_file_path_is_unknown)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .build()
            }
            return
        }
        if (!mReadOnly && (isTextChanged || saveStickyDirty)) {
            save()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ handOverToDesigner(currentUri) }) { e: Throwable ->
                    e.printStackTrace()
                    showSnack(this, e.message, true)
                }
        } else {
            handOverToDesigner(currentUri)
        }
    }

    @SuppressLint("CheckResult")
    private fun showPrivilegedExternalOpenNotice(): Boolean {
        val currentUri = uri?.takeIf { PrivilegedEditSessions.isSessionUri(context, it) } ?: return false
        if (mFileSaveInProgress || mSaveAsInProgress || mEditorLoading || editor.isLoadingText ||
            mEncodingOperationInProgress || mDraftRestoreInProgress
        ) {
            showSnack(this, R.string.text_please_wait)
            return true
        }
        editorTextProvider.getTextForSaving()
            .take(1)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ text ->
                if (mDestroyed || uri != currentUri) return@subscribe
                val encoding = EditorFileSaveEncoding(mCurrentCharset, mHadBom)
                DialogUtils.buildAndShowAdaptive {
                    MaterialDialog.Builder(context)
                        .title(R.string.text_explorer_privileged_access)
                        .content(R.string.text_privileged_edit_external_apps)
                        .negativeText(R.string.dialog_button_cancel)
                        .onNegative { dialog, _ -> dialog.dismiss() }
                        .positiveText(R.string.dialog_button_save_as)
                        .positiveColorRes(R.color.dialog_button_attraction)
                        .onPositive { dialog, _ ->
                            fileService.saveCopyFromDialog(dialog, currentUri, name, text, encoding)
                        }
                        .autoDismiss(false)
                        .build()
                }
            }, { error ->
                if (!mDestroyed) showSnack(this, error.message, true)
            })
        return true
    }

    private fun handOverToDesigner(currentUri: Uri) {
        val shared = if (currentUri.scheme == ContentResolver.SCHEME_FILE) {
            IntentUtils.getUriOfFile(context, currentUri.path)
        } else {
            currentUri
        }
        mDesignerHandoff = DesignerHandoff(currentUri, designerFingerprint(currentUri))
        val started = ThreeBlockComposeDesigner.openIntent(shared, writable = !mReadOnly).startSafely(context, true)
        if (!started) {
            mDesignerHandoff = null
            showToast(context, R.string.error_no_applications_available_for_editing_this_file, true)
        }
    }

    private fun designerFingerprint(currentUri: Uri): String? = try {
        ThreeBlockComposeDesigner.fingerprint(context.contentResolver.openInputStream(currentUri))
    } catch (e: Exception) {
        null
    }

    /**
     * Called when the editor returns to the foreground after a designer hand-over: a file the designer wrote
     * back is reloaded through the existing encoding-preserving reload; unsaved edits here are asked about first.
     */
    fun reloadAfterDesignerIfChanged() {
        val handoff = mDesignerHandoff ?: return
        mDesignerHandoff = null
        val currentUri = uri ?: return
        if (currentUri != handoff.uri) return
        val now = designerFingerprint(currentUri)
        if (now == null || now == handoff.fingerprint) return
        if (!isTextChanged && !saveStickyDirty) {
            reloadWithEncoding(currentUri, mCurrentCharset)
            return
        }
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_designer_changed_file_title)
                .content(R.string.description_designer_changed_file_reload_or_keep)
                .positiveText(R.string.dialog_button_reload)
                .negativeText(R.string.dialog_button_cancel)
                .onPositive { _, _ -> reloadWithEncoding(currentUri, mCurrentCharset) }
                .build()
        }
    }

    // endregion

    fun showFindOrReplaceDialog() {
        showFindOrReplaceDialog(null)
    }

    private fun showFindOrReplaceDialog(payload: JSONObject?) {
        val payloadQuery = payload?.optNonBlankString(PAYLOAD_QUERY)
        val preferredQuery = payloadQuery ?: getPreferredSearchQueryForDialogOrNull()
        val builder = FindOrReplaceDialogBuilder(context, this)
        if (payload?.has(PAYLOAD_USING_REGEX) == true) {
            builder.setUsingRegex(payload.optBoolean(PAYLOAD_USING_REGEX))
        } else if (preferredQuery != null) {
            builder.setUsingRegex(getPreferredSearchUsingRegexForDialog())
        }
        builder.setQueryIfNotEmpty(preferredQuery ?: editor.selectedTextSnapshot)
        builder.setReplacement(payload?.optString(PAYLOAD_REPLACEMENT))
        builder.setReplaceMode(
            replace = payload?.optBoolean(PAYLOAD_REPLACE, false) == true,
            replaceAll = payload?.optBoolean(PAYLOAD_REPLACE_ALL, false) == true,
        )
        builder.positiveColorRes(R.color.dialog_button_attraction)
        builder.show()
    }

    fun showJumpToLineDialog() {
        showJumpToLineDialog(null)
    }

    private fun showJumpToLineDialog(initialLine: Int?) {
        val lineCount = editor.lineCountSnapshot.coerceAtLeast(1)
        val initialInput = initialLine
            ?.coerceIn(1, lineCount)
            ?.toString()
            .orEmpty()
        val builder = MaterialDialog.Builder(context)
            .title(R.string.text_jump_to_line)
            .input("1 - $lineCount", initialInput) { _, input ->
                if (input.isNullOrEmpty()) {
                    return@input
                }
                val line = input.toString().toIntOrNull() ?: return@input
                val targetLine = line.coerceIn(1, lineCount)
                mCommandDispatcher.jumpTo(targetLine - 1, 0)
                recordAceJumpToLineHistory(targetLine)
            }
            .inputType(InputType.TYPE_CLASS_NUMBER)
            .positiveColorRes(R.color.dialog_button_attraction)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .widgetThemeColor()
        val dialog = builder.show()
        dialog.inputEditText?.addTextChangedListener(
            SimpleTextWatcher { input ->
                jumpToLineUpperBoundCorrection(input, lineCount)?.let { maximum ->
                    input.replace(0, input.length, maximum)
                }
            },
        )
    }

    fun showEncodingDialog() {
        val currentUri = uri
        if (currentUri == null) {
            showEncodingOperationError(
                context.getString(R.string.error_unable_to_change_file_encoding_as_current_file_path_is_unknown),
            )
            return
        }
        if (!canChangeFileEncoding()) return

        val currentCharset = mCurrentCharset
        val currentHadBom = mHadBom
        // Acceptance controls ordinary Save only. Convert would still make replacement characters
        // permanent on disk, so its risk remains yellow after an explicitly accepted Reload.
        // zh-CN: 接受状态仅控制普通保存. Convert 仍会把替换字符永久写入磁盘,
        // 因此即使用户已明确接受 Reload, 转换风险仍应保持黄色.
        val currentTextHadDecodingErrors = mCurrentHadDecodingErrors
        mEncodingDialogLoading = true
        mEditorEncodingStatus.isEnabled = false
        mEditorEncodingStatus.setText(R.string.text_loading_with_dots)

        mEncodingDialogDisposable?.dispose()
        mEncodingDialogDisposable = editorTextProvider.getTextForSaving()
            .flatMap { currentText ->
                Observable.fromCallable {
                    val probe = fileService.readEncodingProbe(currentUri)
                    val detection = StringUtils.detectEncoding(probe.bytes, endOfInput = !probe.truncated)
                    val riskProbe = if (probe.bytes.size > ENCODING_RISK_PROBE_LIMIT_BYTES) {
                        probe.copy(
                            bytes = probe.bytes.copyOf(ENCODING_RISK_PROBE_LIMIT_BYTES),
                            truncated = true,
                        )
                    } else {
                        probe
                    }
                    val textSample = EditorEncodingCodec.takeCodePointSafePrefix(
                        currentText,
                        ENCODING_TEXT_PROBE_LIMIT_CHARS,
                    )
                    EncodingDialogData(
                        candidates = EditorEncodingRiskEvaluator.evaluate(
                            probe = riskProbe,
                            currentTextSample = textSample,
                            currentTextTruncated = currentText.length > textSample.length,
                            currentCharset = currentCharset,
                            currentHadBom = currentHadBom,
                            detection = detection,
                            currentTextHadDecodingErrors = currentTextHadDecodingErrors,
                        ),
                    )
                }.subscribeOn(Schedulers.io())
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally {
                mEncodingDialogLoading = false
                post {
                    mEditorEncodingStatus.isEnabled = true
                    updateEncodingStatus()
                }
            }
            .subscribe({ data ->
                if (mFileSaveInProgress || mSaveAsInProgress ||
                    mEncodingOperationInProgress || mEditorLoading ||
                    editor.isLoadingText || mLargeFileFrameCallback != null
                ) {
                    return@subscribe
                }
                DialogUtils.buildAndShowAdaptive {
                    EditorEncodingDialog.build(
                        context = context,
                        candidates = data.candidates,
                        onSelected = ::showReloadOrConvertEncodingDialog,
                    )
                }
            }, { error ->
                showEncodingOperationError(error.message ?: error.toString())
            })
    }

    private fun showReloadOrConvertEncodingDialog(candidate: EditorEncodingCandidate) {
        if (!canChangeFileEncoding()) return
        val currentUri = uri ?: return
        val targetName = candidate.charset.name()
        val supportsBom = EditorEncodingDetector.supportsBom(candidate.charset)
        val initialAddBom = EditorEncodingDetector.shouldWriteBomOnConvert(
            targetCharset = candidate.charset,
            currentCharset = mCurrentCharset,
            currentHadBom = mHadBom,
        )
        val details = buildString {
            append(
                context.getString(
                    R.string.description_editor_encoding_reload_or_convert,
                    targetName,
                    name,
                ),
            )
            if (saveStickyDirty || editor.isTextChanged) {
                append("\n\n")
                append(context.getString(R.string.description_editor_encoding_reload_discards_unsaved_changes))
            }
            if (candidate.reloadRisk == EditorEncodingRisk.ERROR) {
                append("\n\n")
                append(context.getString(R.string.description_editor_encoding_reload_replaces_invalid_bytes))
            }
            if (candidate.convertRisk == EditorEncodingRisk.ERROR) {
                append("\n\n")
                append(context.getString(R.string.description_editor_encoding_convert_unavailable, targetName))
            } else if (candidate.convertRisk == EditorEncodingRisk.WARNING) {
                append("\n\n")
                append(
                    context.getString(
                        if (mCurrentHadDecodingErrors) {
                            R.string.description_editor_encoding_convert_persists_replacement_characters
                        } else {
                            R.string.text_encoding_may_change_content
                        },
                    ),
                )
            }
            if (mReadOnly) {
                append("\n\n")
                append(context.getString(R.string.description_editor_encoding_convert_unavailable_read_only))
            }
            if (mFileStateRequiresReload) {
                append("\n\n")
                append(context.getString(R.string.error_file_state_unknown_reload_required))
            }
        }
        val builder = MaterialDialog.Builder(context)
            .title(context.getString(R.string.text_reload_or_convert_to_encoding, targetName))
            .content(details)
            .neutralText(R.string.dialog_button_reload)
            .neutralColorRes(
                if (candidate.reloadRisk == EditorEncodingRisk.SAFE &&
                    !saveStickyDirty && !editor.isTextChanged
                ) {
                    R.color.dialog_button_hint
                } else {
                    R.color.dialog_button_warn
                },
            )
            .onNeutral { _, _ -> requestReloadWithEncoding(currentUri, candidate.charset) }
            .negativeText(R.string.dialog_button_convert)
            .negativeColorRes(
                if (candidate.convertRisk == EditorEncodingRisk.SAFE) {
                    R.color.dialog_button_attraction
                } else {
                    R.color.dialog_button_warn
                },
            )
            .onNegative { dialog, _ ->
                convertToEncoding(
                    charset = candidate.charset,
                    addBom = supportsBom && dialog.isPromptCheckBoxChecked,
                )
            }
            .positiveText(R.string.dialog_button_cancel)
            .positiveColorRes(R.color.dialog_button_default)
            .widgetThemeColor()
        if (supportsBom) {
            builder.checkBoxPrompt(
                context.getString(R.string.text_add_bom),
                initialAddBom,
                null,
            )
        }
        val dialog = builder.build()
        dialog.setOnShowListener {
            dialog.getActionButton(DialogAction.NEGATIVE).isEnabled =
                !mReadOnly && !mFileStateRequiresReload &&
                    candidate.convertRisk != EditorEncodingRisk.ERROR
        }
        DialogUtils.buildAndShowAdaptive {
            if (supportsBom) DialogUtils.fixCheckBoxGravity(dialog) else dialog
        }
    }

    private fun requestReloadWithEncoding(currentUri: Uri, charset: Charset) {
        if (saveStickyDirty || editor.isTextChanged) {
            showSaveBeforeEncodingReloadDialog(currentUri, charset)
        } else {
            reloadWithEncoding(currentUri, charset)
        }
    }

    private fun showSaveBeforeEncodingReloadDialog(currentUri: Uri, charset: Charset) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_unsaved_changes)
                .content(context.getString(R.string.description_editor_encoding_reload_save_prompt, name))
                .positiveText(R.string.dialog_button_save_and_reload)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { _, _ -> saveBeforeEncodingReload(currentUri, charset) }
                .neutralText(R.string.dialog_button_reload_without_saving)
                .neutralColorRes(R.color.dialog_button_warn)
                .onNeutral { _, _ -> reloadWithEncoding(currentUri, charset) }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .widgetThemeColor()
                .build()
        }
    }

    private fun saveBeforeEncodingReload(currentUri: Uri, charset: Charset) {
        if (mSaveThenReloadInProgress) return
        mSaveThenReloadInProgress = true
        if (!mReadOnly) {
            editor.setReadOnly(true)
        }
        activity.invalidateOptionsMenu()
        save()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { /* Wait for save()'s finalizer before starting another editor operation. */ },
                { error ->
                    afterSaveThenReloadFinalized {
                        showEncodingOperationError(error.message ?: error.toString())
                    }
                },
                { reloadAfterSaveFinalized(currentUri, charset) },
            )
    }

    private fun afterSaveThenReloadFinalized(onReleased: () -> Unit) {
        post {
            if (mDestroyed) return@post
            if (mFileSaveInProgress) {
                afterSaveThenReloadFinalized(onReleased)
                return@post
            }
            mSaveThenReloadInProgress = false
            if (!mReadOnly && !mEditorLoading && !mEncodingOperationInProgress &&
                !mSaveAsInProgress && !mDraftRestoreInProgress
            ) {
                editor.setReadOnly(false)
            }
            syncPrimaryMenuState()
            activity.invalidateOptionsMenu()
            onReleased()
        }
    }

    private fun reloadAfterSaveFinalized(currentUri: Uri, charset: Charset) {
        post {
            if (mDestroyed) return@post
            if (mFileSaveInProgress) {
                reloadAfterSaveFinalized(currentUri, charset)
            } else {
                mSaveThenReloadInProgress = false
                if (!reloadWithEncoding(currentUri, charset) && !mReadOnly) {
                    editor.setReadOnly(false)
                    syncPrimaryMenuState()
                }
                activity.invalidateOptionsMenu()
            }
        }
    }

    private fun reloadWithEncoding(currentUri: Uri, charset: Charset): Boolean {
        if (mEncodingOperationInProgress || mFileSaveInProgress || mSaveAsInProgress ||
            mEditorLoading || editor.isLoadingText || mDraftRestoreInProgress
        ) {
            return false
        }
        val historyBefore = captureEncodingHistoryState()
        val rollback = EncodingReloadRollback(
            uri = currentUri,
            text = editor.textSnapshot,
            charset = mCurrentCharset,
            charsetConfidence = mCurrentCharsetConfidence,
            hadBom = mHadBom,
            hadDecodingErrors = mCurrentHadDecodingErrors,
            decodingErrorsWereAccepted = mCurrentDecodingErrorsWereAccepted,
            draftEncodingValidationPending = mDraftEncodingValidationPending,
            fileEncodingValidationComplete = mFileEncodingValidationComplete,
            saveStickyDirty = saveStickyDirty,
            hadDirectEditSinceSave = mHadDirectEditSinceSave,
            draftRestoredInThisSession = mDraftRestoredInThisSession,
            restoredText = mRestoredText,
            largeFileMode = mLargeFileMode,
            fileStateRequiresReload = mFileStateRequiresReload,
        )
        mEncodingReloadRollback = rollback
        mEncodingOperationGeneration++
        mEncodingReloadReplacementStarted = false
        mEncodingOperationInProgress = true
        mEditorEncodingStatus.isEnabled = false
        mEditorLoading = true
        mDraftRestoredInThisSession = false
        mRestoredText = null
        beginEditorLoadingUi(context.getString(R.string.text_loading_with_dots))
        syncPrimaryMenuState()

        mEncodingOperationDisposable?.dispose()
        mEncodingOperationDisposable = Observable.fromCallable {
            EncodingReloadReadResult(
                readResult = fileService.readText(currentUri, charset),
                sizeOrNull = runCatching { fileService.queryContentLengthOrNull(currentUri) }.getOrNull(),
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                if (mDestroyed || mEncodingReloadRollback !== rollback) return@subscribe
                val token = UUID.randomUUID().toString()
                val historyAfter = historyBefore.copy(
                    charset = result.readResult.metadata.charset,
                    charsetConfidence = result.readResult.metadata.charsetConfidence,
                    hadBom = result.readResult.metadata.hadBom,
                    hadDecodingErrors = result.readResult.metadata.hadDecodingErrors,
                    decodingErrorsWereAccepted = result.readResult.metadata.decodingErrorsWereAccepted,
                    draftEncodingValidationPending = false,
                    fileEncodingValidationComplete = true,
                )
                mEncodingReloadReplacementStarted = true
                editor.setLoadingText(true)
                editor.setRedoUndoEnabled(true)
                editor.replaceAllTextUndoably(result.readResult.text, token) { succeeded ->
                    post {
                        if (mDestroyed || mEncodingReloadRollback !== rollback) return@post
                        if (!succeeded) {
                            rollbackEncodingReload(
                                IllegalStateException(context.getString(R.string.text_failed)),
                            )
                            return@post
                        }
                        mEncodingHistoryEntries[token] = EncodingHistoryEntry(historyBefore, historyAfter)
                        applyEncodingHistoryState(historyAfter)
                        mPersistedFileEncoding = EditorFileSaveEncoding(
                            charset = result.readResult.metadata.charset,
                            hadBom = result.readResult.metadata.hadBom,
                        )
                        mFileStateRequiresReload = false
                        mCurrentFileSizeBytes = result.sizeOrNull
                        mDraftRestoredInThisSession = false
                        mRestoredText = null
                        mLargeFileMode = false
                        mTextRevision++
                        editor.markTextAsSaved()
                        editor.setLoadingText(false)
                        saveStickyDirty = false
                        mHadDirectEditSinceSave = false
                        preservePendingPrivilegedDraft()
                        finishEncodingReload(rollback)
                    }
                }
            }, { error ->
                if (mEncodingOperationInProgress && !rollbackEncodingReload(error)) {
                    showEncodingOperationError(error.message ?: error.toString())
                }
            })
        return true
    }

    private fun finishEncodingReload(rollback: EncodingReloadRollback) {
        if (mEncodingReloadRollback !== rollback) return
        mEncodingReloadRollback = null
        mEncodingOperationGeneration++
        mEncodingReloadReplacementStarted = false
        mEncodingOperationInProgress = false
        mEncodingOperationDisposable?.dispose()
        mEncodingOperationDisposable = null
        mEditorEncodingStatus.isEnabled = true
        mEditorLoading = false
        endEditorLoadingUi()
        updateEncodingStatus()
        syncPrimaryMenuState()
        refreshAceStateAfterEncodingOperation()
        fileService.discardStableDraft(rollback.uri)
    }

    private fun rollbackEncodingReload(error: Throwable, showError: Boolean = true): Boolean {
        val rollback = mEncodingReloadRollback ?: return false
        mEncodingReloadRollback = null
        mEncodingOperationGeneration++
        val replacementStarted = mEncodingReloadReplacementStarted
        mEncodingReloadReplacementStarted = false
        mEncodingOperationInProgress = false
        mEncodingOperationDisposable?.dispose()
        mEncodingOperationDisposable = null
        mEditorEncodingStatus.isEnabled = true
        if (replacementStarted) runCatching {
            try {
                textLoadTarget.loadRestoredText(
                    rollback.text,
                    undoRedoEnabled = !rollback.largeFileMode,
                )
            } finally {
                editor.setLoadingText(false)
            }
            mEncodingHistoryEntries.clear()
        } else {
            editor.setLoadingText(false)
        }
        mCurrentCharset = rollback.charset
        mCurrentCharsetConfidence = rollback.charsetConfidence
        mHadBom = rollback.hadBom
        mCurrentHadDecodingErrors = rollback.hadDecodingErrors
        mCurrentDecodingErrorsWereAccepted = rollback.decodingErrorsWereAccepted
        mDraftEncodingValidationPending = rollback.draftEncodingValidationPending
        mFileEncodingValidationComplete = rollback.fileEncodingValidationComplete
        saveStickyDirty = rollback.saveStickyDirty
        mHadDirectEditSinceSave = rollback.hadDirectEditSinceSave
        mDraftRestoredInThisSession = rollback.draftRestoredInThisSession
        mRestoredText = rollback.restoredText
        mLargeFileMode = rollback.largeFileMode
        mFileStateRequiresReload = rollback.fileStateRequiresReload
        mEditorLoading = false
        updateEncodingStatus()
        endEditorLoadingUi()
        syncPrimaryMenuState()
        if (showError) {
            showEncodingOperationError(error.message ?: error.toString())
        }
        return true
    }

    private fun prepareEditorForAdoption(
        candidate: ScriptEditorEngine,
        snapshot: EditorStateSnapshot,
    ) {
        candidate.restoreStateSnapshot(snapshot)
        candidate.setReadOnly(snapshot.readOnly)
        candidate.setDocumentPath(currentDocumentPathOrName())
    }

    private fun recoverNativeEditorAfterFailedSwitch(
        oldEditor: ScriptEditorEngine,
        failedEditor: ScriptEditorEngine,
        snapshot: EditorStateSnapshot,
        wasAttached: Boolean,
        oldEditorLoading: Boolean,
        oldLargeFileMode: Boolean,
        oldSaveStickyDirty: Boolean,
        oldHadDirectEditSinceSave: Boolean,
    ) {
        if (wasAttached) {
            runCatching { unregisterFunctionsKeyboardBackHandler() }
        }
        mFunctionsKeyboardHelper?.hideFunctionsLayout(false)
        mFunctionsKeyboardHelper = null
        mAutoCompletion?.shutdown()
        mAutoCompletion = null
        runCatching { binding.editorContainer.removeView(failedEditor.view) }
        runCatching { binding.editorContainer.removeView(oldEditor.view) }
        if (failedEditor !== oldEditor) {
            runCatching { failedEditor.destroy() }
        }

        val rescueEditor = EditorEngineFactory.create(context, EditorEngineType.NATIVE)
        prepareEditorForAdoption(rescueEditor, snapshot)
        editor = rescueEditor
        mEncodingHistoryEntries.clear()
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
        applyCurrentEditorThemePreference()
        mEditorLoading = oldEditorLoading
        mLargeFileMode = oldLargeFileMode
        saveStickyDirty = oldSaveStickyDirty || snapshot.dirty
        mHadDirectEditSinceSave = oldHadDirectEditSinceSave || snapshot.dirty
        editor.setReadOnly(shouldKeepEditorReadOnlyAfterSwitch())
        setEnhanceBarInteractive(!shouldKeepEditorReadOnlyAfterSwitch())
        runCatching { oldEditor.destroy() }
        syncPrimaryMenuState()
        activity.invalidateOptionsMenu()
    }

    private fun shouldKeepEditorReadOnlyAfterSwitch(): Boolean =
        mReadOnly || mEditorLoading || mEncodingOperationInProgress || mFileSaveInProgress ||
            mSaveThenReloadInProgress || mSaveAsInProgress || mDraftRestoreInProgress

    private fun captureEditorTransferSnapshot(
        oldEditor: ScriptEditorEngine,
        latestText: String,
        stickyDirty: Boolean,
    ): EditorStateSnapshot {
        val snapshot = runCatching { oldEditor.createStateSnapshot() }.getOrElse {
            val selection = runCatching { oldEditor.selection }.getOrNull()
            EditorStateSnapshot(
                text = latestText,
                dirty = stickyDirty || runCatching { oldEditor.isTextChanged }.getOrDefault(true),
                cursorLine = selection?.endLine ?: 0,
                cursorColumn = selection?.endColumn ?: 0,
                selection = selection,
                firstVisibleLine = null,
                firstVisibleColumn = null,
                breakpoints = runCatching { oldEditor.breakpointsSnapshot }.getOrDefault(emptyList()),
                debuggingLine = -1,
                readOnly = shouldKeepEditorReadOnlyAfterSwitch(),
                fontSizeSp = runCatching { oldEditor.getTextSizeSp() }.getOrDefault(14f),
                themeKey = themeFontService.currentTheme?.name,
                searchQuery = null,
                searchOptions = null,
            )
        }
        return snapshot.copy(
            text = latestText,
            dirty = stickyDirty || snapshot.dirty || runCatching { oldEditor.isTextChanged }.getOrDefault(true),
            readOnly = shouldKeepEditorReadOnlyAfterSwitch(),
            themeKey = themeFontService.currentTheme?.name ?: snapshot.themeKey,
        )
    }

    private fun finishEditorEngineSwitch(
        generation: Long,
        reconcileDesired: Boolean,
    ) {
        if (generation != mEditorEngineSwitchGeneration) return
        val active = mActiveEditorEngineSwitch
        mActiveEditorEngineSwitch = null
        mSwitchingEditorEngine = false
        if (active?.reason == EditorEngineSwitchReason.PLUGIN_UNAVAILABLE ||
            active?.reason == EditorEngineSwitchReason.ACE_FAILURE
        ) {
            mAceFallbackInProgress.set(false)
        }
        var pending = mPendingEditorEngineSwitch.also { mPendingEditorEngineSwitch = null }
        if (mDestroyed) return

        val pluginState = AceEditorPluginStateMonitor.current(context)
        if (pending != null && isEditorEngineSwitchRequestValid(pending) &&
            !pending.recreateSameType && pending.targetType == editor.engineType
        ) {
            completeEditorEngineSwitchEffects(pending)
            pending = null
        }
        val nextRequest = when {
            editor.engineType == EditorEngineType.ACE && !pluginState.available ->
                EditorEngineSwitchRequest(
                    targetType = EditorEngineType.NATIVE,
                    reason = EditorEngineSwitchReason.PLUGIN_UNAVAILABLE,
                    pluginChange = AceEditorPluginStateMonitor.latestChange(context),
                )
            pending != null && isEditorEngineSwitchRequestValid(pending) &&
                (pending.recreateSameType || pending.targetType != editor.engineType) -> pending
            reconcileDesired && EditorEnginePreferences.get() != editor.engineType ->
                EditorEngineSwitchRequest(
                    targetType = EditorEnginePreferences.get(),
                    reason = EditorEngineSwitchReason.PREFERENCE,
                    showSwitchToast = true,
                )
            editor.engineType == EditorEngineType.ACE && pluginState.available &&
                mAceSessionInvalidated && EditorEnginePreferences.get() == EditorEngineType.ACE ->
                EditorEngineSwitchRequest(
                    targetType = EditorEngineType.ACE,
                    reason = EditorEngineSwitchReason.PLUGIN_RELOAD,
                    recreateSameType = true,
                    allowFactoryFallback = true,
                    pluginChange = AceEditorPluginStateMonitor.latestChange(context),
                )
            else -> null
        }
        nextRequest?.let { request -> post { enqueueEditorEngineSwitch(request) } }
    }

    private fun convertToEncoding(charset: Charset, addBom: Boolean) {
        if (mReadOnly || mEncodingOperationInProgress || mFileSaveInProgress ||
            mSaveAsInProgress || mEditorLoading || editor.isLoadingText || mDraftRestoreInProgress
        ) {
            return
        }
        if (mFileStateRequiresReload) {
            showEncodingOperationError(
                context.getString(R.string.error_file_state_unknown_reload_required),
            )
            return
        }
        if (mDestroyed) return
        val targetHadBom = EditorEncodingDetector.normalizeBomPreference(charset, addBom)
        if (charset == mCurrentCharset && targetHadBom == mHadBom &&
            !mCurrentHadDecodingErrors
        ) {
            return
        }

        mEncodingOperationInProgress = true
        val operationGeneration = ++mEncodingOperationGeneration
        mEditorEncodingStatus.isEnabled = false
        mEditorLoading = true
        beginEditorLoadingUi(context.getString(R.string.text_loading_with_dots))
        syncPrimaryMenuState()
        val historyBefore = captureEncodingHistoryState()
        val historyAfter = historyBefore.copy(
            charset = charset,
            charsetConfidence = 100,
            hadBom = targetHadBom,
            decodingErrorsWereAccepted = historyBefore.decodingErrorsWereAccepted ||
                historyBefore.hadDecodingErrors,
            draftEncodingValidationPending = false,
            fileEncodingValidationComplete = true,
        )
        mEncodingOperationDisposable?.dispose()
        mEncodingOperationDisposable = editorTextProvider.getTextForSaving()
            .flatMap { latestText ->
                Observable.fromCallable {
                    if (!EditorEncodingCodec.canEncode(latestText, charset)) {
                        throw IllegalArgumentException(
                            context.getString(R.string.error_text_cannot_be_encoded_with_charset, charset.name()),
                        )
                    }
                    latestText
                }.subscribeOn(Schedulers.io())
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (mDestroyed || !mEncodingOperationInProgress ||
                    mEncodingOperationGeneration != operationGeneration
                ) {
                    return@subscribe
                }
                val token = UUID.randomUUID().toString()
                editor.addUndoableHistoryMarker(token) { succeeded ->
                    post {
                        if (mDestroyed || !mEncodingOperationInProgress ||
                            mEncodingOperationGeneration != operationGeneration
                        ) {
                            return@post
                        }
                        if (!succeeded) {
                            finishEncodingOperation(operationGeneration)
                            showEncodingOperationError(context.getString(R.string.text_failed))
                            return@post
                        }
                        mEncodingHistoryEntries[token] = EncodingHistoryEntry(historyBefore, historyAfter)
                        applyEncodingHistoryState(historyAfter)
                        mTextRevision++
                        saveStickyDirty = true
                        finishEncodingOperation(operationGeneration)
                        showSnack(this, R.string.text_file_encoding_converted)
                    }
                }
            }, { error ->
                if (!mDestroyed && mEncodingOperationInProgress &&
                    mEncodingOperationGeneration == operationGeneration
                ) {
                    finishEncodingOperation(operationGeneration)
                    showEncodingOperationError(error.message ?: error.toString())
                }
            })
    }

    private fun finishEncodingOperation(expectedGeneration: Long? = null) {
        if (expectedGeneration != null && mEncodingOperationGeneration != expectedGeneration) return
        mEncodingOperationGeneration++
        mEncodingOperationInProgress = false
        mEncodingOperationDisposable?.dispose()
        mEncodingOperationDisposable = null
        mEditorEncodingStatus.isEnabled = true
        mEditorLoading = false
        endEditorLoadingUi()
        syncPrimaryMenuState()
        refreshAceStateAfterEncodingOperation()
    }

    private fun refreshAceStateAfterEncodingOperation() {
        val aceEditor = editor as? AceScriptEditorEngine ?: return
        aceEditor.refreshState()
        // ACE commits a document delta to UndoManager on its own event-loop turn. Refresh once
        // more after that turn so the host toolbar observes the newly available history item.
        mUiHandler.postDelayed({
            if (!mDestroyed && editor === aceEditor) {
                aceEditor.refreshState()
            }
        }, ACE_HISTORY_STATE_SETTLE_DELAY_MS)
    }

    private fun captureEncodingHistoryState(): EncodingHistoryState = EncodingHistoryState(
        charset = mCurrentCharset,
        charsetConfidence = mCurrentCharsetConfidence,
        hadBom = mHadBom,
        hadDecodingErrors = mCurrentHadDecodingErrors,
        decodingErrorsWereAccepted = mCurrentDecodingErrorsWereAccepted,
        draftEncodingValidationPending = mDraftEncodingValidationPending,
        fileEncodingValidationComplete = mFileEncodingValidationComplete,
    )

    private fun applyEncodingHistoryOperation(token: String, direction: EditorHistoryDirection) {
        // Toolbar commands are asynchronous in ACE. Prefer the direction requested by the host
        // while it is pending so delayed bridge events cannot temporarily rewind only metadata.
        val effectiveDirection = mPendingHistoryNavigation ?: direction
        mEncodingHistoryEntries[token]?.let { entry ->
            applyEncodingHistoryState(
                when (effectiveDirection) {
                    EditorHistoryDirection.UNDO -> entry.before
                    EditorHistoryDirection.REDO -> entry.after
                },
            )
        }
        // This is a document-state revision, so metadata-only Convert markers and their history
        // navigation invalidate an in-flight save snapshot just like text edits do.
        mTextRevision++
        if (!editor.isTextChanged) {
            mHadDirectEditSinceSave = false
        }
        saveStickyDirty = editor.isTextChanged || mHadDirectEditSinceSave
        completeHistoryNavigation()
        syncPrimaryMenuState()
        schedulePrimaryMenuStateSync()
    }

    private fun beginHistoryNavigation(direction: EditorHistoryDirection): Boolean {
        if (mPendingHistoryNavigation != null) return false
        mPendingHistoryNavigation = direction
        val generation = ++mHistoryNavigationGeneration
        syncPrimaryMenuState()
        mUiHandler.postDelayed({
            if (!mDestroyed && mHistoryNavigationGeneration == generation &&
                mPendingHistoryNavigation == direction
            ) {
                mPendingHistoryNavigation = null
                mHistoryNavigationGeneration++
                syncPrimaryMenuState()
            }
        }, HISTORY_NAVIGATION_TIMEOUT_MS)
        return true
    }

    private fun completeHistoryNavigation() {
        if (mPendingHistoryNavigation == null) return
        mPendingHistoryNavigation = null
        mHistoryNavigationGeneration++
    }

    private fun applyEncodingHistoryState(state: EncodingHistoryState) {
        mCurrentCharset = state.charset
        mCurrentCharsetConfidence = state.charsetConfidence
        mHadBom = state.hadBom
        mCurrentHadDecodingErrors = state.hadDecodingErrors
        mCurrentDecodingErrorsWereAccepted = state.decodingErrorsWereAccepted
        mDraftEncodingValidationPending = state.draftEncodingValidationPending
        mFileEncodingValidationComplete = state.fileEncodingValidationComplete
        updateEncodingStatus()
    }

    private fun showEncodingOperationError(message: String) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_file_encoding)
                .content(message)
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .build()
        }
    }

    fun showFileDetailsDialog() {
        val path = uri?.path
        if (path.isNullOrEmpty()) {
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.error_unable_to_display_file_details_as_current_file_path_is_unknown)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .build()
            }
            return
        }
        EditableFileInfoDialogManager.showEditableFileInfoDialog(context, File(path)) {
            editor.text
        }
    }

    fun startBuildApkActivity() {
        startBuildApkActivity(null)
    }

    private fun startBuildApkActivity(fallbackPath: String?) {
        val path = uri?.path?.takeIf { it.isNotEmpty() }
            ?: fallbackPath?.takeIf { it.isNotEmpty() }
        if (path.isNullOrEmpty()) {
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.error_unable_to_package_application_as_current_file_path_is_unknown)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .build()
            }
            return
        }
        BuildActivity.launch(context, path)
    }

    fun showJavaClassSearchDialog() {
        showJavaClassSearchDialog(null)
    }

    private fun showJavaClassSearchDialog(initialQuery: String?) {
        val query = initialQuery?.takeIf { it.isNotBlank() } ?: editor.selectedTextSnapshot
        ClassSearchDialogBuilder(context)
            .setQuery(query)
            .itemClick { dialog, item, _ -> showClassSearchingItem(dialog, item) }
            .title(R.string.text_find_java_classes)
            .show()
    }

    private fun showClassSearchingItem(dialog: MaterialDialog, item: ClassSearchingItem) {
        val title: String
        val description: String
        if (item is ClassSearchingItem.ClassItem) {
            val androidClass: AndroidClass = item.androidClass
            title = androidClass.className
            description = androidClass.fullName
        } else {
            title = (item as ClassSearchingItem.PackageItem).packageName
            description = title
        }
        MaterialDialog.Builder(context)
            .title(title)
            .content(description)
            .neutralText(R.string.text_view_docs)
            .neutralColorRes(R.color.dialog_button_hint)
            .negativeText(R.string.text_en_import)
            .negativeColorRes(R.color.dialog_button_hint)
            .positiveText(R.string.text_copy)
            .positiveColorRes(R.color.dialog_button_hint)
            .onPositive { _, _ ->
                ClipboardUtils.setClip(context, description)
                showToast(context, R.string.text_already_copied_to_clip)
                dialog.dismiss()
            }
            .onNegative { _, _ ->
                val executionInfo = JavaScriptFileSource.parseExecutionMode(editor.text)
                recordAceJavaClassImportHistory(item, description)
                mCommandDispatcher.insert(executionInfo.lineno, item.importText + ";\n")
            }
            .onNeutral { _, _ ->
                IntentUtils.browse(context, item.url, ToastExceptionHolder(context))
            }
            .onAny { _, _ -> dialog.dismiss() }
            .show()
    }

    fun beautifyCode() {
        mCommandDispatcher.beautifyCode()
    }

    @SuppressLint("CheckResult")
    fun selectEditorTheme() {
        editor.setProgress(true)
        Themes.getAllThemes(context, editor.engineType)
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
        DialogUtils.buildAndShowAdaptive {
            AceEditorFontDialog.build(
                context = context,
                currentFontId = themeFontService.currentAceFont(),
                onSelected = { font ->
                    themeFontService.persistAndApplyAceFont(font)
                    showToast(
                        context,
                        context.getString(R.string.text_ace_editor_font_changed, themeFontService.aceEditorFontLabel(font)),
                    )
                },
                onPreviewRequested = { fontId ->
                    openFontPreviewer(returnToFontDialog = true, initialFontId = fontId)
                },
            )
        }
    }

    fun setTextSize(value: Int) {
        themeFontService.setTextSize(value)
    }

    private fun selectEditorTheme(themes: List<Theme>) {
        val i = themes.indexOf(themeFontService.currentTheme).coerceAtLeast(0)
        val defaultIndex = themes.indexOfFirst { it.name == Themes.getDefaultThemeName(context, editor.engineType) }.coerceAtLeast(0)
        fun previewFromDialog(dialog: MaterialDialog) {
            val selectedTheme = themes.getOrNull(dialog.selectedIndex) ?: themeFontService.currentTheme ?: themes[i]
            dialog.dismiss()
            recordAceHistoryAction(themePreviewerHistoryAction())
            openThemePreviewer(returnToThemeDialog = true, initialTheme = selectedTheme)
        }
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_editor_theme)
                .items(themes)
                .choiceWidgetThemeColor()
                .options(
                    listOf(
                        MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_use_default)) { dialog ->
                            dialog.selectedIndex = defaultIndex
                        },
                        MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_details)) {
                            DialogUtils.buildAndShowAdaptive {
                                MaterialDialog.Builder(context)
                                    .title(R.string.text_editor_theme)
                                    .content(R.string.description_editor_theme_dialog)
                                    .positiveText(R.string.dialog_button_dismiss)
                                    .positiveColorRes(R.color.dialog_button_default)
                                    .build()
                            }
                        },
                    ),
                )
                .itemsCallbackSingleChoice(i) { _, _, which, _ ->
                    themes[which].let {
                        persistAndApplyEditorTheme(it)
                    }
                    true
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .neutralText(R.string.dialog_button_preview)
                .neutralColorRes(R.color.dialog_button_default)
                .onNeutral { dialog, _ ->
                    previewFromDialog(dialog)
                }
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
            .setAction(R.string.text_details) { LogActivity.launch(context, name, uri?.path) }
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
        if (handleAceSymbolClick(completions[pos])) {
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

    override fun shouldRepeatHintLongClick(completions: CodeCompletions, pos: Int): Boolean {
        if (pos < 0 || pos >= completions.size()) {
            return false
        }
        return shouldRepeatAceShortcutLongClick(completions[pos])
    }

    override fun onHintLongClickRepeat(completions: CodeCompletions, pos: Int): Boolean {
        if (pos < 0 || pos >= completions.size()) {
            return false
        }
        return handleAceShortcutLongClickRepeat(completions[pos])
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
            ?.let { executeAceShortcutAction(it, record = false) }
            ?: false
    }

    private fun handleAceSymbolClick(completion: CodeCompletion): Boolean {
        if (editor.engineType != EditorEngineType.ACE || completion.url != null) {
            return false
        }
        if (AceShortcutPanel.commandOf(completion) != null) {
            return false
        }
        val insertText = completion.insertText.takeIf { it.isNotEmpty() } ?: return false
        val aceEditor = editor as? AceScriptEditorEngine ?: return false
        aceEditor.insertShortcutText(insertText)
        return true
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
                recordAceHistoryAction(historyAction(ACTION_EXIT_EDITOR, R.string.text_exit, mutatesText = false))
                triggerHostBackPress()
            }
            AceShortcutPanel.Command.MoveUp -> true.also {
                if (!handleAceShortcutVerticalLongClick(-1)) {
                    recordAceHistoryAction(historyAction(ACTION_JUMP_TO_START, R.string.text_jump_to_start, mutatesText = false))
                    jumpWithAceShortcutReturnPoint { editor.jumpToStart() }
                }
            }
            AceShortcutPanel.Command.MoveDown -> true.also {
                if (!handleAceShortcutVerticalLongClick(1)) {
                    recordAceHistoryAction(historyAction(ACTION_JUMP_TO_END, R.string.text_jump_to_end, mutatesText = false))
                    jumpWithAceShortcutReturnPoint { editor.jumpToEnd() }
                }
            }
            AceShortcutPanel.Command.MoveLeft -> true.also {
                recordAceHistoryAction(historyAction(ACTION_JUMP_TO_LINE_START, R.string.text_jump_to_line_start, mutatesText = false))
                jumpWithAceShortcutReturnPoint { editor.jumpToLineStart() }
            }
            AceShortcutPanel.Command.MoveRight -> true.also {
                recordAceHistoryAction(historyAction(ACTION_JUMP_TO_LINE_END, R.string.text_jump_to_line_end, mutatesText = false))
                jumpWithAceShortcutReturnPoint { editor.jumpToLineEnd() }
            }
            AceShortcutPanel.Command.Tab -> true.also {
                val aceEditor = editor as? AceScriptEditorEngine
                if (aceEditor?.supportsSmartHint == true) {
                    recordAceHistoryAction(historyAction(ACTION_SMART_HINT, R.string.text_smart_hint, mutatesText = false))
                    aceEditor.triggerSmartHint()
                } else if (toggleAceShortcutReturnPoint()) {
                    // Plugins older than editor contract 6 keep the previous TAB long-press behavior.
                    recordAceHistoryAction(historyAction(ACTION_JUMP_TO_PREVIOUS_CURSOR, R.string.text_jump_to_previous_cursor, mutatesText = false))
                }
            }
            null -> false
        }
    }

    private fun showAceActionHistoryDialog() {
        AceActionFavoritesDialog.show(context) { action ->
            executeAceHistoryAction(action, record = true)
        }
    }

    private fun repeatLastAceAction() {
        val action = AceActionHistoryStore.getLastAction(context)
        if (action == null) {
            showToast(context, R.string.text_ace_no_last_action)
            return
        }
        executeAceHistoryAction(action, record = true)
    }

    fun recordAceActionForMenuItem(itemId: Int) {
        actionForMenuItem(itemId)?.let(::recordAceHistoryAction)
    }

    @JvmOverloads
    fun showEditorSettingsDialog(
        initialPreferenceKey: String? = null,
        suppressInitialSettingActionRecord: Boolean = false,
    ) {
        EditorSettingsDialogLauncher.show(
            context = context,
            initialPreferenceKey = initialPreferenceKey,
            recordAceActionFavorites = editor.engineType == EditorEngineType.ACE,
            suppressInitialActionRecord = suppressInitialSettingActionRecord,
            themePreviewHandler = { theme ->
                recordAceHistoryAction(themePreviewerHistoryAction())
                openThemePreviewer(returnToThemeDialog = true, initialTheme = theme)
            },
            fontPreviewHandler = { fontId ->
                openFontPreviewer(returnToFontDialog = true, initialFontId = fontId)
            },
        )
    }

    private fun executeAceHistoryAction(
        action: AceActionHistoryStore.Action,
        record: Boolean,
    ): Boolean {
        if (action.mutatesText && editor.isReadOnly) {
            return true
        }
        val handled = when (action.id) {
            ACTION_RUN -> true.also { runAndSaveFileIfNeeded() }
            ACTION_FORCE_STOP -> true.also { forceStop() }
            ACTION_SAVE -> true.also { saveFile() }
            ACTION_UNDO -> true.also { undo() }
            ACTION_REDO -> true.also { redo() }
            ACTION_FIND_NEXT -> true.also {
                editor.resetFoundIndexForFindNextFromCursor()
                findNext()
                onSearchStateMaybeChanged()
            }
            ACTION_FIND_PREV -> true.also {
                editor.resetFoundIndexForFindPrevFromCursor()
                findPrev()
                onSearchStateMaybeChanged()
            }
            ACTION_CANCEL_SEARCH -> true.also { cancelSearch() }
            ACTION_FIND_OR_REPLACE -> true.also { showFindOrReplaceDialog(action.payloadObject()) }
            ACTION_REPLACE_SELECTION -> true.also {
                replace()
                onSearchStateMaybeChanged()
            }
            ACTION_COPY_SELECTION -> true.also { copySelectedTextToClipboard() }
            ACTION_COPY_ALL -> true.also { copyAllToClipboard() }
            ACTION_COPY_LINE -> true.also { mCommandDispatcher.copyLine() }
            ACTION_PASTE -> true.also { pasteFromClipboard() }
            ACTION_SELECT_ALL -> (editor as? AceScriptEditorEngine)?.let { aceEditor ->
                true.also { aceEditor.selectAll() }
            } ?: false
            ACTION_DELETE_LINE -> true.also { mCommandDispatcher.deleteLine() }
            ACTION_CLEAR -> true.also { mCommandDispatcher.clear() }
            ACTION_TOGGLE_COMMENT -> true.also { mCommandDispatcher.toggleComment() }
            ACTION_BEAUTIFY -> true.also { mCommandDispatcher.beautifyCode() }
            ACTION_UNFOLD_ALL -> mCommandDispatcher.unfoldAll()
            ACTION_FOLD_ALL -> mCommandDispatcher.foldAll()
            ACTION_JUMP_TO_LINE -> true.also { showJumpToLineDialog(action.payloadInt(PAYLOAD_LINE)) }
            ACTION_JUMP_TO_START -> true.also { mCommandDispatcher.jumpToStart() }
            ACTION_JUMP_TO_END -> true.also { mCommandDispatcher.jumpToEnd() }
            ACTION_JUMP_TO_LINE_START -> true.also { mCommandDispatcher.jumpToLineStart() }
            ACTION_JUMP_TO_LINE_END -> true.also { mCommandDispatcher.jumpToLineEnd() }
            ACTION_TOGGLE_BREAKPOINT -> true.also { mCommandDispatcher.addOrRemoveBreakpointAtCurrentLine() }
            ACTION_DEBUG -> true.also { debug() }
            ACTION_REMOVE_ALL_BREAKPOINTS -> true.also { mCommandDispatcher.removeAllBreakpoints() }
            ACTION_BUILD_APK -> true.also { startBuildApkActivity(action.payloadString(PAYLOAD_PATH)) }
            ACTION_LOG -> true.also { openEditorLogByPreference() }
            ACTION_SETTINGS -> true.also { showEditorSettingsDialog() }
            ACTION_TOGGLE_FULLSCREEN -> true.also { toggleFullscreenMode() }
            ACTION_CONSOLE -> true.also { showConsole() }
            ACTION_IMPORT_JAVA_CLASS -> true.also {
                showJavaClassSearchDialog(
                    action.payloadString(PAYLOAD_QUERY)
                        ?: action.payloadString(PAYLOAD_IMPORT_TEXT),
                )
            }
            ACTION_FILE_DETAILS -> true.also { showFileDetailsDialog() }
            ACTION_READ_ONLY -> true.also { toggleReadOnlyMode() }
            ACTION_COPY_ACE_DIAGNOSTICS -> true.also { copyAceDiagnosticsToClipboard() }
            ACTION_OPEN_BY_OTHER_APPS -> true.also { openByOtherApps() }
            ACTION_VERSION_HISTORY -> true.also { showVersionHistoryDialog() }
            ACTION_CREATE_SNAPSHOT -> true.also { showCreateSnapshotDialog() }
            ACTION_THEME_PREVIEWER -> true.also { openThemePreviewer(returnToThemeDialog = false) }
            ACTION_EXIT_EDITOR -> true.also { triggerHostBackPress() }
            ACTION_JUMP_TO_PREVIOUS_CURSOR -> toggleAceShortcutReturnPoint()
            ACTION_SMART_HINT -> (editor as? AceScriptEditorEngine)
                ?.takeIf { it.supportsSmartHint }
                ?.let { aceEditor -> true.also { aceEditor.triggerSmartHint() } }
                ?: false
            else -> executeEditorSettingHistoryAction(action)
        }
        if (handled && record && !isDeferredParameterizedHistoryAction(action.id)) {
            AceActionHistoryStore.record(context, actionForHistoryRecord(action))
        }
        return handled
    }

    private fun executeEditorSettingHistoryAction(action: AceActionHistoryStore.Action): Boolean {
        val preferenceKey = EditorSettingActionRegistry.preferenceKeyFromActionId(action.id)
            ?: return false
        if (!EditorSettingActionRegistry.isKnownPreferenceKey(context, preferenceKey)) {
            return false
        }
        showEditorSettingsDialog(
            initialPreferenceKey = preferenceKey,
            suppressInitialSettingActionRecord = true,
        )
        return true
    }

    private fun recordAceHistoryAction(action: AceActionHistoryStore.Action) {
        if (editor.engineType != EditorEngineType.ACE) {
            return
        }
        if (isDeferredParameterizedHistoryAction(action.id) && action.payloadJson.isNullOrBlank()) {
            return
        }
        AceActionHistoryStore.record(context, action)
    }

    fun recordAceFindOrReplaceHistory(
        keywords: String,
        replacement: String,
        usingRegex: Boolean,
        replace: Boolean,
        replaceAll: Boolean,
    ) {
        val query = keywords.takeIf { it.isNotEmpty() } ?: return
        val payloadJson = historyPayload(
            PAYLOAD_QUERY to query,
            PAYLOAD_REPLACEMENT to replacement,
            PAYLOAD_USING_REGEX to usingRegex,
            PAYLOAD_REPLACE to replace,
            PAYLOAD_REPLACE_ALL to replaceAll,
        )
        val payloadSummary = buildSearchPayloadSummary(
            query = query,
            replacement = replacement,
            usingRegex = usingRegex,
            replace = replace,
        )
        recordAceHistoryAction(
            historyAction(
                ACTION_FIND_OR_REPLACE,
                R.string.text_find_or_replace,
                mutatesText = true,
                payloadJson = payloadJson,
                payloadSummary = payloadSummary,
            ),
        )
    }

    private fun recordAceJumpToLineHistory(line: Int) {
        recordAceHistoryAction(
            historyAction(
                ACTION_JUMP_TO_LINE,
                R.string.text_jump_to_line,
                mutatesText = false,
                payloadJson = historyPayload(PAYLOAD_LINE to line),
                payloadSummary = "#$line",
            ),
        )
    }

    private fun recordAceJavaClassImportHistory(item: ClassSearchingItem, query: String) {
        val importText = item.importText.takeIf { it.isNotBlank() } ?: return
        recordAceHistoryAction(
            historyAction(
                ACTION_IMPORT_JAVA_CLASS,
                R.string.text_find_java_classes,
                mutatesText = true,
                payloadJson = historyPayload(
                    PAYLOAD_QUERY to query,
                    PAYLOAD_IMPORT_TEXT to importText,
                ),
                payloadSummary = compactHistorySummary(importText),
            ),
        )
    }

    private fun actionForToolbarItem(id: Int): AceActionHistoryStore.Action? = when (id) {
        R.id.action_log -> historyAction(ACTION_LOG, R.string.text_log, mutatesText = false)
        R.id.run -> historyAction(
            if (isScriptRunning()) ACTION_FORCE_STOP else ACTION_RUN,
            if (isScriptRunning()) R.string.text_force_stop else R.string.text_run,
            mutatesText = false,
        )
        R.id.save -> historyAction(ACTION_SAVE, R.string.text_save, mutatesText = false)
        R.id.undo -> historyAction(ACTION_UNDO, R.string.text_undo, mutatesText = true)
        R.id.redo -> historyAction(ACTION_REDO, R.string.text_redo, mutatesText = true)
        R.id.find_next -> historyAction(ACTION_FIND_NEXT, R.string.text_find_next_simplified, mutatesText = false)
        R.id.find_prev -> historyAction(ACTION_FIND_PREV, R.string.text_find_prev_simplified, mutatesText = false)
        R.id.replace -> historyAction(ACTION_REPLACE_SELECTION, R.string.text_replace, mutatesText = true)
        else -> null
    }

    private fun actionForAceSelectionAction(action: AceScriptEditorEngine.SelectionAction): AceActionHistoryStore.Action? = when (action) {
        AceScriptEditorEngine.SelectionAction.Copy -> historyAction(ACTION_COPY_SELECTION, R.string.text_copy, mutatesText = false)
        AceScriptEditorEngine.SelectionAction.Paste -> historyAction(ACTION_PASTE, R.string.text_paste, mutatesText = true)
        AceScriptEditorEngine.SelectionAction.SelectAll -> historyAction(ACTION_SELECT_ALL, R.string.text_select_all, mutatesText = false)
        AceScriptEditorEngine.SelectionAction.DeleteLine -> historyAction(ACTION_DELETE_LINE, R.string.text_delete_line, mutatesText = true)
        AceScriptEditorEngine.SelectionAction.CopyLine -> historyAction(ACTION_COPY_LINE, R.string.text_copy_line, mutatesText = false)
    }

    private fun actionForMenuItem(itemId: Int): AceActionHistoryStore.Action? = when (itemId) {
        R.id.action_force_stop -> historyAction(ACTION_FORCE_STOP, R.string.text_force_stop, mutatesText = false)
        R.id.action_find_or_replace -> historyAction(ACTION_FIND_OR_REPLACE, R.string.text_find_or_replace, mutatesText = true)
        R.id.action_copy_all -> historyAction(ACTION_COPY_ALL, R.string.text_copy_all, mutatesText = false)
        R.id.action_copy_line -> historyAction(ACTION_COPY_LINE, R.string.text_copy_line, mutatesText = false)
        R.id.action_paste -> historyAction(ACTION_PASTE, R.string.text_paste, mutatesText = true)
        R.id.action_delete_line -> historyAction(ACTION_DELETE_LINE, R.string.text_delete_line, mutatesText = true)
        R.id.action_clear -> historyAction(ACTION_CLEAR, R.string.text_clear, mutatesText = true)
        R.id.action_comment -> historyAction(ACTION_TOGGLE_COMMENT, R.string.text_comment, mutatesText = true)
        R.id.action_beautify -> historyAction(ACTION_BEAUTIFY, R.string.text_code_beautify, mutatesText = true)
        R.id.action_unfold_all -> historyAction(ACTION_UNFOLD_ALL, R.string.text_unfold_all, mutatesText = false)
        R.id.action_fold_all -> historyAction(ACTION_FOLD_ALL, R.string.text_fold_all, mutatesText = false)
        R.id.action_jump_to_line -> historyAction(ACTION_JUMP_TO_LINE, R.string.text_jump_to_line, mutatesText = false)
        R.id.action_jump_to_start -> historyAction(ACTION_JUMP_TO_START, R.string.text_jump_to_start, mutatesText = false)
        R.id.action_jump_to_end -> historyAction(ACTION_JUMP_TO_END, R.string.text_jump_to_end, mutatesText = false)
        R.id.action_jump_to_line_start -> historyAction(ACTION_JUMP_TO_LINE_START, R.string.text_jump_to_line_start, mutatesText = false)
        R.id.action_jump_to_line_end -> historyAction(ACTION_JUMP_TO_LINE_END, R.string.text_jump_to_line_end, mutatesText = false)
        R.id.action_jump_to_previous_cursor -> historyAction(ACTION_JUMP_TO_PREVIOUS_CURSOR, R.string.text_jump_to_previous_cursor, mutatesText = false)
        R.id.action_breakpoint -> historyAction(ACTION_TOGGLE_BREAKPOINT, R.string.text_set_breakpoint, mutatesText = false)
        R.id.action_launch_debugger -> historyAction(ACTION_DEBUG, R.string.text_launch_debugger, mutatesText = false)
        R.id.action_remove_all_breakpoints -> historyAction(ACTION_REMOVE_ALL_BREAKPOINTS, R.string.text_remove_all_breakpoints, mutatesText = false)
        R.id.action_build_apk -> buildApkHistoryAction()
        R.id.action_log -> historyAction(ACTION_LOG, R.string.text_log, mutatesText = false)
        R.id.action_editor_settings -> historyAction(ACTION_SETTINGS, R.string.text_settings, mutatesText = false)
        R.id.action_fullscreen_mode -> historyAction(ACTION_TOGGLE_FULLSCREEN, R.string.text_editor_fullscreen_mode, mutatesText = false)
        R.id.action_console -> historyAction(ACTION_CONSOLE, R.string.text_console, mutatesText = false)
        R.id.action_import_java_class -> historyAction(ACTION_IMPORT_JAVA_CLASS, R.string.text_find_java_classes, mutatesText = true)
        R.id.action_file_details -> historyAction(ACTION_FILE_DETAILS, R.string.text_file_details, mutatesText = false)
        R.id.action_ace_read_only_mode -> historyAction(ACTION_READ_ONLY, R.string.text_ace_read_only_mode, mutatesText = false)
        R.id.action_copy_ace_diagnostics -> historyAction(ACTION_COPY_ACE_DIAGNOSTICS, R.string.text_copy_ace_diagnostics, mutatesText = false)
        R.id.action_open_by_other_apps -> historyAction(ACTION_OPEN_BY_OTHER_APPS, R.string.text_open_by_other_apps, mutatesText = false)
        R.id.action_version_history -> historyAction(ACTION_VERSION_HISTORY, R.string.text_version_history, mutatesText = false)
        R.id.action_create_snapshot -> historyAction(ACTION_CREATE_SNAPSHOT, R.string.text_create_snapshot, mutatesText = false)
        else -> null
    }

    private fun historyAction(
        id: String,
        @StringRes labelResId: Int,
        mutatesText: Boolean,
        payloadJson: String? = null,
        payloadSummary: String? = null,
    ) = AceActionHistoryStore.Action(
        id = id,
        label = context.getString(labelResId),
        mutatesText = mutatesText,
        payloadJson = payloadJson,
        payloadSummary = payloadSummary,
    )

    private fun buildApkHistoryAction(fallbackPath: String? = null): AceActionHistoryStore.Action {
        val path = uri?.path?.takeIf { it.isNotEmpty() }
            ?: fallbackPath?.takeIf { it.isNotEmpty() }
        return historyAction(
            ACTION_BUILD_APK,
            R.string.text_build_apk,
            mutatesText = false,
            payloadJson = path?.let { historyPayload(PAYLOAD_PATH to it) },
        payloadSummary = path?.let { compactHistorySummary(File(it).name.ifBlank { it }) },
        )
    }

    private fun themePreviewerHistoryAction(): AceActionHistoryStore.Action {
        return historyAction(ACTION_THEME_PREVIEWER, R.string.text_theme_previewer, mutatesText = false)
    }

    private fun actionForHistoryRecord(action: AceActionHistoryStore.Action): AceActionHistoryStore.Action {
        return when (action.id) {
            ACTION_BUILD_APK -> buildApkHistoryAction(action.payloadString(PAYLOAD_PATH))
            else -> action
        }
    }

    private fun isDeferredParameterizedHistoryAction(id: String): Boolean {
        return id == ACTION_FIND_OR_REPLACE ||
            id == ACTION_JUMP_TO_LINE ||
            id == ACTION_IMPORT_JAVA_CLASS
    }

    private fun historyPayload(vararg values: Pair<String, Any?>): String {
        return JSONObject().apply {
            values.forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is Boolean -> put(key, value)
                    is Number -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }.toString()
    }

    private fun AceActionHistoryStore.Action.payloadObject(): JSONObject? {
        val raw = payloadJson?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun AceActionHistoryStore.Action.payloadString(key: String): String? {
        return payloadObject()?.optNonBlankString(key)
    }

    private fun AceActionHistoryStore.Action.payloadInt(key: String): Int? {
        val payload = payloadObject() ?: return null
        if (!payload.has(key)) {
            return null
        }
        return payload.optInt(key)
    }

    private fun JSONObject.optNonBlankString(key: String): String? {
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun buildSearchPayloadSummary(
        query: String,
        replacement: String,
        usingRegex: Boolean,
        replace: Boolean,
    ): String {
        val querySummary = compactHistorySummary(
            if (usingRegex) {
                "/$query/"
            } else {
                "\"$query\""
            },
            maxLength = 48,
        )
        if (!replace) {
            return querySummary
        }
        return "$querySummary -> ${compactHistorySummary("\"$replacement\"", maxLength = 32)}"
    }

    private fun compactHistorySummary(value: String, maxLength: Int = 72): String {
        val compact = value.replace(Regex("\\s+"), " ").trim()
        if (compact.length <= maxLength) {
            return compact
        }
        return compact.take((maxLength - 3).coerceAtLeast(0)) + "..."
    }

    private fun copyAllToClipboard() {
        ClipboardUtils.setClip(context, editor.text)
        showSnack(this, R.string.text_already_copied_to_clip)
    }

    private fun copySelectedTextToClipboard() {
        val selectedText = editor.selectedTextSnapshot
        if (selectedText.isEmpty()) {
            return
        }
        ClipboardUtils.setClip(context, selectedText)
        showSnack(this, R.string.text_already_copied_to_clip)
    }

    private fun pasteFromClipboard() {
        ClipboardUtils.getClip(context)?.let { mCommandDispatcher.insert(it) }
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

    private fun shouldRepeatAceShortcutLongClick(completion: CodeCompletion): Boolean {
        if (editor.engineType != EditorEngineType.ACE) {
            return false
        }
        return when (AceShortcutPanel.commandOf(completion)) {
            AceShortcutPanel.Command.MoveUp,
            AceShortcutPanel.Command.MoveDown -> (editor as? AceScriptEditorEngine)?.isCompletionPopupOpen() == true
            else -> false
        }
    }

    private fun handleAceShortcutLongClickRepeat(completion: CodeCompletion): Boolean {
        val aceEditor = editor as? AceScriptEditorEngine ?: return false
        if (!aceEditor.isCompletionPopupOpen()) {
            return false
        }
        val delta = when (AceShortcutPanel.commandOf(completion)) {
            AceShortcutPanel.Command.MoveUp -> -1
            AceShortcutPanel.Command.MoveDown -> 1
            else -> return false
        }
        aceEditor.moveCompletionSelectionOrCursor(delta)
        return true
    }

    private fun handleAceShortcutVerticalLongClick(delta: Int): Boolean {
        val aceEditor = editor as? AceScriptEditorEngine
        if (aceEditor?.isCompletionPopupOpen() == true) {
            aceEditor.moveCompletionSelectionOrCursor(delta)
            return true
        }
        return false
    }

    private fun jumpWithAceShortcutReturnPoint(jump: () -> Unit) {
        rememberAceShortcutReturnPoint()
        jump()
    }

    private fun toggleAceShortcutReturnPoint(): Boolean {
        val target = mAceShortcutReturnSelection ?: return false
        val current = editor.selection
        mAceShortcutReturnSelection = current
        mAceShortcutLastObservedSelection = current
        mAceShortcutReturnJumpInProgress = true
        editor.jumpTo(target.startLine, target.startColumn)
        return true
    }

    /** Menu entry point for the Ace-only "jump to previous cursor" toggle. */
    fun jumpToPreviousCursor(): Boolean = toggleAceShortcutReturnPoint()

    fun supportsPreviousCursorJump(): Boolean = editor.engineType == EditorEngineType.ACE

    fun hasPreviousCursorPosition(): Boolean = supportsPreviousCursorJump() && mAceShortcutReturnSelection != null

    private fun rememberAceShortcutReturnPoint() {
        val current = editor.selection
        if (!current.isCollapsed) {
            return
        }
        mAceShortcutReturnSelection = current
        mAceShortcutLastObservedSelection = current
    }

    private fun recordAceShortcutCursorHistory() {
        if (editor.engineType != EditorEngineType.ACE) {
            return
        }
        val current = editor.selection
        if (!current.isCollapsed) {
            mAceShortcutLastObservedSelection = current
            return
        }
        if (mAceShortcutReturnJumpInProgress) {
            mAceShortcutReturnJumpInProgress = false
            mAceShortcutLastObservedSelection = current
            return
        }
        val previous = mAceShortcutLastObservedSelection
        if (previous != null && previous.isCollapsed && !previous.isSameCollapsedCursor(current)) {
            mAceShortcutReturnSelection = previous
        }
        mAceShortcutLastObservedSelection = current
    }

    private fun EditorSelection.isSameCollapsedCursor(other: EditorSelection): Boolean {
        return startLine == other.startLine &&
            startColumn == other.startColumn &&
            endLine == other.endLine &&
            endColumn == other.endColumn
    }

    private fun triggerHostBackPress() {
        post {
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showManual(urlSuffix: String, title: String) {
        val absUrl = getUrl(urlSuffix)
        val colors = currentEditorUiPalette()
        ManualDialog(context, colors.background, colors.foreground)
            .title(title)
            .url(absUrl)
            .pinToLeft {
                mDocsPaneController.loadUrl(absUrl)
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
        putParcelable(STATE_SUPER_DATA, super.onSaveInstanceState())
        putInt(STATE_SCRIPT_EXECUTION_ID, scriptExecutionId)
        val selection = mPendingRestoredSelection ?: editor.selection.let {
            it.startOffset to it.endOffset
        }
        putInt(STATE_SELECTION_START, selection.first)
        putInt(STATE_SELECTION_END, selection.second)
        // The Activity persists draft text/path separately. Keeping this metadata in the view
        // state avoids duplicating large text while preserving a deferred Convert across process
        // recreation. It is ignored unless a draft is actually restored.
        putString(STATE_DRAFT_CHARSET, mCurrentCharset.name())
        putInt(STATE_DRAFT_CHARSET_CONFIDENCE, mCurrentCharsetConfidence)
        putBoolean(STATE_DRAFT_HAD_BOM, mHadBom)
        putBoolean(STATE_DRAFT_HAD_DECODING_ERRORS, mCurrentHadDecodingErrors)
        putBoolean(
            STATE_DRAFT_DECODING_ERRORS_ACCEPTED,
            mCurrentDecodingErrorsWereAccepted,
        )
        putBoolean(
            STATE_DRAFT_ENCODING_VALIDATION_PENDING,
            mDraftEncodingValidationPending,
        )
        putBoolean(
            STATE_FILE_ENCODING_VALIDATION_COMPLETE,
            mFileEncodingValidationComplete,
        )
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val bundle = state as Bundle

        @Suppress("DEPRECATION")
        val superData = bundle.getParcelable<Parcelable>(STATE_SUPER_DATA)
        scriptExecutionId = bundle.getInt(STATE_SCRIPT_EXECUTION_ID, ScriptExecution.NO_ID)
        super.onRestoreInstanceState(superData)
        if (bundle.containsKey(STATE_SELECTION_START) && bundle.containsKey(STATE_SELECTION_END)) {
            mPendingRestoredSelection = bundle.getInt(STATE_SELECTION_START) to
                bundle.getInt(STATE_SELECTION_END)
            scheduleCursorStatusAfterInitialLoad(preserveCurrentCursor = true)
        }
        bundle.getString(STATE_DRAFT_CHARSET)
            ?.let { charsetName -> runCatching { Charset.forName(charsetName) }.getOrNull() }
            ?.let { charset ->
                mPendingRestoredDraftEncodingState = EncodingHistoryState(
                    charset = charset,
                    charsetConfidence = bundle.getInt(STATE_DRAFT_CHARSET_CONFIDENCE, 100),
                    hadBom = EditorEncodingDetector.normalizeBomPreference(
                        charset,
                        bundle.getBoolean(STATE_DRAFT_HAD_BOM, false),
                    ),
                    hadDecodingErrors = bundle.getBoolean(
                        STATE_DRAFT_HAD_DECODING_ERRORS,
                        false,
                    ),
                    decodingErrorsWereAccepted = bundle.getBoolean(
                        STATE_DRAFT_DECODING_ERRORS_ACCEPTED,
                        false,
                    ),
                    draftEncodingValidationPending = bundle.getBoolean(
                        STATE_DRAFT_ENCODING_VALIDATION_PENDING,
                        false,
                    ),
                    fileEncodingValidationComplete = bundle.getBoolean(
                        STATE_FILE_ENCODING_VALIDATION_COMPLETE,
                        true,
                    ),
                )
            }
        applyPendingRestoredDraftEncodingStateIfReady()
        syncPrimaryMenuState()
        notifyScriptRunningStateChanged()
    }

    fun destroy() {
        mDestroyed = true
        abandonAceProjectRenameOnDestroy()
        mFullscreenHintSnackbar?.dismiss()
        mFullscreenHintSnackbar = null
        removeFullscreenSnackbarBottomProtection()
        mEditorEngineSwitchGeneration++
        mActiveEditorEngineSwitch = null
        mPendingEditorEngineSwitch = null
        mSwitchingEditorEngine = false
        mAceFallbackInProgress.set(false)
        AceEditorPluginStateMonitor.removeListener(mAcePluginStateListener)
        mDocsPaneController.dispose()
        // Cancel loading before destroying editor resources.
        // zh-CN: 销毁前先取消加载, 再销毁编辑器相关资源.
        cancelLargeFileLoading()
        mEncodingDialogDisposable?.dispose()
        mEncodingDialogDisposable = null
        mSaveAsAdoptionDisposable?.dispose()
        mSaveAsAdoptionDisposable = null
        cancelAceDefinitionNavigation()
        fileService.destroy()
        mEncodingOperationDisposable?.dispose()
        mEncodingOperationDisposable = null
        mEncodingOperationGeneration++
        mEncodingHistoryEntries.clear()
        mPendingHistoryNavigation = null
        mHistoryNavigationGeneration++

        // Cancel search stats.
        // zh-CN: 取消搜索统计任务.
        cancelSearchStats()

        mFontPreviewDownloadSubscription?.cancel()
        mFontPreviewDownloadSubscription = null
        mFontPreviewCatalogRefreshSubscription?.cancel()
        mFontPreviewCatalogRefreshSubscription = null

        editor.destroy()
        mDocumentLocationChangedListener = null
        mAutoCompletion?.shutdown()
        if (mDebugging) {
            exitDebugging()
            forceStop()
        }
    }

    private fun temporaryScriptExtensionWithDot(): String {
        return EditorScriptRunPolicy.temporaryExtensionWithDot(
            documentDisplayName = mDocumentDisplayName,
            uriPath = uri?.path,
            editorName = _name,
        )
    }

    fun cleanBeforeExit() {
        mTmpSavedFileForRunning?.deleteOnExit()
    }

    private data class InitialPositionRequest(
        val lineNumber: Int,
        val columnNumber: Int,
    )

    private data class EditorRunOrigin(
        val documentIdentityGeneration: Long,
        val fileLoadGeneration: Long,
        val uri: Uri?,
        val uriText: String?,
        val path: String?,
        val displayName: String,
        val mimeType: String?,
    )

    private data class EncodingDialogData(
        val candidates: List<EditorEncodingCandidate>,
    )

    private data class SaveWriteResult(
        val text: String,
        val revision: Long,
        val currentUri: Uri,
        val encoding: EditorFileSaveEncoding,
    )

    private data class ContentLengthResult(
        val sizeOrNull: Long?,
        val displayName: String?,
        val privilegedDraftNeedsSave: Boolean,
    )

    private data class EncodingReloadReadResult(
        val readResult: org.autojs.autojs.ui.edit.editor.loading.EditorFileReadResult,
        val sizeOrNull: Long?,
    )

    private data class EncodingHistoryState(
        val charset: Charset,
        val charsetConfidence: Int,
        val hadBom: Boolean,
        val hadDecodingErrors: Boolean,
        val decodingErrorsWereAccepted: Boolean,
        val draftEncodingValidationPending: Boolean,
        val fileEncodingValidationComplete: Boolean,
    )

    private data class EncodingHistoryEntry(
        val before: EncodingHistoryState,
        val after: EncodingHistoryState,
    )

    private data class EncodingReloadRollback(
        val uri: Uri,
        val text: String,
        val charset: Charset,
        val charsetConfidence: Int,
        val hadBom: Boolean,
        val hadDecodingErrors: Boolean,
        val decodingErrorsWereAccepted: Boolean,
        val draftEncodingValidationPending: Boolean,
        val fileEncodingValidationComplete: Boolean,
        val saveStickyDirty: Boolean,
        val hadDirectEditSinceSave: Boolean,
        val draftRestoredInThisSession: Boolean,
        val restoredText: String?,
        val largeFileMode: Boolean,
        val fileStateRequiresReload: Boolean,
    )

    private enum class ThemePreviewFilter(
        @param:StringRes val labelResId: Int,
        @param:DrawableRes val iconResId: Int,
    ) {
        All(R.string.text_theme_preview_filter_all, R.drawable.ic_day_and_night),
        Dark(R.string.text_theme_preview_filter_dark, R.drawable.ic_moon),
        Light(R.string.text_theme_preview_filter_light, R.drawable.ic_sun),
        ;

        fun allows(theme: Theme): Boolean = when (this) {
            All -> true
            Dark -> theme.isDark
            Light -> !theme.isDark
        }

        fun next(): ThemePreviewFilter = when (this) {
            All -> Dark
            Dark -> Light
            Light -> All
        }
    }

    private enum class PreviewMode {
        None,
        Theme,
        Font,
    }

    companion object {


        // zh-CN: 内部存储根目录 (不访问外置 SD).

        private const val STATE_SUPER_DATA = "super_data"
        private const val STATE_SELECTION_START = "selection_start"
        private const val STATE_SELECTION_END = "selection_end"
        private const val STATE_SCRIPT_EXECUTION_ID = "script_execution_id"
        private const val STATE_DRAFT_CHARSET = "draft_charset"
        private const val STATE_DRAFT_CHARSET_CONFIDENCE = "draft_charset_confidence"
        private const val STATE_DRAFT_HAD_BOM = "draft_had_bom"
        private const val STATE_DRAFT_HAD_DECODING_ERRORS = "draft_had_decoding_errors"
        private const val STATE_DRAFT_DECODING_ERRORS_ACCEPTED = "draft_decoding_errors_accepted"
        private const val STATE_DRAFT_ENCODING_VALIDATION_PENDING =
            "draft_encoding_validation_pending"
        private const val STATE_FILE_ENCODING_VALIDATION_COMPLETE =
            "file_encoding_validation_complete"

        const val EXTRA_PATH = "path"
        const val EXTRA_NAME = "name"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_READ_ONLY = "readOnly"
        const val EXTRA_SAVE_ENABLED = "saveEnabled"
        const val EXTRA_RUN_ENABLED = "runEnabled"
        const val EXTRA_INITIAL_LINE = "initialLine"
        const val EXTRA_INITIAL_COLUMN = "initialColumn"
        const val EXTRA_EDITOR_SESSION_ID = "editorSessionId"

        private const val ACTION_RUN = "editor:run"
        private const val ACTION_FORCE_STOP = "editor:force_stop"
        private const val ACTION_SAVE = "editor:save"
        private const val ACTION_UNDO = "editor:undo"
        private const val ACTION_REDO = "editor:redo"
        private const val ACTION_FIND_NEXT = "editor:find_next"
        private const val ACTION_FIND_PREV = "editor:find_prev"
        private const val ACTION_CANCEL_SEARCH = "editor:cancel_search"
        private const val ACTION_FIND_OR_REPLACE = "editor:find_or_replace"
        private const val ACTION_REPLACE_SELECTION = "editor:replace_selection"
        private const val ACTION_COPY_SELECTION = "editor:copy_selection"
        private const val ACTION_COPY_ALL = "editor:copy_all"
        private const val ACTION_COPY_LINE = "editor:copy_line"
        private const val ACTION_PASTE = "editor:paste"
        private const val ACTION_SELECT_ALL = "editor:select_all"
        private const val ACTION_DELETE_LINE = "editor:delete_line"
        private const val ACTION_CLEAR = "editor:clear"
        private const val ACTION_TOGGLE_COMMENT = "editor:toggle_comment"
        private const val ACTION_BEAUTIFY = "editor:beautify"
        private const val ACTION_UNFOLD_ALL = "editor:unfold_all"
        private const val ACTION_FOLD_ALL = "editor:fold_all"
        private const val ACTION_JUMP_TO_LINE = "editor:jump_to_line"
        private const val ACTION_JUMP_TO_START = "editor:jump_to_start"
        private const val ACTION_JUMP_TO_END = "editor:jump_to_end"
        private const val ACTION_JUMP_TO_LINE_START = "editor:jump_to_line_start"
        private const val ACTION_JUMP_TO_LINE_END = "editor:jump_to_line_end"
        private const val ACTION_TOGGLE_BREAKPOINT = "editor:toggle_breakpoint"
        private const val ACTION_DEBUG = "editor:debug"
        private const val ACTION_REMOVE_ALL_BREAKPOINTS = "editor:remove_all_breakpoints"
        private const val ACTION_BUILD_APK = "editor:build_apk"
        private const val ACTION_LOG = "editor:log"
        private const val ACTION_SETTINGS = "editor:settings"
        private const val ACTION_TOGGLE_FULLSCREEN = "editor:toggle_fullscreen"
        private const val ACTION_CONSOLE = "editor:console"
        private const val ACTION_IMPORT_JAVA_CLASS = "editor:import_java_class"
        private const val ACTION_FILE_DETAILS = "editor:file_details"
        private const val ACTION_READ_ONLY = "editor:read_only"
        private const val ACTION_COPY_ACE_DIAGNOSTICS = "editor:copy_ace_diagnostics"
        private const val ACTION_OPEN_BY_OTHER_APPS = "editor:open_by_other_apps"
        private const val ACTION_VERSION_HISTORY = "editor:version_history"
        private const val ACTION_CREATE_SNAPSHOT = "editor:create_snapshot"
        private const val ACTION_THEME_PREVIEWER = "editor:theme_previewer"
        private const val ACTION_EXIT_EDITOR = "editor:exit"
        private const val ACTION_JUMP_TO_PREVIOUS_CURSOR = "editor:jump_to_previous_cursor"
        private const val ACTION_SMART_HINT = "editor:smart_hint"

        private const val ACE_SHORTCUT_ITEM_WIDTH_DP = 40f
        private const val MIN_IME_OVERLAP_DP = 80F
        private const val ACE_HISTORY_STATE_SETTLE_DELAY_MS = 160L
        private const val EDITOR_ENGINE_SWITCH_LOAD_RETRY_DELAY_MS = 50L
        private const val HISTORY_NAVIGATION_TIMEOUT_MS = 10_000L
        private const val CURSOR_STATUS_PLACEHOLDER = "-:-"
        private const val ENCODING_RISK_PROBE_LIMIT_BYTES = 64 * 1024
        private const val ENCODING_TEXT_PROBE_LIMIT_CHARS = 64 * 1024
        private const val STREAM_UI_QUEUE_CAPACITY = 32
        private const val STREAM_UI_QUEUE_OFFER_TIMEOUT_MS = 50L

        private const val PAYLOAD_QUERY = "query"
        private const val PAYLOAD_REPLACEMENT = "replacement"
        private const val PAYLOAD_USING_REGEX = "usingRegex"
        private const val PAYLOAD_REPLACE = "replace"
        private const val PAYLOAD_REPLACE_ALL = "replaceAll"
        private const val PAYLOAD_LINE = "line"
        private const val PAYLOAD_IMPORT_TEXT = "importText"
        private const val PAYLOAD_PATH = "path"
    }
}
