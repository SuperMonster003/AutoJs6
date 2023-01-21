package org.autojs.autojs.runtime.api

import android.util.Log
import org.autojs.autojs.runtime.exception.ScriptException

/**
 * Created by Stardust on 2017/5/1.
 */
abstract class AbstractConsole : Console {

    fun printf(level: Int, data: Any?, vararg options: Any?) {
        println(level, format(data, *options))
    }

    fun format(data: Any?, vararg options: Any?): CharSequence {
        data ?: return "\n"
        return when (options.isEmpty()) {
            true -> data.toString()
            else -> String.format(data.toString(), *options)
        }
    }

    protected abstract fun write(level: Int, data: CharSequence?)

    override fun print(level: Int, data: Any?, vararg options: Any?) {
        write(level, format(data, *options))
    }

    override fun verbose(data: Any?, vararg options: Any?) {
        printf(Log.VERBOSE, data, *options)
    }

    override fun log(data: Any?, vararg options: Any?) {
        printf(Log.DEBUG, data, *options)
    }

    override fun info(data: Any?, vararg options: Any?) {
        printf(Log.INFO, data, *options)
    }

    override fun warn(data: Any?, vararg options: Any?) {
        printf(Log.WARN, data, *options)
    }

    override fun error(data: Any?, vararg options: Any?) {
        printf(Log.ERROR, data, *options)
    }

    override fun assertTrue(value: Boolean, data: Any?, vararg options: Any?) {
        if (!value) {
            printf(Log.ASSERT, data, *options)
            throw ScriptException(AssertionError(format(data, *options)))
        }
    }

}