package org.autojs.autojs.theme.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorSelectLegacyBinding

/**
 * Created by SuperMonster003 on Mar 20, 2025.
 */
class ColorSelectLegacyActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorSelectLegacyBinding

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