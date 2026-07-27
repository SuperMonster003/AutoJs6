package org.autojs.autojs.ui.edit

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.get
import androidx.core.view.size
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.app.OnActivityResultDelegate
import org.autojs.autojs.app.OnActivityResultDelegate.DelegateHost
import org.autojs.autojs.core.permission.OnRequestPermissionsResultCallback
import org.autojs.autojs.core.permission.PermissionRequestProxyActivity
import org.autojs.autojs.core.permission.RequestPermissionCallbacks
import org.autojs.autojs.theme.widget.ThemeColorToolbar
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.edit.editor.service.EditorDraftService
import org.autojs.autojs.ui.edit.editor.service.editorDocumentIdentity
import org.autojs.autojs.ui.edit.editor.service.EditorPendingSaveAsObservation
import org.autojs.autojs.ui.edit.editor.service.EditorPendingSaveAsRecord
import org.autojs.autojs.ui.edit.editor.service.EditorPendingSaveAsRegistration
import org.autojs.autojs.ui.edit.editor.service.EditorPendingSaveAsStore
import org.autojs.autojs.ui.error.ErrorDialogActivity
import org.autojs.autojs.ui.log.LogBottomSheet
import org.autojs.autojs.ui.log.StackFrameTargetResolver
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.titleView
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityEditBinding
import java.io.File
import java.util.UUID

/**
 * Created by Stardust on Jan 29, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 15, 2026.
 * Modified by SuperMonster003 as of Feb 15, 2026.
 */
open class EditActivity : BaseActivity(), DelegateHost, PermissionRequestProxyActivity {

    private var mReadOnly: Boolean = false

    private val mOnBackPressedCallback = object : OnBackPressedCallback(true) {

        // override fun onBackPressed() {
        //     if (!mEditorView.onBackPressed()) {
        //         super.onBackPressed()
        //     }
        // }

        override fun handleOnBackPressed() {
            if (mEditorView.onBackPressed()) {
                return
            }
            mEditorView.cancelLargeFileLoading()

            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override val handleContentViewFromHorizontalNavigationBarAutomatically = false

    private var mToolbar: ThemeColorToolbar? = null
    private val mMediator = OnActivityResultDelegate.Mediator()

    private lateinit var mEditorView: EditorView
    private lateinit var mEditorMenu: EditorMenu
    private var mLogBottomSheet: LogBottomSheet? = null

    private val mRequestPermissionCallbacks = RequestPermissionCallbacks()
    private var mNewTask = false

    private lateinit var draftService: EditorDraftService
    private lateinit var mEditorSessionId: String
    private var mPendingSaveAsRegistration: EditorPendingSaveAsRegistration? = null
    private var mEditorIntentHandled = false
    private var mActivityDestroyed = false

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        mEditorSessionId = ensureEditorSessionId()
        EditorPendingSaveAsStore.peekCompleted(mEditorSessionId)?.let {
            applyPendingSaveAsRecordToIntent(it)
        }
        super.onCreate(savedInstanceState)

        val binding = ActivityEditBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }
        val readOnly = intent.getBooleanExtra(EditorView.EXTRA_READ_ONLY, false).also {
            mReadOnly = it
        }
        val toolbar = findViewById<ThemeColorToolbar>(R.id.toolbar).also {
            mToolbar = it
        }
        val editorView = binding.editorView.also {
            mEditorView = it
            it.setEditorSessionId(mEditorSessionId)
            it.setCompletedSaveAsAdoptedListener { record, exactSavedSnapshot ->
                if (exactSavedSnapshot) {
                    draftService.deleteDraft()
                }
                EditorPendingSaveAsStore.removeIfSame(record)
            }
        }
        EditorMenu(editorView, readOnly).also {
            mEditorMenu = it
        }

        // Use a stable key from intent instead of editorView.uri (which is not set yet here).
        // zh-CN: 使用 intent 中的稳定 key, 避免此处 editorView.uri 尚未赋值导致 key 为 null.
        val draftKeyPath = intent.getStringExtra(EditorView.EXTRA_PATH)
            ?.let { Uri.fromFile(File(it)).editorDocumentIdentity() }
            ?: intent.data?.editorDocumentIdentity()

        draftService = EditorDraftService(
            context = this,
            draftKeyPath = draftKeyPath,
            currentText = { mEditorView.textForDraftSaving() },
            shouldSaveDraft = { mEditorView.shouldSaveDraft() },
            restoreDraftText = { draftText -> mEditorView.restoreDraftTextForThisSession(draftText) },
            onRestorePendingChanged = { pending -> mEditorView.setDraftRestorePending(pending) },
            log = { message -> Log.d(LOG_TAG, message) },
        )
        editorView.setDocumentLocationChangedListener { documentUri, documentName ->
            val newDraftKey = documentUri.editorDocumentIdentity()
            draftService.rebindDraftKeyPath(newDraftKey)
            intent = Intent(intent).apply {
                data = documentUri
                removeExtra(EditorView.EXTRA_CONTENT)
                if (documentUri.scheme.equals("file", ignoreCase = true)) {
                    putExtra(EditorView.EXTRA_PATH, documentUri.path)
                } else {
                    removeExtra(EditorView.EXTRA_PATH)
                }
                putExtra(EditorView.EXTRA_NAME, documentName)
            }
        }

        // Restore draft as early as possible to avoid being overwritten by async file loading.
        // We intentionally do this in onCreate(), not in onRestoreInstanceState(),
        // because handleIntent() may start async loading and call setInitialText() later.
        //
        // zh-CN:
        // 尽可能早地恢复草稿, 避免被异步文件加载覆盖.
        // 我们刻意在 onCreate() 中恢复, 而不是在 onRestoreInstanceState() 中恢复,
        // 因为 handleIntent() 可能启动异步加载并在稍后调用 setInitialText().
        draftService.restoreDraftForThisSession(savedInstanceState)

        (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0).also {
            mNewTask = it
        }

        toolbar.setTitleTextAppearance(this, R.style.TextAppearanceEditorTitle)
        setToolbarAsBack("")
        handleEditorIntentWhenSaveAsSettles()
        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)

        // Setup log bottom sheet
        setUpLogSheet()
    }

    private fun ensureEditorSessionId(): String {
        intent.getStringExtra(EditorView.EXTRA_EDITOR_SESSION_ID)
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        return UUID.randomUUID().toString().also { sessionId ->
            intent = Intent(intent).putExtra(EditorView.EXTRA_EDITOR_SESSION_ID, sessionId)
        }
    }

    private fun applyPendingSaveAsRecordToIntent(record: EditorPendingSaveAsRecord) {
        val destinationUri = Uri.fromFile(record.destination)
        intent = Intent(intent).apply {
            data = destinationUri
            removeExtra(EditorView.EXTRA_CONTENT)
            putExtra(EditorView.EXTRA_PATH, record.destination.path)
            putExtra(EditorView.EXTRA_NAME, record.destination.nameWithoutExtension)
        }
        if (::draftService.isInitialized) {
            draftService.rebindDraftKeyPath(destinationUri.editorDocumentIdentity())
        }
    }

    private fun handleEditorIntentWhenSaveAsSettles() {
        when (val observation = EditorPendingSaveAsStore.consumeOrObserve(
            mEditorSessionId,
            ::onPendingSaveAsSettled,
        )) {
            EditorPendingSaveAsObservation.None -> handleEditorIntentOnce()
            is EditorPendingSaveAsObservation.Completed -> {
                mEditorView.prepareCompletedSaveAsAdoption(observation.record)
                applyPendingSaveAsRecordToIntent(observation.record)
                handleEditorIntentOnce()
            }
            is EditorPendingSaveAsObservation.Waiting -> {
                mPendingSaveAsRegistration = observation.registration
                mEditorView.setSaveAsRecreationPending(true)
            }
        }
    }

    private fun onPendingSaveAsSettled(record: EditorPendingSaveAsRecord?) {
        if (mActivityDestroyed) return
        mPendingSaveAsRegistration = null
        if (record != null) {
            mEditorView.prepareCompletedSaveAsAdoption(record)
            applyPendingSaveAsRecordToIntent(record)
        }
        mEditorView.setSaveAsRecreationPending(false)
        handleEditorIntentOnce()
    }

    private fun handleEditorIntentOnce() {
        if (mEditorIntentHandled || mActivityDestroyed) return
        mEditorIntentHandled = true
        mEditorView.handleIntent(intent)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(Observers.emptyConsumer()) { ex: Throwable -> onLoadFileError(ex.message) }
    }

    private fun setUpLogSheet() {
        mEditorView.setScriptRunningStateListener(object : EditorView.ScriptRunningStateListener {
            override fun onScriptRunningStateChanged(isRunning: Boolean) {
                val bottomSheet = mLogBottomSheet
                if (bottomSheet == null || !bottomSheet.isAdded) {
                    if (bottomSheet != null && !bottomSheet.isAdded) {
                        mLogBottomSheet = null
                    }
                    return
                }
                bottomSheet.updateScriptRunningState(isRunning)
            }
        })

        mEditorView.setLogPanelCallback(object : EditorView.LogPanelCallback {
            override fun onShowLogPanel() {
                val existing = supportFragmentManager.findFragmentByTag(LOG_BOTTOM_SHEET_TAG) as? LogBottomSheet
                if (existing?.isAdded == true) {
                    mLogBottomSheet = existing
                    existing.updateScriptRunningState(mEditorView.isScriptRunning())
                    return
                }

                val scriptName = mEditorView.name
                val scriptPath = mEditorView.uri?.path

                val bottomSheet = LogBottomSheet.newInstance(scriptName, scriptPath, mEditorView.getEditorTheme()?.backgroundColor)
                bottomSheet.setOnStackFrameClickListener(object : LogBottomSheet.OnStackFrameClickListener {
                    override fun onStackFrameClick(fileName: String, lineNumber: Int, columnNumber: Int): LogBottomSheet.StackFrameClickResult {
                        return try {
                            if (lineNumber < 0) {
                                return LogBottomSheet.StackFrameClickResult.FAILED
                            }
                            val currentPath = mEditorView.uri?.path
                            val column = if (columnNumber > 0) columnNumber else 0
                            if (StackFrameTargetResolver.matchesCurrentFile(fileName, currentPath)) {
                                val editor = mEditorView.editor
                                if (lineNumber !in 0 until editor.lineCountSnapshot) {
                                    return LogBottomSheet.StackFrameClickResult.FAILED
                                }
                                editor.jumpTo(lineNumber, column)
                                editor.requestEditorFocus()
                                return LogBottomSheet.StackFrameClickResult.DISMISS
                            }
                            val targetPath = StackFrameTargetResolver.resolveSourcePath(fileName, currentPath)
                                ?: return LogBottomSheet.StackFrameClickResult.FAILED
                            val opened = editFileAt(
                                context = this@EditActivity,
                                name = File(targetPath).name,
                                path = targetPath,
                                lineNumber = lineNumber,
                                columnNumber = column,
                                newTask = false,
                            )
                            if (opened) {
                                LogBottomSheet.StackFrameClickResult.KEEP_OPEN
                            } else {
                                LogBottomSheet.StackFrameClickResult.FAILED
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            LogBottomSheet.StackFrameClickResult.FAILED
                        }
                    }
                })
                bottomSheet.setScriptControlListener(object : LogBottomSheet.ScriptControlListener {
                    override fun isScriptRunning(): Boolean {
                        return mEditorView.isScriptRunning()
                    }

                    override fun onRunScript() {
                        mEditorView.runAndSaveFileIfNeeded(showLogPanelAfterRun = false)
                    }

                    override fun onStopScript() {
                        mEditorView.forceStop()
                    }
                })
                bottomSheet.updateScriptRunningState(mEditorView.isScriptRunning())
                mLogBottomSheet = bottomSheet
                bottomSheet.show(supportFragmentManager, LOG_BOTTOM_SHEET_TAG)
            }
        })
    }

    private fun onLoadFileError(message: String?) {
        MaterialDialog.Builder(this)
            .title(getString(R.string.text_cannot_read_file))
            .apply { message?.let(::content) }
            .positiveText(R.string.text_exit)
            .positiveColorRes(R.color.dialog_button_failure)
            .onPositive { _, _ -> finish() }
            .cancelable(false)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        mToolbar?.let { toolbar ->
            toolbar.setMenuIconsColorByThemeColorLuminance(this)
            toolbar.onceGlobalLayout { toolbar.titleView?.adjustTitleTextView() }
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        mEditorMenu.prepareOptionsMenu(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun TextView.adjustTitleTextView() = this.post {
        ValueAnimator.ofFloat(this.textSize, calculatedTextSize(this) ?: return@post).let { animator ->
            animator.duration = 120L
            animator.addUpdateListener { this.setTextSize(TypedValue.COMPLEX_UNIT_PX, it.animatedValue as Float) }
            animator.start()
        }
    }

    // @Created by JetBrains AI Assistant on Mar 24, 2025.
    private fun calculatedTextSize(textView: TextView): Float? {

        // Available width excluding padding.
        // zh-CN: 可用宽度, 排除内边距.
        val availableWidth = textView.width - textView.paddingLeft - textView.paddingRight
        if (availableWidth <= 0) return null

        val textStr = textView.text.toString()
        val isNonAscii = textStr.any { it.code >= 0x80 }
        val paint: TextPaint = textView.paint
        val step = resources.getDimension(R.dimen.editor_title_text_size_step)

        /* Try single line display.
         * zh-CN: 尝试一行显示 (单行). */

        textView.isSingleLine = true
        textView.maxLines = 1

        var oneLineTextSizePx = textView.textSize
        val minOneLineSizePx = resources.getDimension(R.dimen.editor_title_min_text_size_single_line)

        // Measure single line text width.
        // zh-CN: 测量一行文字宽度.
        paint.textSize = oneLineTextSizePx
        var measuredWidth = paint.measureText(textStr)
        // Gradually decrease text size when width exceeds and size is above minimum.
        // zh-CN: 当文字宽度超出可用宽度并且字号还高于下限的时候逐步降低字号.
        while (measuredWidth > availableWidth && oneLineTextSizePx > minOneLineSizePx) {
            oneLineTextSizePx -= step
            paint.textSize = oneLineTextSizePx
            measuredWidth = paint.measureText(textStr)
        }

        // If text fits in one line, apply changes directly.
        // zh-CN: 如果一行能够显示文字, 则直接应用修改.
        if (measuredWidth <= availableWidth) {
            return oneLineTextSizePx
        }

        /* Switch to double line display.
         * zh-CN: 切换为两行显示. */

        textView.isSingleLine = false
        textView.maxLines = 2

        // Reset text size.
        // zh-CN: 重置字号.
        var twoLineTextSize = when (isNonAscii) {
            true -> resources.getDimension(R.dimen.editor_title_text_size_double_line_non_ascii)
            else -> resources.getDimension(R.dimen.editor_title_text_size_double_line_ascii)
        }

        val minTwoLineSize = resources.getDimension(R.dimen.editor_title_min_text_size_double_line)
        paint.textSize = twoLineTextSize

        // Check if two lines are sufficient using StaticLayout to measure text display.
        // zh-CN: 检查两行显示是否足够: 利用 StaticLayout 测量文字显示效果.
        fun createStaticLayout(textSize: Float): StaticLayout {
            paint.textSize = textSize
            return StaticLayout.Builder
                .obtain(textStr, 0, textStr.length, paint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .build()
        }

        var layout = createStaticLayout(twoLineTextSize)
        // When line count exceeds 2 and text size is above minimum, decrease size gradually.
        // zh-CN: 当行数超出了两行且字号尚未到达最低要求, 则逐步降低字号.
        while (layout.lineCount > 2 && twoLineTextSize > minTwoLineSize) {
            twoLineTextSize -= step
            layout = createStaticLayout(twoLineTextSize)
        }
        if (layout.lineCount <= 2) {
            return twoLineTextSize
        }

        /* Switch to triple line display.
         * zh-CN: 切换为三行显示. */

        textView.maxLines = 3

        var threeLineSize = when (isNonAscii) {
            true -> resources.getDimension(R.dimen.editor_title_text_size_triple_line_non_ascii)
            else -> resources.getDimension(R.dimen.editor_title_text_size_triple_line_ascii)
        }

        val minThreeLineSize = resources.getDimension(R.dimen.editor_title_min_text_size_triple_line)

        paint.textSize = threeLineSize
        layout = createStaticLayout(threeLineSize)
        while (layout.lineCount > 3 && threeLineSize > minThreeLineSize) {
            threeLineSize -= step
            layout = createStaticLayout(threeLineSize)
        }
        return threeLineSize
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return mEditorMenu.onOptionsItemSelected(item)
    }

    override fun onActionModeStarted(mode: ActionMode) {
        Log.d(LOG_TAG, "onActionModeStarted: $mode")

        val menu = mode.menu
        val item = menu[menu.size - 1]

        if (!mReadOnly && !mEditorView.isReadOnly() && menu.findItem(R.id.action_delete_line) == null) {
            addMenuItem(menu, item.groupId, R.id.action_delete_line, 10000, R.string.text_delete_line) { mEditorMenu.deleteLine() }
        }
        if (menu.findItem(R.id.action_copy_line) == null) {
            addMenuItem(menu, item.groupId, R.id.action_copy_line, 20000, R.string.text_copy_line) { mEditorMenu.copyLine() }
        }

        super.onActionModeStarted(mode)
    }

    private fun addMenuItem(menu: Menu, groupId: Int, itemId: Int, order: Int, titleRes: Int, runnable: Runnable) {
        try {
            menu.add(groupId, itemId, order, titleRes).setOnMenuItemClickListener {
                try {
                    runnable.run()
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        } catch (e: Exception) {
            // @Example android.content.res.Resources.NotFoundException
            //  ! on MIUI devices (maybe more)
            e.printStackTrace()
        }
    }

    override fun finish() {
        if (mEditorView.saveStickyDirty) {
            showExitConfirmDialog()
        } else {
            finishAndRemoveFromRecents()
        }
    }

    private fun finishAndRemoveFromRecents() {
        finishAndRemoveTask()
        mEditorView.cleanBeforeExit()
        if (mNewTask) {
            MainActivity.launch(this)
        }
    }

    @SuppressLint("CheckResult")
    private fun showExitConfirmDialog() {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(R.string.edit_exit_without_save_warn)
                .neutralText(R.string.dialog_button_back)
                .negativeText(R.string.text_exit_directly)
                .onNeutral { d, _ -> d.dismiss() }
                .negativeColorRes(R.color.dialog_button_caution)
                .onNegative { d, _ ->
                    runCatching { d.dismiss() }
                    finishAndRemoveFromRecents()
                }
                .positiveText(R.string.text_save_and_exit)
                .positiveColorRes(R.color.dialog_button_warn)
                .onPositive { d, _ ->
                    // Save is async, exit only after success.
                    // zh-CN: 保存是异步的, 保存成功后再退出.
                    d.apply {
                        setCancelable(false)
                        getActionButton(DialogAction.NEUTRAL).apply {
                            isEnabled = false
                            setTextColor(getColor(R.color.dialog_button_unavailable))
                        }
                        getActionButton(DialogAction.NEGATIVE).apply {
                            isEnabled = false
                            setTextColor(getColor(R.color.dialog_button_unavailable))
                        }
                        getActionButton(DialogAction.POSITIVE).apply {
                            isEnabled = false
                            setTextColor(getColor(R.color.dialog_button_unavailable))
                        }
                        contentView?.postDelayed({
                            contentView?.text = getString(R.string.text_saving)
                        }, 300)
                    }

                    mEditorView
                        .save()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            // Save succeeded: remove draft for this file.
                            // zh-CN: 保存成功: 删除该文件对应草稿.
                            draftService.deleteDraft()

                            runCatching { d.dismiss() }
                            finishAndRemoveFromRecents()
                        }, { e: Throwable ->
                            // Save failed, keep editor open.
                            // zh-CN: 保存失败, 保持编辑器不退出.
                            e.printStackTrace()
                            runCatching { d.dismiss() }
                            ErrorDialogActivity.showErrorDialog(this@EditActivity, R.string.error_failed_to_save, e.message)
                        })
                }
                .autoDismiss(false)
                .build()
                .apply {
                    contentView?.apply {
                        setLineSpacing(0f, 1.2f)
                        setLines(2)
                        minLines = 2
                        gravity = Gravity.CENTER_VERTICAL
                    }
                }
        }
    }

    override fun onDestroy() {
        mActivityDestroyed = true
        mPendingSaveAsRegistration?.close()
        mPendingSaveAsRegistration = null
        draftService.destroy()
        mEditorView.destroy()
        super.onDestroy()
    }

    override fun getOnActivityResultDelegateMediator(): OnActivityResultDelegate.Mediator {
        return mMediator
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        mMediator.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {

        // Save draft when content actually differs from baseline OR UI indicates "needs save".
        // This makes state restore robust against any sticky/menu state glitches.
        //
        // zh-CN:
        // 当内容相对基线确实发生变化, 或 UI 表示 "需要保存" 时, 保存草稿.
        // 这能使状态恢复不再受 sticky/menu 状态偶发异常的影响.
        if (!draftService.saveDraftToInstanceState(outState)) {
            super.onSaveInstanceState(outState)
            return
        }

        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        mEditorView.syncPrimaryMenuState()
        runCatching { mEditorView.refreshSymbolsBar() }
    }

    override fun addRequestPermissionsCallback(callback: OnRequestPermissionsResultCallback) {
        mRequestPermissionCallbacks.addCallback(callback)
    }

    override fun removeRequestPermissionsCallback(callback: OnRequestPermissionsResultCallback): Boolean {
        return mRequestPermissionCallbacks.removeCallback(callback)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mRequestPermissionCallbacks.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {

        private const val LOG_TAG = "EditActivity"
        private const val LOG_BOTTOM_SHEET_TAG = "LogBottomSheet"

        @JvmStatic
        fun editFile(context: Context, path: String?, newTask: Boolean) =
            editFile(context, null, path, newTask)

        @JvmStatic
        fun editFile(context: Context, uri: Uri?, newTask: Boolean) =
            editFile(context, uri, newTask, 0)

        @JvmStatic
        fun editFile(context: Context, uri: Uri?, newTask: Boolean, uriPermissionFlags: Int): Boolean {
            val allowedPermissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            val forwardedPermissionFlags = uriPermissionFlags and allowedPermissionFlags
            return when {
                newIntent(context).apply {
                    data = uri
                    addFlags(forwardedPermissionFlags)
                }.startSafely(context) -> true
                newIntentFallback(context, newTask).apply {
                    data = uri
                    addFlags(forwardedPermissionFlags)
                }.startSafely(context) -> true
                else -> false
            }
        }

        @JvmStatic
        fun editFile(context: Context, name: String?, path: String?, newTask: Boolean) =
            when {
                newIntent(context).apply {
                    putExtra(EditorView.EXTRA_PATH, path)
                    putExtra(EditorView.EXTRA_NAME, name)
                }.startSafely(context) -> true
                newIntentFallback(context, newTask).apply {
                    putExtra(EditorView.EXTRA_PATH, path)
                    putExtra(EditorView.EXTRA_NAME, name)
                }.startSafely(context) -> true
                else -> false
            }

        @JvmStatic
        fun editFileAt(context: Context, name: String?, path: String?, lineNumber: Int, columnNumber: Int, newTask: Boolean) =
            when {
                newIntent(context).apply {
                    putExtra(EditorView.EXTRA_PATH, path)
                    putExtra(EditorView.EXTRA_NAME, name)
                    putExtra(EditorView.EXTRA_INITIAL_LINE, lineNumber)
                    putExtra(EditorView.EXTRA_INITIAL_COLUMN, columnNumber)
                    if (newTask || context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }.startSafely(context) -> true
                newIntentFallback(context, newTask).apply {
                    putExtra(EditorView.EXTRA_PATH, path)
                    putExtra(EditorView.EXTRA_NAME, name)
                    putExtra(EditorView.EXTRA_INITIAL_LINE, lineNumber)
                    putExtra(EditorView.EXTRA_INITIAL_COLUMN, columnNumber)
                }.startSafely(context) -> true
                else -> false
            }

        @JvmStatic
        fun viewContent(context: Context, name: String?, content: String?, newTask: Boolean) =
            when {
                newIntent(context).apply {
                    putExtra(EditorView.EXTRA_CONTENT, content)
                    putExtra(EditorView.EXTRA_NAME, name)
                    putExtra(EditorView.EXTRA_READ_ONLY, true)
                }.startSafely(context) -> true
                newIntentFallback(context, newTask).apply {
                    putExtra(EditorView.EXTRA_CONTENT, content)
                    putExtra(EditorView.EXTRA_NAME, name)
                    putExtra(EditorView.EXTRA_READ_ONLY, true)
                }.startSafely(context) -> true
                else -> false
            }

        @JvmStatic
        fun viewPath(context: Context, name: String?, path: String?, newTask: Boolean) =
            when {
                newIntent(context).apply {
                    putExtra(EditorView.EXTRA_PATH, path)
                    putExtra(EditorView.EXTRA_NAME, name)
                    putExtra(EditorView.EXTRA_READ_ONLY, true)
                }.startSafely(context) -> true
                newIntentFallback(context, newTask).apply {
                    putExtra(EditorView.EXTRA_PATH, path)
                    putExtra(EditorView.EXTRA_NAME, name)
                    putExtra(EditorView.EXTRA_READ_ONLY, true)
                }.startSafely(context) -> true
                else -> false
            }

        private fun newIntent(context: Context): Intent {
            // @Caution by SuperMonster003 on Sep 11, 2022.
            //  ! FLAG_ACTIVITY_NEW_TASK makes screen flash when Activity started.
            //  ! The safety of disabling this flag has been well-tested on several AOSP system
            //  ! and Android Studio AVD (from API Level 24 to 33),
            //  ! but not on other systems like MIUI, EMUI, ColorOS, Oxygen OS and so forth.
            //  ! There, therefor, is a fallback named "newIntentFallback".
            //  ! zh-CN:
            //  ! FLAG_ACTIVITY_NEW_TASK 标识在启动 Activity (活动) 时会使屏幕闪烁.
            //  ! 禁用上述标识的安全性在多个 AOSP 系统
            //  ! 以及 Android Studio AVD (API 级别 24 到 33) 上均已经过良好测试,
            //  ! 但在 MIUI, EMUI, ColorOS, Oxygen OS 等其他系统上并未进行测试.
            //  ! 因此, 这里有一个名为 "newIntentFallback" 的后备方案.
            return Intent(context, EditActivity::class.java)
        }

        private fun newIntentFallback(context: Context, newTask: Boolean): Intent {
            return newIntent(context).apply {
                if (newTask || context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }
}
