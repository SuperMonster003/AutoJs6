package org.autojs.autojs.ui.main

import androidx.annotation.CallSuper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.view.View

import com.stardust.util.BackPressedHandler

import org.autojs.autojs.ui.BaseFragment

/**
 * Created by Stardust on 2017/8/22.
 */

abstract class ViewPagerFragment(private val mFabRotation: Int) : BaseFragment(), BackPressedHandler {
    private var mFab: FloatingActionButton? = null
    var isShown: Boolean = false
        private set
    private val mOnFabClickListener = { v -> onFabClick(v as FloatingActionButton) }

    fun setFab(fab: FloatingActionButton) {
        mFab = fab
    }

    protected abstract fun onFabClick(fab: FloatingActionButton)

    @CallSuper
    open fun onPageShow() {
        isShown = true
        if (mFabRotation == ROTATION_GONE) {
            if (mFab!!.visibility == View.VISIBLE) {
                mFab!!.hide()
            }
            mFab!!.setOnClickListener(null)
            return
        }
        mFab!!.setOnClickListener(mOnFabClickListener)
        if (mFab!!.visibility != View.VISIBLE) {
            mFab!!.rotation = mFabRotation.toFloat()
            mFab!!.show()
        } else if (Math.abs(mFab!!.rotation - mFabRotation) > 0.1f) {
            mFab!!.animate()
                    .rotation(mFabRotation.toFloat())
                    .setDuration(300)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
        }
    }


    @CallSuper
    open fun onPageHide() {
        isShown = false
    }

    companion object {

        protected val ROTATION_GONE = -1
    }
}
