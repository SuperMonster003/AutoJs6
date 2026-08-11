package org.autojs.autojs.core.plugin.lua

import android.content.Context
import org.autojs.autojs6.R

/** Localized Android facade over the Android-free Lua runtime failure classification. */
object AndroidLuaRuntimeFailureMessages {

    @JvmStatic
    fun resolve(context: Context, failure: Throwable): String? =
        LuaRuntimeFailurePresentation.messageOrOriginal(failure) { presentation ->
            context.getString(
                when (presentation) {
                    LuaRuntimeFailurePresentation.UNAVAILABLE -> R.string.error_lua_runtime_unavailable
                    LuaRuntimeFailurePresentation.COMPATIBILITY -> R.string.error_lua_runtime_incompatible
                    LuaRuntimeFailurePresentation.AMBIGUOUS -> R.string.error_lua_runtime_ambiguous
                    LuaRuntimeFailurePresentation.IDENTITY_OR_BIND -> R.string.error_lua_runtime_identity_or_bind
                },
            )
        }
}
