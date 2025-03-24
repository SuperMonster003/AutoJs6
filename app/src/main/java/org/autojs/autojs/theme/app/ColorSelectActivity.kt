package org.autojs.autojs.theme.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorSelectBinding

/**
 * Created by SuperMonster003 on Mar 20, 2025.
 */
class ColorSelectActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorSelectBinding

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_color_select, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_toggle_color_select_layout -> {
                isLegacyLayout = true
                startActivity(this)
            }
            R.id.action_locate_current_color -> {

            }
            R.id.action_search_color -> {

            }
        }
        return true
    }

}