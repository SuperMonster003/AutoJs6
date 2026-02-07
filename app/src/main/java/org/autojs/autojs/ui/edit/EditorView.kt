package org.autojs.autojs.ui.edit

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.AutoJs
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.core.pref.Pref.getEditorTextSize
import org.autojs.autojs.core.pref.Pref.setEditorTextSize
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.event.BackPressedHandler.HostActivity
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.model.autocomplete.AutoCompletion
import org.autojs.autojs.model.autocomplete.CodeCompletions
import org.autojs.autojs.model.autocomplete.Symbols
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
import org.autojs.autojs.storage.file.TmpScriptFiles
import org.autojs.autojs.storage.history.HistoryPrefs
import org.autojs.autojs.storage.history.HistoryRepository
import org.autojs.autojs.storage.history.HistoryUriUtils
import org.autojs.autojs.storage.history.VersionHistoryController
import org.autojs.autojs.tool.Callback
import org.autojs.autojs.ui.doc.ManualDialog
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar.OnHintClickListener
import org.autojs.autojs.ui.edit.debug.DebugBar
import org.autojs.autojs.ui.edit.editor.CodeEditor
import org.autojs.autojs.ui.edit.editor.CodeEditor.CheckedPatternSyntaxException
import org.autojs.autojs.ui.edit.keyboard.FunctionsKeyboardHelper
import org.autojs.autojs.ui.edit.keyboard.FunctionsKeyboardView
import org.autojs.autojs.ui.edit.keyboard.FunctionsKeyboardView.ClickCallback
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
import org.autojs.autojs.util.DisplayUtils.pxToSp
import org.autojs.autojs.util.DocsUtils.getUrl
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.R.string.text_unknown
import org.autojs.autojs6.databinding.EditorViewBinding
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Created by Stardust on Sep 28, 2017.
 * Transformed by SuperMonster003 on May 1, 2023.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 3, 2026.
 * Modified by SuperMonster003 as of Feb 3, 2026.
 */
@SuppressLint("CheckResult")
class EditorView : LinearLayout, OnHintClickListener, ClickCallback, ToolbarFragment.OnMenuItemClickListener {

    private var binding: EditorViewBinding = EditorViewBinding.bind(View.inflate(context, R.layout.editor_view, this))

    @JvmField
    val editor: CodeEditor = binding.editor

    @JvmField
    val debugBar: DebugBar = binding.debugBar

    private var _name: String? = null

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

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setUpEditor()
        setUpInputMethodEnhancedBar()
        setUpFunctionsKeyboard()
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(mOnRunFinishedReceiver)
        (context as? HostActivity)?.backPressedObserver?.unregisterHandler(mFunctionsKeyboardHelper)
    }

    fun handleIntent(intent: Intent): Observable<String> {
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
                if (!intent.getBooleanExtra(EXTRA_RUN_ENABLED, true)) {
                    findViewById<View>(R.id.run).visibility = GONE
                }
                if (readOnly) {
                    editor.setReadOnly(true)
                }
            }
    }

    fun setRestoredText(text: String) {
        mRestoredText = text
        editor.text = text
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

    @SuppressLint("CheckResult")
    private fun loadUri(uri: Uri): Observable<String> {
        return Observable
            .fromCallable {
                val resolver = context.contentResolver
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

                val detectedCharsetWrapper = StringUtils.detectCharset(rawBytes)
                mCurrentCharsetConfidence = detectedCharsetWrapper.confidence ?: 0
                mCurrentCharset = detectedCharsetWrapper.charsetOrDefault()
                mHadBom = StringUtils.hasBom(rawBytes, mCurrentCharset)

                val effectiveBytes = if (mHadBom) {
                    StringUtils.dropBom(rawBytes, mCurrentCharset)
                } else rawBytes

                String(effectiveBytes, mCurrentCharset)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { text: String -> setInitialText(text) }
    }

    private fun setInitialText(text: String) {
        if (mRestoredText != null) {
            editor.text = mRestoredText!!
            mRestoredText = null
            return
        }
        editor.setInitialText(text)
    }

    private fun setMenuItemStatus(id: Int, enabled: Boolean) {
        mMenuItemStatus.put(id, enabled)
        val supportManager = activity.supportFragmentManager
        val fragment = supportManager.findFragmentById(R.id.toolbar_menu) as ToolbarFragment<*>?
        if (fragment == null) {
            mNormalToolbar.setMenuItemStatus(id, enabled)
        } else {
            fragment.setMenuItemStatus(id, enabled)
        }
    }

    fun getMenuItemStatus(id: Int, defValue: Boolean): Boolean {
        return mMenuItemStatus[id, defValue]
    }

    private fun initNormalToolbar() {
        mNormalToolbar.apply {
            setOnMenuItemClickListener(this@EditorView)
            setOnMenuItemLongClickListener { id ->
                when (id) {
                    R.id.run if !mReadOnly -> true.also { debug() }
                    else -> false
                }
            }
        }
        activity.supportFragmentManager.findFragmentById(R.id.toolbar_menu) ?: showNormalToolbar()
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
            bar.codeCompletions = Symbols.getSymbols()
        }
        mCodeCompletionBar.let { bar ->
            bar.setOnHintClickListener(this)
            AutoCompletion(context, editor.codeEditText).apply {
                setAutoCompleteCallback { bar.codeCompletions = it }
                mAutoCompletion = this
            }
        }
    }

    private fun setUpEditor() {
        editor.let { editor ->
            editor.codeEditText.let { editText ->
                editText.addTextChangedListener(SimpleTextWatcher { _ ->
                    setMenuItemStatus(R.id.save, editor.isTextChanged)
                    setMenuItemStatus(R.id.undo, editor.canUndo())
                    setMenuItemStatus(R.id.redo, editor.canRedo())
                })
                editText.textSize = getEditorTextSize(pxToSp(editText.textSize).toInt()).toFloat()
            }
            editor.addCursorChangeCallback(object : CodeEditor.CursorChangeCallback {
                override fun onCursorChange(line: String, cursor: Int) {
                    autoComplete(line, cursor)
                }
            })
            editor.layoutDirection = LAYOUT_DIRECTION_LTR
        }
    }

    private fun autoComplete(line: String, cursor: Int) {
        mAutoCompletion!!.onCursorChange(line, cursor)
    }

    private fun setTheme(theme: Theme?) {
        theme?.let {
            mEditorTheme = it
            editor.setTheme(it)
            mInputMethodEnhanceBar.setBackgroundColor(it.imeBarBackgroundColor)
            val textColor = it.imeBarForegroundColor
            mCodeCompletionBar.setTextColor(textColor)
            mSymbolBar.setTextColor(textColor)
            mShowFunctionsButton.setColorFilter(textColor)
            ViewUtils.setNavigationBarBackgroundColor(activity, it.imeBarBackgroundColor)
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
            R.id.replace -> replace()
            R.id.find_next -> findNext()
            R.id.find_prev -> findPrev()
            R.id.cancel_search -> cancelSearch()
        }
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

    private fun undo() = editor.undo()

    private fun redo() = editor.redo()

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
                setMenuItemStatus(R.id.save, false)
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
        val raw = if (baseName.isBlank()) "untitled" else baseName
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
                    },
                    onRestoredUi = {
                        setMenuItemStatus(R.id.save, true)
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
                showToast(context, e.message, true)
            }
    }

    private fun findNext() = editor.findNext()

    private fun findPrev() = editor.findPrev()

    private fun cancelSearch() = showNormalToolbar()

    private fun showNormalToolbar() {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.toolbar_menu, mNormalToolbar)
            .commitAllowingStateLoss()
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
            .subscribe { themes: List<Theme?> ->
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

    private fun selectEditorTheme(themes: List<Theme?>) {
        var i = themes.indexOf(mEditorTheme)
        if (i < 0) {
            i = 0
        }
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_editor_theme)
                .items(themes)
                .choiceWidgetThemeColor()
                .itemsCallbackSingleChoice(i) { _, _, which, _ ->
                    themes[which]?.let {
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

    @Throws(CheckedPatternSyntaxException::class)
    fun find(keywords: String, usingRegex: Boolean) {
        editor.find(keywords, usingRegex)
        showSearchToolbar(false)
    }

    private fun showSearchToolbar(showReplaceItem: Boolean) {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.toolbar_menu, SearchToolbarFragment().apply {
                setOnMenuItemClickListener(this@EditorView)
                arguments ?: let { arguments = Bundle() }
                arguments!!.putBoolean(SearchToolbarFragment.ARGUMENT_SHOW_REPLACE_ITEM, showReplaceItem)
            })
            .commit()
    }

    @Throws(CheckedPatternSyntaxException::class)
    fun replace(keywords: String, replacement: String, usingRegex: Boolean) {
        editor.replace(keywords, replacement, usingRegex)
        showSearchToolbar(true)
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
                it.insertText == "/" -> editor.commentHelper.handle()
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

    companion object {

        private const val MIN_CONFIDENCE_TO_WRITE_FILE = 90
        private val DEFAULT_CHARSET_TO_WRITE_FILE = StandardCharsets.UTF_8

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
