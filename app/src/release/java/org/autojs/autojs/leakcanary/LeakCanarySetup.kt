package org.autojs.autojs.leakcanary

import android.app.Application
import android.content.Context
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.runtime.memory.LeakCanaryStatus

object LeakCanarySetup {

    fun setup(@Suppress("UNUSED_PARAMETER") application: Application) {
        println("${LeakCanarySetup::class.java.simpleName}: LeakCanary won't be included and set up in \"release\" build variant")
    }

    @JvmStatic
    fun apply(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit

    @JvmStatic
    fun watch(@Suppress("UNUSED_PARAMETER") watchedObject: Any, @Suppress("UNUSED_PARAMETER") description: String) = Unit

    @JvmStatic
    fun watchEngine(@Suppress("UNUSED_PARAMETER") engine: ScriptEngine<*>) = Unit

    @JvmStatic
    fun status(@Suppress("UNUSED_PARAMETER") context: Context): LeakCanaryStatus? = null

}
