package org.autojs.autojs.script

import java.io.Reader
import java.io.StringReader
import java.util.Locale

abstract class LuaScriptSource(name: String) : ScriptSource(name, EXTENSION) {

    abstract val script: String

    abstract val scriptReader: Reader?

    val nonNullScriptReader: Reader
        get() = scriptReader ?: StringReader(script)

    final override val engineName: String
        get() = ENGINE

    companion object {
        const val ENGINE = "lua"
        const val EXTENSION = "lua"

        @JvmStatic
        fun isLuaFileName(fileName: String?): Boolean {
            return fileName?.lowercase(Locale.ROOT)?.endsWith(".$EXTENSION") == true
        }
    }
}
