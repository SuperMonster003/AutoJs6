package org.autojs.autojs.app.tool

import android.content.Context
import android.content.DialogInterface
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import org.autojs.autojs.App
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.ui.main.drawer.SocketItemHelper

/**
 * Created by SuperMonster003 on Jun 24, 2022.
 */
abstract class AbstractJsonSocketTool(final override val context: Context) : SocketItemHelper {

    protected val devPlugin by lazy { App.app.devPluginService }

    @JvmField
    protected var onConnectionException: Consumer<in Throwable> = Consumer { }

    @JvmField
    protected var stateDisposable: Disposable? = null

    @JvmField
    protected var onConnectionDialogDismissed = DialogInterface.OnDismissListener { }

    fun setOnConnectionException(consumer: Consumer<in Throwable>) = also { onConnectionException = consumer }

    fun setStateDisposable(disposable: Disposable?) = also { stateDisposable = disposable }

    fun setOnConnectionDialogDismissed(dialog: DialogInterface.OnDismissListener) = also { onConnectionDialogDismissed = dialog }

}