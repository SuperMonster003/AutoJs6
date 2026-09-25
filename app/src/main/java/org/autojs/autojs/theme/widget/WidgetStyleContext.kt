package org.autojs.autojs.theme.widget

import android.content.Context
import android.util.TypedValue
import androidx.appcompat.view.ContextThemeWrapper

/** Preserve the four-argument framework constructor's fallback style with AppCompat widgets. */
internal fun Context.withWidgetFallbackStyle(defStyleAttr: Int, defStyleRes: Int): Context {
    if (defStyleRes == 0) return this
    val style = TypedValue()
    if (defStyleAttr != 0 && theme.resolveAttribute(defStyleAttr, style, true) && style.resourceId != 0) {
        return this
    }
    return ContextThemeWrapper(this, defStyleRes)
}
