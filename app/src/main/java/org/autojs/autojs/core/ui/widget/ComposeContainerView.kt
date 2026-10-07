package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import org.autojs.autojs.core.ui.JsViewHelper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import java.lang.ref.WeakReference

/** An XML widget boundary; its sole child belongs to a Compose session, never to the inflater. */
class ComposeContainerView(context: Context, runtime: ScriptRuntime) : FrameLayout(context) {
    private val owner = WeakReference(runtime)

    init {
        if (Looper.myLooper() != Looper.getMainLooper()) throw ComposeUiContractException(
            ComposeUiErrorCodes.INVALID_ARGUMENT, "Compose containers must be inflated on the main thread")
    }

    internal fun belongsTo(runtime: ScriptRuntime) = owner.get() === runtime
    fun id(id: String?) = JsViewHelper.findViewByStringId(this, id)
}
