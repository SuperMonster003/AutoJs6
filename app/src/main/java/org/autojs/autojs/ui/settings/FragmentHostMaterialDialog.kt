@file:Suppress("OVERRIDE_DEPRECATION")

package org.autojs.autojs.ui.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.afollestad.materialdialogs.MaterialDialog

internal class FragmentHostMaterialDialog private constructor(
    builder: Builder,
) : MaterialDialog(builder) {

    override fun setContentView(layoutResID: Int) {
        setContentView(LayoutInflater.from(context).inflate(layoutResID, null))
    }

    override fun setContentView(view: View) {
        attachFragmentView(view, view.layoutParams)
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
        attachFragmentView(view, params)
    }

    private fun attachFragmentView(view: View, params: ViewGroup.LayoutParams?) {
        val host = getCustomView() as? ViewGroup
            ?: getView().findViewById<ViewGroup>(com.afollestad.materialdialogs.R.id.md_customViewFrame)
            ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        host.removeAllViews()
        host.addView(
            view,
            params ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    class Builder(context: Context) : MaterialDialog.Builder(context) {

        init {
            customView(
                FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                },
                false,
            )
        }

        override fun build(): MaterialDialog = FragmentHostMaterialDialog(this)
    }
}
