package org.autojs.autojs.app.tool

import android.content.Context
import android.content.DialogInterface
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import org.autojs.autojs.AutoJs
import org.autojs.autojs.ui.main.drawer.SocketItemHelper

/**
 * Created by SuperMonster003 on Jun 24, 2022.
 * Modified by SuperMonster003 as of Jan 17, 2026.
 */
abstract class AbstractJsonSocketTool(final override val context: Context) : SocketItemHelper {

    protected val devPlugin by lazy { AutoJs.instance.devPluginService }

    @JvmField
    protected var stateDisposable: Disposable? = null

    @JvmField
    protected var onConnectionException: Consumer<in Throwable> = Consumer { }

    @JvmField
    protected var onConnectionDialogDismissed = DialogInterface.OnDismissListener { }

    internal abstract fun connectIfNotNormallyClosed()

    fun setOnConnectionException(consumer: Consumer<in Throwable>) {
        onConnectionException = consumer
    }

    fun setStateDisposable(disposable: Disposable?) {
        stateDisposable = disposable
    }

    fun setOnConnectionDialogDismissed(dialog: DialogInterface.OnDismissListener) {
        onConnectionDialogDismissed = dialog
    }

}