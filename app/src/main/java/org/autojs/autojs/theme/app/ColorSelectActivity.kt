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
import org.autojs.autojs6.databinding.MtActivityColorSelectBinding

/**
 * Created by SuperMonster003 on Mar 20, 2025.
 */
class ColorSelectActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorSelectBinding

    private lateinit var mColorSettingRecyclerView: ColorSettingRecyclerView

    private val mOnItemClickListener = object : OnItemClickListener {
        override fun onItemClick(v: View?, position: Int) {
            mColorSettingRecyclerView.selectedThemeColor?.let {
                setColorWithAnimation(it.colorPrimary)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MtActivityColorSelectBinding.inflate(layoutInflater).also {
            binding = it
            setContentView(it.root)
            setUpToolbar(it.toolbar)
            setUpColorSettingRecyclerView(it.colorSettingRecyclerView)
            setUpAppBar(it.appBar, it.appBarContainer)
        }
    }

    private fun setUpColorSettingRecyclerView(colorSettingRecyclerView: ColorSettingRecyclerView) {
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
        menuInflater.inflate(R.menu.menu_color_select, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_color_palette -> {
                ViewUtils.showToast(this, R.string.text_under_development_title)
            }
            R.id.action_search_color -> {
                ViewUtils.showToast(this, R.string.text_under_development_title)
            }
            R.id.action_new_color_library -> {
                ViewUtils.showToast(this, R.string.text_under_development_title)
            }
            R.id.action_locate_current_color -> {
                ViewUtils.showToast(this, R.string.text_under_development_title)
            }
            R.id.action_toggle_color_select_layout -> {
                isLegacyLayout = true
                startActivity(this)
            }
        }
        return true
    }

    override fun getSubtitle() = getCurrentColorSummary(this)

}