package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class RecyclerViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewGroupAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as RecyclerView

}
