package org.autojs.autojs.ui.edit

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
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
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.Observers
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityEditBinding
import java.io.File
import java.io.IOException

/**
 * Created by Stardust on 2017/1/29.
 * Modified by SuperMonster003 as of Jan 21, 2023.
 */
open class EditActivity : BaseActivity(), DelegateHost, PermissionRequestProxyActivity {

    private val mMediator = OnActivityResultDelegate.Mediator()

    private lateinit var mEditorView: EditorView
    private lateinit var mEditorMenu: EditorMenu

    private val mRequestPermissionCallbacks = RequestPermissionCallbacks()
    private var mNewTask = false

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mEditorView = binding.editorView.apply {
            handleIntent(intent)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Observers.emptyConsumer()) { ex: Throwable -> onLoadFileError(ex.message) }
        }

        mEditorMenu = EditorMenu(mEditorView)

        mNewTask = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
        setUpToolbar()
        window.statusBarColor = ThemeColorManager.colorPrimary
    }

    override fun onWindowStartingActionMode(callback: ActionMode.Callback): ActionMode? {
        return super.onWindowStartingActionMode(callback)
    }

    override fun onWindowStartingActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        return super.onWindowStartingActionMode(callback, type)
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
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return mEditorMenu.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        Log.d(LOG_TAG, "onPrepareOptionsMenu: $menu")

        // val isScriptRunning = mEditorView.scriptExecutionId != ScriptExecution.NO_ID
        // val forceStopItem = menu.findItem(R.id.action_force_stop)
        // forceStopItem.isEnabled = isScriptRunning

        return super.onPrepareOptionsMenu(menu)
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
            try {
                context.startActivity(newIntent(context).setData(uri))
            } catch (_: Exception) {
                context.startActivity(newIntentFallback(context, newTask).setData(uri))
            }
        }

        @JvmStatic
        fun editFile(context: Context, name: String?, path: String?, newTask: Boolean) {
            try {
                context.startActivity(newIntent(context).apply {
                    putExtra(EditorView.EXTRA_PATH, path)
                    putExtra(EditorView.EXTRA_NAME, name)
                })
            } catch (_: Exception) {
                context.startActivity(newIntentFallback(context, newTask).apply {
                    putExtra(EditorView.EXTRA_PATH, path)
                    putExtra(EditorView.EXTRA_NAME, name)
                })
            }
        }

        @JvmStatic
        fun viewContent(context: Context, name: String?, content: String?, newTask: Boolean) {
            try {
                context.startActivity(newIntent(context).apply {
                    putExtra(EditorView.EXTRA_CONTENT, content)
                    putExtra(EditorView.EXTRA_NAME, name)
                    putExtra(EditorView.EXTRA_READ_ONLY, true)
                })
            } catch (_: Exception) {
                context.startActivity(newIntentFallback(context, newTask).apply {
                    putExtra(EditorView.EXTRA_CONTENT, content)
                    putExtra(EditorView.EXTRA_NAME, name)
                    putExtra(EditorView.EXTRA_READ_ONLY, true)
                })
            }
        }

        private fun newIntent(context: Context): Intent {
            // @Caution by SuperMonster003 on Sep 11, 2022.
            //  ! FLAG_ACTIVITY_NEW_TASK makes screen flash when activity started.
            //  ! The safety of disabling this flag has been well-tested on several AOSP system
            //  ! and Android Studio AVD (from API Level 24 to 33),
            //  ! but not on other systems like MIUI, EMUI, ColorOS, Oxygen OS and so forth.
            //  ! There, therefor, is a fallback named "newIntentFallback".
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