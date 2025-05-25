package org.autojs.autojs.core.ui.widget

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import org.autojs.autojs.util.ContextUtils.requireActivity
import org.autojs.autojs6.R

class JsToolbar : Toolbar {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val activity: Activity
        get() {
            return context.requireActivity()
        }

    fun setupWithDrawer(drawerLayout: DrawerLayout) {
        val drawerToggle = ActionBarDrawerToggle(activity, drawerLayout, this, R.string.text_drawer_open, R.string.text_drawer_close)
        drawerToggle.syncState()
        drawerLayout.addDrawerListener(drawerToggle)
    }

}