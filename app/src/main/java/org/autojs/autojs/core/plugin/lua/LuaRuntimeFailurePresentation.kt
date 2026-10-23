package org.autojs.autojs.core.plugin.lua

import java.util.Collections
import java.util.IdentityHashMap

/** Android-free classification of host-side Lua runtime failures for user-facing surfaces. */
internal enum class LuaRuntimeFailurePresentation {
    UNAVAILABLE,
    COMPATIBILITY,
    AMBIGUOUS,
    IDENTITY_OR_BIND,
    ;

    companion object {
        fun from(failure: Throwable): LuaRuntimeFailurePresentation? {
            val hostFailure = failure.findLuaRuntimeHostFailure() ?: return null
            return when (hostFailure.code) {
                LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE -> when {
                    hostFailure.evaluations.any { it.rejection in COMPATIBILITY_REJECTIONS } -> COMPATIBILITY
                    hostFailure.evaluations.any { it.rejection in IDENTITY_OR_BIND_REJECTIONS } -> IDENTITY_OR_BIND
                    else -> UNAVAILABLE
                }
                LuaRuntimeHostFailureCode.LUA_RUNTIME_AMBIGUOUS -> AMBIGUOUS
                LuaRuntimeHostFailureCode.LUA_RUNTIME_IDENTITY_CHANGED,
                LuaRuntimeHostFailureCode.LUA_RUNTIME_BIND_FAILED,
                -> IDENTITY_OR_BIND
            }
        }

        fun messageOrOriginal(
            failure: Throwable,
            render: (LuaRuntimeFailurePresentation) -> String,
        ): String? = from(failure)?.let(render) ?: failure.message

        private val COMPATIBILITY_REJECTIONS = setOf(
            LuaRuntimeProviderRejection.INFO_PACKAGE_VERSION_MISMATCH,
            LuaRuntimeProviderRejection.INFO_ABI_INVENTORY_INVALID,
            LuaRuntimeProviderRejection.INFO_RUNTIME_VERSION_MISMATCH,
            LuaRuntimeProviderRejection.INFO_RUNTIME_ABI_MISMATCH,
            LuaRuntimeProviderRejection.INFO_ABI_UNSUPPORTED,
            LuaRuntimeProviderRejection.INVALID_RUNTIME_INFO,
            LuaRuntimeProviderRejection.HOST_VERSION_UNSUPPORTED,
            LuaRuntimeProviderRejection.PROTOCOL_UNSUPPORTED,
            LuaRuntimeProviderRejection.RUNTIME_SLOT_UNSUPPORTED,
            LuaRuntimeProviderRejection.ABI_UNSUPPORTED,
            LuaRuntimeProviderRejection.CAPABILITY_MISMATCH,
        )

        private val IDENTITY_OR_BIND_REJECTIONS = setOf(
            LuaRuntimeProviderRejection.BIND_FAILED,
            LuaRuntimeProviderRejection.BOUND_COMPONENT_MISMATCH,
        )
    }
}

private fun Throwable.findLuaRuntimeHostFailure(): LuaRuntimeHostException? {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is LuaRuntimeHostException) return current
        current = current.cause
    }
    return null
}
