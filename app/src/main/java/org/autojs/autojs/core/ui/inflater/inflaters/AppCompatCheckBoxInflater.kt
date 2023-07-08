package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatCheckBox
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class AppCompatCheckBoxInflater<V : AppCompatCheckBox>(resourceParser: ResourceParser) : CheckBoxInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<AppCompatCheckBox> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): AppCompatCheckBox {
            return AppCompatCheckBox(context)
        }
    }

}
