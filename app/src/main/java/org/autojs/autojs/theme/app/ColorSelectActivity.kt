package org.autojs.autojs.theme.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorSelectBinding

/**
 * Created by SuperMonster003 on Mar 20, 2025.
 */
class ColorSelectActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorSelectBinding

    private lateinit var mColorSettingRecyclerView: ColorSettingRecyclerView

    private var mSelectedPosition = SELECT_NONE

    private val mOnItemClickListener = object : OnItemClickListener {
        override fun onItemClick(v: View?, position: Int) {
            mColorSettingRecyclerView.selectedThemeColor?.let {
                mSelectedPosition = position
                setColorWithAnimation(it.colorPrimary)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MtActivityColorSelectBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpToolbar(it.toolbar)
            setUpColorSettingRecyclerView(it.colorSettingRecyclerView)
            setUpAppBar(it.appBar, it.appBarContainer)
        }
    }

    private fun setUpColorSettingRecyclerView(colorSettingRecyclerView: ColorSettingRecyclerView) {
        mColorSettingRecyclerView = colorSettingRecyclerView.apply {
            setUpSelectedPosition(currentColor)
            setOnItemClickListener(mOnItemClickListener)
            ViewUtils.excludePaddingClippableViewFromNavigationBar(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_color_select, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_toggle_color_select_layout -> {
                isLegacyLayout = false
                startActivity(this)
            }
        }
        return true
    }

}