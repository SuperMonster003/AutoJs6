package org.autojs.autojs.ui.main.drawer

import android.annotation.SuppressLint
import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs6.R

open class DrawerMenuToggleableItem : DrawerMenuItem, IToggleableItem {

    private lateinit var mItemHelper: DrawerMenuItemHelper
    private lateinit var mContext: Context

    constructor(helper: DrawerMenuItemHelper, icon: Int, title: Int) : super(icon, title, DEFAULT_PREFERENCE_KEY) {
        init(helper, helper.context, null)
    }

    constructor(helper: DrawerMenuItemHelper, icon: Int, title: Int, content: Int) : super(icon, title, DEFAULT_PREFERENCE_KEY) {
        init(helper, helper.context, helper.context.takeUnless { content == DEFAULT_DIALOG_CONTENT }?.getString(content))
    }

    constructor(helper: DrawerMenuItemHelper, icon: Int, title: Int, content: Int, prefKey: Int = DEFAULT_PREFERENCE_KEY) : super(icon, title, prefKey) {
        init(helper, helper.context, helper.context.takeUnless { content == DEFAULT_DIALOG_CONTENT }?.getString(content))
    }

    private fun init(itemHelper: DrawerMenuItemHelper, context: Context, content: String?) {
        mItemHelper = itemHelper
        mContext = context
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
            mItemHelper.refreshSubtitle()
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
            val key = "${DrawerMenuToggleableItem::class.simpleName}\$${mContext.getString(title)}"
            NotAskAgainDialog.Builder(mContext, key)
                .title(title)
                .content(it)
                .negativeText(R.string.dialog_button_cancel)
                .positiveText(R.string.dialog_button_continue)
                .onPositive { _, _ -> toggle(aimState) }
                .dismissListener { sync() }
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

    override fun toggle() = toggle { mItemHelper.toggle() }

    override fun toggle(aimState: Boolean) = toggle { mItemHelper.toggle(aimState) }

    @SuppressLint("CheckResult")
    private fun toggle(runnable: Runnable) {
        if (!isHidden) {
            when (mItemHelper.isInMainThread) {
                true -> runnable.run()
                else -> {
                    isProgress = true
                    Observable
                        .fromCallable { runnable.run() }
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { isProgress = false; syncDelay() }
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