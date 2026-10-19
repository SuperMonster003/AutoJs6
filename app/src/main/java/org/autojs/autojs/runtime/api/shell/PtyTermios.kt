package org.autojs.autojs.runtime.api.shell

import android.util.Log
import androidx.annotation.Keep

/**
 * Terminal settings of a [PtyProcess], through `libautojs6_pty`: Android's own `stty` cannot change them on Android 9
 * and below (toybox 0.7.x), so they are set natively on the master side.
 * zh-CN: [PtyProcess] 的终端设置, 经 `libautojs6_pty` 完成: Android 9 及以下自带的 `stty` (toybox 0.7.x) 无法修改它们, 因此在主端以原生方式设置.
 */
@Keep
internal object PtyTermios {

    private const val TAG = "PtyTermios"

    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("autojs6_pty")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "libautojs6_pty is not available: ${e.message}")
            false
        }
    }

    /** No echo, no carriage return before line feeds; false if it could not be set. zh-CN: 无回显, 换行前不加回车; 无法设置时返回 false. */
    fun applyPipeLike(fd: Int): Boolean {
        if (!loaded) return false
        val errno = setPipeLike(fd)
        if (errno != 0) Log.w(TAG, "tcsetattr failed: errno $errno")
        return errno == 0
    }

    @JvmStatic
    private external fun setPipeLike(fd: Int): Int

}
