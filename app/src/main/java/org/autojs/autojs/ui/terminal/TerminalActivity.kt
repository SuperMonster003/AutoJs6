package org.autojs.autojs.ui.terminal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import jackpal.androidterm.emulatorview.ColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.terminal.NodeCliLocator
import org.autojs.autojs.core.terminal.NodeCliLocator.Resolution
import org.autojs.autojs.core.terminal.ShellQuoting
import org.autojs.autojs.core.terminal.TerminalKeySequences
import org.autojs.autojs.core.terminal.TerminalNodeSetup
import org.autojs.autojs.core.terminal.TerminalPaths
import org.autojs.autojs.core.terminal.TerminalPreferences
import org.autojs.autojs.core.terminal.TerminalSessionManager
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.settings.TerminalNodeProbePreference
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityTerminalBinding
import java.io.File

/**
 * Interactive shell of the app: a pty-backed `/system/bin/sh` rendered by the jackpal emulator view,
 * with a key bar, npm helpers and a banner that explains why `node` may be unavailable.
 *
 * zh-CN: 应用内交互式 shell: 由 jackpal 模拟器视图渲染的 pty `/system/bin/sh`, 带按键栏, npm 辅助动作,
 * 以及说明 `node` 为何不可用的横幅.
 */
class TerminalActivity : BaseActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private lateinit var npmActions: TerminalNpmActions
    private var session: TerminalSessionManager.Session? = null
    private var nodeResolution: Resolution? = null
    private var nodeBannerDismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(R.string.text_terminal)
        binding.toolbar.setNavigationOnClickListener { leave() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        npmActions = TerminalNpmActions(this, ::typeCommand) { session?.pty?.currentDirectory() }

        val directory = resolveDirectory(intent)
        setUpKeyBar()
        setUpBackHandling()
        setUpNodeBanner()
        // A recreated activity restores its own session; a fresh launch reattaches the newest live
        // session (kept by TerminalSessionService) and only starts a shell when there is none.
        // zh-CN: 重建的 Activity 恢复自己的会话; 新启动时接回最新的存活会话 (由 TerminalSessionService 保持), 没有时才启动新 shell.
        val restored = savedInstanceState?.getString(STATE_SESSION_ID)?.let(TerminalSessionManager::get)
            ?: TerminalSessionManager.activeSessions.lastOrNull { it.pty.isProcessAlive }
        if (restored != null) {
            attachSession(restored)
            updateSubtitle(restored.initialDirectory)
            val requested = intent.getStringExtra(EXTRA_DIRECTORY)?.takeIf { it.isNotBlank() }
            if (savedInstanceState == null && requested != null) {
                changeDirectory(requested)
            }
            refreshNodeAvailability()
        } else {
            startSession(directory)
        }
        showTipsIfNeeded()
    }

    /**
     * Resolves the plugin launcher first so the shell starts with node / npm on its PATH; the
     * archive extraction (first run after a plugin install or update) shows a progress dialog.
     * zh-CN: 先解析插件启动器, 让 shell 启动时 PATH 中即有 node / npm; 资产解压 (插件安装或更新后的首次) 显示进度对话框.
     */
    private fun startSession(directory: String) {
        lifecycleScope.launch {
            val paths = TerminalPaths.of(this@TerminalActivity).ensureLayout()
            val resolution = withContext(Dispatchers.IO) {
                runCatching { NodeCliLocator.resolve(applicationContext) }
                    .getOrElse { Resolution.Unavailable.PluginMissing }
            }
            val progress = if (TerminalNodeSetup.needsInstall(paths, resolution)) {
                MaterialDialog.Builder(this@TerminalActivity)
                    .content(R.string.text_terminal_preparing_npm)
                    .progress(true, 0)
                    .cancelable(false)
                    .widgetThemeColor()
                    .showAdaptive()
            } else {
                null
            }
            val options = TerminalPreferences.nodeEnvironmentOptions()
            val prepared = withContext(Dispatchers.IO) {
                TerminalNodeSetup.prepare(applicationContext, paths, resolution, options)
            }
            progress?.dismiss()
            if (isFinishing || isDestroyed) return@launch
            nodeResolution = prepared.resolution
            attachSession(TerminalSessionManager.create(this@TerminalActivity, directory, prepared.environment))
            updateSubtitle(directory)
            renderNodeBanner(prepared.resolution)
            invalidateOptionsMenu()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val requested = intent.getStringExtra(EXTRA_DIRECTORY)?.takeIf { it.isNotBlank() } ?: return
        changeDirectory(requested)
    }

    /** Types a `cd` into the running shell (used when the terminal is reopened for a directory). zh-CN: 向运行中的 shell 键入 `cd` (为某目录重新打开终端时使用). */
    private fun changeDirectory(requested: String) {
        val pty = session?.pty ?: return
        if (!pty.isProcessAlive) return
        pty.clearModifiers()
        pty.write("cd " + ShellQuoting.quote(requested) + TerminalKeySequences.ENTER)
        updateSubtitle(requested)
    }

    override fun onResume() {
        super.onResume()
        binding.terminal.onResume()
        refreshSubtitle()
    }

    override fun onPause() {
        binding.terminal.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SESSION_ID, session?.id)
    }

    /**
     * Leaving the screen never ends the session: it stays in [TerminalSessionManager] under the
     * foreground service until "Close session" or the shell's own exit; only a dead shell is dropped.
     * zh-CN: 离开界面不会结束会话: 它由前台服务保持在 [TerminalSessionManager] 中, 直到 "关闭会话" 或 shell 自行退出; 仅移除已死亡的 shell.
     */
    override fun onDestroy() {
        val current = session
        if (current != null) {
            current.pty.onProcessExited = null
            current.pty.onInputAfterExit = null
            current.pty.onModifiersChanged = null
            if (isFinishing && !current.pty.isProcessAlive) {
                TerminalSessionManager.close(current.id)
            }
        }
        super.onDestroy()
    }

    private fun resolveDirectory(intent: Intent?): String {
        val requested = intent?.getStringExtra(EXTRA_DIRECTORY)?.takeIf { it.isNotBlank() }
        return requested?.takeIf { File(it).isDirectory } ?: WorkingDirectoryUtils.path
    }

    private fun attachSession(newSession: TerminalSessionManager.Session) {
        session = newSession
        val pty = newSession.pty
        val view = binding.terminal
        val scheme = ColorScheme(getColor(R.color.day_night), getColor(R.color.window_background))
        pty.setColorScheme(scheme)
        view.attachSession(pty)
        // The view allocates its paints in attachSession, so the scheme has to follow it.
        // zh-CN: 视图在 attachSession 中才创建画笔, 配色必须在其后设置.
        view.setColorScheme(scheme)
        view.setExtGestureListener(object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (view.selectingText) {
                    copyToClipboard(view.selectedText)
                    view.toggleSelectingText()
                    return true
                }
                showSoftKeyboard()
                return false
            }
        })
        registerForContextMenu(view)
        pty.onProcessExited = { onSessionEnded() }
        pty.onInputAfterExit = { finish() }
        pty.onModifiersChanged = { ctrl, alt -> binding.keyBar.setModifiers(ctrl, alt) }
        binding.keyBar.setModifiers(pty.ctrlArmed, pty.altArmed)
        if (pty.exitCode != null) {
            onSessionEnded()
        } else {
            view.postDelayed({ if (!isDestroyed) refreshSubtitle() }, SUBTITLE_REFRESH_DELAY_MILLIS)
        }
    }

    private fun setUpKeyBar() {
        binding.keyBar.listener = object : TerminalToolbarView.Listener {
            override fun onKey(key: TerminalKeySequences.Key) {
                session?.pty?.sendKey(key)
            }

            override fun onText(text: String) {
                session?.pty?.write(text)
            }

            override fun onToggleCtrl() {
                session?.pty?.toggleCtrl()
            }

            override fun onToggleAlt() {
                session?.pty?.toggleAlt()
            }
        }
    }

    private fun setUpBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leave()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        leave()
        return true
    }

    /**
     * Back / Up: the session keeps running in the background; a toast says so while a child process is busy.
     * zh-CN: 返回 / 向上: 会话在后台继续运行; 有子进程忙碌时以 toast 说明.
     */
    private fun leave() {
        val pty = session?.pty
        if (pty != null && pty.isProcessAlive && pty.hasLiveChildren()) {
            ViewUtils.showToast(this, R.string.text_terminal_session_kept_running)
        }
        finish()
    }

    /** Menu "Close session": ends the shell (after confirmation while a child process runs) and leaves. zh-CN: 菜单 "关闭会话": 结束 shell (有子进程运行时先确认) 并离开. */
    private fun requestCloseSession() {
        val current = session
        val pty = current?.pty
        if (current == null || pty == null || !pty.isProcessAlive || !pty.hasLiveChildren()) {
            current?.let { TerminalSessionManager.close(it.id) }
            finish()
            return
        }
        MaterialDialog.Builder(this)
            .title(R.string.text_terminal_close_session)
            .content(R.string.text_terminal_confirm_close_running)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_warn)
            .onPositive { _, _ ->
                TerminalSessionManager.close(current.id)
                finish()
            }
            .widgetThemeColor()
            .show()
    }

    private fun onSessionEnded() {
        binding.toolbar.subtitle = getString(R.string.text_terminal_session_ended)
        binding.keyBar.setModifiers(ctrl = false, alt = false)
    }

    private fun updateSubtitle(directory: String) {
        val paths = session?.paths ?: return
        binding.toolbar.subtitle = paths.toTildePath(directory)
    }

    /**
     * Shows where the shell actually is: the requested directory may be unreadable (no storage
     * permission), in which case the launcher falls back to `$HOME`.
     * zh-CN: 显示 shell 实际所在目录: 请求的目录可能不可读 (无存储权限), 此时启动命令会回退到 `$HOME`.
     */
    private fun refreshSubtitle() {
        val pty = session?.pty ?: return
        if (!pty.isProcessAlive) return
        val current = pty.currentDirectory()?.path ?: return
        updateSubtitle(current)
    }

    // region Node availability

    private fun setUpNodeBanner() {
        binding.nodeBanner.nodeBannerClose.setOnClickListener {
            nodeBannerDismissed = true
            binding.nodeBanner.nodeBannerRoot.isVisible = false
        }
    }

    /**
     * For restored sessions only: the environment of a running shell cannot change, so this just
     * refreshes the banner and menu state.
     * zh-CN: 仅用于恢复的会话: 运行中 shell 的环境无法更改, 这里只刷新横幅与菜单状态.
     */
    private fun refreshNodeAvailability() {
        lifecycleScope.launch {
            val resolution = withContext(Dispatchers.IO) {
                runCatching { NodeCliLocator.resolve(applicationContext) }
                    .getOrElse { Resolution.Unavailable.PluginMissing }
            }
            nodeResolution = resolution
            renderNodeBanner(resolution)
            invalidateOptionsMenu()
        }
    }

    private fun renderNodeBanner(resolution: Resolution) {
        val banner = binding.nodeBanner
        if (resolution is Resolution.Available || nodeBannerDismissed) {
            banner.nodeBannerRoot.isVisible = false
            return
        }
        banner.nodeBannerAction.setTextColor(ColorUtils.adjustThemeColorForContrast(getColor(R.color.item_background), 4.5))
        when (resolution) {
            is Resolution.Unavailable.PluginMissing -> bannerToPluginCenter(getString(R.string.text_terminal_node_missing))
            is Resolution.Unavailable.PluginNotAuthorized -> bannerToPluginCenter(getString(R.string.text_terminal_node_plugin_not_authorized))
            is Resolution.Unavailable.PluginTooOld -> bannerToPluginCenter(getString(R.string.text_terminal_node_plugin_too_old))
            is Resolution.Unavailable.ExecutableMissing -> bannerToPluginCenter(
                getString(R.string.text_terminal_node_executable_missing, resolution.abi),
            )
            is Resolution.Unavailable.ExecDenied -> {
                banner.nodeBannerText.setText(R.string.text_terminal_node_exec_denied)
                banner.nodeBannerAction.setText(R.string.text_details)
                banner.nodeBannerAction.setOnClickListener { showProbeDetails(resolution) }
            }
            is Resolution.Unavailable.SetupFailed -> {
                banner.nodeBannerText.setText(R.string.text_terminal_npm_setup_failed)
                banner.nodeBannerAction.setText(R.string.text_details)
                banner.nodeBannerAction.setOnClickListener { showProbeDetails(resolution) }
            }
            is Resolution.Available -> Unit
        }
        banner.nodeBannerRoot.isVisible = true
    }

    private fun bannerToPluginCenter(text: String) {
        val banner = binding.nodeBanner
        banner.nodeBannerText.text = text
        banner.nodeBannerAction.setText(R.string.text_plugin_center)
        banner.nodeBannerAction.setOnClickListener {
            PluginCenterActivity.launch(
                context = this,
                sourceScope = PluginIndexSource.OFFICIAL,
                initialQuery = NodeCliLocator.OFFICIAL_PLUGIN_PACKAGE,
            )
        }
    }

    /**
     * npm menu items stay visible so the feature is discoverable; when node is unavailable they
     * explain why (and offer Plugin Center) instead of typing a command that cannot run.
     * zh-CN: npm 菜单项保持可见以便发现功能; node 不可用时解释原因 (并提供插件中心) 而不是键入无法运行的命令.
     */
    private fun ensureNodeAvailable(): Boolean {
        val resolution = nodeResolution
        if (resolution is Resolution.Available) return true
        val message = when (resolution) {
            is Resolution.Unavailable.PluginNotAuthorized -> getString(R.string.text_terminal_node_plugin_not_authorized)
            is Resolution.Unavailable.PluginTooOld -> getString(R.string.text_terminal_node_plugin_too_old)
            is Resolution.Unavailable.ExecutableMissing -> getString(R.string.text_terminal_node_executable_missing, resolution.abi)
            is Resolution.Unavailable.ExecDenied -> getString(R.string.text_terminal_node_exec_denied)
            is Resolution.Unavailable.SetupFailed -> getString(R.string.text_terminal_npm_setup_failed)
            else -> getString(R.string.text_terminal_node_missing)
        }
        val builder = MaterialDialog.Builder(this)
            .title(R.string.text_nodejs)
            .content(message)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .widgetThemeColor()
        if (resolution is Resolution.Unavailable.ExecDenied || resolution is Resolution.Unavailable.SetupFailed) {
            builder.positiveText(R.string.text_details).onPositive { _, _ -> showProbeDetails(resolution) }
        } else {
            builder.positiveText(R.string.text_plugin_center)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { _, _ ->
                    PluginCenterActivity.launch(
                        context = this,
                        sourceScope = PluginIndexSource.OFFICIAL,
                        initialQuery = NodeCliLocator.OFFICIAL_PLUGIN_PACKAGE,
                    )
                }
        }
        builder.show()
        return false
    }

    private fun showProbeDetails(resolution: Resolution) {
        val report = TerminalNodeProbePreference.render(resolution)
        MaterialDialog.Builder(this)
            .title(R.string.text_terminal_node_probe)
            .content(report)
            .neutralText(R.string.text_copy)
            .onNeutral { _, _ -> copyToClipboard(report) }
            .positiveText(R.string.dialog_button_confirm)
            .widgetThemeColor()
            .show()
    }

    // endregion

    // region Menu

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_terminal, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId in NPM_MENU_ITEMS && !ensureNodeAvailable()) {
            return true
        }
        when (item.itemId) {
            R.id.action_npm_init -> npmActions.npmInit()
            R.id.action_npm_install -> npmActions.npmInstall()
            R.id.action_npm_install_package -> npmActions.npmInstallPackage()
            R.id.action_npm_run_script -> npmActions.npmRunScript()
            R.id.action_other_package_managers -> npmActions.otherPackageManagers()
            R.id.action_search_npm -> npmActions.searchNpm()
            R.id.action_copy_transcript -> copyToClipboard(session?.pty?.transcriptText)
            R.id.action_paste -> paste()
            R.id.action_share_transcript -> IntentUtils.shareText(this, session?.pty?.transcriptText.orEmpty())
            R.id.action_copy_home_path -> copyToClipboard(session?.paths?.home?.path)
            R.id.action_text_size -> showTextSizeDialog()
            R.id.action_show_keyboard -> showSoftKeyboard()
            R.id.action_clear -> clearScreen()
            R.id.action_tips -> showTips(force = true)
            R.id.action_close_session -> requestCloseSession()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menu.add(Menu.NONE, CONTEXT_SELECT_TEXT, Menu.NONE, R.string.text_terminal_select_text)
        menu.add(Menu.NONE, CONTEXT_COPY_ALL, Menu.NONE, R.string.text_copy_all)
        menu.add(Menu.NONE, CONTEXT_PASTE, Menu.NONE, R.string.text_paste)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            CONTEXT_SELECT_TEXT -> binding.terminal.toggleSelectingText()
            CONTEXT_COPY_ALL -> copyToClipboard(session?.pty?.transcriptText)
            CONTEXT_PASTE -> paste()
            else -> return super.onContextItemSelected(item)
        }
        return true
    }

    // endregion

    private fun typeCommand(command: String) {
        val pty = session?.pty ?: return
        pty.clearModifiers()
        pty.write(command + TerminalKeySequences.ENTER)
    }

    private fun paste() {
        val text = ClipboardUtils.getClip(this)?.toString().orEmpty()
        if (text.isEmpty()) return
        session?.pty?.write(text)
    }

    private fun copyToClipboard(text: String?) {
        val value = text?.takeIf { it.isNotEmpty() } ?: return
        ClipboardUtils.setClip(this, value)
        ViewUtils.showToast(this, R.string.text_already_copied_to_clip)
    }

    private fun clearScreen() {
        val pty = session?.pty ?: return
        pty.reset()
        if (pty.isProcessAlive) {
            TerminalKeySequences.control('l')?.let { pty.write(it.toString()) }
        }
    }

    private fun showSoftKeyboard() {
        val view = binding.terminal
        view.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showTextSizeDialog() {
        val sizes = TEXT_SIZE_CHOICES
        val currentSize = binding.terminal.textSizeSp
        val current = sizes.indexOf(currentSize).takeIf { it >= 0 }
            ?: sizes.indexOfFirst { it >= currentSize }.takeIf { it >= 0 }
            ?: sizes.lastIndex
        MaterialDialog.Builder(this)
            .title(R.string.text_text_size)
            .items(sizes.map { "$it sp" })
            .itemsCallbackSingleChoice(current) { dialog, _, which, _ ->
                binding.terminal.textSizeSp = sizes[which]
                dialog.dismiss()
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .widgetThemeColor()
            .show()
    }

    private fun showTipsIfNeeded() = showTips(force = false)

    private fun showTips(force: Boolean) {
        // The first-run tip appears before the session exists, so the home comes from the layout itself.
        // zh-CN: 首次提示在会话创建之前弹出, 因此主目录直接取自目录布局.
        val home = TerminalPaths.of(this).home.path
        val content = getString(R.string.text_terminal_tips_content, home)
        if (force) {
            MaterialDialog.Builder(this)
                .title(R.string.text_terminal_tips)
                .content(content)
                .positiveText(R.string.dialog_button_confirm)
                .widgetThemeColor()
                .show()
            return
        }
        NotAskAgainDialog.Builder(this, getString(R.string.key_dialog_terminal_tips))
            .title(R.string.text_terminal_tips)
            .content(content)
            .positiveText(R.string.dialog_button_confirm)
            .widgetThemeColor()
            .show()
    }

    companion object {

        private const val EXTRA_DIRECTORY = "directory"
        private const val STATE_SESSION_ID = "session_id"
        private const val CONTEXT_SELECT_TEXT = 1
        private const val CONTEXT_COPY_ALL = 2
        private const val CONTEXT_PASTE = 3
        private const val SUBTITLE_REFRESH_DELAY_MILLIS = 800L

        private val TEXT_SIZE_CHOICES = listOf(8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 28, 32)

        private val NPM_MENU_ITEMS = listOf(
            R.id.action_npm_init,
            R.id.action_npm_install,
            R.id.action_npm_install_package,
            R.id.action_npm_run_script,
            R.id.action_other_package_managers,
        )

        @JvmStatic
        @JvmOverloads
        fun intent(context: Context, directory: String? = null): Intent = Intent(context, TerminalActivity::class.java).apply {
            directory?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_DIRECTORY, it) }
        }

        @JvmStatic
        @JvmOverloads
        fun launch(context: Context, directory: String? = null) {
            intent(context, directory).startSafely(context)
        }

    }

}
