package org.autojs.autojs.script

import java.io.Reader

/**
 * An in-memory Lua entry, e.g. a `.lua` file sent by the VSCode extension.
 * zh-CN: 内存中的 Lua 脚本, 例如 VSCode 插件发送的 `.lua` 文件.
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
class LuaStringSource(name: String, override val script: String) : LuaScriptSource(name) {

    override val scriptReader: Reader?
        get() = null

    override fun toString(): String {
        return originPath ?: originUri ?: (prefix.orEmpty() + name)
    }

}
