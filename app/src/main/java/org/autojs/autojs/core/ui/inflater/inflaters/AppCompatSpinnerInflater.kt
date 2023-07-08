package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatSpinner
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class AppCompatSpinnerInflater<V : AppCompatSpinner>(resourceParser: ResourceParser) : SpinnerInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<AppCompatSpinner> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): AppCompatSpinner {
            return AppCompatSpinner(context)
        }
    }

}
