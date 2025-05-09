package org.autojs.autojs.ui.main.drawer

import android.annotation.SuppressLint
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.extension.MaterialDialogExtensions.widgetThemeColor
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs6.R

open class DrawerMenuToggleableItem : DrawerMenuItem, IToggleableItem {

    private lateinit var mItemHelper: DrawerMenuItemHelper

    constructor(helper: DrawerMenuItemHelper, icon: Int, title: Int) : super(icon, title, DEFAULT_PREFERENCE_KEY) {
        init(helper, null)
    }

    constructor(helper: DrawerMenuItemHelper, icon: Int, title: Int, content: Int) : super(icon, title, DEFAULT_PREFERENCE_KEY) {
        init(helper, helper.context.takeUnless { content == DEFAULT_DIALOG_CONTENT }?.getString(content))
    }

    constructor(helper: DrawerMenuItemHelper, icon: Int, title: Int, content: Int, prefKey: Int = DEFAULT_PREFERENCE_KEY) : super(icon, title, prefKey) {
        init(helper, helper.context.takeUnless { content == DEFAULT_DIALOG_CONTENT }?.getString(content))
    }

    private fun init(itemHelper: DrawerMenuItemHelper, content: String?) {
        mItemHelper = itemHelper
        setContent(content)
        setAction { holder ->
            when (holder.switchCompat.isChecked) {
                true -> getPrompt(true) ?: toggle(true)
                else -> toggle(false)
            }
        }
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
        return content?.let {
            var isPositiveButtonPressed = false
            val key = "${DrawerMenuToggleableItem::class.simpleName}\$${mItemHelper.context.getString(title)}"
            NotAskAgainDialog.Builder(mItemHelper.context, key)
                .title(title)
                .content(it)
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
    }

    override fun setProgress(onProgress: Boolean) {
        super.setProgress(onProgress)
        notifyItemChanged()
    }

    private fun notifyItemChanged() {
        DrawerFragment.drawerMenuAdapter.notifyItemChanged(this)
    }

    @SuppressLint("CheckResult")
    override fun toggle(aimState: Boolean) {
        if (!isHidden) {
            when (mItemHelper.isInMainThread) {
                true -> {
                    val tryToggleResult = mItemHelper.toggle(aimState)
                    syncDelay { if (tryToggleResult) mItemHelper.callback(aimState) }
                }
                else -> {
                    isProgress = true
                    Observable
                        .fromCallable { mItemHelper.toggle(aimState) }
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { tryToggleResult ->
                            isProgress = false
                            syncDelay { if (tryToggleResult) mItemHelper.callback(aimState) }
                        }
                }
            }
        }
    }

    fun selfActive() {
        if (!isHidden && isPrefEnabled()) {
            mItemHelper.active()
        }
    }

    private fun isPrefEnabled() = prefKey != DEFAULT_PREFERENCE_KEY && Pref.getBoolean(prefKey, false)

}