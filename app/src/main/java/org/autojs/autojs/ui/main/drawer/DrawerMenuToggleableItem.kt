package org.autojs.autojs.ui.main.drawer

import android.annotation.SuppressLint
import android.app.Activity
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

open class DrawerMenuToggleableItem : DrawerMenuItem, IToggleableItem {

    private lateinit var mItemHelper: DrawerMenuItemHelper

    private var mDescription: String? = null

    private var mDescriptionDialogBuilderProvider: (() -> MaterialDialog.Builder)? = null
    private var mOnTitleContainerClickListener: (MaterialDialog.Builder.() -> Boolean)? = null

    private var mOnLauncherManagerListener: ((MaterialDialog?) -> Unit)? = null
    private var mOnLaunchSettingsListener: ((MaterialDialog) -> Unit)? = null

    private var mOnNotifyItemChangedListener: ((DrawerMenuItem) -> Unit)? = null

    constructor(
        helper: DrawerMenuItemHelper,
        icon: Int,
        title: Int,
        descriptionRes: Int? = null,
        onTitleContainerClickListener: (MaterialDialog.Builder.() -> Any?)? = null,
    ) : super(icon, title, DEFAULT_PREFERENCE_KEY) {
        init(helper, null, descriptionRes, onTitleContainerClickListener)
    }

    constructor(
        helper: DrawerMenuItemHelper,
        icon: Int,
        title: Int,
        switchOnHintRes: Int? = null,
        descriptionRes: Int? = null,
        onTitleContainerClickListener: (MaterialDialog.Builder.() -> Any?)? = null,
    ) : super(icon, title, DEFAULT_PREFERENCE_KEY) {
        init(helper, switchOnHintRes, descriptionRes, onTitleContainerClickListener)
    }

    constructor(
        helper: DrawerMenuItemHelper,
        icon: Int,
        title: Int,
        switchOnHintRes: Int? = null,
        prefKey: Int = DEFAULT_PREFERENCE_KEY,
        descriptionRes: Int? = null,
        onTitleContainerClickListener: (MaterialDialog.Builder.() -> Any?)? = null,
    ) : super(icon, title, prefKey) {
        init(helper, switchOnHintRes, descriptionRes, onTitleContainerClickListener)
    }

    private fun init(
        itemHelper: DrawerMenuItemHelper,
        switchOnHintRes: Int?,
        descriptionRes: Int?,
        onTitleContainerClickListener: (MaterialDialog.Builder.() -> Any?)? = null,
    ) {
        mItemHelper = itemHelper
        switchOnHintRes?.let { content = itemHelper.context.getString(it) }
        setAction { holder ->
            when (holder.isChecked()) {
                true -> getPrompt(true) ?: toggle(true)
                else -> toggle(false)
            }
        }
        val description = descriptionRes?.let {
            itemHelper.context.getString(it)
        } ?: itemHelper.context.getString(R.string.text_no_content_description)
        mDescription = description

        // Provide a fresh builder each time to avoid stale context.
        // zh-CN: 每次都提供新的 builder, 避免 context 过期.
        mDescriptionDialogBuilderProvider = {
            MaterialDialog.Builder(mItemHelper.context)
                .title(title)
                .content(description)
                .negativeText(R.string.dialog_button_dismiss)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .autoDismiss(false)
                .apply {
                    mOnLauncherManagerListener?.let { listener ->
                        neutralText(R.string.dialog_button_manager)
                        neutralColorRes(R.color.dialog_button_manager)
                        onNeutral { d, _ ->
                            runCatching {
                                listener(d)
                            }.onFailure { e ->
                                MaterialDialog.Builder(itemHelper.context)
                                    .title(R.string.error_failed_to_launch_manager)
                                    .apply { e.message?.let { content(it) } }
                                    .positiveText(R.string.dialog_button_dismiss)
                                    .positiveColorRes(R.color.dialog_button_default)
                                    .show()
                            }
                        }
                    }
                    mOnLaunchSettingsListener?.let { listener ->
                        positiveText(R.string.dialog_button_system_settings_simplified)
                        positiveColorRes(R.color.dialog_button_advanced_settings)
                        onPositive { d, _ ->
                            runCatching {
                                listener(d)
                            }.onFailure { e ->
                                MaterialDialog.Builder(itemHelper.context)
                                    .title(R.string.error_failed_to_launch_system_settings)
                                    .apply { e.message?.let { content(it) } }
                                    .positiveText(R.string.dialog_button_dismiss)
                                    .positiveColorRes(R.color.dialog_button_default)
                                    .show()
                            }
                        }
                    }
                }
        }

        mOnTitleContainerClickListener = listener@{
            val listener = onTitleContainerClickListener ?: return@listener false
            when (val result = listener(this)) {
                is Boolean -> result
                is Unit -> false
                is MaterialDialog.Builder -> false
                else -> throw IllegalArgumentException("onTitleContainerClickListener must return Boolean, MaterialDialog.Builder or Unit")
            }
        }
    }

    fun setOnNotifyItemChangedListener(listener: ((DrawerMenuItem) -> Unit)?) {
        mOnNotifyItemChangedListener = listener
    }

    fun getHelper(): DrawerMenuItemHelper {
        return mItemHelper
    }

    private fun isContextInvalidForDialog(): Boolean {
        val a = (mItemHelper.context as? Activity) ?: return false
        return a.isFinishing || a.isDestroyed
    }

    fun onTitleContainerClick() {
        if (isContextInvalidForDialog()) return
        val builder = mDescriptionDialogBuilderProvider?.invoke() ?: return

        val handled = mOnTitleContainerClickListener?.invoke(builder) == true
        if (handled) return

        runCatching { builder.show() }
    }

    fun launchManagerIfPossible(): Boolean {
        val listener = mOnLauncherManagerListener ?: return false
        return runCatching {
            listener(null)
            true
        }.getOrDefault(false)
    }

    fun setOnLaunchManagerListener(onClickListener: (MaterialDialog?) -> Unit) = also {
        mOnLauncherManagerListener = onClickListener
    }

    fun setOnLaunchSettingsListener(onClickListener: (MaterialDialog) -> Unit) = also {
        mOnLaunchSettingsListener = onClickListener
    }

    override fun sync() {
        if (!isHidden) {
            val aimState = mItemHelper.isActive
            if (prefKey != DEFAULT_PREFERENCE_KEY && isPrefEnabled() != aimState) {
                Pref.putBoolean(prefKey, aimState)
            } else {
                setCheckedIfNeeded(aimState)
            }
            mItemHelper.refreshSubtitle(aimState)
        }
    }

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        notifyItemChanged()
    }

    fun setCheckedIfNeeded(aimState: Boolean) {
        if (isChecked != aimState) {
            isChecked = aimState
        }
    }

    fun getPrompt(aimState: Boolean): MaterialDialog? {
        val dialogContent = content ?: return null
        var isPositiveButtonPressed = false
        val key = "${DrawerMenuToggleableItem::class.simpleName}\$${mItemHelper.context.getString(title)}"
        return NotAskAgainDialog.Builder(mItemHelper.context, key)
            .title(title)
            .content(dialogContent)
            .widgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_continue)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ ->
                isPositiveButtonPressed = true
                toggle(aimState)
            }
            .dismissListener { if (!isPositiveButtonPressed) sync() }
            .show()
    }

    override fun setProgress(onProgress: Boolean) {
        super.setProgress(onProgress)
        notifyItemChanged()
    }

    private fun notifyItemChanged() {
        mOnNotifyItemChangedListener?.invoke(this)
    }

    @SuppressLint("CheckResult")
    override fun toggle(aimState: Boolean) {
        if (isHidden) return
        if (mItemHelper.isInMainThread) {
            val tryToggleResult = mItemHelper.toggle(aimState)
            syncDelay { if (tryToggleResult) mItemHelper.callback(aimState) }
        } else {
            isProgress = true
            Observable
                .fromCallable { mItemHelper.toggle(aimState) }
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ tryToggleResult ->
                    isProgress = false
                    syncDelay { if (tryToggleResult) mItemHelper.callback(aimState) }
                }, { e ->
                    isProgress = false
                    syncDelay { sync() }
                    ViewUtils.showToast(mItemHelper.context, e.message ?: mItemHelper.context.getString(R.string.error_failed_to_change_the_toggle_state), true)
                })
        }
    }

    fun selfActive() {
        if (!isHidden && isPrefEnabled()) {
            mItemHelper.active()
        }
    }

    private fun isPrefEnabled() = prefKey != DEFAULT_PREFERENCE_KEY && Pref.getBoolean(prefKey, false)

}