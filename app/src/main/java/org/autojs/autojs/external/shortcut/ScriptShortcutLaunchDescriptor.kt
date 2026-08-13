package org.autojs.autojs.external.shortcut

import org.autojs.autojs.script.LuaScriptSource

/** Language identity persisted by newly-created Lua script shortcuts. */
internal data class ScriptShortcutLaunchDescriptor(
    val schema: Int,
    val engine: String,
    val sourceKind: String,
) {

    enum class Evaluation {
        LEGACY,
        ACCEPTED,
        REJECTED,
    }

    companion object {

        const val SCHEMA_V1 = 1
        const val ENGINE_LUA = LuaScriptSource.ENGINE
        const val SOURCE_KIND_FILE = "file"

        /** Returns a V1 descriptor only for Lua files; other shortcut kinds remain path-only. */
        @JvmStatic
        fun forPath(path: String?): ScriptShortcutLaunchDescriptor? {
            return if (isLuaPath(path)) {
                ScriptShortcutLaunchDescriptor(
                    schema = SCHEMA_V1,
                    engine = ENGINE_LUA,
                    sourceKind = SOURCE_KIND_FILE,
                )
            } else {
                null
            }
        }

        /**
         * Evaluates raw persisted fields without depending on Android Intent or Bundle classes.
         * A fully absent descriptor preserves legacy extension-based shortcut behavior. Once any
         * descriptor extra is present, every field and the resolved launch engine must match V1.
         */
        @JvmStatic
        fun evaluate(
            descriptorPresent: Boolean,
            schema: Any?,
            engine: Any?,
            sourceKind: Any?,
            path: String?,
            actualEngine: String?,
        ): Evaluation {
            val anyFieldPresent = schema != null || engine != null || sourceKind != null
            if (!descriptorPresent && !anyFieldPresent) {
                return Evaluation.LEGACY
            }
            if (!descriptorPresent ||
                schema !is Int || schema != SCHEMA_V1 ||
                engine !is String || engine != ENGINE_LUA ||
                sourceKind !is String || sourceKind != SOURCE_KIND_FILE ||
                !isLuaPath(path) || actualEngine != ENGINE_LUA
            ) {
                return Evaluation.REJECTED
            }
            return Evaluation.ACCEPTED
        }

        private fun isLuaPath(path: String?): Boolean {
            return LuaScriptSource.isLuaFileName(path)
        }
    }
}
