package org.autojs.autojs.leakcanary

import android.app.Application

object LeakCanarySetup {

    fun setup(application: Application) {
        println("${LeakCanarySetup::class.java.simpleName}: LeakCanary won't be included and set up in \"release\" build variant")
    }

}