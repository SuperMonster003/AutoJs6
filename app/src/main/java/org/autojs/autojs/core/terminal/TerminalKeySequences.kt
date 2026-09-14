package org.autojs.autojs.core.terminal

/**
 * Byte sequences for the keys of the terminal toolbar and the Ctrl / Alt modifiers.
 *
 * The sequences follow xterm: cursor keys switch between normal (`ESC [ A`) and application
 * (`ESC O A`) mode, editing keys use the `ESC [ n ~` form, Ctrl folds a character into the
 * C0 range and Alt prefixes it with ESC.
 *
 * zh-CN: 终端工具栏按键与 Ctrl / Alt 修饰键对应的字节序列. 序列遵循 xterm 约定.
 */
object TerminalKeySequences {

    val ESC: String = Char(27).toString()
    val TAB: String = "\t"
    val ENTER: String = "\r"
    val BACKSPACE: String = Char(127).toString()
    val DELETE: String = "$ESC[3~"
    val INSERT: String = "$ESC[2~"
    val HOME: String = "$ESC[H"
    val END: String = "$ESC[F"
    val PAGE_UP: String = "$ESC[5~"
    val PAGE_DOWN: String = "$ESC[6~"

    enum class Key(val label: String) {
        ESC("Esc"),
        TAB("Tab"),
        UP("↑"),
        DOWN("↓"),
        LEFT("←"),
        RIGHT("→"),
        HOME("Home"),
        END("End"),
        PAGE_UP("PgUp"),
        PAGE_DOWN("PgDn"),
        DELETE("Del"),
    }

    /**
     * @param applicationCursorKeys true when the emulator is in DECCKM application mode
     */
    @JvmStatic
    @JvmOverloads
    fun sequence(key: Key, applicationCursorKeys: Boolean = false): String = when (key) {
        Key.ESC -> ESC
        Key.TAB -> TAB
        Key.UP -> cursor('A', applicationCursorKeys)
        Key.DOWN -> cursor('B', applicationCursorKeys)
        Key.RIGHT -> cursor('C', applicationCursorKeys)
        Key.LEFT -> cursor('D', applicationCursorKeys)
        Key.HOME -> HOME
        Key.END -> END
        Key.PAGE_UP -> PAGE_UP
        Key.PAGE_DOWN -> PAGE_DOWN
        Key.DELETE -> DELETE
    }

    private fun cursor(final: Char, application: Boolean) = if (application) "${ESC}O$final" else "$ESC[$final"

    /**
     * Maps a character to its control code, or null when the character has no control form.
     * zh-CN: 将字符映射为其控制码, 无控制形式时返回 null.
     */
    @JvmStatic
    fun control(char: Char): Char? = when (char) {
        in 'a'..'z' -> Char(char.code - 'a'.code + 1)
        in 'A'..'Z' -> Char(char.code - 'A'.code + 1)
        '@', ' ' -> Char(0)
        '[' -> Char(27)
        '\\' -> Char(28)
        ']' -> Char(29)
        '^' -> Char(30)
        '_' -> Char(31)
        '?' -> Char(127)
        else -> null
    }

    /**
     * Applies sticky modifiers to a chunk of keyboard input. Ctrl only folds single printable
     * characters; Alt prefixes the whole chunk with ESC. Unchanged input is returned as-is.
     * zh-CN: 对一段键盘输入应用粘滞修饰键. Ctrl 仅折叠单个可打印字符; Alt 为整段输入加 ESC 前缀.
     */
    @JvmStatic
    fun applyModifiers(input: String, ctrl: Boolean, alt: Boolean): String {
        var result = input
        if (ctrl && input.length == 1) {
            control(input[0])?.let { result = it.toString() }
        }
        if (alt && result.isNotEmpty()) {
            result = ESC + result
        }
        return result
    }

}
