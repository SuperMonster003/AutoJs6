package org.autojs.autojs.util

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * Created by Stardust on 2017/12/9.
 */
object InputMethodUtils {

    fun dismissInputMethod(context: Context, view: View) {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

}