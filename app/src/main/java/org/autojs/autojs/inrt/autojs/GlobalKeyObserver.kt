package org.autojs.autojs.inrt.autojs

import android.util.Log
import android.view.KeyEvent

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.inrt.Pref
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.OnKeyListener
import org.autojs.autojs.core.inputevent.InputEventObserver
import org.autojs.autojs.core.inputevent.ShellKeyObserver


/**
 * Created by Stardust on 2017/8/14.
 */
class GlobalKeyObserver internal constructor() : OnKeyListener, ShellKeyObserver.KeyListener {
    private var mVolumeDownFromShell: Boolean = false
    private var mVolumeDownFromAccessibility: Boolean = false
    private var mVolumeUpFromShell: Boolean = false
    private var mVolumeUpFromAccessibility: Boolean = false

    init {
        AccessibilityService.stickOnKeyObserver
            .addListener(this)
        val observer = ShellKeyObserver()
        observer.setKeyListener(this)
        InputEventObserver.getGlobal(GlobalAppContext.get()).addListener(observer)
    }

    fun onVolumeUp() {
        Log.d(LOG_TAG, "onVolumeUp at " + System.currentTimeMillis())
        if (Pref.shouldStopAllScriptsWhenVolumeUp()) {
            AutoJs.instance.scriptEngineService.stopAllAndToast()
        }
    }

    override fun onKeyEvent(keyCode: Int, event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_UP)
            return
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (mVolumeDownFromShell) {
                mVolumeDownFromShell = false
                return
            }
            mVolumeUpFromAccessibility = true
            onVolumeDown()
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (mVolumeUpFromShell) {
                mVolumeUpFromShell = false
                return
            }
            mVolumeUpFromAccessibility = true
            onVolumeUp()
        }
    }

    fun onVolumeDown() {

    }


    override fun onKeyDown(keyName: String) {

    }

    override fun onKeyUp(keyName: String) {
        if ("KEY_VOLUMEUP" == keyName) {
            if (mVolumeUpFromAccessibility) {
                mVolumeUpFromAccessibility = false
                return
            }
            mVolumeUpFromShell = true
            onVolumeUp()
        } else if ("KEY_VOLUMEDOWN" == keyName) {
            if (mVolumeDownFromAccessibility) {
                mVolumeDownFromAccessibility = false
                return
            }
            mVolumeDownFromShell = true
            onVolumeDown()
        }
    }

    companion object {
        private const val LOG_TAG = "GlobalKeyObserver"
        private val sSingleton = GlobalKeyObserver()

        fun init() {
            //do nothing
        }
    }
}
