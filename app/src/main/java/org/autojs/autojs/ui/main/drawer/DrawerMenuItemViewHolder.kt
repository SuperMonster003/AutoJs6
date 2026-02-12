package org.autojs.autojs.ui.main.drawer

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import me.zhanghai.android.materialprogressbar.MaterialProgressBar
import org.autojs.autojs.ui.widget.BindableViewHolder
import org.autojs.autojs.ui.widget.PrefSwitch
import org.autojs.autojs.util.ViewUtils.installFullRowRippleForwarder
import org.autojs.autojs6.databinding.DrawerMenuItemBinding

/**
 * Created by Stardust on Dec 10, 2017.
 * Modified by SuperMonster003 as of Jan 14, 2026.
 * Transformed by SuperMonster003 on May 13, 2023.
 */
class DrawerMenuItemViewHolder(itemView: View) : BindableViewHolder<DrawerMenuItem?>(itemView) {

    private val mSwitchCompat: PrefSwitch
    private val mSwitchDivider: View
    private val mProgressBar: MaterialProgressBar
    private val mIcon: ImageView
    private val mTitle: TextView
    private val mSubtitle: TextView

    private var mAntiShake = false
    private var mLastClickMillis: Long = 0

    private lateinit var mDrawerMenuItem: DrawerMenuItem

    init {
        val binding = DrawerMenuItemBinding.bind(itemView)
        mSwitchCompat = binding.sw
        mSwitchDivider = binding.switchDivider
        mProgressBar = binding.progressBar
        mIcon = binding.icon
        mTitle = binding.title
        mSubtitle = binding.subtitle
        mSwitchCompat.setOnCheckedChangeListener { _, _ ->
            onClick()
        }

        val iconContainer = binding.iconContainer
        val titleContainer = binding.titleContainer

        // Install full-row ripple forwarding once.
        // zh-CN: 安装整行 Ripple 转发, 通常只需安装一次.
        installFullRowRippleForwarder(itemView, iconContainer, titleContainer)

        val toggleAction = {
            if (mSwitchCompat.isVisible) {
                mSwitchCompat.toggle()
            } else {
                onClick()
            }
        }

        iconContainer.setOnClickListener {
            toggleAction()
        }

        iconContainer.setOnLongClickListener {
            when (val menuItem = mDrawerMenuItem) {
                is DrawerMenuToggleableItem -> {
                    menuItem.launchManagerIfPossible()
                }
                else -> false
            }
        }

        titleContainer.setOnClickListener {
            when (val menuItem = mDrawerMenuItem) {
                is DrawerMenuToggleableItem -> {
                    menuItem.onTitleContainerClick()
                }
                else -> toggleAction()
            }
        }

        titleContainer.setOnLongClickListener {
            when (val menuItem = mDrawerMenuItem) {
                is DrawerMenuToggleableItem -> {
                    menuItem.launchManagerIfPossible()
                }
                else -> false
            }
        }
    }

    override fun bind(data: DrawerMenuItem?, position: Int) {
        data ?: return
        mDrawerMenuItem = data
        mIcon.setImageResource(data.icon)
        mTitle.setText(data.title)

        val subtitle = data.subtitle
        mSubtitle.text = subtitle
        mSubtitle.visibility = if (subtitle != null) View.VISIBLE else View.GONE

        mAntiShake = data.antiShake()
        setSwitch(data)
        setProgress(data.isProgress)
    }

    fun isChecked() = mSwitchCompat.isChecked

    private fun setSwitch(item: DrawerMenuItem) {
        if (!item.isSwitchEnabled) {
            mSwitchCompat.visibility = View.GONE
            mSwitchDivider.visibility = View.GONE
            return
        }
        mSwitchCompat.visibility = View.VISIBLE
        mSwitchDivider.visibility = View.VISIBLE
        val prefKey = item.prefKey
        if (prefKey == DrawerMenuItem.DEFAULT_PREFERENCE_KEY) {
            mSwitchCompat.setChecked(item.isChecked, false)
            mSwitchCompat.setPrefKey(null)
        } else {
            mSwitchCompat.setPrefKey(itemView.resources.getString(prefKey))
        }
    }

    private fun onClick() {
        runCatching {
            mDrawerMenuItem.isChecked = mSwitchCompat.isChecked
            if (mAntiShake && System.currentTimeMillis() - mLastClickMillis < CLICK_TIMEOUT) {
                mSwitchCompat.setChecked(!mSwitchCompat.isChecked, false)
                return
            }
            mLastClickMillis = System.currentTimeMillis()
            mDrawerMenuItem.performAction(this)
        }
    }

    private fun setProgress(onProgress: Boolean) {
        mProgressBar.visibility = if (onProgress) View.VISIBLE else View.GONE
        mIcon.visibility = if (onProgress) View.GONE else View.VISIBLE
        mSwitchCompat.isEnabled = !onProgress
        itemView.isEnabled = !onProgress
    }

    companion object {

        const val CLICK_TIMEOUT: Long = 540

    }

}
