package org.autojs.autojs.inrt.autojs

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.mlkit.common.MlKit
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.inrt.LogActivity
import org.autojs.autojs.inrt.SettingsActivity
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.AutoJs as AutoJsApp

/**
 * Created by Stardust on Apr 2, 2017.
 */
class AutoJs(application: Application) : AutoJsApp(application) {

    init {
        initMLKit(application)
    }

    override fun createAppUtils(context: Context) = AppUtils(context, AppFileProvider.AUTHORITY)

    override fun createRuntime() = super.createRuntime().apply {
        putProperty("class.settings", SettingsActivity::class.java)
        putProperty("class.console", LogActivity::class.java)
    }

    private fun initMLKit(application: Application) {
        // @Hint by SuperMonster003 on Nov 26, 2023.
        //  ! To avoid the exception as below (only happened on packaged apps).
        //  # IllegalStateException: MlKitContext has not been initialized.
        //  ! zh-CN:
        //  ! 用于避免以下异常 (仅发生在打包应用中).
        //  # IllegalStateException: MlKitContext 未初始化.
        MlKit.initialize(application)
        Log.d(TAG, "MLKit initialized")
    }

    companion object {

        private const val TAG = "AutoJsInrt"

        val instance
            get() = AutoJsApp.instance

    }

}
