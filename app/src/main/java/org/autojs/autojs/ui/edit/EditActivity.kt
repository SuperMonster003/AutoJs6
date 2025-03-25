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
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
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
import org.autojs.autojs.storage.file.TmpScriptFiles
import org.autojs.autojs.theme.widget.ThemeColorToolbar
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityEditBinding
import java.io.File
import java.io.IOException

/**
 * Created by Stardust on Jan 29, 2017.
 * Modified by SuperMonster003 as of Jan 21, 2023.
 */
open class EditActivity : BaseActivity(), DelegateHost, PermissionRequestProxyActivity {

    private var mToolbar: ThemeColorToolbar? = null
    private val mMediator = OnActivityResultDelegate.Mediator()

    private lateinit var mEditorView: EditorView
    private lateinit var mEditorMenu: EditorMenu

    private val mRequestPermissionCallbacks = RequestPermissionCallbacks()
    private var mNewTask = false

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityEditBinding.inflate(layoutInflater).also { setContentView(it.root) }
        mToolbar = findViewById<ThemeColorToolbar>(R.id.toolbar).apply {
            setTitleTextAppearance(this@EditActivity, R.style.TextAppearanceEditorTitle)
        }
        mEditorView = binding.editorView.apply {
            handleIntent(intent)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Observers.emptyConsumer()) { ex: Throwable -> onLoadFileError(ex.message) }
        }
        mEditorMenu = EditorMenu(mEditorView)
        mNewTask = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
        setUpToolbar()
    }

    private fun onLoadFileError(message: String?) {
        MaterialDialog.Builder(this)
            .title(getString(R.string.text_cannot_read_file))
            .content(message ?: "")
            .positiveText(R.string.text_exit)
            .cancelable(false)
            .onPositive { _, _ -> finish() }
            .show()
    }

    private fun setUpToolbar() {
        setToolbarAsBack(mEditorView.name)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        mToolbar?.let { toolbar ->
            toolbar.setMenuIconsColorByThemeColorLuminance(this)
            toolbar.onceGlobalLayout {
                val titleView = toolbar.findViewById(com.google.android.material.R.id.action_bar_title) ?: run {
                    Toolbar::class.java.getDeclaredField("mTitleTextView").apply { isAccessible = true }.get(toolbar) as TextView?
                }
                titleView?.adjustTitleTextView()
            }
        }
        return true
    }

    private fun TextView.adjustTitleTextView() = this.post {
        ValueAnimator.ofFloat(this.textSize, calculatedTextSize(this) ?: return@post).let {
            it.duration = 120L
            it.addUpdateListener { this.setTextSize(TypedValue.COMPLEX_UNIT_PX, it.animatedValue as Float) }
            it.start()
        }
    }

    // @Created by JetBrains AI Assistant on Mar 24, 2025.
    private fun calculatedTextSize(textView: TextView): Float? {
        // 可用宽度, 排除内边距
        val availableWidth = textView.width - textView.paddingLeft - textView.paddingRight
        if (availableWidth <= 0) return null

        val textStr = textView.text.toString()
        val isNonAscii = textStr.any { it.code >= 0x80 }
        val paint: TextPaint = textView.paint
        val step = resources.getDimension(R.dimen.editor_title_text_size_step)

        /* ---------- 尝试一行显示 (单行) ---------- */

        textView.isSingleLine = true
        textView.maxLines = 1

        var oneLineTextSizePx = textView.textSize
        val minOneLineSizePx = resources.getDimension(R.dimen.editor_title_min_text_size_single_line)

        // 测量一行文字宽度
        paint.textSize = oneLineTextSizePx
        var measuredWidth = paint.measureText(textStr)
        // 当文字宽度超出可用宽度并且字号还高于下限的时候逐步降低字号
        while (measuredWidth > availableWidth && oneLineTextSizePx > minOneLineSizePx) {
            oneLineTextSizePx -= step
            paint.textSize = oneLineTextSizePx
            measuredWidth = paint.measureText(textStr)
        }

        // 如果一行能够显示文字, 则直接应用修改
        if (measuredWidth <= availableWidth) {
            return oneLineTextSizePx
        }

        /* ---------- 切换为两行显示 ---------- */

        textView.isSingleLine = false
        textView.maxLines = 2

        // 重置字号
        var twoLineTextSize = when (isNonAscii) {
            true -> resources.getDimension(R.dimen.editor_title_text_size_double_line_non_ascii)
            else -> resources.getDimension(R.dimen.editor_title_text_size_double_line_ascii)
        }

        val minTwoLineSize = resources.getDimension(R.dimen.editor_title_min_text_size_double_line)
        paint.textSize = twoLineTextSize

        // 检查两行显示是否足够: 利用 StaticLayout 测量文字显示效果
        fun createStaticLayout(textSize: Float): StaticLayout {
            paint.textSize = textSize
            return StaticLayout.Builder
                .obtain(textStr, 0, textStr.length, paint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .build()
        }

        var layout = createStaticLayout(twoLineTextSize)
        // 当行数超出了两行且字号尚未到达最低要求, 则逐步降低字号
        while (layout.lineCount > 2 && twoLineTextSize > minTwoLineSize) {
            twoLineTextSize -= step
            layout = createStaticLayout(twoLineTextSize)
        }
        if (layout.lineCount <= 2) {
            return twoLineTextSize
        }

        /* ---------- 切换为三行显示 ---------- */

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
        val item = menu.getItem(menu.size() - 1)

        addMenuItem(menu, item.groupId, R.id.action_delete_line, 10000, R.string.text_delete_line) { mEditorMenu.deleteLine() }
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

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        if (!mEditorView.onBackPressed()) {
            super.onBackPressed()
        }
    }

    override fun finish() {
        if (mEditorView.isTextChanged) {
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

    private fun showExitConfirmDialog() {
        MaterialDialog.Builder(this)
            .title(R.string.text_prompt)
            .content(R.string.edit_exit_without_save_warn)
            .neutralText(R.string.dialog_button_back)
            .negativeText(R.string.text_exit_directly)
            .negativeColorRes(R.color.dialog_button_caution)
            .positiveText(R.string.text_save_and_exit)
            .positiveColorRes(R.color.dialog_button_warn)
            .onNegative { _, _ -> finishAndRemoveFromRecents() }
            .onPositive { _, _ ->
                mEditorView.saveFile()
                finishAndRemoveFromRecents()
            }
            .show()
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
        if (!mEditorView.isTextChanged) {
            return
        }
        val text = mEditorView.editor.text
        if (text.length < 256 * 1024) {
            outState.putString("text", text)
        } else {
            val tmp = saveToTmpFile(text)
            if (tmp != null) {
                outState.putString("path", tmp.path)
            }
        }
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("CheckResult")
    private fun saveToTmpFile(text: String): File? = try {
        TmpScriptFiles.create(this).also { tmp ->
            Observable.just(text)
                .observeOn(Schedulers.io())
                .subscribe { t: String? -> PFiles.write(tmp, t) }
        }
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getString("text")?.let {
            mEditorView.setRestoredText(it)
            return
        }
        savedInstanceState.getString("path")?.let { path ->
            Observable.just(path)
                .observeOn(Schedulers.io())
                .map { PFiles.read(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ mEditorView.editor.text = it }, Throwable::printStackTrace)
        }
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
        fun editFile(context: Context, path: String?, newTask: Boolean) {
            editFile(context, null, path, newTask)
        }

        @JvmStatic
        fun editFile(context: Context, uri: Uri?, newTask: Boolean) {
            runCatching {
                context.startActivity(newIntent(context).setData(uri))
            }.getOrElse {
                context.startActivity(newIntentFallback(context, newTask).setData(uri))
            }
        }

        @JvmStatic
        fun editFile(context: Context, name: String?, path: String?, newTask: Boolean) {
            runCatching {
                context.startActivity(newIntent(context).apply {
                    putExtra(EditorView.EXTRA_PATH, path)
                    putExtra(EditorView.EXTRA_NAME, name)
                })
            }.getOrElse {
                context.startActivity(newIntentFallback(context, newTask).apply {
                    putExtra(EditorView.EXTRA_PATH, path)
                    putExtra(EditorView.EXTRA_NAME, name)
                })
            }
        }

        @JvmStatic
        fun viewContent(context: Context, name: String?, content: String?, newTask: Boolean) {
            runCatching {
                context.startActivity(newIntent(context).apply {
                    putExtra(EditorView.EXTRA_CONTENT, content)
                    putExtra(EditorView.EXTRA_NAME, name)
                    putExtra(EditorView.EXTRA_READ_ONLY, true)
                })
            }.getOrElse {
                context.startActivity(newIntentFallback(context, newTask).apply {
                    putExtra(EditorView.EXTRA_CONTENT, content)
                    putExtra(EditorView.EXTRA_NAME, name)
                    putExtra(EditorView.EXTRA_READ_ONLY, true)
                })
            }
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