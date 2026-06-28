package org.autojs.autojs.ui.log

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import org.autojs.autojs.AutoJs.Companion.instance
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.util.IntentUtils.startSafelyWithOptions
import org.autojs.autojs.util.ViewUtils.excludeFloatingActionButtonFromBottomNavigationBar
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityLogBinding
import java.io.File

class LogActivity : BaseActivity() {

    private lateinit var mConsoleImpl: GlobalConsole
    private var mEditorScriptName: String? = null
    private var mEditorScriptPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityLogBinding = ActivityLogBinding.inflate(layoutInflater)
        mEditorScriptName = intent.getStringExtra(EXTRA_EDITOR_SCRIPT_NAME).takeUnless { it.isNullOrBlank() }
        mEditorScriptPath = intent.getStringExtra(EXTRA_EDITOR_SCRIPT_PATH).takeUnless { it.isNullOrBlank() }

        setContentView(activityLogBinding.root)
        setToolbarAsBack(R.string.text_log)

        activityLogBinding.console.apply {
            setConsole(instance.globalConsole.also { mConsoleImpl = it })
            setLogActivity(this@LogActivity)
            setPinchToZoomEnabled(true)
            setEnableStackFrameLinks(mEditorScriptPath != null)
            setOnStackFrameClickListener { fileName, lineNumber, columnNumber ->
                openStackFrameTarget(fileName, lineNumber, columnNumber)
            }
        }

        activityLogBinding.fab.apply {
            setOnClickListener { mConsoleImpl.clear() }
            excludeFloatingActionButtonFromBottomNavigationBar()
        }
    }

    private fun openStackFrameTarget(fileName: String, lineNumber: Int, columnNumber: Int) {
        if (lineNumber < 0) {
            showToast(this, R.string.text_failed_to_locate, true)
            return
        }
        val targetPath = StackFrameTargetResolver.resolveSourcePath(fileName, mEditorScriptPath)
        if (targetPath == null) {
            showToast(this, R.string.text_failed_to_locate, true)
            return
        }
        val targetName = when {
            StackFrameTargetResolver.matchesCurrentFile(fileName, mEditorScriptPath) -> mEditorScriptName
            else -> File(targetPath).name
        }
        val opened = EditActivity.editFileAt(
            context = this,
            name = targetName,
            path = targetPath,
            lineNumber = lineNumber,
            columnNumber = columnNumber.coerceAtLeast(0),
            newTask = true,
        )
        if (opened) {
            finish()
        } else {
            showToast(this, R.string.text_failed_to_locate, true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_console, menu)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RESULT_CODE_EXPORT && resultCode == RESULT_OK) {
            mConsoleImpl.export(data?.data)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_copy -> {
                mConsoleImpl.copyAll()
            }
            R.id.action_send -> {
                mConsoleImpl.send()
            }
            R.id.action_export -> {
                mConsoleImpl.export()
            }
            R.id.action_clear -> {
                mConsoleImpl.clear()
            }
            R.id.action_settings -> {
                // TODO by SuperMonster003 on Jul 3, 2023.
                showToast(this, R.string.text_under_development)
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.no_anim_fade_in, R.anim.no_anim_fade_out)
    }

    @Suppress("DEPRECATION")
    fun export(fileName: String?) {
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setType(Mime.TEXT_PLAIN)
            putExtra(Intent.EXTRA_TITLE, fileName)
            startActivityForResult(this, RESULT_CODE_EXPORT)
        }
    }

    companion object {

        private const val EXTRA_EDITOR_SCRIPT_NAME = "editor_script_name"
        private const val EXTRA_EDITOR_SCRIPT_PATH = "editor_script_path"
        private const val RESULT_CODE_EXPORT = 33128

        @JvmStatic
        fun launch(context: Context) {
            launch(context, null, null)
        }

        @JvmStatic
        fun launch(context: Context, scriptName: String?, scriptPath: String?) {
            val intent = Intent(context, LogActivity::class.java).apply {
                putExtra(EXTRA_EDITOR_SCRIPT_NAME, scriptName)
                putExtra(EXTRA_EDITOR_SCRIPT_PATH, scriptPath)
            }
            val options = ActivityOptions.makeCustomAnimation(context, R.anim.no_anim_fade_in, R.anim.no_anim_fade_out)
            intent.startSafelyWithOptions(context, options.toBundle())
        }

    }

}
