package org.autojs.autojs.core.console

import org.autojs.autojs.runtime.api.Console

/**
 * Created by Stardust on 2017/9/21.
 */
class NoOpConsole : Console {

    override fun verbose(data: Any?, vararg options: Any?) {}
    override fun log(data: Any?, vararg options: Any?) {}
    override fun print(level: Int, data: Any?, vararg options: Any?) {}
    override fun info(data: Any?, vararg options: Any?) {}
    override fun warn(data: Any?, vararg options: Any?) {}
    override fun error(data: Any?, vararg options: Any?) {}
    override fun assertTrue(value: Boolean, data: Any?, vararg options: Any?) {}
    override fun clear() {}
    override fun show() {}
    override fun hide() {}
    override fun println(level: Int, charSequence: CharSequence) = ""
    override fun setTitle(title: CharSequence) {}
    override fun setSize(w: Int, h: Int) {}
    override fun setPosition(x: Int, y: Int) {}
    override fun rawInput() = null

}