package org.autojs.plugin.typescript.api

object TypeScriptPluginIds {
    const val ID = "typescript"
    const val ENGINE = "typescript"
    const val VARIANT_PREFIX = "typescript_"

    fun variant(compilerVersion: String): String =
        VARIANT_PREFIX + compilerVersion.replace('.', '_')
}
