package org.autojs.autojs.ui.wizard

import android.os.Bundle
import android.view.View
import androidx.annotation.ColorInt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardDialog
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.ui.widget.CustomSnackbar
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.wizard.permission.PermissionWizardDialog
import org.autojs.autojs.ui.wizard.permission.PermissionWizardEntry
import org.autojs.autojs.ui.wizard.permission.PermissionWizardRunner
import org.autojs.autojs.ui.wizard.permission.PermissionWizardStates
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.SnackbarUtils.coverNavigationBar
import org.autojs.autojs6.R

/**
 * Entry of the setup wizards. After the first launch the home screen shows a welcome snackbar
 * instead of requesting anything, so the app is usable right away; its "Start wizard" action opens
 * a chooser of the permission wizard and the plugin install wizard, and the chosen wizards run one
 * after another. Later launches only remind of missing permissions of the "Minimal" profile.
 *
 * zh-CN: 设置向导的入口. 首次启动后主页显示欢迎 Snackbar 而不主动申请任何内容, 使应用可立即使用;
 * 其 "开始向导" 操作打开权限申请向导与插件安装向导的选择对话框, 所选向导依次进行.
 * 之后的启动仅在缺少 "最小" 配置中的权限时给出提醒.
 */
object SetupWizards {

    enum class Kind { PERMISSIONS, PLUGINS }

    /** Background of the AppCompat snackbar, against which action colors keep their contrast. */
    @ColorInt
    private const val SNACKBAR_BACKGROUND = 0xFF323232.toInt()

    private const val SNACKBAR_ACTION_MIN_CONTRAST = 3.0

    /** Startup hook of the home screen; does nothing when the activity is recreated. */
    fun onHomeCreated(activity: FragmentActivity, savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val anchor = WizardUi.findSnackbarAnchor(activity) ?: return
        anchor.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            if (!MiscStateKeys.SETUP_WIZARD_WELCOME_DONE.get()) {
                showWelcome(activity, anchor)
            } else {
                remindMissingPermissions(activity, anchor)
            }
        }
    }

    /**
     * Stays until the user picks an action or swipes it away; when another snackbar replaces it or
     * the app closes first, it returns on the next launch.
     */
    private fun showWelcome(activity: FragmentActivity, anchor: View) {
        CustomSnackbar.make(anchor, activity.getString(R.string.text_welcome_to_app, activity.getString(R.string.app_name)), BaseTransientBottomBar.LENGTH_INDEFINITE)
            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_SLIDE)
            .setActionTwo(R.string.dialog_button_close) { markWelcomeDone() }
            .setActionOne(R.string.text_start_wizard) {
                markWelcomeDone()
                showChooser(activity)
            }
            .setActionTextColor(snackbarActionColor())
            .coverNavigationBar()
            .addCallback(object : BaseTransientBottomBar.BaseCallback<CustomSnackbar>() {
                override fun onDismissed(transientBottomBar: CustomSnackbar?, event: Int) {
                    if (event == BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_SWIPE) markWelcomeDone()
                }
            })
            .show()
    }

    private fun markWelcomeDone() = MiscStateKeys.SETUP_WIZARD_WELCOME_DONE.set(true)

    /** A non-blocking reminder replaces the permission dialogs that used to appear on every launch. */
    private fun remindMissingPermissions(activity: FragmentActivity, anchor: View) {
        val minimal = PermissionWizardEntry.entries.filter { it.inMinimal }
        val missing = PermissionWizardStates.missing(activity, minimal)
        if (missing.isEmpty()) return
        val names = missing.joinToString(", ") { activity.getString(it.titleRes) }
        Snackbar.make(anchor, activity.getString(R.string.text_permission_wizard_reminder, names), Snackbar.LENGTH_LONG)
            .setAction(R.string.text_permission_wizard_grant) { requestPermissions(activity, missing) }
            .setActionTextColor(snackbarActionColor())
            .coverNavigationBar()
            .show()
    }

    private fun requestPermissions(activity: FragmentActivity, entries: List<PermissionWizardEntry>) {
        val hadAllFilesAccess = PermissionWizardStates.isGranted(activity, PermissionWizardEntry.ALL_FILES_ACCESS)
        activity.lifecycleScope.launch(Dispatchers.Main) {
            PermissionWizardRunner.run(activity, entries)
            if (!hadAllFilesAccess && PermissionWizardStates.isGranted(activity, PermissionWizardEntry.ALL_FILES_ACCESS)) {
                onAllFilesAccessGranted()
            }
        }
    }

    @ColorInt
    private fun snackbarActionColor(): Int =
        ColorUtils.adjustColorForContrast(SNACKBAR_BACKGROUND, ThemeColorManager.colorPrimary, SNACKBAR_ACTION_MIN_CONTRAST)

    /** Lets the user pick the wizards to run; both are ticked, and "Next" needs at least one. */
    fun showChooser(activity: FragmentActivity) {
        val kinds = listOf(Kind.PERMISSIONS, Kind.PLUGINS)
        MaterialDialog.Builder(activity)
            .title(R.string.text_setup_wizards)
            .content(R.string.text_setup_wizards_hint)
            .items(activity.getString(R.string.text_permission_wizard), activity.getString(R.string.text_plugin_install_wizard))
            .itemsCallbackMultiChoice(kinds.indices.toList().toTypedArray()) { dialog, which, _ ->
                dialog.getActionButton(DialogAction.POSITIVE).isEnabled = which.isNotEmpty()
                true
            }
            .alwaysCallMultiChoiceCallback()
            .choiceWidgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_next_step)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { dialog, _ ->
                val chosen = dialog.selectedIndices.orEmpty().sorted().mapNotNull { kinds.getOrNull(it) }
                if (chosen.isNotEmpty()) run(activity, chosen)
            }
            .show()
    }

    /** Opens the permission wizard alone. */
    fun showPermissionWizard(activity: FragmentActivity) = run(activity, listOf(Kind.PERMISSIONS))

    /** Runs [kinds] one after another; each wizard starts once the previous one has completely ended. */
    fun run(activity: FragmentActivity, kinds: List<Kind>) = runFrom(activity, kinds, 0)

    private fun runFrom(activity: FragmentActivity, kinds: List<Kind>, index: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        val kind = kinds.getOrNull(index) ?: return
        val isFollowed = index < kinds.lastIndex
        val next = { activity.window.decorView.post { runFrom(activity, kinds, index + 1) } }
        when (kind) {
            Kind.PERMISSIONS -> PermissionWizardDialog.show(activity, isFollowed) { allFilesAccessGranted ->
                if (allFilesAccessGranted) onAllFilesAccessGranted()
                next()
            }
            Kind.PLUGINS -> PluginInstallWizardDialog.show(activity, activity.lifecycleScope, isFollowed) { next() }
        }
    }

    /** The workspace was listed without storage access; list it again now. */
    private fun onAllFilesAccessGranted() {
        runCatching { Explorers.workspace().refreshAll() }
    }

}
