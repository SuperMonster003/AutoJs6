package org.autojs.autojs.ui.log

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.AutoJs.Companion.instance
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.pref.keys.ConsoleKeys
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.util.IntentUtils.startSafelyWithOptions
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityLogBinding
import java.io.File

class LogActivity : BaseActivity() {

    private lateinit var mBinding: ActivityLogBinding
    private lateinit var mConsoleImpl: GlobalConsole
    private var mEditorScriptName: String? = null
    private var mEditorScriptPath: String? = null
    private lateinit var mWorkingDirectory: String
    private lateinit var mTypeScriptEvaluator: TypeScriptConsoleEvaluator
    private var mTypeScriptAvailabilityJob: Job? = null
    private var mTypeScriptConsoleAvailable = false
    private var mTypeScriptConsoleVisible = false
    private lateinit var mSettings: LogPageConsoleSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityLogBinding.inflate(layoutInflater)
        mEditorScriptName = intent.getStringExtra(EXTRA_EDITOR_SCRIPT_NAME).takeUnless { it.isNullOrBlank() }
        mEditorScriptPath = intent.getStringExtra(EXTRA_EDITOR_SCRIPT_PATH).takeUnless { it.isNullOrBlank() }
        mWorkingDirectory = mEditorScriptPath
            ?.let(::File)
            ?.absoluteFile
            ?.parent
            ?.takeIf(String::isNotBlank)
            ?: WorkingDirectoryUtils.path
        mTypeScriptEvaluator = TypeScriptConsoleEvaluator(
            workingDirectory = mWorkingDirectory,
        )

        setContentView(mBinding.root)
        setToolbarAsBack(R.string.text_log)
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { view, insets ->
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars(),
            ).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
            insets
        }
        ViewCompat.requestApplyInsets(mBinding.root)

        mBinding.console.apply {
            setConsole(instance.globalConsole.also { mConsoleImpl = it })
            setLogActivity(this@LogActivity)
            setPinchToZoomEnabled(true)
            setEnableStackFrameLinks(true)
            setOnStackFrameClickListener { fileName, lineNumber, columnNumber ->
                openStackFrameTarget(fileName, lineNumber, columnNumber)
            }
            mSettings = LogPageConsoleSettings(this, ConsoleKeys.LOG_ACTIVITY_INPUT_BAR_VISIBLE).also { it.apply() }
            setOnCommandSubmitListener { source ->
                setCommandInputBusy(true)
                mTypeScriptEvaluator.evaluate(source) {
                    post { setCommandInputBusy(false) }
                }
            }
            setOnCommandStopListener { mTypeScriptEvaluator.stop() }
        }

        mBinding.fab.apply {
            setOnClickListener { mConsoleImpl.clear() }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTypeScriptConsoleAvailability()
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
            newTask = false,
        )
        if (!opened) {
            showToast(this, R.string.text_failed_to_locate, true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_console, menu)
        syncTypeScriptConsoleMenuItem(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        syncTypeScriptConsoleMenuItem(menu)
        return super.onPrepareOptionsMenu(menu)
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
                mSettings.showDialog(this)
            }
            R.id.action_input_bar -> {
                mSettings.isInputBarVisible = !mSettings.isInputBarVisible
                invalidateOptionsMenu()
            }
            R.id.action_typescript_console -> {
                setTypeScriptConsoleVisible(!mTypeScriptConsoleVisible, persist = true)
            }
        }
        return true
    }

    private fun refreshTypeScriptConsoleAvailability() {
        mTypeScriptAvailabilityJob?.cancel()
        mTypeScriptAvailabilityJob = lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) {
                TypeScriptConsoleAvailability.isAvailable(applicationContext)
            }
            mTypeScriptConsoleAvailable = available
            if (!available) {
                mTypeScriptEvaluator.stop()
            }
            setTypeScriptConsoleVisible(
                visible = available && ConsoleKeys.LOG_ACTIVITY_TYPESCRIPT_CONSOLE_VISIBLE.get(),
                persist = false,
            )
        }
    }

    private fun setTypeScriptConsoleVisible(visible: Boolean, persist: Boolean) {
        val resolved = visible && mTypeScriptConsoleAvailable
        mTypeScriptConsoleVisible = resolved
        mBinding.console.isCommandModeEnabled = resolved
        mBinding.toolbar.subtitle = if (resolved) {
            getString(R.string.text_working_dir_path_with_value, mWorkingDirectory)
        } else {
            null
        }
        if (persist) {
            ConsoleKeys.LOG_ACTIVITY_TYPESCRIPT_CONSOLE_VISIBLE.set(resolved)
        }
        invalidateOptionsMenu()
    }

    private fun syncTypeScriptConsoleMenuItem(menu: Menu) {
        menu.findItem(R.id.action_typescript_console)?.apply {
            isVisible = mTypeScriptConsoleAvailable
            isChecked = mTypeScriptConsoleVisible
        }
        menu.findItem(R.id.action_input_bar)?.isChecked = mSettings.isInputBarVisible
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
