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
import android.util.Log
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
import org.autojs.autojs.core.pref.Pref.getEditorTextSize
import org.autojs.autojs.core.pref.Pref.setEditorTextSize
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.event.BackPressedHandler.HostActivity
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.extension.MaterialDialogExtensions.choiceWidgetThemeColor
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
import org.autojs.autojs.pio.PFiles.move
import org.autojs.autojs.pio.PFiles.write
import org.autojs.autojs.storage.file.TmpScriptFiles
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
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.widget.EWebView
import org.autojs.autojs.ui.widget.SimpleTextWatcher
import org.autojs.autojs.util.DisplayUtils.pxToSp
import org.autojs.autojs.util.DocsUtils.getUrl
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.EditorViewBinding
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Created by Stardust on Sep 28, 2017.
 * Modified by SuperMonster003 as of May 1, 2023.
 * Transformed by SuperMonster003 on May 1, 2023.
 */
@SuppressLint("CheckResult")
class EditorView : LinearLayout, OnHintClickListener, ClickCallback, ToolbarFragment.OnMenuItemClickListener {

    private var binding: EditorViewBinding = EditorViewBinding.bind(View.inflate(context, R.layout.editor_view, this))

    @JvmField
    val editor: CodeEditor = binding.editor

    @JvmField
    val debugBar: DebugBar = binding.debugBar

    lateinit var name: String

    lateinit var uri: Uri

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
        intent.getStringExtra(EXTRA_NAME)?.let { name = it }
        return handleText(intent)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                mReadOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false)
                val saveEnabled = intent.getBooleanExtra(EXTRA_SAVE_ENABLED, true)
                if (mReadOnly || !saveEnabled) {
                    findViewById<View>(R.id.save).visibility = GONE
                }
                if (!intent.getBooleanExtra(EXTRA_RUN_ENABLED, true)) {
                    findViewById<View>(R.id.run).visibility = GONE
                }
                if (mReadOnly) {
                    editor.setReadOnly(true)
                }
            }
    }

    fun setRestoredText(text: String) {
        mRestoredText = text
        editor.text = text
    }

    private fun handleText(intent: Intent): Observable<String> {
        val path = intent.getStringExtra(EXTRA_PATH)
        val content = intent.getStringExtra(EXTRA_CONTENT)
        if (content != null) {
            setInitialText(content)
            return Observable.just(content)
        }
        uri = if (path == null) {
            intent.data ?: return Observable.error(IllegalArgumentException("path and content is empty"))
        } else {
            Uri.fromFile(File(path))
        }
        if (!::name.isInitialized) {
            name = getNameWithoutExtension(uri.path!!)
        }
        return loadUri(uri)
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
            setOnMenuItemLongClickListener { id -> if (id == R.id.run) true.also { debug() } else false }
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
    fun run(showMessage: Boolean, file: File? = uri.path?.let { File(it) }, overriddenFullPath: String? = null): ScriptExecution? {
        file ?: return null
        if (showMessage) {
            showSnack(this, R.string.text_start_running)
        }
        // TODO by Stardust on Oct 24, 2018.
        val execution = runWithBroadcastSender(
            file,
            workingDirectory = uri.path?.let { File(it).parent },
            overriddenFullPath,
        ) ?: return null
        scriptExecutionId = execution.id
        setMenuItemStatus(R.id.run, false)
        return execution
    }

    private fun runTmpFile(file: File? = uri.path?.let { File(it) }): ScriptExecution? {
        return run(true, file, uri.path)
    }

    private fun undo() = editor.undo()

    private fun redo() = editor.redo()

    fun save(): Observable<String> {
        val path = uri.path!!
        val backPath = "$path.save"
        move(path, backPath)
        return Observable
            .fromCallable {
                editor.text.apply {
                    writeTextWithCharset(uri, this)
                }
            }
            .observeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                editor.markTextAsSaved()
                setMenuItemStatus(R.id.save, false)
            }
            .doOnNext {
                if (!File(backPath).delete()) {
                    Log.e(TAG, "save: failed")
                }
            }
    }

    private fun writeTextWithCharset(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "rwt")?.use { out ->
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
            if (needBom) {
                out.write(StringUtils.bomBytes(targetCharset))
            }
            out.write(text.toByteArray(targetCharset))
        } ?: throw IOException("Cannot open output stream for $uri")
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
        openByOtherApps(uri)
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
        TextSizeSettingDialogBuilder(context)
            .initialValue(pxToSp(editor.codeEditText.textSize).toInt())
            .callback { value: Int -> setTextSize(value) }
            .show()
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
            .show()
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
        mInputMethodEnhanceBar.visibility = GONE
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
        mInputMethodEnhanceBar.visibility = VISIBLE
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

        private val TAG = EditorView::class.java.simpleName

        private const val MIN_CONFIDENCE_TO_WRITE_FILE = 90
        private val DEFAULT_CHARSET_TO_WRITE_FILE = StandardCharsets.UTF_8

        const val EXTRA_PATH = "path"
        const val EXTRA_NAME = "name"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_READ_ONLY = "readOnly"
        const val EXTRA_SAVE_ENABLED = "saveEnabled"
        const val EXTRA_RUN_ENABLED = "runEnabled"

    }

}