package org.autojs.autojs.core.accessibility

import android.view.KeyEvent

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Created by Stardust on Feb 27, 2018.
 */
interface KeyInterceptor {

    fun onInterceptKeyEvent(event: KeyEvent): Boolean

    class Observer : KeyInterceptor {
        private val mKeyInterceptors = CopyOnWriteArrayList<KeyInterceptor>()

        fun addKeyInterrupter(interrupter: KeyInterceptor) {
            mKeyInterceptors.add(interrupter)
        }

        fun removeKeyInterrupter(interrupter: KeyInterceptor): Boolean {
            return mKeyInterceptors.remove(interrupter)
        }


        override fun onInterceptKeyEvent(event: KeyEvent): Boolean {
            for (interrupter in mKeyInterceptors) {
                try {
                    if (interrupter.onInterceptKeyEvent(event)) {
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
            return false
        }

    }

}
