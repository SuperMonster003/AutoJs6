package org.autojs.autojs.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Created by Stardust on Mar 10, 2017.
 */
object ClipboardUtils {

    private fun getManager(context: Context) = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @JvmStatic
    fun setClip(context: Context, text: CharSequence?) = getManager(context).setPrimaryClip(ClipData.newPlainText("", text))

    @JvmStatic
    fun getClip(context: Context) = getManager(context).primaryClip?.getItemAt(0)?.text

    @JvmStatic
    fun getClipOrEmpty(context: Context) = getClip(context) ?: ""

}