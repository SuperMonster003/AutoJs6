package org.autojs.autojs.ui.fragment

import androidx.fragment.app.Fragment
import android.view.View
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object BindingDelegates {

    fun <T> Fragment.viewBinding(bindView: (View) -> T) = object : ReadOnlyProperty<Fragment, T> {

        private var binding: T? = null

        override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
            return binding ?: bindView(thisRef.requireView()).also { binding = it }
        }

        override fun toString() = "Binding for ${this@viewBinding.javaClass.name}: $binding"

    }

}