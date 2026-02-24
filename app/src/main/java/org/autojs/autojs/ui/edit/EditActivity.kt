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
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.app.OnActivityResultDelegate
import org.autojs.autojs.app.OnActivityResultDelegate.DelegateHost
import org.autojs.autojs.core.permission.OnRequestPermissionsResultCallback
import org.autojs.autojs.core.permission.PermissionRequestProxyActivity
import org.autojs.autojs.core.permission.RequestPermissionCallbacks
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.file.StableDraftFileHelper
import org.autojs.autojs.theme.widget.ThemeColorToolbar
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.error.ErrorDialogActivity
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.main.scripts.EditableFileInfoDialogManager
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setOnTitleViewClickListener
import org.autojs.autojs.util.ViewUtils.titleView
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityEditBinding
import java.io.File

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

    private val mRequestPermissionCallbacks = RequestPermissionCallbacks()
    private var mNewTask = false

    private lateinit var draftFileHelper: StableDraftFileHelper

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {

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
        }
        EditorMenu(editorView, readOnly).also {
            mEditorMenu = it
        }

        // Restore draft as early as possible to avoid being overwritten by async file loading.
        // We intentionally do this in onCreate(), not in onRestoreInstanceState(),
        // because handleIntent() may start async loading and call setInitialText() later.
        //
        // zh-CN:
        // 尽可能早地恢复草稿, 避免被异步文件加载覆盖.
        // 我们刻意在 onCreate() 中恢复, 而不是在 onRestoreInstanceState() 中恢复,
        // 因为 handleIntent() 可能启动异步加载并在稍后调用 setInitialText().
        savedInstanceState?.getString("text")?.let { draftText ->
            mEditorView.restoreDraftTextForThisSession(draftText)
        } ?: savedInstanceState?.getString("path")?.let { path ->
            Observable.just(path)
                .observeOn(Schedulers.io())
                .map { PFiles.read(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ draftText ->
                    mEditorView.restoreDraftTextForThisSession(draftText)
                }, Throwable::printStackTrace)
        }

        // Use a stable key from intent instead of editorView.uri (which is not set yet here).
        // zh-CN: 使用 intent 中的稳定 key, 避免此处 editorView.uri 尚未赋值导致 key 为 null.
        val draftKeyPath = intent.getStringExtra(EditorView.EXTRA_PATH) ?: intent.data?.path

        StableDraftFileHelper(this, draftKeyPath).also {
            draftFileHelper = it
        }
        (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0).also {
            mNewTask = it
        }

        toolbar.setTitleTextAppearance(this, R.style.TextAppearanceEditorTitle)
        toolbar.setOnTitleViewClickListener {
            val path = mEditorView.uri?.path
            if (path != null) {
                EditableFileInfoDialogManager.showEditableFileInfoDialog(this, File(path)) {
                    mEditorView.editor.text
                }
            }
        }
        editorView.handleIntent(intent)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(Observers.emptyConsumer()) { ex: Throwable -> onLoadFileError(ex.message) }

        setToolbarAsBack(editorView.name)
        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)
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

        if (!mReadOnly) {
            addMenuItem(menu, item.groupId, R.id.action_delete_line, 10000, R.string.text_delete_line) { mEditorMenu.deleteLine() }
        }
        addMenuItem(menu, item.groupId, R.id.action_copy_line, 20000, R.string.text_copy_line) { mEditorMenu.copyLine() }

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
                            draftFileHelper.deleteDraft()

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
        super.onDestroy()
        mEditorView.destroy()
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
        val needDraft = mEditorView.isTextChanged || mEditorView.saveStickyDirty
        if (!needDraft) {
            super.onSaveInstanceState(outState)
            return
        }

        val text = mEditorView.editor.text
        when {
            text.length < 256 * 1024 -> {
                Log.d(LOG_TAG, "saveDraftText, length: ${text.length}")
                outState.putString("text", text)
            }
            else -> {
                Log.d(LOG_TAG, "saveDraftFile, length: ${text.length}")

                // Use stable file key (real script path) to avoid accumulating tmp files.
                // zh-CN: 使用稳定 key (脚本真实 path) 避免累计 tmp 文件.
                draftFileHelper.saveDraft(text)?.let { tmp ->
                    outState.putString("path", tmp.path)
                }
            }
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

        @JvmStatic
        fun editFile(context: Context, path: String?, newTask: Boolean) =
            editFile(context, null, path, newTask)

        @JvmStatic
        fun editFile(context: Context, uri: Uri?, newTask: Boolean) =
            when {
                newIntent(context).setData(uri).startSafely(context) -> true
                newIntentFallback(context, newTask).setData(uri).startSafely(context) -> true
                else -> false
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