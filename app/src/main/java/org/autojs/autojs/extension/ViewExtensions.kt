package org.autojs.autojs.extension

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout

/**
 * Created by SuperMonster003 on May 7, 2025.
 */
object ViewExtensions {

    fun PopupMenu.setForceShowIconCompat() {
        val popup = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true)
        } else {
            runCatching {
                @SuppressLint("DiscouragedPrivateApi")
                val field = PopupMenu::class.java.getDeclaredField("mPopup")
                field.isAccessible = true
                // MenuPopupHelper
                val helper = field.get(popup)
                val method = helper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                method.invoke(helper, true)
            }
        }
    }

    val Toolbar.titleView: TextView?
        get() = this.findViewById(com.google.android.material.R.id.action_bar_title)
            ?: Toolbar::class.java.getDeclaredField("mTitleTextView").apply { isAccessible = true }.get(this@titleView) as TextView?

    val Toolbar.subtitleView: TextView?
        get() = this.findViewById(com.google.android.material.R.id.action_bar_subtitle)
            ?: Toolbar::class.java.getDeclaredField("mSubtitleTextView").apply { isAccessible = true }.get(this@subtitleView) as TextView?

    @JvmStatic
    fun Toolbar.setOnTitleViewClickListener(l: View.OnClickListener?) {
        this.setOnTitleViewClickListener(l, withFallback = true)
    }

    @JvmStatic
    fun Toolbar.setOnTitleViewClickListener(l: View.OnClickListener?, withFallback: Boolean) {
        this.onceGlobalLayout {
            this.titleView?.setOnClickListener(l) ?: run { if (withFallback) this.setOnClickListener(l) }
        }
    }

    @JvmStatic
    fun Toolbar.setOnSubtitleViewClickListener(l: View.OnClickListener?) {
        this.onceGlobalLayout {
            this.subtitleView?.setOnClickListener(l)
        }
    }

    @JvmStatic
    fun Toolbar.setOnTitleViewLongClickListener(l: View.OnLongClickListener?) {
        this.setOnTitleViewLongClickListener(l, withFallback = true)
    }

    @JvmStatic
    fun Toolbar.setOnTitleViewLongClickListener(l: View.OnLongClickListener?, withFallback: Boolean) {
        this.onceGlobalLayout {
            this.titleView?.setOnLongClickListener(l) ?: run { if (withFallback) this.setOnLongClickListener(l) }
        }
    }

}
