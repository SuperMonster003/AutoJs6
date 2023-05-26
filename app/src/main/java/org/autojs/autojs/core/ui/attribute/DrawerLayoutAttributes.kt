package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class DrawerLayoutAttributes(resourceParser: ResourceParser, view: View) : ViewGroupAttributes(resourceParser, view) {

    override val view = super.view as DrawerLayout

}
