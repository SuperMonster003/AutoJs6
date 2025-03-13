package org.autojs.autojs.ui.fragment

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object BindingDelegates {

    // @Created by JetBrains AI Assistant on Mar 12, 2025.
    // @Modified by JetBrains AI Assistant as of Mar 13, 2025.
    inline fun <reified T : ViewBinding> Fragment.viewBinding(crossinline bindView: (View) -> T) = object : ReadOnlyProperty<Fragment, T> {

        private var binding: T? = null

        override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
            return binding ?: bindView(requireView()).also {
                binding = it

                viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        binding = null
                    }
                })
            }
        }
    }

}