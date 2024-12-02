package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class DrawerLayoutAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewGroupAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as DrawerLayout

}
