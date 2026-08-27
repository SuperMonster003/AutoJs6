package org.autojs.autojs.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

internal fun interface LuaToastSink {
    /** Returns true only when the Host-owned UI lane accepted this message for enqueueing. */
    fun enqueue(text: String): Boolean

    data object Unavailable : LuaToastSink {
        override fun enqueue(text: String): Boolean = false
    }
}

internal class AndroidLuaToastSink(context: Context) : LuaToastSink {
    private val applicationContext = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun enqueue(text: String): Boolean = mainHandler.post {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }
}
