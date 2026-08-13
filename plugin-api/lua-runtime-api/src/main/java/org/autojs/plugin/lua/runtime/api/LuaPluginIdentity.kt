package org.autojs.plugin.lua.runtime.api

import org.autojs.plugin.common.api.PluginActions

/** Stable plugin metadata identities. These are not ScriptEngine class names. */
object LuaPluginIds {
    const val ID = "lua-runtime"
    const val ENGINE = "lua"
    const val VARIANT_PUC_LUA_5_4 = "puc-lua54"
}

object LuaPluginActions {
    const val INFO = PluginActions.INFO
    const val RUNTIME = "org.autojs.plugin.lua.RUNTIME"
}
