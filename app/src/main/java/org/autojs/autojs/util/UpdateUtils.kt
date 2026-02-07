package org.autojs.autojs.util

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.Pref.isAutoCheckForUpdatesEnabled
import org.autojs.autojs.core.pref.Pref.lastNoNewerUpdatesTimestamp
import org.autojs.autojs.core.pref.Pref.lastUpdatesAutoCheckedTimestamp
import org.autojs.autojs.core.pref.Pref.lastUpdatesPostponedTimestamp
import org.autojs.autojs.network.UpdateChecker
import org.autojs.autojs.network.UpdateChecker.PromptMode
import org.autojs.autojs.network.entity.VersionInfo
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on May 29, 2022.
 */
object UpdateUtils {

    private val ignoredVersions: LinkedHashSet<String> by lazy {
        Pref.getLinkedHashSet(R.string.key_ignored_updates)
    }

    fun manageIgnoredUpdates(context: Context) {
        MaterialDialog.Builder(context)
            .title(R.string.text_ignored_updates)
            .options(
                listOf(
                    MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_details)) {
                        MaterialDialog.Builder(context)
                            .title(R.string.text_details)
                            .content(R.string.description_manage_ignored_updates_preference)
                            .positiveText(R.string.dialog_button_dismiss)
                            .positiveColorRes(R.color.dialog_button_default)
                            .showAdaptive()
                    },
                ),
            )
            .content(R.string.text_no_ignored_updates)
            .items(ignoredVersions)
            .itemsLongCallback { dialog, _, _, text ->
                false.also { showRemoveIgnoredVersionPrompt(context, dialog, null, text) }
            }
            .itemsCallback { dialog, _, _, text ->

                // show info and delete dialog neutral button

                val simpleInfo = VersionInfo.parseSummary(text)
                MaterialDialog.Builder(context)
                    .title(R.string.text_version_info)
                    .content(
                        "${context.getString(R.string.text_version_name)}: ${simpleInfo.versionName}\n" +
                                "${context.getString(R.string.text_version_code)}: ${simpleInfo.versionCode}"
                    )
                    .neutralText(R.string.dialog_button_remove)
                    .neutralColorRes(R.color.dialog_button_warn)
                    .onNeutral { dVersionInfo, _ ->
                        showRemoveIgnoredVersionPrompt(context, dialog, dVersionInfo, text)
                    }
                    .positiveText(R.string.dialog_button_back)
                    .positiveColorRes(R.color.dialog_button_default)
                    .onPositive { dVersionInfo, _ -> dVersionInfo.dismiss() }
                    .autoDismiss(false)
                    .show()
            }
            .choiceWidgetThemeColor()
            .neutralText(R.string.dialog_button_clear_items)
            .neutralColorRes(R.color.dialog_button_warn)
            .onNeutral { dialogParent, _ ->
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.text_confirm_to_clear_all_items)
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_caution)
                    .onPositive { _, _ ->
                        clearIgnoredVersions()
                        dialogParent.items?.let {
                            it.clear()
                            dialogParent.notifyItemsChanged()
                            DialogUtils.toggleContentViewByItems(dialogParent)
                            DialogUtils.toggleActionButtonAbilityByItems(dialogParent, DialogAction.NEUTRAL)
                        }
                        ViewUtils.showSnack(dialogParent.view, R.string.text_all_items_cleared)
                    }
                    .showAdaptive()
            }
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .onPositive { dialog, _ -> dialog.dismiss() }
            .autoDismiss(false)
            .build()
            .also {
                DialogUtils.toggleContentViewByItems(it)
                DialogUtils.toggleActionButtonAbilityByItems(it, DialogAction.NEUTRAL)
            }
            .showAdaptive()
    }

    private fun showRemoveIgnoredVersionPrompt(context: Context, dialogHoldingItems: MaterialDialog, dialogPromptParent: MaterialDialog?, text: CharSequence) {
        MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(R.string.text_confirm_to_remove)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                dialogHoldingItems.items!!.remove(text)
                dialogHoldingItems.notifyItemsChanged()
                removeIgnoredVersion(text)
                DialogUtils.toggleContentViewByItems(dialogHoldingItems)
                dialogPromptParent?.dismiss()
            }
            .showAdaptive()
    }

    @JvmStatic
    fun isVersionIgnored(versionInfo: VersionInfo) = ignoredVersions.contains(versionInfo.toSummary())

    @JvmStatic
    fun addIgnoredVersion(versionInfo: VersionInfo) {
        ignoredVersions.add(versionInfo.toSummary())
        Pref.putLinkedHashSet(R.string.key_ignored_updates, ignoredVersions)
    }

    private fun removeIgnoredVersion(versionInfo: VersionInfo) = removeIgnoredVersion(versionInfo.toSummary())

    private fun removeIgnoredVersion(versionSummary: CharSequence) {
        ignoredVersions.remove(versionSummary.toString())
        Pref.putLinkedHashSet(R.string.key_ignored_updates, ignoredVersions)
    }

    private fun clearIgnoredVersions() {
        ignoredVersions.clear()
        Pref.putLinkedHashSet(R.string.key_ignored_updates, ignoredVersions)
    }

    @JvmStatic
    @JvmOverloads
    fun autoCheckForUpdatesIfNeededWithSnackbar(activity: AppCompatActivity, snackbarViewIdRes: Int = android.R.id.content) {
        if (!isAutoCheckForUpdatesEnabled) return

        val checker = IntervalChecker()
        if (!checker.isBeyondNoNewer || !checker.isBeyondPostponed || !checker.isBeyondAutoChecked) return

        UpdateChecker.Builder(activity.findViewById(snackbarViewIdRes))
            .setPromptMode(PromptMode.SNACKBAR)
            .setGitHubMainBranch("master")
            .build()
            .checkNow()

        Pref.refreshLastUpdatesAutoCheckedTimestamp()
    }

    private class IntervalChecker {

        private val mMinCheckedIntervalNoNewer: Long = 120 * 60 * 1000
        private val mMinCheckedIntervalPostponed: Long = 30 * 60 * 1000
        private val mMinCheckedIntervalAutoChecked: Long = 15 * 1000

        val isBeyondNoNewer: Boolean
            get() {
                val now = System.currentTimeMillis()
                val lastNoNewer = lastNoNewerUpdatesTimestamp
                return now - lastNoNewer > mMinCheckedIntervalNoNewer
            }

        val isBeyondPostponed: Boolean
            get() {
                val now = System.currentTimeMillis()
                val lastPostponed = lastUpdatesPostponedTimestamp
                return now - lastPostponed > mMinCheckedIntervalPostponed
            }

        val isBeyondAutoChecked: Boolean
            get() {
                val now = System.currentTimeMillis()
                val lastAutoChecked = lastUpdatesAutoCheckedTimestamp
                return now - lastAutoChecked > mMinCheckedIntervalAutoChecked
            }

    }

}