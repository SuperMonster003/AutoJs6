package org.autojs.autojs.ui.main.drawer

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import me.zhanghai.android.materialprogressbar.MaterialProgressBar
import org.autojs.autojs.ui.widget.BindableViewHolder
import org.autojs.autojs.ui.widget.PrefSwitch
import org.autojs.autojs6.databinding.DrawerMenuItemBinding
import java.io.IOException

/**
 * Created by Stardust on Dec 10, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on May 13, 2023.
 */
class DrawerMenuItemViewHolder(itemView: View) : BindableViewHolder<DrawerMenuItem?>(itemView) {

    private val mSwitchCompat: PrefSwitch
    private val mProgressBar: MaterialProgressBar
    private val mIcon: ImageView
    private val mTitle: TextView
    private val mSubtitle: TextView

    private var mAntiShake = false
    private var mLastClickMillis: Long = 0
    private var mDrawerMenuItem: DrawerMenuItem? = null

    val switchCompat
        get() = mSwitchCompat

    init {
        val binding = DrawerMenuItemBinding.bind(itemView)
        mSwitchCompat = binding.sw
        mProgressBar = binding.progressBar
        mIcon = binding.icon
        mTitle = binding.title
        mSubtitle = binding.subtitle
        mSwitchCompat.setOnCheckedChangeListener { _, _ ->
            try {
                onClick()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        itemView.setOnClickListener {
            if (mSwitchCompat.visibility == View.VISIBLE) {
                mSwitchCompat.toggle()
            } else {
                try {
                    onClick()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
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

    private fun setSwitch(item: DrawerMenuItem) {
        if (!item.isSwitchEnabled) {
            mSwitchCompat.visibility = View.GONE
            return
        }
        mSwitchCompat.visibility = View.VISIBLE
        val prefKey = item.prefKey
        if (prefKey == DrawerMenuItem.DEFAULT_PREFERENCE_KEY) {
            mSwitchCompat.setChecked(item.isChecked, false)
            mSwitchCompat.setPrefKey(null)
        } else {
            mSwitchCompat.setPrefKey(itemView.resources.getString(prefKey))
        }
    }

    @Throws(IOException::class)
    private fun onClick() {
        mDrawerMenuItem!!.isChecked = mSwitchCompat.isChecked
        if (mAntiShake && System.currentTimeMillis() - mLastClickMillis < CLICK_TIMEOUT) {
            mSwitchCompat.setChecked(!mSwitchCompat.isChecked, false)
            return
        }
        mLastClickMillis = System.currentTimeMillis()
        if (mDrawerMenuItem != null) {
            mDrawerMenuItem!!.performAction(this)
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
