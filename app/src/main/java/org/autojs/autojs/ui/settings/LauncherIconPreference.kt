package org.autojs.autojs.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.external.shortcut.ShortcutActivity
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ShortcutUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class LauncherIconPreference : MaterialListPreference, SharedPreferences.OnSharedPreferenceChangeListener {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes, createDefaultBundle(context))

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr, 0, createDefaultBundle(context))

    constructor(context: Context, attrs: AttributeSet?) :
            super(context, attrs, 0, 0, createDefaultBundle(context))

    constructor(context: Context) :
            super(context, null, 0, 0, createDefaultBundle(context))

    init {
        Pref.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        syncValueWithCurrentComponent()
    }

    private fun syncValueWithCurrentComponent() {
        val isComponentTransparent = isComponentEnabled(prefContext, ALIAS_TRANSPARENT_BACKGROUND)
        val isBeforeAndroidO = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
        val shouldBeTransparent = isComponentTransparent || isBeforeAndroidO
        when (shouldBeTransparent) {
            true -> R.string.key_launcher_icon_transparent_background
            else -> R.string.key_launcher_icon_adaptive
        }.also { resetIfNeeded(it) }
    }

    private fun resetIfNeeded(aimValueRes: Int) {
        val aimValue = prefContext.getString(aimValueRes)
        if (getPersistedString("") != aimValue) {
            persistString(aimValue)
            notifyChanged()
        }
    }

    override fun onChangeConfirmed(dialog: MaterialDialog) {
        super.onChangeConfirmed(dialog)

        when (dialog.items?.get(dialog.selectedIndex)?.toString()) {
            prefContext.getString(R.string.entry_launcher_icon_adaptive) -> {
                showPromptAboutShortcutsLost {
                    switchComponent(prefContext, ALIAS_ADAPTIVE, ALIAS_TRANSPARENT_BACKGROUND)
                }
            }
            prefContext.getString(R.string.entry_launcher_icon_transparent_background) -> {
                showPromptAboutTransparentBackground {
                    showPromptAboutShortcutsLost {
                        switchComponent(prefContext, ALIAS_TRANSPARENT_BACKGROUND, ALIAS_ADAPTIVE)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun showPromptAboutTransparentBackground(callback: () -> Unit) {
        NotAskAgainDialog.Builder(context, key(R.string.key_dialog_transparent_background_launcher_icon)).run {
            title(R.string.text_prompt)
            content(R.string.text_transparent_background_launcher_icon_may_not_take_effect)
            widgetThemeColor()
            negativeText(R.string.dialog_button_abandon)
            negativeColorRes(R.color.dialog_button_default)
            onNegative { dialog, _ -> dialog.dismiss().also { resetIfNeeded(R.string.key_launcher_icon_adaptive) } }
            positiveText(R.string.dialog_button_continue)
            positiveColorRes(R.color.dialog_button_warn)
            onPositive { dialog, _ -> dialog.dismiss().also { callback() } }
            cancelable(false)
            autoDismiss(false)
            show()
        } ?: callback()
    }

    private fun showPromptAboutShortcutsLost(callback: () -> Unit) {
        val shortcuts = ShortcutUtils.getAllShortcutScriptPaths(context)
        if (shortcuts.isEmpty()) {
            callback()
            return
        }
        NotAskAgainDialog.Builder(context, key(R.string.key_dialog_transparent_background_launcher_icon)).run {
            title(R.string.text_prompt)
            content(R.string.text_launcher_shortcuts_may_be_lost_after_launcher_icon_changed)
            widgetThemeColor()
            neutralText(R.string.dialog_button_view_shortcuts)
            neutralColorRes(R.color.dialog_button_hint)
            onNeutral { dialog, _ ->
                MaterialDialog.Builder(context).run {
                    title(R.string.text_launcher_shortcuts)
                    items(shortcuts)
                    itemsCallback { _, _, _, text ->
                        true.also {
                            ClipboardUtils.setClip(context, text)
                            ViewUtils.showSnack(dialog.view, R.string.text_already_copied_to_clip, false)
                        }
                    }
                    neutralText(R.string.text_copy_all)
                    neutralColorRes(R.color.dialog_button_hint)
                    onNeutral { dialog, _ ->
                        ClipboardUtils.setClip(context, shortcuts.joinToString("\n"))
                        ViewUtils.showSnack(dialog.view, R.string.text_already_copied_to_clip, false)
                    }
                    positiveText(R.string.dialog_button_dismiss)
                    positiveColorRes(R.color.dialog_button_default)
                    onPositive { dialog, _ -> dialog.dismiss() }
                    autoDismiss(false)
                    show()
                }
            }
            negativeText(R.string.dialog_button_abandon)
            negativeColorRes(R.color.dialog_button_default)
            onNegative { dialog, _ -> dialog.dismiss().also { syncValueWithCurrentComponent() } }
            positiveText(R.string.dialog_button_continue)
            positiveColorRes(R.color.dialog_button_caution)
            onPositive { dialog, _ -> dialog.dismiss().also { callback() } }
            cancelable(false)
            autoDismiss(false)
            show()
        } ?: callback()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) = notifyChanged()

    private fun switchComponent(ctx: Context, wantEnable: String, wantDisable: String) {
        val pm = ctx.packageManager

        pm.setComponentEnabledSetting(
            ComponentName(ctx, wantEnable),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ctx.getSystemService(ShortcutManager::class.java)?.let { sm ->
                sm.pinnedShortcuts
                    .filterNot { it.isDeclaredInManifest }
                    .filterNot { Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && it.isImmutable }
                    .mapNotNull { shortcutInfo ->
                        ShortcutInfo.Builder(ctx, shortcutInfo.id).apply {
                            shortcutInfo.intent?.let {
                                it.component?.let { component ->
                                    setActivity(ComponentName(component.packageName, component.className))
                                } ?: run {
                                    setActivity(ComponentName(ctx, ShortcutActivity::class.java))
                                }
                                setIntent(it)
                            } ?: run {
                                setActivity(ComponentName(ctx, ShortcutActivity::class.java))
                                setIntent(Intent(ctx, ShortcutActivity::class.java))
                            }
                            (shortcutInfo.shortLabel ?: shortcutInfo.longLabel)?.let {
                                setShortLabel(it)
                            }
                            (shortcutInfo.longLabel ?: shortcutInfo.shortLabel)?.let {
                                setLongLabel(it)
                            }
                        }.build()
                    }.takeUnless { it.isEmpty() }?.let { toUpdate ->
                        sm.updateShortcuts(toUpdate)
                    }
            }
        }

        pm.setComponentEnabledSetting(
            ComponentName(ctx, wantDisable),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    @Suppress("SameParameterValue")
    private fun isComponentEnabled(ctx: Context, className: String): Boolean {
        val state = ctx.packageManager.getComponentEnabledSetting(
            ComponentName(ctx, className)
        )
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }

    companion object {

        private const val ALIAS_ADAPTIVE = "org.autojs.autojs.launcher.AdaptiveIconAlias"
        private const val ALIAS_TRANSPARENT_BACKGROUND = "org.autojs.autojs.launcher.TransparentBackgroundIconAlias"

        private fun createDefaultBundle(context: Context) = Bundle().apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                putString(key(R.string.key_pref_bundle_default_item), context.getString(R.string.key_launcher_icon_transparent_background))
                putIntegerArrayList(key(R.string.key_pref_bundle_disabled_items), arrayListOf(R.string.key_launcher_icon_adaptive))
            }
        }

    }

}