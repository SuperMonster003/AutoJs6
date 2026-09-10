package org.autojs.autojs.ui.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.annotation.MainThread
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.autojs.autojs.util.DialogUtils.makeTextCopyable
import org.autojs.autojs.util.DialogUtils.showAdaptive

/** Shared presentation and lifetime of a single information dialog. Create a new instance for each show. */
@MainThread
class InfoDialog(context: Context) {

    val items = InfoDialogItems(context)
    private val infoBuilder = object : MaterialDialog.Builder(context) {
        val dismissCallback: DialogInterface.OnDismissListener? get() = dismissListener
    }
    val builder: MaterialDialog.Builder = infoBuilder.customView(items, true)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var dialog: MaterialDialog
        private set

    @JvmOverloads
    fun show(onDismissListener: DialogInterface.OnDismissListener? = null, copyTitle: Boolean = true): MaterialDialog {
        check(!::dialog.isInitialized) { "An InfoDialog can only be shown once" }
        val builderDismissListener = infoBuilder.dismissCallback
        try {
            dialog = builder.build().apply {
                this@InfoDialog.items.bindTo(this)
                if (copyTitle) makeTextCopyable { titleView }
                setOnDismissListener {
                    scope.cancel()
                    builderDismissListener?.onDismiss(this)
                    if (onDismissListener !== builderDismissListener) onDismissListener?.onDismiss(this)
                }
            }
            return dialog.showAdaptive()
        } catch (error: Throwable) {
            scope.cancel()
            throw error
        }
    }
}
