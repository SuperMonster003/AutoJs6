package org.autojs.autojs.core.terminal

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R

/**
 * Persisted terminal settings. zh-CN: 持久化的终端设置.
 */
object TerminalPreferences {

    const val DEFAULT_TEXT_SIZE_SP = 12
    const val MIN_TEXT_SIZE_SP = 6
    const val MAX_TEXT_SIZE_SP = 40

    var textSizeSp: Int
        get() = Pref.getInt(R.string.key_terminal_text_size, DEFAULT_TEXT_SIZE_SP).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        set(value) = Pref.putInt(R.string.key_terminal_text_size, value.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP))

}
