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
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.EViewGroup
import org.androidannotations.annotations.ViewById
import org.autojs.autojs.AutoJs
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
import org.autojs.autojs.pio.PFiles.move
import org.autojs.autojs.pio.PFiles.read
import org.autojs.autojs.pio.PFiles.write
import org.autojs.autojs.pref.Pref.getEditorTextSize
import org.autojs.autojs.pref.Pref.setEditorTextSize
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
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import java.io.File

/**
 * Created by Stardust on 2017/9/28.
 * Modified by SuperMonster003 as of May 1, 2023.
 * Transformed by SuperMonster003 on May 1, 2023.
 */
@SuppressLint("NonConstantResourceId")
@EViewGroup(R.layout.editor_view)
// FIXME by SuperMonster003 on May 13, 2023.
//  ! Failed many times to migrate this view with annotation to view binding.
//  ! And this is the last view with annotation in the whole project so far.
// FIXME by SuperMonster003 on Oct 18, 2023.
//  ! Gradle property "android.nonFinalResIds"
//  ! can't be enabled (by default since Android Gradle Plugin version 8.0),
//  ! as final resource ID used in current class.
open class EditorView : FrameLayout, OnHintClickListener, ClickCallback, ToolbarFragment.OnMenuItemClickListener {

    @JvmField
    @ViewById(R.id.editor)
    var editor: CodeEditor? = null

    @JvmField
    @ViewById(R.id.debug_bar)
    var debugBar: DebugBar? = null

    @JvmField
    @ViewById(R.id.code_completion_bar)
    var mCodeCompletionBar: CodeCompletionBar? = null

    @JvmField
    @ViewById(R.id.input_method_enhance_bar)
    var mInputMethodEnhanceBar: View? = null

    @JvmField
    @ViewById(R.id.symbol_bar)
    var mSymbolBar: CodeCompletionBar? = null

    @JvmField
    @ViewById(R.id.functions)
    var mShowFunctionsButton: ImageView? = null

    @JvmField
    @ViewById(R.id.functions_keyboard)
    var mFunctionsKeyboard: FunctionsKeyboardView? = null

    @JvmField
    @ViewById(R.id.docs)
    var mDocsWebView: EWebView? = null

    @JvmField
    @ViewById(R.id.drawer_layout)
    var mDrawerLayout: DrawerLayout? = null

    var name: String? = null
        private set

    var uri: Uri? = null
        private set

    var scriptExecutionId = 0
        private set

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
                    editor!!.jumpTo(line - 1, col)
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(mOnRunFinishedReceiver, IntentFilter(ACTION_ON_EXECUTION_FINISHED), Context.RECEIVER_NOT_EXPORTED)
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
        name = intent.getStringExtra(EXTRA_NAME)
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
                    editor!!.setReadOnly(true)
                }
            }
    }

    fun setRestoredText(text: String) {
        mRestoredText = text
        editor!!.text = text
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
        if (name == null) {
            name = getNameWithoutExtension(uri!!.path!!)
        }
        return loadUri(uri)
    }

    @SuppressLint("CheckResult")
    private fun loadUri(uri: Uri?): Observable<String> {
        return Observable.fromCallable { read(context.contentResolver.openInputStream(uri!!)!!) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { text: String -> setInitialText(text) }
    }

    private fun setInitialText(text: String) {
        if (mRestoredText != null) {
            editor!!.text = mRestoredText!!
            mRestoredText = null
            return
        }
        editor!!.setInitialText(text)
    }

    private fun setMenuItemStatus(id: Int, enabled: Boolean) {
        mMenuItemStatus.put(id, enabled)
        val supportManager = activity.supportFragmentManager
        val fragment = supportManager.findFragmentById(R.id.toolbar_menu) as ToolbarFragment?
        if (fragment == null) {
            mNormalToolbar.setMenuItemStatus(id, enabled)
        } else {
            fragment.setMenuItemStatus(id, enabled)
        }
    }

    fun getMenuItemStatus(id: Int, defValue: Boolean): Boolean {
        return mMenuItemStatus[id, defValue]
    }

    @SuppressLint("CheckResult")
    @AfterViews
    fun init() {
        setUpEditor()
        setUpInputMethodEnhancedBar()
        setUpFunctionsKeyboard()
        setMenuItemStatus(R.id.save, false)
        mDocsWebView?.apply {
            webView.settings.displayZoomControls = true
            webView.loadUrl(getUrl("index.html"))
        }
        Themes.getCurrent(context)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { theme: Theme? -> setTheme(theme) }
        initNormalToolbar()
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

            // @Hint by SuperMonster003 on Jul 20, 2023.
            //  ! Note the order in which setFunctionsView and setFunctionsTrigger are called.
            //  ! zh-CN: 需留意 setFunctionsView 与 setFunctionsTrigger 的先后顺序.
            .setFunctionsView(mFunctionsKeyboard)
            .setFunctionsTrigger(mShowFunctionsButton)

            .setEditView(editor!!.codeEditText)
            .build()

        // @ArchivedTodo by 抠脚本人 on Jul 10, 2023.
        //  ! 不清楚作用, 暂时注释掉.
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
        mFunctionsKeyboard!!.setClickCallback(this)

        mShowFunctionsButton!!.setOnLongClickListener {
            true.also { editor!!.beautifyCode() }
        }
    }

    private fun setUpInputMethodEnhancedBar() {
        mSymbolBar!!.let { bar ->
            bar.setOnHintClickListener(this)
            bar.codeCompletions = Symbols.getSymbols()
        }
        mCodeCompletionBar!!.let { bar ->
            bar.setOnHintClickListener(this)
            AutoCompletion(context, editor!!.codeEditText).apply {
                setAutoCompleteCallback { bar.codeCompletions = it }
                mAutoCompletion = this
            }
        }
    }

    private fun setUpEditor() {
        editor!!.let { editor ->
            editor.codeEditText.let { editText ->
                editText.addTextChangedListener(SimpleTextWatcher { _ ->
                    setMenuItemStatus(R.id.save, editor.isTextChanged)
                    setMenuItemStatus(R.id.undo, editor.canUndo())
                    setMenuItemStatus(R.id.redo, editor.canRedo())
                })
                editText.textSize = getEditorTextSize(pxToSp(context, editText.textSize).toInt()).toFloat()
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
            editor!!.setTheme(it)
            mInputMethodEnhanceBar!!.setBackgroundColor(it.imeBarBackgroundColor)
            val textColor = it.imeBarForegroundColor
            mCodeCompletionBar!!.setTextColor(textColor)
            mSymbolBar!!.setTextColor(textColor)
            mShowFunctionsButton!!.setColorFilter(textColor)
            invalidate()
        }
    }

    fun onBackPressed(): Boolean {
        if (mDrawerLayout!!.isDrawerOpen(GravityCompat.START)) {
            if (mDocsWebView!!.webView.canGoBack()) {
                mDocsWebView!!.webView.goBack()
            } else {
                mDrawerLayout!!.closeDrawer(GravityCompat.START)
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
    fun run(showMessage: Boolean, file: File? = uri!!.path?.let { File(it) }, overriddenFullPath: String? = null): ScriptExecution? {
        file ?: return null
        if (showMessage) {
            showSnack(this, R.string.text_start_running)
        }
        // TODO: 2018/10/24
        val execution = runWithBroadcastSender(
            file,
            uri!!.path?.let { File(it).parent },
            overriddenFullPath,
        ) ?: return null
        scriptExecutionId = execution.id
        setMenuItemStatus(R.id.run, false)
        return execution
    }

    private fun runTmpFile(file: File? = uri!!.path?.let { File(it) }): ScriptExecution? {
        return run(true, file, uri!!.path)
    }

    private fun undo() = editor!!.undo()

    private fun redo() = editor!!.redo()

    fun save(): Observable<String> {
        val path = uri!!.path
        val backPath = "$path.save"
        move(path!!, backPath)
        return Observable.just(editor!!.text)
            .observeOn(Schedulers.io())
            .doOnNext { s: String? -> write(context.contentResolver.openOutputStream(uri!!)!!, s!!) }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                editor!!.markTextAsSaved()
                setMenuItemStatus(R.id.save, false)
            }
            .doOnNext {
                if (!File(backPath).delete()) {
                    Log.e(TAG, "save: failed")
                }
            }
    }

    fun forceStop() {
        doWithCurrentEngine { obj: ScriptEngine<*> -> obj.forceStop() }
    }

    private fun doWithCurrentEngine(callback: Callback<ScriptEngine<*>>) {
        val execution = AutoJs.instance.scriptEngineService.getScriptExecution(scriptExecutionId)
        if (execution != null) {
            val engine = execution.engine
            if (engine != null) {
                callback.call(engine)
            }
        }
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

    private fun findNext() = editor!!.findNext()

    private fun findPrev() = editor!!.findPrev()

    private fun cancelSearch() = showNormalToolbar()

    private fun showNormalToolbar() {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.toolbar_menu, mNormalToolbar)
            .commitAllowingStateLoss()
    }

    val activity: FragmentActivity
        get() {
            var context = context
            while (context !is Activity && context is ContextWrapper) {
                context = context.baseContext
            }
            return context as FragmentActivity
        }

    fun replace() {
        editor!!.replaceSelection()
    }

    val isTextChanged: Boolean
        get() = editor!!.isTextChanged

    fun showConsole() {
        doWithCurrentEngine { engine: ScriptEngine<*> -> (engine as JavaScriptEngine).runtime.console.show() }
    }

    fun openByOtherApps() {
        if (uri != null) {
            openByOtherApps(uri!!)
        }
    }

    fun beautifyCode() {
        editor!!.beautifyCode()
    }

    @SuppressLint("CheckResult")
    fun selectEditorTheme() {
        editor!!.setProgress(true)
        Themes.getAllThemes(context)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { themes: List<Theme?> ->
                editor!!.setProgress(false)
                selectEditorTheme(themes)
            }
    }

    fun selectTextSize() {
        TextSizeSettingDialogBuilder(context)
            .initialValue(pxToSp(context, editor!!.codeEditText.textSize).toInt())
            .callback { value: Int -> setTextSize(value) }
            .show()
    }

    fun setTextSize(value: Int) {
        setEditorTextSize(value)
        editor!!.codeEditText.textSize = value.toFloat()
        editor!!.lastTextSize = value
    }

    private fun selectEditorTheme(themes: List<Theme?>) {
        var i = themes.indexOf(mEditorTheme)
        if (i < 0) {
            i = 0
        }
        MaterialDialog.Builder(context)
            .title(R.string.text_editor_theme)
            .items(themes)
            .itemsCallbackSingleChoice(i) { _, _, which, _ ->
                themes[which]?.let {
                    setTheme(it)
                    Themes.setCurrent(it.name)
                }
                true
            }
            .show()
    }

    @Throws(CheckedPatternSyntaxException::class)
    fun find(keywords: String, usingRegex: Boolean) {
        editor!!.find(keywords, usingRegex)
        showSearchToolbar(false)
    }

    private fun showSearchToolbar(showReplaceItem: Boolean) {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.toolbar_menu, SearchToolbarFragment().apply {
                setOnMenuItemClickListener(this@EditorView)
                arguments?.putBoolean(SearchToolbarFragment.ARGUMENT_SHOW_REPLACE_ITEM, showReplaceItem)
            })
            .commit()
    }

    @Throws(CheckedPatternSyntaxException::class)
    fun replace(keywords: String, replacement: String, usingRegex: Boolean) {
        editor!!.replace(keywords, replacement, usingRegex)
        showSearchToolbar(true)
    }

    @Throws(CheckedPatternSyntaxException::class)
    fun replaceAll(keywords: String, replacement: String, usingRegex: Boolean) {
        editor!!.replaceAll(keywords, replacement, usingRegex)
    }

    fun debug() {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.toolbar_menu, DebugToolbarFragment())
            .commit()
        debugBar!!.visibility = VISIBLE
        mInputMethodEnhanceBar!!.visibility = GONE
        mDebugging = true
    }

    fun exitDebugging() {
        val fragmentManager = activity.supportFragmentManager
        val fragment = fragmentManager.findFragmentById(R.id.toolbar_menu)
        if (fragment is DebugToolbarFragment) {
            fragment.detachDebugger()
        }
        showNormalToolbar()
        editor!!.setDebuggingLine(-1)
        debugBar!!.visibility = GONE
        mInputMethodEnhanceBar!!.visibility = VISIBLE
        mDebugging = false
    }

    private fun showErrorMessage(msg: String) {
        Snackbar.make(this@EditorView, context.getString(R.string.text_error) + ": " + msg, Snackbar.LENGTH_LONG)
            .setAction(R.string.text_details) { LogActivity.launch(context) }
            .show()
    }

    override fun onHintClick(completions: CodeCompletions, pos: Int) {
        val completion = completions[pos]

        // @Overruled by SuperMonster003 on Jul 12, 2023.
        //  ! Author: 抠脚本人
        //  ! Related PR:
        //  ! http://pr.autojs6.com/98
        //  ! Reason:
        //  ! In any case, only the simplest input functions should be realized
        //  ! when clicking on the keys of the function keyboard.
        //  ! zh-CN: 在任何情况下, 单击功能键盘的按键时, 均应实现且仅实现最简单的输入功能.
        //  !
        // @ArchivedTodo by 抠脚本人 on Jul 11, 2023.
        //  ! 增加行注释
        // if (completion.insertText == "/") {
        //     editor!!.commentLine()
        // } else editor!!.insert(completion.insertText)

        editor!!.insert(completion.insertText)
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
        // val completion = completions[pos]
        // @ArchivedTodo by 抠脚本人 on Jul 10, 2023.
        //  ! 增加块注释
        // if (completion.insertText == "/") {
        //     editor!!.commentBlock()
        //     return
        // }

        completions[pos].let {
            when {
                // @Inspired by 抠脚本人 (https://github.com/little-alei) on Jul 13, 2023.
                it.insertText == "/" -> editor!!.commentHelper.handle()
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
                mDocsWebView!!.webView.loadUrl(absUrl)
                mDrawerLayout!!.openDrawer(GravityCompat.START)
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
            editor!!.insert(p)
        } else {
            editor!!.insert(m.name + "." + p)
        }
        if (!property.isVariable) {
            editor!!.moveCursor(-1)
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

    val scriptExecution: ScriptExecution?
        get() = AutoJs.instance.scriptEngineService.getScriptExecution(scriptExecutionId)

    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        val superData = super.onSaveInstanceState()
        bundle.putParcelable("super_data", superData)
        bundle.putInt("script_execution_id", scriptExecutionId)
        return bundle
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
        editor!!.destroy()
        mAutoCompletion?.shutdown()
    }

    @SuppressLint("CheckResult")
    private fun saveToTmpFile(): Observable<File> {
        return Observable.fromCallable {
            val tmp = TmpScriptFiles.create(context)
            write(tmp, editor!!.text)
            mTmpSavedFileForRunning = tmp
            tmp
        }.observeOn(Schedulers.io())
    }

    fun cleanBeforeExit() {
        mTmpSavedFileForRunning?.deleteOnExit()
    }

    companion object {
        private val TAG = EditorView::class.java.simpleName
        const val EXTRA_PATH = "path"
        const val EXTRA_NAME = "name"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_READ_ONLY = "readOnly"
        const val EXTRA_SAVE_ENABLED = "saveEnabled"
        const val EXTRA_RUN_ENABLED = "runEnabled"
    }
}