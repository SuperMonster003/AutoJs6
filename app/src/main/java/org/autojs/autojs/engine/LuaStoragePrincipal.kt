package org.autojs.autojs.engine

import org.autojs.autojs.script.LuaFileSource
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.plugin.lua.runtime.api.LuaSha256
import java.io.File

/**
 * Host-private stable identity for one file-backed Lua script.
 *
 * The canonical source identity is domain-separated and hashed before it reaches persistence.
 * Neither the raw path/URI nor this opaque digest crosses Binder or becomes script-visible.
 * Android package and user/profile isolation are supplied by the Host application's private data
 * directory, so the digest only needs to distinguish logical scripts inside that boundary.
 */
internal class LuaStoragePrincipal private constructor(
    val digest: String,
) {
    override fun equals(other: Any?): Boolean =
        other is LuaStoragePrincipal && digest == other.digest

    override fun hashCode(): Int = digest.hashCode()

    override fun toString(): String = "LuaStoragePrincipal(digest=$digest)"

    companion object {
        fun fromSource(source: LuaScriptSource): LuaStoragePrincipal? {
            val fileSource = source as? LuaFileSource ?: return null
            val identity = trustedSemanticPath(source)
                ?.let { path -> "path\u0000$path" }
                ?: source.originUri
                    ?.takeIf(String::isNotBlank)
                    ?.let { uri -> "uri\u0000$uri" }
                ?: trustedFilePath(fileSource.file)
                    ?.let { path -> "path\u0000$path" }
                ?: return null
            return fromStableIdentity(identity)
        }

        internal fun fromStableIdentity(identity: String): LuaStoragePrincipal {
            require(identity.isNotBlank()) { "Lua storage identity must not be blank" }
            return LuaStoragePrincipal(
                LuaSha256.digest(identity.toByteArray(Charsets.UTF_8)).toHexString(),
            )
        }

        private fun trustedSemanticPath(source: LuaScriptSource): String? {
            val raw = source.originPath ?: return null
            return trustedFilePath(File(raw))
        }

        private fun trustedFilePath(file: File): String? {
            if (!file.isAbsolute || !LuaScriptSource.isLuaFileName(file.name)) return null
            return runCatching { file.canonicalFile }
                .getOrNull()
                ?.takeIf(File::isFile)
                ?.path
        }
    }
}
