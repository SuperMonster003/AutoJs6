package org.autojs.autojs.core.looper

import android.os.Looper

/**
 * Created by SuperMonster003 on Dec 19, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
object LooperThread {

    fun getLooper(thread: Thread) = getLooperOrNull(thread) ?: throw IllegalStateException("no looper for thread: $thread")

    fun getLooperOrNull(thread: Thread) = when {
        isEqual(thread, Looper.getMainLooper().thread) -> Looper.getMainLooper()
        thread is ILooperThread -> (thread as ILooperThread).looper
        else -> null
    }

    fun isEqual(oA: Any?, oB: Any?) = if (oA == null) oB == null else oA == oB

}
