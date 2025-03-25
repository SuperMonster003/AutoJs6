package org.autojs.autojs.theme.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorSelectLegacyBinding

/**
 * Created by SuperMonster003 on Mar 20, 2025.
 */
class ColorSelectLegacyActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorSelectLegacyBinding

    private lateinit var mColorSettingRecyclerView: ColorSettingRecyclerViewLegacy

    private val mOnItemClickListener = object : OnItemClickListener {
        override fun onItemClick(v: View?, position: Int) {
            mColorSettingRecyclerView.selectedThemeColor?.let {
                setColorWithAnimation(it.colorPrimary)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MtActivityColorSelectLegacyBinding.inflate(layoutInflater).also {
            binding = it
            setContentView(it.root)
            setUpToolbar(it.toolbar)
            setUpColorSettingRecyclerView(it.colorSettingRecyclerView)
            setUpAppBar(it.appBar, it.appBarContainer)
        }
    }

    private fun setUpColorSettingRecyclerView(colorSettingRecyclerView: ColorSettingRecyclerViewLegacy) {
        mColorSettingRecyclerView = colorSettingRecyclerView.apply {
            setSelectedColor(currentColor)
            setOnItemClickListener(mOnItemClickListener)
            ViewUtils.excludePaddingClippableViewFromNavigationBar(this)
        }
    }

    override fun finish() {
        mColorSettingRecyclerView.selectedThemeColor?.let {
            ThemeColorManager.setThemeColor(it.colorPrimary)
            Pref.putString(key(R.string.key_theme_color), getCurrentColorSummary(this))
            ThemeChangeNotifier.notifyThemeChanged()
        }
        super.finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_color_select_legacy, menu)
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