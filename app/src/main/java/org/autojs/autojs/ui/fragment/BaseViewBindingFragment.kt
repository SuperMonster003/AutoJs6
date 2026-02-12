package org.autojs.autojs.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseViewBindingFragment<T : ViewBinding?> : Fragment() {

    @JvmField
    protected var binding: T? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = createBinding(inflater, container).also {
            this.binding = it
        }
        return getRootView(binding)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    protected abstract fun createBinding(inflater: LayoutInflater, container: ViewGroup?): T

    protected fun getRootView(binding: T): View {
        return binding!!.root
    }
}
